package com.example.examplemod.util;

import com.example.examplemod.enchant.ModEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HarvestLogicUtil {
    private static final int MAX_ROW_CROPS_SAFETY = 64;
    private static final float DURABILITY_DAMAGE_CHANCE = 0.30F;

    private HarvestLogicUtil() {
    }

    public static int harvestSingle(ServerLevel level, Player player, ItemStack tool, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return harvestPositions(level, player, tool, List.of(pos), Map.of(pos, state));
    }

    public static int harvestRow(
            ServerLevel level,
            Player player,
            ItemStack tool,
            BlockPos origin,
            CropBlock targetCrop,
            Direction.Axis axis,
            int acreageLevel
    ) {
        List<BlockPos> positions = new ArrayList<>();
        Map<BlockPos, BlockState> statesByPos = new LinkedHashMap<>();
        int tierLimit = getRowLimitForHoe(tool.getItem(), acreageLevel);
        int maxRowCrops = Math.min(tierLimit, MAX_ROW_CROPS_SAFETY);

        BlockState originState = level.getBlockState(origin);
        if (originState.getBlock() != targetCrop || !targetCrop.isMaxAge(originState)) {
            return 0;
        }

        positions.add(origin);
        statesByPos.put(origin, originState);

        scanRowDirection(level, targetCrop, origin, axis, 1, maxRowCrops, positions, statesByPos);
        if (positions.size() < maxRowCrops) {
            scanRowDirection(level, targetCrop, origin, axis, -1, maxRowCrops, positions, statesByPos);
        }

        return harvestPositions(level, player, tool, positions, statesByPos);
    }

    public static int harvestAreaByAcreageLevel(ServerLevel level, Player player, ItemStack tool, BlockPos center, int acreageLevel) {
        int area = getAreaSizeForAcreageLevel(acreageLevel);
        int radius = (area - 1) / 2;

        List<BlockPos> positions = new ArrayList<>();
        Map<BlockPos, BlockState> statesByPos = new LinkedHashMap<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos currentPos = center.offset(x, 0, z);
                BlockState state = level.getBlockState(currentPos);

                if (!CropDetectionUtil.isMatureCrop(state)) {
                    continue;
                }

                positions.add(currentPos);
                statesByPos.put(currentPos, state);
            }
        }

        return harvestPositions(level, player, tool, positions, statesByPos);
    }

    public static int rollDurabilityDamage(RandomSource random, int harvestedCrops) {
        int damage = 0;
        for (int i = 0; i < harvestedCrops; i++) {
            if (random.nextFloat() < DURABILITY_DAMAGE_CHANCE) {
                damage++;
            }
        }
        return damage;
    }

    private static void scanRowDirection(
            ServerLevel level,
            CropBlock targetCrop,
            BlockPos origin,
            Direction.Axis axis,
            int direction,
            int maxRowCrops,
            List<BlockPos> positions,
            Map<BlockPos, BlockState> statesByPos
    ) {
        for (int step = 1; step < maxRowCrops && positions.size() < maxRowCrops; step++) {
            int xOffset = axis == Direction.Axis.X ? step * direction : 0;
            int zOffset = axis == Direction.Axis.Z ? step * direction : 0;
            BlockPos currentPos = origin.offset(xOffset, 0, zOffset);
            BlockState currentState = level.getBlockState(currentPos);

            if (currentState.getBlock() != targetCrop || !targetCrop.isMaxAge(currentState)) {
                break;
            }

            positions.add(currentPos);
            statesByPos.put(currentPos, currentState);
        }
    }

    private static int harvestPositions(
            ServerLevel level,
            Player player,
            ItemStack tool,
            List<BlockPos> positions,
            Map<BlockPos, BlockState> statesByPos
    ) {
        if (positions.isEmpty()) {
            return 0;
        }

        int bountifulLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.BOUNTIFUL_SEED.get(), tool);

        int harvested = 0;
        for (BlockPos pos : positions) {
            BlockState state = statesByPos.get(pos);
            if (state == null) {
                state = level.getBlockState(pos);
            }

            if (!(state.getBlock() instanceof CropBlock cropBlock) || !cropBlock.isMaxAge(state)) {
                continue;
            }

            harvestAndReplant(level, player, tool, pos, state, cropBlock, bountifulLevel);
            harvested++;
        }

        if (harvested > 1) {
            playMultiHarvestFeedback(level, player, positions, statesByPos);
        }

        return harvested;
    }

    private static void harvestAndReplant(
            ServerLevel level,
            Player player,
            ItemStack tool,
            BlockPos pos,
            BlockState state,
            CropBlock cropBlock,
            int bountifulLevel
    ) {
        ItemStack replantSeed = cropBlock.getCloneItemStack(level, pos, state);
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);

        boolean keepSeedForFree = shouldKeepSeedForFree(level.random, bountifulLevel);
        if (!replantSeed.isEmpty() && !keepSeedForFree) {
            consumeOneMatchingSeed(drops, replantSeed);
        }
        applyBountifulCropBonus(level.random, drops, replantSeed, bountifulLevel);

        BlockState replantedState = cropBlock.getStateForAge(0);
        level.setBlock(pos, replantedState, Block.UPDATE_ALL);

        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
    }

    private static void consumeOneMatchingSeed(List<ItemStack> drops, ItemStack seedTemplate) {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }

            if (ItemStack.isSameItemSameTags(drop, seedTemplate)) {
                drop.shrink(1);
                return;
            }
        }
    }

    private static void playMultiHarvestFeedback(
            ServerLevel level,
            Player player,
            List<BlockPos> positions,
            Map<BlockPos, BlockState> statesByPos
    ) {
        for (BlockPos pos : positions) {
            BlockState state = statesByPos.getOrDefault(pos, level.getBlockState(pos));
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    6,
                    0.25D,
                    0.25D,
                    0.25D,
                    0.02D
            );
            level.playSound(
                    null,
                    pos,
                    state.getSoundType().getBreakSound(),
                    net.minecraft.sounds.SoundSource.BLOCKS,
                    0.5F,
                    1.0F
            );
        }

        player.sweepAttack();
    }

    private static int getAreaSizeForAcreageLevel(int acreageLevel) {
        int clampedLevel = Math.max(1, Math.min(3, acreageLevel));
        if (clampedLevel == 1) {
            return 3;
        }
        if (clampedLevel == 2) {
            return 5;
        }
        return 7;
    }

    private static int getRowLimitForHoe(Item item, int acreageLevel) {
        if (!(item instanceof HoeItem hoeItem)) {
            return 1;
        }

        Tier tier = hoeItem.getTier();
        int clampedAcreage = Math.max(0, Math.min(3, acreageLevel));

        if (tier == Tiers.NETHERITE) {
            return getLevelValue(clampedAcreage, 11, 15, 20);
        }
        if (tier == Tiers.DIAMOND) {
            return getLevelValue(clampedAcreage, 9, 14, 18);
        }
        if (tier == Tiers.IRON) {
            return getLevelValue(clampedAcreage, 7, 12, 15);
        }
        if (tier == Tiers.STONE) {
            return getLevelValue(clampedAcreage, 5, 7, 10);
        }
        return getLevelValue(clampedAcreage, 3, 5, 7);
    }

    private static int getLevelValue(int level, int level1Value, int level2Value, int level3Value) {
        if (level >= 3) {
            return level3Value;
        }
        if (level == 2) {
            return level2Value;
        }
        return level1Value;
    }

    private static boolean shouldKeepSeedForFree(RandomSource random, int bountifulLevel) {
        if (bountifulLevel <= 0) {
            return false;
        }

        float chance = switch (Math.min(bountifulLevel, 3)) {
            case 1 -> 0.15F;
            case 2 -> 0.25F;
            default -> 0.40F;
        };
        return random.nextFloat() < chance;
    }

    private static void applyBountifulCropBonus(RandomSource random, List<ItemStack> drops, ItemStack seedTemplate, int bountifulLevel) {
        if (bountifulLevel <= 0 || drops.isEmpty()) {
            return;
        }

        boolean hasNonSeedDrop = false;
        for (ItemStack drop : drops) {
            if (drop.isEmpty() || seedTemplate.isEmpty()) {
                continue;
            }
            if (!ItemStack.isSameItemSameTags(drop, seedTemplate)) {
                hasNonSeedDrop = true;
                break;
            }
        }

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }

            boolean isSeedItem = !seedTemplate.isEmpty() && ItemStack.isSameItemSameTags(drop, seedTemplate);
            if (isSeedItem && hasNonSeedDrop) {
                continue;
            }

            int extra = rollFortuneLikeBonus(random, bountifulLevel);
            if (extra > 0) {
                drop.grow(extra);
            }
        }
    }

    private static int rollFortuneLikeBonus(RandomSource random, int level) {
        int roll = random.nextInt(level + 2) - 1;
        return Math.max(roll, 0);
    }
}
