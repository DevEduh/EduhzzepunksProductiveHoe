package com.example.examplemod.util;

import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public final class CropDetectionUtil {
    private CropDetectionUtil() {
    }

    public static Optional<CropBlock> getMatureCrop(BlockState state) {
        if (!(state.getBlock() instanceof CropBlock cropBlock)) {
            return Optional.empty();
        }

        return cropBlock.isMaxAge(state) ? Optional.of(cropBlock) : Optional.empty();
    }

    public static boolean isMatureCrop(BlockState state) {
        return getMatureCrop(state).isPresent();
    }
}
