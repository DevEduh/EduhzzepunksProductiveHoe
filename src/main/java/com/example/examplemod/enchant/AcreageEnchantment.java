package com.example.examplemod.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class AcreageEnchantment extends Enchantment {
    public static final EnchantmentCategory HOE_ONLY = EnchantmentCategory.create("productive_hoe_acreage", item -> item instanceof HoeItem);

    public AcreageEnchantment() {
        super(Rarity.UNCOMMON, HOE_ONLY, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        return switch (level) {
            case 1 -> 10;
            case 2 -> 24;
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
