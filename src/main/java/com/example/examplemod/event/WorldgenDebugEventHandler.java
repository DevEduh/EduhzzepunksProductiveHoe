package com.example.examplemod.event;

import com.example.examplemod.ProductiveHoeMod;
import com.mojang.logging.LogUtils;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = ProductiveHoeMod.MODID)
public final class WorldgenDebugEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private WorldgenDebugEventHandler() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.debug("[EPH] Worldgen debug handler disabled for runtime compatibility.");
    }
}
