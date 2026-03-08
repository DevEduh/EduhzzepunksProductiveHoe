package com.example.examplemod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PlantCleanupUtil {
    private static final TagKey<Block> REPLACEABLE_PLANTS = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("minecraft", "replaceable_plants"));
    private static final TagKey<Block> REPLACEABLE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("minecraft", "replaceable"));

    private PlantCleanupUtil() {
    }

    public static boolean isReplaceablePlant(BlockState state) {
        return state.is(REPLACEABLE_PLANTS)
                || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(REPLACEABLE);
    }

    public static int clearReplaceablePlantsArea(
            ServerLevel level,
            Player player,
            ItemStack tool,
            BlockPos origin,
            Direction facing,
            int acreageLevel
    ) {
        int baseWidth = getBaseWidthForHoe(tool.getItem());
        int baseDepth = getBaseDepthForHoe(tool.getItem());

        int width = baseWidth + Math.max(0, acreageLevel) * 2;
        int depth = baseDepth + Math.max(0, acreageLevel) * 2;
        if (width % 2 == 0) {
            width++;
        }

        int forwardX = facing.getStepX();
        int forwardZ = facing.getStepZ();
        int rightX = -forwardZ;
        int rightZ = forwardX;

        int removed = 0;
        int halfWidth = width / 2;

        for (int front = 0; front < depth; front++) {
            for (int side = -halfWidth; side <= halfWidth; side++) {
                for (int y = 0; y <= 1; y++) {
                    int dx = forwardX * front + rightX * side;
                    int dz = forwardZ * front + rightZ * side;
                    BlockPos targetPos = origin.offset(dx, y, dz);
                    BlockState targetState = level.getBlockState(targetPos);

                    if (!isReplaceablePlant(targetState)) {
                        continue;
                    }

                    if (level.destroyBlock(targetPos, false, player)) {
                        removed++;
                    }
                }
            }
        }

        return removed;
    }

    public static int calculateCleanupDurabilityDamage(int removedBlocks) {
        if (removedBlocks <= 0) {
            return 0;
        }
        return (removedBlocks + 4) / 5;
    }

    public static void playCleanupSweepFeedback(ServerLevel level, Player player, Direction facing, int removedBlocks) {
        if (removedBlocks <= 0) {
            return;
        }

        player.sweepAttack();
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.0F);

        int bursts = Math.max(2, Math.min(6, 1 + removedBlocks / 8));
        for (int i = 0; i < bursts; i++) {
            double distance = 0.8D + (i * 0.6D);
            double x = player.getX() + 0.5D + (facing.getStepX() * distance);
            double y = player.getY() + 0.9D;
            double z = player.getZ() + 0.5D + (facing.getStepZ() * distance);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static int getBaseWidthForHoe(Item item) {
        if (!(item instanceof HoeItem hoeItem)) {
            return 3;
        }

        Tier tier = hoeItem.getTier();
        if (tier == Tiers.NETHERITE) {
            return 11;
        }
        if (tier == Tiers.DIAMOND) {
            return 9;
        }
        if (tier == Tiers.IRON) {
            return 7;
        }
        if (tier == Tiers.STONE) {
            return 5;
        }
        return 3;
    }

    private static int getBaseDepthForHoe(Item item) {
        if (!(item instanceof HoeItem hoeItem)) {
            return 3;
        }

        Tier tier = hoeItem.getTier();
        if (tier == Tiers.NETHERITE) {
            return 7;
        }
        if (tier == Tiers.DIAMOND) {
            return 6;
        }
        if (tier == Tiers.IRON) {
            return 5;
        }
        if (tier == Tiers.STONE) {
            return 4;
        }
        return 3;
    }
}
