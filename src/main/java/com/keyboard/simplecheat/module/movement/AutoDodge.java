package com.keyboard.simplecheat.module.movement;

import com.keyboard.simplecheat.module.Category;
import com.keyboard.simplecheat.module.Module;
import com.keyboard.simplecheat.module.setting.BooleanSetting;
import com.keyboard.simplecheat.module.setting.NumberSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.LlamaSpitEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 预测投射物轨迹并横向走位躲开。
 * <p>
 * 箭射出之后的运动没有任何随机成分（每 tick 速度乘 0.99、纵向减 0.05），
 * 所以可以在客户端精确推演，判断它会不会穿过玩家的碰撞箱。
 */
public class AutoDodge extends Module {
    /** 命中判定会把目标碰撞箱外扩这么多，见 ProjectileUtil#getEntityCollision 的 margin 参数。 */
    private static final double HIT_MARGIN = 0.3;
    private static final double AIR_DRAG = 0.99;
    private static final double GRAVITY = 0.05;
    /** 安全检查沿路径的采样间隔，比一格小才不会漏掉窄坑。 */
    private static final double PATH_SAMPLE_STEP = 0.5;
    private static final double MAX_PATH_CHECK = 4.0;

    private final NumberSetting reactTicks = new NumberSetting("react_ticks", "提前反应时间",
            "预计命中还剩多少 tick 时开始躲，太大会频繁乱走", 20.0, 5.0, 60.0, 1.0);
    private final BooleanSetting fullDodgeOnly = new BooleanSetting("full_dodge_only", "只躲得干净才躲",
            "算不出能全部躲开就干脆不动，避免白走位还是挨打", true);
    private final BooleanSetting sprint = new BooleanSetting("sprint", "躲避时冲刺",
            "移动更快更容易躲开，但会消耗饥饿值", true);
    private final BooleanSetting safetyCheck = new BooleanSetting("safety_check", "安全检查",
            "不往悬崖、岩浆、仙人掌等危险方向躲", true);
    private final NumberSetting maxFallDistance = new NumberSetting("max_fall", "最大允许落差",
            "躲避路上最多允许下落几格，超过 3 格开始摔血", 3.0, 0.0, 8.0, 1.0);
    private final BooleanSetting allowBackward = new BooleanSetting("allow_backward", "允许前后闪避",
            "横向躲不开时尝试前后移动", true);
    private final BooleanSetting onlyOnGround = new BooleanSetting("only_on_ground", "仅在地面上",
            "空中横移效果差且容易摔下去", true);
    private final BooleanSetting onlyHarmful = new BooleanSetting("only_harmful", "只躲有伤害的",
            "忽略鱼钩、雪球、鸡蛋等打不掉血的投射物", true);

    private final Set<KeyBinding> overriddenKeys = new HashSet<>();
    private Vec3d activeDodge;
    private int incomingCount;

    public AutoDodge() {
        super("auto_dodge", "自动闪避", "预测投射物落点并走位躲开", Category.MOVEMENT, GLFW.GLFW_KEY_V);
        addSettings(reactTicks, fullDodgeOnly, sprint, safetyCheck, maxFallDistance,
                allowBackward, onlyOnGround, onlyHarmful);
    }

    public boolean isDodging() {
        return activeDodge != null;
    }

    public int getIncomingCount() {
        return incomingCount;
    }

    @Override
    protected void onDisable() {
        restoreKeys();
        activeDodge = null;
        incomingCount = 0;
    }

    @Override
    public void onTick() {
        restoreKeys();
        activeDodge = null;
        incomingCount = 0;

        if (!canDodge()) {
            return;
        }

        int horizon = reactTicks.getInt();
        List<ProjectileEntity> incoming = findIncoming(horizon);
        incomingCount = incoming.size();
        if (incoming.isEmpty()) {
            return;
        }

        Vec3d direction = chooseDirection(incoming, horizon);
        if (direction == null) {
            return;
        }

        activeDodge = direction;
        applyMovement(direction);
    }

    private boolean canDodge() {
        return mc.player != null && mc.world != null
                && mc.player.isAlive() && !mc.player.isSpectator()
                && !mc.player.hasVehicle() && !mc.player.isFallFlying()
                && (!onlyOnGround.get() || mc.player.isOnGround());
    }

