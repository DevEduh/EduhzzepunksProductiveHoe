package com.example.examplemod.farming;

import com.example.examplemod.config.ProductiveHoeConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

public final class SoilFatigueManager extends SavedData {
    private static final int MAX_FATIGUE = 5;
    private static final String DATA_NAME = "eduhzzepunks_productive_hoe_soil_fatigue";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_POS = "pos";
    private static final String TAG_FATIGUE = "fatigue";
    private static final String TAG_CROP = "crop";
    private static final String TAG_ROTATION_BONUS = "rotationBonus";

    private final Long2ObjectOpenHashMap<SoilData> data = new Long2ObjectOpenHashMap<>();

    public SoilFatigueManager() {
    }

    public static SoilFatigueManager get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(SoilFatigueManager::load, SoilFatigueManager::new, DATA_NAME);
    }

    public static float getPenalty(int fatigue) {
        return (float) ProductiveHoeConfig.getPenaltyForFatigue(clampFatigue(fatigue));
    }

    public static int getQualityPercent(int fatigue) {
        int quality = Math.round((1.0F - getPenalty(fatigue)) * 100.0F);
        return Math.max(0, Math.min(100, quality));
    }

    public static int getBlockedPercent(int fatigue) {
        int blocked = Math.round(getPenalty(fatigue) * 100.0F);
        return Math.max(0, Math.min(100, blocked));
    }

    public static int getFatiguePercent(int fatigue) {
        return clampFatigue(fatigue) * 20;
    }

    public int getFatigue(BlockPos pos) {
        SoilData soil = data.get(pos.asLong());
        return soil == null ? 0 : soil.fatigue;
    }

    public void resetFatigue(BlockPos pos) {
        SoilData soil = getOrCreate(pos.asLong());
        if (soil.fatigue != 0 || soil.rotationBonus != 0) {
            soil.fatigue = 0;
            soil.rotationBonus = 0;
            setDirty();
        }
    }

    public int applyOnReplant(ServerLevel level, BlockPos farmlandPos, ResourceLocation cropId) {
        BlockState farmland = level.getBlockState(farmlandPos);
        if (!farmland.is(Blocks.FARMLAND)) {
            clear(farmlandPos.asLong());
            return 0;
        }

        SoilData soil = getOrCreate(farmlandPos.asLong());
        int before = soil.fatigue;
        int beforeBonus = soil.rotationBonus;
        ResourceLocation previousCrop = soil.lastCrop;
        if (soil.lastCrop != null && soil.lastCrop.equals(cropId)) {
            soil.fatigue++;
        } else {
            soil.fatigue -= 2;
        }
        int after = clampFatigue(soil.fatigue);
        soil.fatigue = after;
        soil.lastCrop = cropId;

        if (previousCrop != null && !previousCrop.equals(cropId)) {
            int bonusTicks = ProductiveHoeConfig.getRotationBonusTicks();
            if (bonusTicks > 0) {
                soil.rotationBonus = bonusTicks;
            }
        }

        boolean cropChanged = previousCrop == null ? cropId != null : !previousCrop.equals(cropId);
        if (after != before || cropChanged || soil.rotationBonus != beforeBonus) {
            setDirty();
        }
        return after - before;
    }

    public boolean shouldBlockGrowth(ServerLevel level, BlockPos farmlandPos, int fatigue) {
        if (fatigue <= 0) {
            return false;
        }

        BlockState farmland = level.getBlockState(farmlandPos);
        if (!farmland.is(Blocks.FARMLAND)) {
            clear(farmlandPos.asLong());
            return false;
        }

        float penalty = getPenalty(fatigue);
        SoilData soil = data.get(farmlandPos.asLong());
        int bonus = soil == null ? 0 : soil.rotationBonus;
        if (bonus > 0) {
            penalty *= (float) ProductiveHoeConfig.getRotationBonusPenaltyMultiplier();
        }

        boolean blocked = level.random.nextFloat() < penalty;
        if (bonus > 0 && soil != null) {
            soil.rotationBonus = Math.max(0, bonus - 1);
            setDirty();
        }
        return blocked;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Long2ObjectMap.Entry<SoilData> entry : data.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            SoilData soil = entry.getValue();
            if (soil == null) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong(TAG_POS, key);
            entryTag.putByte(TAG_FATIGUE, (byte) soil.fatigue);
            if (soil.lastCrop != null) {
                entryTag.putString(TAG_CROP, soil.lastCrop.toString());
            }
            if (soil.rotationBonus > 0) {
                entryTag.putInt(TAG_ROTATION_BONUS, soil.rotationBonus);
            }
            list.add(entryTag);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    private static SoilFatigueManager load(CompoundTag tag) {
        SoilFatigueManager manager = new SoilFatigueManager();
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long pos = entry.getLong(TAG_POS);
            int fatigue = entry.getByte(TAG_FATIGUE);
            String cropString = entry.getString(TAG_CROP);
            ResourceLocation crop = cropString.isEmpty() ? null : ResourceLocation.tryParse(cropString);
            int rotationBonus = entry.contains(TAG_ROTATION_BONUS, Tag.TAG_INT) ? entry.getInt(TAG_ROTATION_BONUS) : 0;

            SoilData soil = new SoilData();
            soil.fatigue = clampFatigue(fatigue);
            soil.lastCrop = crop;
            soil.rotationBonus = Math.max(0, Math.min(ProductiveHoeConfig.getMaxRotationBonusTicks(), rotationBonus));
            manager.data.put(pos, soil);
        }
        return manager;
    }

    private SoilData getOrCreate(long key) {
        SoilData soil = data.get(key);
        if (soil != null) {
            return soil;
        }
        soil = new SoilData();
        data.put(key, soil);
        return soil;
    }

    public void clear(BlockPos pos) {
        clear(pos.asLong());
    }

    private void clear(long key) {
        if (data.remove(key) != null) {
            setDirty();
        }
    }

    private static int clampFatigue(int fatigue) {
        if (fatigue < 0) {
            return 0;
        }
        if (fatigue > MAX_FATIGUE) {
            return MAX_FATIGUE;
        }
        return fatigue;
    }

    private static final class SoilData {
        private int fatigue = 0;
        private ResourceLocation lastCrop = null;
        private int rotationBonus = 0;
    }
}
