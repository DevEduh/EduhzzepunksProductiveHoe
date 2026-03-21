package com.example.examplemod.config;

import com.example.examplemod.ProductiveHoeMod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = ProductiveHoeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigEvents {
    private ConfigEvents() {
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ProductiveHoeConfig.SPEC) {
            ProductiveHoeConfig.bake();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ProductiveHoeConfig.SPEC) {
            ProductiveHoeConfig.bake();
        }
    }
}
