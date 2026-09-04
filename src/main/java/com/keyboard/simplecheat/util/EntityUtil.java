package com.keyboard.simplecheat.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class EntityUtil {
    private EntityUtil() {
    }

    /**
     * 眼睛位置到目标碰撞箱表面的距离。原版的攻击判定用的也是碰撞箱而非实体中心，
     * 用中心点距离会让高个子实体（如末影人）在实际能打到时被判定为超出范围。
     */
    public static double distanceToBox(Vec3d eyePos, Entity target) {
        Box box = target.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - eyePos.x, 0.0), eyePos.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - eyePos.y, 0.0), eyePos.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - eyePos.z, 0.0), eyePos.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 瞄准点：碰撞箱内离眼睛最近的点，再稍微上移一点避免打在脚下。
     */
    public static Vec3d getAimPoint(Vec3d eyePos, Entity target) {
        Box box = target.getBoundingBox();
        double x = clamp(eyePos.x, box.minX, box.maxX);
        double y = clamp(eyePos.y, box.minY, box.maxY);
        double z = clamp(eyePos.z, box.minZ, box.maxZ);
        return new Vec3d(x, y, z);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
