package com.example.examplemod.compat.jade;

import com.example.examplemod.ProductiveHoeMod;
import com.example.examplemod.client.SoilFatigueClientOverlay;
import com.example.examplemod.farming.CropDetection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum SoilFatigueJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ProductiveHoeMod.MODID, "soil_fatigue");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!config.get(UID)) {
            return;
        }

        BlockPos farmlandPos = resolveFarmlandPos(accessor);
        if (farmlandPos == null) {
            return;
        }

        int fatigue = SoilFatigueClientOverlay.getCachedFatigue(farmlandPos);
        if (fatigue < 0) {
            return;
        }
        int fatiguePercent = Math.max(0, Math.min(100, fatigue * 20));
        float penalty = Math.min(0.2F * fatigue, 0.95F);
        int blockedPercent = Math.round(penalty * 100.0F);

        tooltip.add(Component.translatable("tooltip.eduhzzepunks_productive_hoe.soil_fatigue", fatigue, fatiguePercent));
        tooltip.add(Component.translatable("tooltip.eduhzzepunks_productive_hoe.soil_growth_blocked", blockedPercent));
    }

    private static BlockPos resolveFarmlandPos(BlockAccessor accessor) {
        BlockState state = accessor.getBlockState();
        BlockPos pos = accessor.getPosition();

        if (state.is(Blocks.FARMLAND)) {
            return pos;
        }

        if (!CropDetection.isCrop(state)) {
            return null;
        }

        BlockPos farmlandPos = pos.below();
        if (accessor.getLevel().getBlockState(farmlandPos).is(Blocks.FARMLAND)) {
            return farmlandPos;
        }

        return null;
    }
}
