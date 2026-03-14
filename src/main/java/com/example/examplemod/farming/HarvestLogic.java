package com.example.examplemod.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class HarvestLogic {
    private static final int MAX_ROW_CROPS_SAFETY = 64;
    private static final float DURABILITY_DAMAGE_CHANCE = 0.30F;

    private HarvestLogic() {
    }

    public static int harvestSingle(ServerLevel level, Player player, ItemStack tool, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        CropDetection.CropInfo info = CropDetection.getMatureCrop(state);
        if (info == null) {
            return 0;
        }
        return harvestPositions(level, player, tool, List.of(pos), List.of(state));
    }

    public static int harvestRow(
            ServerLevel level,
            Player player,
            ItemStack tool,
            BlockPos origin,
            Direction.Axis axis,
            int acreageLevel
    ) {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();

        BlockState originState = level.getBlockState(origin);
        CropDetection.CropInfo originInfo = CropDetection.getMatureCrop(originState);
        if (originInfo == null) {
            return 0;
        }

        if (!isFarmlandBelow(level, origin)) {
            return 0;
        }

        int tierLimit = getRowLimitForHoe(tool.getItem(), acreageLevel);
        int maxRowCrops = Math.min(tierLimit, MAX_ROW_CROPS_SAFETY);
        Block originBlock = originState.getBlock();

        positions.add(origin);
        states.add(originState);

        scanRowDirection(level, originBlock, origin, axis, 1, maxRowCrops, positions, states);
        if (positions.size() < maxRowCrops) {
            scanRowDirection(level, originBlock, origin, axis, -1, maxRowCrops, positions, states);
        }

        return harvestPositions(level, player, tool, positions, states);
    }

    public static int harvestAreaByAcreageLevel(ServerLevel level, Player player, ItemStack tool, BlockPos center, int acreageLevel) {
        int area = getAreaSizeForAcreageLevel(acreageLevel);
        int radius = (area - 1) / 2;

        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos currentPos = center.offset(x, 0, z);
                BlockState state = level.getBlockState(currentPos);

                if (CropDetection.getMatureCrop(state) == null) {
                    continue;
                }

                positions.add(currentPos);
                states.add(state);
            }
        }

        return harvestPositions(level, player, tool, positions, states);
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
            Block targetBlock,
            BlockPos origin,
            Direction.Axis axis,
            int direction,
            int maxRowCrops,
            List<BlockPos> positions,
            List<BlockState> states
    ) {
        for (int step = 1; step < maxRowCrops && positions.size() < maxRowCrops; step++) {
            int xOffset = axis == Direction.Axis.X ? step * direction : 0;
            int zOffset = axis == Direction.Axis.Z ? step * direction : 0;
            BlockPos currentPos = origin.offset(xOffset, 0, zOffset);
            BlockState currentState = level.getBlockState(currentPos);

            if (currentState.getBlock() != targetBlock) {
                break;
            }
            if (CropDetection.getMatureCrop(currentState) == null) {
                break;
            }
            if (!isFarmlandBelow(level, currentPos)) {
                break;
            }

            positions.add(currentPos);
            states.add(currentState);
        }
    }

    private static int harvestPositions(
            ServerLevel level,
            Player player,
            ItemStack tool,
            List<BlockPos> positions,
            List<BlockState> states
    ) {
        if (positions.isEmpty()) {
            return 0;
        }

        int bountifulLevel = EnchantmentEffects.getBountifulLevel(tool);
        int harvested = 0;

        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            BlockState state = states.get(i);
            CropDetection.CropInfo info = CropDetection.getMatureCrop(state);
            if (info == null) {
                continue;
            }

            harvestAndReplant(level, player, tool, pos, state, info, bountifulLevel);
            harvested++;
        }

        if (harvested > 1) {
            playMultiHarvestFeedback(level, player, positions, states);
        }

        return harvested;
    }

    private static void harvestAndReplant(
            ServerLevel level,
            Player player,
            ItemStack tool,
            BlockPos pos,
            BlockState state,
            CropDetection.CropInfo info,
            int bountifulLevel
    ) {
        ItemStack replantSeed = state.getBlock().getCloneItemStack(level, pos, state);
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);

        if (!replantSeed.isEmpty() && EnchantmentEffects.shouldConsumeSeed(level.random, bountifulLevel)) {
            consumeOneMatchingSeed(drops, replantSeed);
        }
        EnchantmentEffects.applyBountifulSeedBonus(level.random, drops, bountifulLevel, replantSeed);

        BlockState replantedState = CropDetection.getReplantState(state, info);
        level.setBlock(pos, replantedState, Block.UPDATE_ALL);

        SoilFatigueManager.get(level).applyOnReplant(
                level,
                pos.below(),
                BuiltInRegistries.BLOCK.getKey(replantedState.getBlock())
        );

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
            List<BlockState> states
    ) {
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            BlockState state = states.get(i);
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

    private static boolean isFarmlandBelow(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(Blocks.FARMLAND);
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
}
