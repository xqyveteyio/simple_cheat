package com.keyboard.simplecheat.util;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtil {
    private RotationUtil() {
    }

    /**
     * 计算从 from 看向 to 所需的 [yaw, pitch]。
     */
    public static float[] getRotationsTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    /** 当前朝向与目标朝向之间的夹角（度），用于按视线偏差排序目标。 */
    public static float angleDifference(float currentYaw, float currentPitch, float[] target) {
        float yawDiff = MathHelper.wrapDegrees(target[0] - currentYaw);
        float pitchDiff = target[1] - currentPitch;
        return MathHelper.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }
}
