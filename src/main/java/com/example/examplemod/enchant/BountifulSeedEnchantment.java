package com.example.examplemod.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class BountifulSeedEnchantment extends Enchantment {
    public static final EnchantmentCategory HOE_ONLY = EnchantmentCategory.create("productive_hoe_bountiful_seed", item -> item instanceof HoeItem);

    public BountifulSeedEnchantment() {
        super(Rarity.RARE, HOE_ONLY, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return switch (level) {
            case 1 -> 8;
            case 2 -> 20;
            default -> 10_000;
        };
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof HoeItem && super.canEnchant(stack);
    }
}
