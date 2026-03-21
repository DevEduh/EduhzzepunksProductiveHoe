package com.example.examplemod.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

public final class SoilFatigueEffects {
    private SoilFatigueEffects() {
    }

    public static void spawnRecoveryParticles(ServerLevel level, BlockPos farmlandPos, int strength) {
        int clampedStrength = Math.max(1, Math.min(3, strength));
        int count = 4 + clampedStrength * 3;
        double x = farmlandPos.getX() + 0.5D;
        double y = farmlandPos.getY() + 0.9D;
        double z = farmlandPos.getZ() + 0.5D;
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                x,
                y,
                z,
                count,
                0.35D,
                0.15D,
                0.35D,
                0.02D
        );
    }
}
