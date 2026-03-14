package com.example.examplemod.client;

import com.example.examplemod.ProductiveHoeMod;
import com.example.examplemod.network.ModNetworking;
import com.example.examplemod.network.SoilFatigueRequest;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = ProductiveHoeMod.MODID, value = Dist.CLIENT)
public final class SoilFatigueClientOverlay {
    private static final int REQUEST_COOLDOWN_TICKS = 10;
    private static final int STALE_TICKS = 60;

    private static final Long2IntOpenHashMap FATIGUE_CACHE = new Long2IntOpenHashMap();
    private static final Long2LongOpenHashMap LAST_REQUEST_TICK = new Long2LongOpenHashMap();
    private static final Long2LongOpenHashMap LAST_UPDATE_TICK = new Long2LongOpenHashMap();

    static {
        FATIGUE_CACHE.defaultReturnValue(-1);
        LAST_REQUEST_TICK.defaultReturnValue(Long.MIN_VALUE);
        LAST_UPDATE_TICK.defaultReturnValue(Long.MIN_VALUE);
    }

    private SoilFatigueClientOverlay() {
    }

    public static void updateFatigue(BlockPos pos, int fatigue) {
        long key = pos.asLong();
        FATIGUE_CACHE.put(key, fatigue);
        long time = 0L;
        if (Minecraft.getInstance().level != null) {
            time = Minecraft.getInstance().level.getGameTime();
        }
        LAST_UPDATE_TICK.put(key, time);
    }

    public static int getCachedFatigue(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return -1;
        }

        long key = pos.asLong();
        long gameTime = minecraft.level.getGameTime();

        long lastRequest = LAST_REQUEST_TICK.get(key);
        if (gameTime - lastRequest >= REQUEST_COOLDOWN_TICKS) {
            LAST_REQUEST_TICK.put(key, gameTime);
            ModNetworking.sendToServer(new SoilFatigueRequest(pos));
        }

        long lastUpdate = LAST_UPDATE_TICK.get(key);
        if (gameTime - lastUpdate > STALE_TICKS) {
            return -1;
        }

        return FATIGUE_CACHE.get(key);
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        HitResult hit = minecraft.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) {
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        if (!minecraft.level.getBlockState(pos).is(Blocks.FARMLAND)) {
            return;
        }

        int fatigue = getCachedFatigue(pos);
        if (fatigue < 0) {
            return;
        }

        float penalty = Math.min(0.2F * fatigue, 0.95F);
        int quality = Math.round((1.0F - penalty) * 100.0F);
        List<Component> lines = List.of(
                Component.literal("Soil Fatigue: " + fatigue + " / 5"),
                Component.literal("Soil Quality: " + quality + "%")
        );

        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = screenWidth / 2 + 8;
        int y = screenHeight / 2 + 8;
        guiGraphics.renderTooltip(minecraft.font, lines, Optional.empty(), x, y);
    }
}
