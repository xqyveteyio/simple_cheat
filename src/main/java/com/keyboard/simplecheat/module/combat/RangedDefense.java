package com.keyboard.simplecheat.module.combat;

import com.keyboard.simplecheat.module.Category;
import com.keyboard.simplecheat.module.Module;
import com.keyboard.simplecheat.module.setting.BooleanSetting;
import com.keyboard.simplecheat.module.setting.ModeSetting;
import com.keyboard.simplecheat.module.setting.NumberSetting;
import com.keyboard.simplecheat.util.EntityUtil;
import com.keyboard.simplecheat.util.RotationUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class RangedDefense extends Module {
    private static final String MODE_SMART = "smart";
    private static final String MODE_ALWAYS = "always";

    private final ModeSetting mode = new ModeSetting("mode", "举盾方式",
            "常驻举盾会让移动速度只剩 20% 且无法冲刺",
            new String[]{MODE_SMART, MODE_ALWAYS},
            new String[]{"检测到威胁时", "常驻举盾"}, 0);
    private final BooleanSetting onProjectile = new BooleanSetting("on_projectile", "投射物飞来时",
            "检测朝你飞来的箭、火球、药水等", true);
    private final BooleanSetting onAiming = new BooleanSetting("on_aiming", "敌人瞄准时",
            "骷髅拉弓、掠夺者装填弩时就提前举盾", true);
    private final BooleanSetting onNearby = new BooleanSetting("on_nearby", "附近有远程敌人时",
            "范围内只要有远程敌人就举盾，最保险但最影响移动", false);
    private final NumberSetting detectRange = new NumberSetting("detect_range", "威胁检测距离",
            "在多远范围内寻找投射物和远程敌人", 24.0, 8.0, 48.0, 1.0);
    private final BooleanSetting faceThreat = new BooleanSetting("face_threat", "自动面向威胁",
            "盾牌只挡面朝的半边，这里用静默转向，不会动你的视角", true);
    private final NumberSetting holdTicks = new NumberSetting("hold_ticks", "威胁解除后保持",
            "单位 tick，避免盾牌频繁开合", 10.0, 0.0, 40.0, 1.0);
    private final BooleanSetting prioritizeRanged = new BooleanSetting("prioritize_ranged", "优先击杀远程敌人",
            "让杀戮光环把进入攻击范围的远程敌人排到最前面", true);

    private Entity currentThreat;
    private int holdCounter;
    private boolean raisedByUs;

    public RangedDefense() {
        super("ranged_defense", "远程防护", "自动举盾格挡投射物，并优先处理远程敌人",
                Category.COMBAT, GLFW.GLFW_KEY_B);
        addSettings(mode, onProjectile, onAiming, onNearby, detectRange, faceThreat, holdTicks,
                prioritizeRanged);
    }

    /** 骷髅、女巫、掠夺者等会远程攻击的生物，供杀戮光环排序时复用。 */
    public static boolean isRangedAttacker(LivingEntity entity) {
        return entity instanceof RangedAttackMob
                || entity instanceof GhastEntity
                || entity instanceof ShulkerEntity
                || entity instanceof BlazeEntity;
    }

    public boolean shouldPrioritizeRanged() {
        return isEnabled() && prioritizeRanged.get();
    }

    public Entity getCurrentThreat() {
        return currentThreat;
    }

    @Override
    protected void onDisable() {
        releaseShield();
        currentThreat = null;
        holdCounter = 0;
    }

    @Override
    public void onTick() {
        currentThreat = null;

        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || !mc.player.isAlive() || mc.player.isSpectator()) {
            releaseShield();
            return;
        }

        Hand shieldHand = findShieldHand();
        // 盾牌被斧头劈掉后有 100 tick 冷却，这期间举不起来，硬举会让原版反复触发右键交互
        if (shieldHand == null || mc.player.getItemCooldownManager().isCoolingDown(Items.SHIELD)) {
            releaseShield();
            return;
        }

        if (mode.is(MODE_SMART)) {
            currentThreat = findThreat();
            if (currentThreat != null) {
                holdCounter = holdTicks.getInt();
            } else if (holdCounter > 0) {
                holdCounter--;
            }
        }

        if (!mode.is(MODE_ALWAYS) && currentThreat == null && holdCounter <= 0) {
            releaseShield();
            return;
        }

        if (faceThreat.get() && currentThreat != null) {
            faceTowards(currentThreat);
        }
        raiseShield(shieldHand);
    }

    private Entity findThreat() {
        Vec3d eyePos = mc.player.getEyePos();
        double range = detectRange.get();
        double rangeSquared = range * range;

        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) {
                continue;
            }
            double distance = entity.squaredDistanceTo(mc.player);
            if (distance > rangeSquared || distance >= nearestDistance) {
                continue;
            }

            boolean dangerous;
            if (entity instanceof ProjectileEntity projectile) {
                dangerous = onProjectile.get() && isIncoming(projectile, eyePos);
            } else if (entity instanceof LivingEntity living && living.isAlive() && isRangedAttacker(living)) {
                dangerous = onNearby.get() || (onAiming.get() && living.isUsingItem());
            } else {
                continue;
            }

            if (dangerous) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** 投射物是否正朝自己飞来：速度方向和「指向自己」的方向基本一致。 */
    private boolean isIncoming(ProjectileEntity projectile, Vec3d eyePos) {
        if (projectile.getOwner() == mc.player) {
            return false;
        }
        Vec3d velocity = projectile.getVelocity();
        // 已经落地插在方块上的箭速度接近 0，不算威胁
        if (velocity.lengthSquared() < 0.01) {
            return false;
        }
        Vec3d toSelf = eyePos.subtract(projectile.getPos());
        if (toSelf.lengthSquared() < 1.0E-4) {
            return true;
        }
        return velocity.normalize().dotProduct(toSelf.normalize()) > 0.9;
    }

    private void faceTowards(Entity threat) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d aimPoint = threat instanceof LivingEntity
                ? EntityUtil.getAimPoint(eyePos, threat)
                : threat.getPos();
        float[] rotations = RotationUtil.getRotationsTo(eyePos, aimPoint);
        mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(rotations[0], rotations[1], mc.player.isOnGround()));
    }

    /**
     * 原版每 tick 都会检查「右键没按住就放下盾」，而重新举盾会把 itemUseTimeLeft 归零、
     * 永远凑不满 isBlocking 需要的 5 tick。所以这里必须让原版以为右键是按住的。
     */
    private void raiseShield(Hand hand) {
        if (!isShieldUp()) {
            mc.interactionManager.interactItem(mc.player, hand);
            raisedByUs = true;
        }
        if (raisedByUs) {
            mc.options.useKey.setPressed(true);
        }
    }

    private void releaseShield() {
        if (!raisedByUs) {
            return;
        }
        raisedByUs = false;
        mc.options.useKey.setPressed(false);
        if (mc.player != null && mc.interactionManager != null && mc.player.isUsingItem()) {
            mc.interactionManager.stopUsingItem(mc.player);
        }
    }

    private boolean isShieldUp() {
        return mc.player.isUsingItem() && mc.player.getActiveItem().getItem() instanceof ShieldItem;
    }

    private Hand findShieldHand() {
        if (mc.player.getOffHandStack().getItem() instanceof ShieldItem) {
            return Hand.OFF_HAND;
        }
        if (mc.player.getMainHandStack().getItem() instanceof ShieldItem) {
            return Hand.MAIN_HAND;
        }
        return null;
    }
}
