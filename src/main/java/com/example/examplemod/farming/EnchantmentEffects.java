package com.example.examplemod.farming;

import com.example.examplemod.enchant.ModEnchantments;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.List;

public final class EnchantmentEffects {
    private EnchantmentEffects() {
    }

    public static int getAcreageLevel(ItemStack tool) {
        return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ACREAGE.get(), tool);
    }

    public static int getBountifulLevel(ItemStack tool) {
        return EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.BOUNTIFUL_SEED.get(), tool);
    }

    public static void applyBountifulSeedBonus(RandomSource random, List<ItemStack> drops, int level) {
        if (level <= 0 || drops.isEmpty()) {
            return;
        }

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            int extra = rollFortuneLikeBonus(random, level);
            if (extra > 0) {
                drop.grow(extra);
            }
        }
    }

    private static int rollFortuneLikeBonus(RandomSource random, int level) {
        if (level <= 0) {
            return 0;
        }
        return random.nextInt(level + 1);
    }
}
