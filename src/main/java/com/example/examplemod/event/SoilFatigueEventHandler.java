package com.example.examplemod.event;

import com.example.examplemod.ProductiveHoeMod;
import com.example.examplemod.farming.CropDetection;
import com.example.examplemod.farming.SoilFatigueManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProductiveHoeMod.MODID)
public final class SoilFatigueEventHandler {
    private SoilFatigueEventHandler() {
    }

    @SubscribeEvent
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        if (!CropDetection.isCrop(state)) {
            return;
        }

        BlockPos farmlandPos = pos.below();
        if (!level.getBlockState(farmlandPos).is(Blocks.FARMLAND)) {
            return;
        }

        SoilFatigueManager manager = SoilFatigueManager.get(level);
        int fatigue = manager.getFatigue(farmlandPos);
        if (manager.shouldBlockGrowth(level, farmlandPos, fatigue)) {
            event.setResult(Event.Result.DENY);
            return;
        }

    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.FARMLAND)) {
            return;
        }

        if (CropDetection.isCrop(level.getBlockState(pos.above()))) {
            return;
        }

        SoilFatigueManager.get(level).resetFatigue(pos);
    }

    @SubscribeEvent
    public static void onCropPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getPlacedBlock();
        CropDetection.CropInfo info = CropDetection.getCropInfo(state);
        if (info == null) {
            return;
        }

        int age = state.getValue(info.ageProperty());
        if (age != 0) {
            return;
        }

        BlockPos farmlandPos = pos.below();
        if (!level.getBlockState(farmlandPos).is(Blocks.FARMLAND)) {
            return;
        }

        SoilFatigueManager.get(level).applyOnReplant(
                level,
                farmlandPos,
                BuiltInRegistries.BLOCK.getKey(state.getBlock())
        );
    }
}
