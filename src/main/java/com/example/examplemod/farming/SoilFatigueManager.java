package com.example.examplemod.farming;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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
    private static final String DATA_NAME = "eduhzzepunks_productive_hoe_soil_fatigue";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_POS = "pos";
    private static final String TAG_FATIGUE = "fatigue";
    private static final String TAG_CROP = "crop";

    private final Long2ObjectOpenHashMap<SoilData> data = new Long2ObjectOpenHashMap<>();
    private final LongArrayList keys = new LongArrayList();
    private final Long2IntOpenHashMap keyIndex = new Long2IntOpenHashMap();
    private int cursor = 0;

    public SoilFatigueManager() {
        keyIndex.defaultReturnValue(-1);
    }

    public static SoilFatigueManager get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(SoilFatigueManager::load, SoilFatigueManager::new, DATA_NAME);
    }

    public int getFatigue(BlockPos pos) {
        SoilData soil = data.get(pos.asLong());
        return soil == null ? 0 : soil.fatigue;
    }

    public void resetFatigue(BlockPos pos) {
        SoilData soil = getOrCreate(pos.asLong());
        if (soil.fatigue != 0) {
            soil.fatigue = 0;
            setDirty();
        }
    }

    public void applyOnReplant(ServerLevel level, BlockPos farmlandPos, ResourceLocation cropId) {
        BlockState farmland = level.getBlockState(farmlandPos);
        if (!farmland.is(Blocks.FARMLAND)) {
            remove(farmlandPos.asLong());
            return;
        }

        SoilData soil = getOrCreate(farmlandPos.asLong());
        if (soil.lastCrop != null && soil.lastCrop.equals(cropId)) {
            soil.fatigue++;
        } else {
            soil.fatigue -= 2;
        }
        soil.fatigue = clampFatigue(soil.fatigue);
        soil.lastCrop = cropId;
        setDirty();
    }

    public boolean shouldBlockGrowth(ServerLevel level, BlockPos farmlandPos, int fatigue) {
        if (fatigue <= 0) {
            return false;
        }

        BlockState farmland = level.getBlockState(farmlandPos);
        if (!farmland.is(Blocks.FARMLAND)) {
            remove(farmlandPos.asLong());
            return false;
        }

        float penalty = Math.min(0.2F * fatigue, 0.95F);
        return level.random.nextFloat() < penalty;
    }

    public void maybeRecover(ServerLevel level, BlockPos farmlandPos) {
        SoilData soil = data.get(farmlandPos.asLong());
        if (soil == null || soil.fatigue <= 0) {
            return;
        }

        BlockState farmland = level.getBlockState(farmlandPos);
        if (!farmland.is(Blocks.FARMLAND)) {
            remove(farmlandPos.asLong());
            return;
        }

        if (level.random.nextFloat() < 0.05F) {
            soil.fatigue = clampFatigue(soil.fatigue - 1);
            setDirty();
        }
    }

    public void tickRecovery(ServerLevel level) {
        if (keys.isEmpty()) {
            return;
        }

        int size = keys.size();
        int samples = Math.min(16, Math.max(1, size / 512));

        for (int i = 0; i < samples && !keys.isEmpty(); i++) {
            if (cursor >= keys.size()) {
                cursor = 0;
            }
            long key = keys.getLong(cursor++);
            SoilData soil = data.get(key);
            if (soil == null || soil.fatigue <= 0) {
                continue;
            }
            BlockPos pos = BlockPos.of(key);
            BlockState farmland = level.getBlockState(pos);
            if (!farmland.is(Blocks.FARMLAND)) {
                remove(key);
                continue;
            }
            if (level.random.nextFloat() < 0.05F) {
                soil.fatigue = clampFatigue(soil.fatigue - 1);
                setDirty();
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (long key : keys) {
            SoilData soil = data.get(key);
            if (soil == null) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putLong(TAG_POS, key);
            entry.putByte(TAG_FATIGUE, (byte) soil.fatigue);
            if (soil.lastCrop != null) {
                entry.putString(TAG_CROP, soil.lastCrop.toString());
            }
            list.add(entry);
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

            SoilData soil = new SoilData();
            soil.fatigue = clampFatigue(fatigue);
            soil.lastCrop = crop;
            manager.data.put(pos, soil);
            manager.addKey(pos);
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
        addKey(key);
        return soil;
    }

    private void addKey(long key) {
        if (keyIndex.containsKey(key)) {
            return;
        }
        keyIndex.put(key, keys.size());
        keys.add(key);
    }

    private void remove(long key) {
        SoilData removed = data.remove(key);
        if (removed == null) {
            return;
        }

        int index = keyIndex.remove(key);
        if (index < 0 || index >= keys.size()) {
            return;
        }

        int lastIndex = keys.size() - 1;
        if (index != lastIndex) {
            long lastKey = keys.getLong(lastIndex);
            keys.set(index, lastKey);
            keyIndex.put(lastKey, index);
        }
        keys.removeLong(lastIndex);
        if (cursor > keys.size()) {
            cursor = keys.size();
        }
    }

    private static int clampFatigue(int fatigue) {
        if (fatigue < 0) {
            return 0;
        }
        if (fatigue > 5) {
            return 5;
        }
        return fatigue;
    }

    private static final class SoilData {
        private int fatigue = 0;
        private ResourceLocation lastCrop = null;
    }
}
