package com.example.examplemod;

import com.example.examplemod.enchant.ModEnchantments;
import com.example.examplemod.network.ModNetworking;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ProductiveHoeMod.MODID)
public class ProductiveHoeMod {
    public static final String MODID = "eduhzzepunks_productive_hoe";

    public ProductiveHoeMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModEnchantments.register(modEventBus);
        ModNetworking.register();
    }
}