    private List<ProjectileEntity> findIncoming(int horizon) {
        List<ProjectileEntity> result = new ArrayList<>();
        Box playerBox = mc.player.getBoundingBox().expand(HIT_MARGIN);

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ProjectileEntity projectile) || projectile.getOwner() == mc.player) {
                continue;
            }
            // 箭初速 1.6 格/tick，60 格外的在预测窗口内不可能飞到
            if (projectile.squaredDistanceTo(mc.player) > 60.0 * 60.0) {
                continue;
            }
            // 已经落地插在方块上的箭速度归零
            if (projectile.getVelocity().lengthSquared() < 0.01) {
                continue;
            }
            if (onlyHarmful.get() && !isHarmful(projectile)) {
                continue;
            }
            if (ticksUntilHit(projectile, playerBox, Vec3d.ZERO, 0.0, horizon) > 0) {
                result.add(projectile);
            }
        }
        return result;
    }

    /** 鱼钩、雪球、鸡蛋这类打在身上不掉血的东西不值得为它让位。 */
    private boolean isHarmful(ProjectileEntity projectile) {
        return projectile instanceof PersistentProjectileEntity
                || projectile instanceof ExplosiveProjectileEntity
                || projectile instanceof ShulkerBulletEntity
                || projectile instanceof PotionEntity
                || projectile instanceof LlamaSpitEntity;
    }

    /**
     * 逐 tick 推演投射物，返回命中所需的 tick 数，不会命中则返回 -1。
     * dodgeDirection 与 speed 用来同时模拟玩家自己的位移。
     */
    private int ticksUntilHit(ProjectileEntity projectile, Box playerBox,
                              Vec3d dodgeDirection, double speed, int horizon) {
        Vec3d pos = projectile.getPos();
        Vec3d velocity = projectile.getVelocity();
        boolean hasGravity = !projectile.hasNoGravity();

        for (int tick = 1; tick <= horizon; tick++) {
            Vec3d next = pos.add(velocity);
            Vec3d offset = dodgeDirection.multiply(speed * tick);
            Box box = speed > 0.0 ? playerBox.offset(offset) : playerBox;

            if (box.raycast(pos, next).isPresent()) {
                return tick;
            }

            pos = next;
            // 原版是先做碰撞检测，再施加阻力和重力，这里保持一致
            velocity = velocity.multiply(AIR_DRAG);
            if (hasGravity) {
                velocity = new Vec3d(velocity.x, velocity.y - GRAVITY, velocity.z);
            }
        }
        return -1;
    }

    private Vec3d chooseDirection(List<ProjectileEntity> incoming, int horizon) {
        Vec3d reference = incoming.get(0).getVelocity();
        Vec3d horizontal = new Vec3d(reference.x, 0.0, reference.z);
        if (horizontal.lengthSquared() < 1.0E-6) {
            return null;
        }
        horizontal = horizontal.normalize();

        List<Vec3d> candidates = new ArrayList<>();
        Vec3d left = new Vec3d(-horizontal.z, 0.0, horizontal.x);
        candidates.add(left);
        candidates.add(left.negate());
        if (allowBackward.get()) {
            candidates.add(horizontal);
            candidates.add(horizontal.negate());
        }

        double speed = estimateSpeed();
        Box playerBox = mc.player.getBoundingBox().expand(HIT_MARGIN);
        Vec3d currentVelocity = mc.player.getVelocity();

        // 开启「只躲得干净才躲」时要求一个都打不中，否则只要比站着不动强就值得动
        int maxAcceptedHits = fullDodgeOnly.get() ? 0 : incoming.size() - 1;
        Vec3d best = null;
        int fewestHits = Integer.MAX_VALUE;
        double bestAlignment = -Double.MAX_VALUE;

        // 躲避会持续到预测窗口结束，安全检查要覆盖这段路可能走到的最远处
        double checkDistance = Math.min(MAX_PATH_CHECK, speed * horizon);

        for (Vec3d candidate : candidates) {
            if (safetyCheck.get() && !isSafeDirection(candidate, checkDistance)) {
                continue;
            }

            int hits = 0;
            for (ProjectileEntity projectile : incoming) {
                if (ticksUntilHit(projectile, playerBox, candidate, speed, horizon) > 0) {
                    hits++;
                }
            }
            if (hits > maxAcceptedHits) {
                continue;
            }

            // 效果相同时顺着当前移动方向走，减少突兀的方向突变
            double alignment = candidate.dotProduct(currentVelocity);
            if (hits < fewestHits || (hits == fewestHits && alignment > bestAlignment)) {
                fewestHits = hits;
                bestAlignment = alignment;
                best = candidate;
            }
        }
        return best;
    }

    private double estimateSpeed() {
        // 走路约 0.215 格/tick，冲刺约 0.28，这里往低了估，因为起步有加速过程
        return sprint.get() ? 0.22 : 0.17;
    }

    /**
     * 沿整条躲避路径采样，而不是只看落点。躲避会持续好几 tick，
     * 只检查一个点的话，两格外是平地、四格外是悬崖的地形照样会走下去。
     */
    private boolean isSafeDirection(Vec3d direction, double maxDistance) {
        Vec3d origin = mc.player.getPos();
        for (double distance = PATH_SAMPLE_STEP; distance <= maxDistance + 1.0E-6; distance += PATH_SAMPLE_STEP) {
            if (!isSafeAt(origin.add(direction.multiply(distance)))) {
                return false;
            }
        }
        return true;
    }

    private boolean isSafeAt(Vec3d point) {
        BlockPos feet = BlockPos.ofFloored(point);

        if (isBlocked(feet) || isBlocked(feet.up())) {
            return false;
        }
        if (isHazard(feet) || isHazard(feet.up())) {
            return false;
        }
        // 踩在半砖之类的矮台阶上会整体抬高，多留一格头顶空间
        if (!mc.world.getBlockState(feet).getCollisionShape(mc.world, feet).isEmpty()
                && isBlocked(feet.up(2))) {
            return false;
        }

        // 一路向下找落脚点，超过允许落差就当悬崖
        int maxDrop = maxFallDistance.getInt();
        BlockPos below = feet.down();
        for (int drop = 0; drop <= maxDrop; drop++) {
            if (isHazard(below)) {
                return false;
            }
            if (hasSupport(below)) {
                return true;
            }
            below = below.down();
        }
        return false;
    }

    /** 碰撞箱顶部低于抬腿高度的（半砖、地毯之类）能直接走上去，不算挡路。 */
    private boolean isBlocked(BlockPos pos) {
        VoxelShape shape = mc.world.getBlockState(pos).getCollisionShape(mc.world, pos);
        if (shape.isEmpty()) {
            return false;
        }
        return shape.getMax(Direction.Axis.Y) > mc.player.getStepHeight();
    }

    /** 判断能不能落脚。水虽然没有碰撞箱，但能接住人且不摔伤，算安全落点。 */
    private boolean hasSupport(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.getFluidState().isIn(FluidTags.WATER)) {
            return true;
        }
        return !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private boolean isHazard(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.getFluidState().isIn(FluidTags.LAVA)) {
            return true;
        }
        return state.isOf(Blocks.FIRE)
                || state.isOf(Blocks.SOUL_FIRE)
                || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.POWDER_SNOW)
                || state.isOf(Blocks.CAMPFIRE)
                || state.isOf(Blocks.SOUL_CAMPFIRE);
    }

    /**
     * 把世界坐标下的躲避方向换算成前后左右按键。原版的移动输入本身就是从按键状态读的
     * （见 KeyboardInput#tick），所以按住按键就能走出完全合法的移动，不需要改速度。
     */
    private void applyMovement(Vec3d direction) {
        float yaw = mc.player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double sin = MathHelper.sin(yaw);
        double cos = MathHelper.cos(yaw);

        double forward = direction.z * cos - direction.x * sin;
        double sideways = direction.x * cos + direction.z * sin;

        if (forward > 0.3) {
            press(mc.options.forwardKey);
        } else if (forward < -0.3) {
            press(mc.options.backKey);
        }

        // movementSideways 为正对应左键，见 KeyboardInput#getMovementMultiplier
        if (sideways > 0.3) {
            press(mc.options.leftKey);
        } else if (sideways < -0.3) {
            press(mc.options.rightKey);
        }

        if (sprint.get()) {
            press(mc.options.sprintKey);
        }
    }

    private void press(KeyBinding binding) {
        binding.setPressed(true);
        overriddenKeys.add(binding);
    }

    /** 把我们动过的按键还原成键盘上的真实状态，避免把玩家自己按住的键吃掉。 */
    private void restoreKeys() {
        if (overriddenKeys.isEmpty()) {
            return;
        }
        for (KeyBinding binding : overriddenKeys) {
            binding.setPressed(isPhysicallyPressed(binding));
        }
        overriddenKeys.clear();
    }

    private boolean isPhysicallyPressed(KeyBinding binding) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        if (key.getCategory() != InputUtil.Type.KEYSYM || key.getCode() == GLFW.GLFW_KEY_UNKNOWN) {
            return false;
        }
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), key.getCode());
    }
}
