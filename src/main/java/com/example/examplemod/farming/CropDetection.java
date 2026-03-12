package com.example.examplemod.farming;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;

public final class CropDetection {
    public record CropInfo(Block block, CropBlock cropBlock, IntegerProperty ageProperty, int maxAge) {
        public boolean isCropBlock() {
            return cropBlock != null;
        }
    }

    private static final Map<Block, IntegerProperty> AGE_PROPERTIES = new HashMap<>();
    private static final Map<Block, Integer> MAX_AGES = new HashMap<>();

    private CropDetection() {
    }

    public static CropInfo getCropInfo(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock cropBlock) {
            return new CropInfo(block, cropBlock, CropBlock.AGE, cropBlock.getMaxAge());
        }

        IntegerProperty ageProperty = getAgeProperty(state);
        if (ageProperty == null) {
            return null;
        }

        int maxAge = getMaxAge(block, ageProperty);
        return new CropInfo(block, null, ageProperty, maxAge);
    }

    public static boolean isCrop(BlockState state) {
        return getCropInfo(state) != null;
    }

    public static boolean isMature(BlockState state, CropInfo info) {
        if (info == null) {
            return false;
        }
        int age = state.getValue(info.ageProperty());
        return age >= info.maxAge();
    }

    public static CropInfo getMatureCrop(BlockState state) {
        CropInfo info = getCropInfo(state);
        if (info == null) {
            return null;
        }
        return isMature(state, info) ? info : null;
    }

    public static BlockState getReplantState(BlockState state, CropInfo info) {
        if (info.isCropBlock()) {
            return info.cropBlock().getStateForAge(0);
        }
        return state.setValue(info.ageProperty(), 0);
    }

    private static IntegerProperty getAgeProperty(BlockState state) {
        Block block = state.getBlock();
        if (AGE_PROPERTIES.containsKey(block)) {
            return AGE_PROPERTIES.get(block);
        }

        IntegerProperty ageProperty = null;
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty intProperty && "age".equals(property.getName())) {
                ageProperty = intProperty;
                break;
            }
        }

        AGE_PROPERTIES.put(block, ageProperty);
        return ageProperty;
    }

    private static int getMaxAge(Block block, IntegerProperty ageProperty) {
        Integer cached = MAX_AGES.get(block);
        if (cached != null) {
            return cached;
        }

        int max = 0;
        for (Integer value : ageProperty.getPossibleValues()) {
            if (value > max) {
                max = value;
            }
        }

        MAX_AGES.put(block, max);
        return max;
    }
}
