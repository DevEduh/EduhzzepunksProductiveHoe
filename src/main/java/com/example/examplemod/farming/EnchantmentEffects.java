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

    public static boolean shouldConsumeSeed(RandomSource random, int level) {
        if (level <= 0) {
            return true;
        }

        float noConsumeChance = switch (level) {
            case 1 -> 0.15F;
            case 2 -> 0.25F;
            default -> 0.40F;
        };
        return random.nextFloat() >= noConsumeChance;
    }

    public static void applyBountifulSeedBonus(RandomSource random, List<ItemStack> drops, int level, ItemStack seedTemplate) {
        if (level <= 0 || drops.isEmpty()) {
            return;
        }

        boolean hasNonSeed = false;
        if (!seedTemplate.isEmpty()) {
            for (ItemStack drop : drops) {
                if (drop.isEmpty()) {
                    continue;
                }
                if (!ItemStack.isSameItemSameTags(drop, seedTemplate)) {
                    hasNonSeed = true;
                    break;
                }
            }
        }

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            boolean isSeed = !seedTemplate.isEmpty() && ItemStack.isSameItemSameTags(drop, seedTemplate);
            if (isSeed && hasNonSeed) {
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
        int roll = random.nextInt(level + 2) - 1;
        return Math.max(0, roll);
    }
}
