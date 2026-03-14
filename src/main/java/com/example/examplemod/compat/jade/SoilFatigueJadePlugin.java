package com.example.examplemod.compat.jade;

import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.CropBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class SoilFatigueJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SoilFatigueJadeProvider.INSTANCE, FarmBlock.class);
        registration.registerBlockComponent(SoilFatigueJadeProvider.INSTANCE, CropBlock.class);
    }
}
