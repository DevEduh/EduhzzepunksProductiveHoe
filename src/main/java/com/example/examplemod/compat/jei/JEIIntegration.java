package com.example.examplemod.compat.jei;

import com.example.examplemod.ProductiveHoeMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

@JeiPlugin
public class JEIIntegration implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ProductiveHoeMod.MODID, "jei_integration");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(
                Blocks.FARMLAND,
                Component.literal("Soil Fatigue is shown when you look at tilled farmland."),
                Component.literal("Soil Fatigue: 0 / 5 = 100% quality."),
                Component.literal("Soil Fatigue: 5 / 5 = 5% quality.")
        );
    }
}
