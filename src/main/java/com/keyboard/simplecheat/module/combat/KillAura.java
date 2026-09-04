package com.keyboard.simplecheat.module.combat;

import com.keyboard.simplecheat.SimpleCheatClient;
import com.keyboard.simplecheat.module.Category;
import com.keyboard.simplecheat.module.Module;
import com.keyboard.simplecheat.module.setting.BooleanSetting;
import com.keyboard.simplecheat.module.setting.ModeSetting;
import com.keyboard.simplecheat.module.setting.NumberSetting;
import com.keyboard.simplecheat.util.EntityUtil;
import com.keyboard.simplecheat.util.RotationUtil;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {
    private static final String SORT_DISTANCE = "distance";
    private static final String SORT_HEALTH = "health";
    private static final String SORT_ANGLE = "angle";

    private static final String PACE_COOLDOWN = "cooldown";
    private static final String PACE_CPS = "cps";

    private static final String ROTATE_NONE = "none";
    private static final String ROTATE_SILENT = "silent";
    private static final String ROTATE_CLIENT = "client";

    private final NumberSetting range = new NumberSetting("range", "攻击距离",
            "到目标碰撞箱的最大距离，原版为 3.0", 4.0, 2.0, 6.0, 0.1);
    private final ModeSetting pace = new ModeSetting("pace", "攻击节奏",
            "等待冷却可以打出满伤害，固定 CPS 会更快但伤害较低",
            new String[]{PACE_COOLDOWN, PACE_CPS},
            new String[]{"等待冷却（满伤害）", "固定 CPS"}, 0);
    private final NumberSetting cps = new NumberSetting("cps", "每秒攻击次数",
            "仅在固定 CPS 节奏下生效", 8.0, 1.0, 20.0, 0.5);
    private final ModeSetting sort = new ModeSetting("sort", "目标优先级",
            "同时有多个目标时先打谁",
            new String[]{SORT_DISTANCE, SORT_HEALTH, SORT_ANGLE},
            new String[]{"距离最近", "血量最低", "视线最近"}, 0);
    private final BooleanSetting multiTarget = new BooleanSetting("multi_target", "同时攻击多个目标",
            "一次 tick 内攻击范围内所有目标", false);
    private final NumberSetting maxTargets = new NumberSetting("max_targets", "同时攻击上限",
            "仅在同时攻击多个目标时生效", 3.0, 2.0, 10.0, 1.0);
    private final ModeSetting rotate = new ModeSetting("rotate", "转向方式",
            "静默转向只把朝向发给服务端，你的视角不会动",
            new String[]{ROTATE_NONE, ROTATE_SILENT, ROTATE_CLIENT},
            new String[]{"不转向", "静默转向", "真实转动视角"}, 1);
    private final BooleanSetting skipInvulnerable = new BooleanSetting("skip_invulnerable", "跳过无敌帧目标",
            "生物挨打后有 0.5 秒无敌帧，期间攻击完全无效，跳过它去打别的目标", true);
    private final BooleanSetting requireLineOfSight = new BooleanSetting("require_los", "需要视线",
            "关闭后可以隔墙攻击", true);
    private final BooleanSetting swingHand = new BooleanSetting("swing", "挥手动画",
            "关闭后没有挥手动作，但依然会造成伤害", true);
    private final BooleanSetting pauseInScreen = new BooleanSetting("pause_in_screen", "打开界面时暂停",
            "打开背包、箱子等界面时停止攻击（聊天框除外）", true);
    private final BooleanSetting pauseWhileUsingItem = new BooleanSetting("pause_using_item", "使用物品时暂停",
            "吃东西、拉弓、举盾时不攻击", true);

    private final BooleanSetting targetHostile = new BooleanSetting("target_hostile", "敌对生物",
            "僵尸、苦力怕、骷髅等", true);
    private final BooleanSetting targetAnimal = new BooleanSetting("target_animal", "动物",
            "牛、羊、鱼等被动生物", false);
    private final BooleanSetting targetPlayer = new BooleanSetting("target_player", "玩家",
            "不会攻击同队玩家和创造/旁观模式的玩家", false);
    private final BooleanSetting targetNeutral = new BooleanSetting("target_neutral", "村民与傀儡",
            "村民、流浪商人、铁傀儡、雪傀儡", false);
    private final BooleanSetting targetTamed = new BooleanSetting("target_tamed", "已驯服的生物",
            "狼、猫、马等已驯服的生物", false);
    private final BooleanSetting targetArmorStand = new BooleanSetting("target_armor_stand", "盔甲架",
            "", false);
    private final BooleanSetting targetOther = new BooleanSetting("target_other", "其他生物",
            "未归入以上分类的生物", false);

    private long lastAttackTime;
    private LivingEntity currentTarget;

    public KillAura() {
        super("killaura", "杀戮光环", "自动攻击附近的生物", Category.COMBAT, GLFW.GLFW_KEY_R);
        addSettings(range, pace, cps, sort, multiTarget, maxTargets, rotate,
                skipInvulnerable, requireLineOfSight, swingHand, pauseInScreen, pauseWhileUsingItem,
                targetHostile, targetAnimal, targetPlayer, targetNeutral, targetTamed,
                targetArmorStand, targetOther);
    }

    public LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    @Override
    protected void onDisable() {
        currentTarget = null;
    }

    @Override
    public void onTick() {
        currentTarget = null;

        if (!canRun()) {
            return;
        }

        List<LivingEntity> targets = collectTargets();
        if (targets.isEmpty()) {
            return;
        }

        sortTargets(targets);
        currentTarget = pickPrimaryTarget(targets);

        if (!isAttackReady()) {
            return;
        }

        int limit = multiTarget.get() ? maxTargets.getInt() : 1;
        int attacked = 0;
        for (LivingEntity target : targets) {
            if (attacked >= limit) {
                break;
            }
            if (isInvulnerable(target)) {
                continue;
            }
            attack(target);
            attacked++;
        }

        if (attacked > 0) {
            lastAttackTime = System.currentTimeMillis();
        }
    }

    /**
     * 生物挨打后 hurtTime 会从 10 递减到 0，这段时间内原版的 LivingEntity#damage 会把
     * 同等伤害的攻击直接判定为无效（既不掉血也不击退），所以打它纯属浪费攻击。
     */
    private boolean isInvulnerable(LivingEntity target) {
        return skipInvulnerable.get() && target.hurtTime > 0;
    }

    /** HUD 上显示的目标取下一个真正会被攻击的，全都在无敌帧里时退回优先级最高的那个。 */
    private LivingEntity pickPrimaryTarget(List<LivingEntity> targets) {
        for (LivingEntity target : targets) {
            if (!isInvulnerable(target)) {
                return target;
            }
        }
        return targets.get(0);
    }

    private boolean canRun() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null) {
            return false;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        // 举盾要排除在外：原版举盾时不能攻击纯粹是客户端输入层拦的，
        // 服务端不管这事，所以这里可以边举盾边打，正好配合远程防护模块。
        if (pauseWhileUsingItem.get() && player.isUsingItem()
                && !(player.getActiveItem().getItem() instanceof ShieldItem)) {
            return false;
        }
        if (pauseInScreen.get() && mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) {
            return false;
        }
        return true;
    }

    /**
     * 等待冷却模式下依赖原版攻击冷却条，这样每次都是满伤害；
     * 固定 CPS 模式则只看距离上次攻击的时间。
     */
    private boolean isAttackReady() {
        if (pace.is(PACE_COOLDOWN)) {
            return mc.player.getAttackCooldownProgress(0.0F) >= 1.0F;
        }
        long interval = (long) (1000.0 / cps.get());
        return System.currentTimeMillis() - lastAttackTime >= interval;
    }

    private List<LivingEntity> collectTargets() {
        List<LivingEntity> targets = new ArrayList<>();
        Vec3d eyePos = mc.player.getEyePos();
        double maxRange = range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (EntityUtil.distanceToBox(eyePos, living) > maxRange) {
                continue;
            }
            if (!isValidTarget(living)) {
                continue;
            }
            if (requireLineOfSight.get() && !mc.player.canSee(living)) {
                continue;
            }
            targets.add(living);
        }
        return targets;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player || !entity.isAlive() || entity.isRemoved() || entity.getHealth() <= 0.0F) {
            return false;
        }

        if (entity instanceof PlayerEntity player) {
            if (!targetPlayer.get() || player.isSpectator() || player.isCreative()) {
                return false;
            }
            return !mc.player.isTeammate(player);
        }

        if (entity instanceof ArmorStandEntity) {
            return targetArmorStand.get();
        }

        boolean tamed = (entity instanceof TameableEntity tameable && tameable.isTamed())
                || (entity instanceof AbstractHorseEntity horse && horse.isTame());
        if (tamed) {
            return targetTamed.get();
        }

        if (entity instanceof Monster) {
            return targetHostile.get();
        }

        if (entity instanceof MerchantEntity || entity instanceof GolemEntity) {
            return targetNeutral.get();
        }

        if (entity instanceof AnimalEntity || entity instanceof WaterCreatureEntity || entity instanceof AmbientEntity) {
            return targetAnimal.get();
        }

        return targetOther.get();
    }

    private void sortTargets(List<LivingEntity> targets) {
        Vec3d eyePos = mc.player.getEyePos();

        if (sort.is(SORT_HEALTH)) {
            targets.sort(Comparator.comparingDouble(e -> e.getHealth() + e.getAbsorptionAmount()));
        } else if (sort.is(SORT_ANGLE)) {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            targets.sort(Comparator.comparingDouble(e -> RotationUtil.angleDifference(yaw, pitch,
                    RotationUtil.getRotationsTo(eyePos, EntityUtil.getAimPoint(eyePos, e)))));
        } else {
            targets.sort(Comparator.comparingDouble(e -> EntityUtil.distanceToBox(eyePos, e)));
        }

        // List#sort 是稳定排序，所以这一步只是把远程敌人整体提前，各自组内仍保持上面的顺序
        if (SimpleCheatClient.getModuleManager().getRangedDefense().shouldPrioritizeRanged()) {
            targets.sort(Comparator.comparing(entity -> !RangedDefense.isRangedAttacker(entity)));
        }
    }

    private void attack(LivingEntity target) {
        ClientPlayerEntity player = mc.player;

        if (!rotate.is(ROTATE_NONE)) {
            Vec3d eyePos = player.getEyePos();
            float[] rotations = RotationUtil.getRotationsTo(eyePos, EntityUtil.getAimPoint(eyePos, target));
            if (rotate.is(ROTATE_CLIENT)) {
                player.setYaw(rotations[0]);
                player.setPitch(rotations[1]);
            } else {
                // 只把朝向发给服务端，本地视角保持不动；下一 tick 原版的移动包会把朝向改回来
                player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.LookAndOnGround(rotations[0], rotations[1], player.isOnGround()));
            }
        }

        mc.interactionManager.attackEntity(player, target);
        if (swingHand.get()) {
            player.swingHand(Hand.MAIN_HAND);
        }
    }
}
