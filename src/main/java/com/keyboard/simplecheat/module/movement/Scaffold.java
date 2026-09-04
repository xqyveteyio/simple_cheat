package com.keyboard.simplecheat.module.movement;

import com.keyboard.simplecheat.module.Category;
import com.keyboard.simplecheat.module.Module;
import com.keyboard.simplecheat.module.setting.BooleanSetting;
import com.keyboard.simplecheat.module.setting.ModeSetting;
import com.keyboard.simplecheat.module.setting.NumberSetting;
import com.keyboard.simplecheat.util.RotationUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/**
 * 在脚下自动放置方块，走到哪铺到哪。
 * <p>
 * 服务端放置方块时并不校验玩家朝向（见 ServerPlayNetworkHandler#onPlayerInteractBlock），
 * 只检查眼睛到方块中心不超过 6 格、命中点在方块中心 ±1 格内、以及建筑权限，
 * 所以转向纯粹是为了动作看起来正常。
 */
public class Scaffold extends Module {
    /** 优先拿身边的路面当支撑，脚下和头顶通常是空的，放最后试。 */
    private static final Direction[] PLACEMENT_ORDER = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            Direction.DOWN, Direction.UP
    };

    /** 脚下那格放不进去时往下最多退几格找落点，3 格以内不摔血。 */
    private static final int MAX_PLACE_DEPTH = 3;

    /** 能站人但不适合当路的方块：会爆炸、会弹、会滑、或者右键会有别的反应。 */
    private static final Set<Block> BLACKLIST = Set.of(
            Blocks.TNT, Blocks.CRAFTING_TABLE, Blocks.NOTE_BLOCK, Blocks.JUKEBOX,
            Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK, Blocks.ICE, Blocks.PACKED_ICE,
            Blocks.BLUE_ICE, Blocks.FROSTED_ICE, Blocks.MAGMA_BLOCK, Blocks.SOUL_SAND,
            Blocks.RESPAWN_ANCHOR
    );

    private final BooleanSetting predictive = new BooleanSetting("predictive", "预测落点",
            "按移动速度提前在前方铺一格，走快了不容易踩空", true);
    private final NumberSetting lookahead = new NumberSetting("lookahead", "预测提前量",
            "落点往前推算几 tick，跑跳时调大一些更不容易断", 4.0, 1.0, 10.0, 1.0);
    private final NumberSetting maxPerTick = new NumberSetting("max_per_tick", "每 tick 最多放置",
            "跑跳时一 tick 可能缺不止一格，调大能跟得上", 2.0, 1.0, 5.0, 1.0);
    private final BooleanSetting tower = new BooleanSetting("tower", "塔式上升",
            "上升过程中也在脚下铺，按住跳跃就能原地往上叠；关掉则跳跃时不放", false);
    private final ModeSetting rotate = new ModeSetting("rotate", "转向方式",
            "服务端不校验放置朝向，转向只是让动作自然一点",
            new String[]{"silent", "none"}, new String[]{"静默转向", "不转向"}, 0);
    private final BooleanSetting safeBlocksOnly = new BooleanSetting("safe_blocks", "只用安全方块",
            "跳过沙子、TNT、箱子、冰这类会掉落、爆炸、开界面或站不稳的方块", true);
    private final BooleanSetting restoreSlot = new BooleanSetting("restore_slot", "放完切回原槽位",
            "放置后把手上物品换回去，不打断你原本拿着的东西", true);
    private final BooleanSetting pauseOnSneak = new BooleanSetting("pause_on_sneak", "潜行时暂停",
            "想主动下去的时候按住潜行即可", true);
    private final NumberSetting delay = new NumberSetting("delay", "放置间隔",
            "两次放置之间等待几 tick，0 表示每 tick 都放", 0.0, 0.0, 10.0, 1.0);

    private int delayCounter;
    private boolean placing;
    private int availableBlocks;

    public Scaffold() {
        super("scaffold", "自动搭路", "在脚下自动放方块，走到哪铺到哪", Category.MOVEMENT, GLFW.GLFW_KEY_G);
        addSettings(predictive, lookahead, maxPerTick, tower, rotate, safeBlocksOnly,
                restoreSlot, pauseOnSneak, delay);
    }

    public boolean isPlacing() {
        return placing;
    }

    public int getAvailableBlocks() {
        return availableBlocks;
    }

    @Override
    protected void onDisable() {
        delayCounter = 0;
        placing = false;
        availableBlocks = 0;
    }

    @Override
    public void onTick() {
        placing = false;
        if (!canPlace()) {
            availableBlocks = 0;
            return;
        }

        availableBlocks = countBlocks();
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        // 跑跳时一 tick 可能同时缺水平和垂直方向的格子，只补一格会跟不上
        int limit = maxPerTick.getInt();
        for (int placed = 0; placed < limit; placed++) {
            BlockPos target = findTarget();
            if (target == null) {
                break;
            }
            Placement placement = findPlacementWithBridge(target);
            if (placement == null) {
                break;
            }
            int slot = findBlockSlot();
            if (slot < 0) {
                break;
            }
            if (!place(placement, slot)) {
                break;
            }
        }
    }

    private boolean canPlace() {
        return mc.player != null && mc.world != null && mc.interactionManager != null
                && mc.player.isAlive() && !mc.player.isSpectator() && !mc.player.hasVehicle()
                && mc.currentScreen == null
                && (!pauseOnSneak.get() || !mc.player.isSneaking());
    }

    /** 玩家脚下那一格；站在实地上时这里已经有方块，只有踩空或塔式上升才需要铺。 */
    private BlockPos findTarget() {
        if (!tower.get() && !mc.player.isOnGround() && mc.player.getVelocity().y > 0.0) {
            return null;
        }

        Vec3d pos = mc.player.getPos();
        BlockPos under = findPlaceableUnder(pos.x, pos.z);
        if (under != null) {
            return under;
        }

        if (predictive.get()) {
            // 逐 tick 采样而不是只看终点，否则走得快时会跳过中间格，铺出来的路是断的
            Vec3d velocity = mc.player.getVelocity();
            int steps = lookahead.getInt();
            for (int step = 1; step <= steps; step++) {
                BlockPos ahead = findPlaceableUnder(pos.x + velocity.x * step, pos.z + velocity.z * step);
                if (ahead != null) {
                    return ahead;
                }
            }
        }
        return null;
    }

    /**
     * 从脚下往下找第一个真正放得进去的格子。
     * <p>
     * 方块不能放在与实体相交的位置（见 CollisionView#canPlace），所以玩家一旦开始下落，
     * 脚底那格就会和自己的碰撞箱重叠、放置被拒绝。这时往下退一格照样能接住人，
     * 掉这一格也不会摔血，比放弃不放好得多。
     */
    private BlockPos findPlaceableUnder(double x, double z) {
        Box playerBox = mc.player.getBoundingBox();
        for (int depth = 0; depth < MAX_PLACE_DEPTH; depth++) {
            BlockPos pos = BlockPos.ofFloored(x, mc.player.getY() - 0.5 - depth, z);
            if (!isReplaceable(pos)) {
                return null;
            }
            if (!playerBox.intersects(new Box(pos))) {
                return pos;
            }
        }
        return null;
    }

    private boolean isReplaceable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isAir() || state.getCollisionShape(mc.world, pos).isEmpty();
    }

    /**
     * 斜着往上搭的时候，目标格常常只和已铺的方块对角相接，六个面都没得点。
     * 这时先在中间补一格把它们连起来，下一 tick 就能接着铺了。
     */
    private Placement findPlacementWithBridge(BlockPos target) {
        Placement direct = findPlacement(target);
        if (direct != null) {
            return direct;
        }

        Box playerBox = mc.player.getBoundingBox();
        for (Direction direction : PLACEMENT_ORDER) {
            BlockPos bridge = target.offset(direction);
            if (!isReplaceable(bridge) || playerBox.intersects(new Box(bridge))) {
                continue;
            }
            Placement placement = findPlacement(bridge);
            if (placement != null) {
                return placement;
            }
        }
        return null;
    }

    /**
     * 放方块必须点在一个已存在方块的面上，这里找目标格周围可以点的那一面。
     * 流体和没有碰撞体积的方块点不了。
     */
    private Placement findPlacement(BlockPos target) {
        for (Direction direction : PLACEMENT_ORDER) {
            BlockPos neighbor = target.offset(direction);
            BlockState state = mc.world.getBlockState(neighbor);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            if (state.getCollisionShape(mc.world, neighbor).isEmpty()) {
                continue;
            }

            Direction side = direction.getOpposite();
            // 面中心距方块中心正好 0.5 格，满足服务端 ±1 格的命中点校验
            Vec3d hit = Vec3d.ofCenter(neighbor).add(Vec3d.of(side.getVector()).multiply(0.5));
            return new Placement(neighbor, side, hit);
        }
        return null;
    }

    private int findBlockSlot() {
        PlayerInventory inventory = mc.player.getInventory();
        if (isUsableBlock(inventory.getStack(inventory.selectedSlot))) {
            return inventory.selectedSlot;
        }
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (isUsableBlock(inventory.getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private int countBlocks() {
        PlayerInventory inventory = mc.player.getInventory();
        int total = 0;
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (isUsableBlock(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean isUsableBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        if (!safeBlocksOnly.get()) {
            return true;
        }

        Block block = blockItem.getBlock();
        if (BLACKLIST.contains(block) || block instanceof FallingBlock) {
            return false;
        }
        BlockState state = block.getDefaultState();
        if (state.hasBlockEntity()) {
            return false;
        }
        return state.isFullCube(mc.world, BlockPos.ORIGIN);
    }

    private boolean place(Placement placement, int slot) {
        PlayerInventory inventory = mc.player.getInventory();
        int original = inventory.selectedSlot;
        // interactBlock 第一件事就是 syncSelectedSlot，改字段即可，不用自己发切换包
        inventory.selectedSlot = slot;

        if (rotate.is("silent")) {
            float[] rotations = RotationUtil.getRotationsTo(mc.player.getEyePos(), placement.hit());
            mc.player.networkHandler.sendPacket(
                    new PlayerMoveC2SPacket.LookAndOnGround(rotations[0], rotations[1], mc.player.isOnGround()));
        }

        BlockHitResult hitResult = new BlockHitResult(placement.hit(), placement.side(), placement.neighbor(), false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        boolean accepted = result.isAccepted();
        if (accepted) {
            if (result.shouldSwingHand()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            placing = true;
            delayCounter = delay.getInt();
        }

        if (restoreSlot.get()) {
            inventory.selectedSlot = original;
        }
        return accepted;
    }

    private record Placement(BlockPos neighbor, Direction side, Vec3d hit) {
    }
}
