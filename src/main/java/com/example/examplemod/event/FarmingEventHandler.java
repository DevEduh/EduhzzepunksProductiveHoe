package com.example.examplemod.event;

import com.example.examplemod.ProductiveHoeMod;
import com.example.examplemod.farming.CropDetection;
import com.example.examplemod.farming.EnchantmentEffects;
import com.example.examplemod.farming.HarvestLogic;
import com.example.examplemod.util.PlantCleanupUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ProductiveHoeMod.MODID)
public final class FarmingEventHandler {
    private FarmingEventHandler() {
    }

    @SubscribeEvent
    public static void onRightClickCropWithHoe(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();
        if (!(heldItem.getItem() instanceof HoeItem)) {
            return;
        }

        BlockPos clickedPos = event.getPos();
        BlockState clickedState = serverLevel.getBlockState(clickedPos);
        int acreageLevel = EnchantmentEffects.getAcreageLevel(heldItem);
        if (CropDetection.getMatureCrop(clickedState) == null) {
            if (!PlantCleanupUtil.isReplaceablePlant(clickedState)) {
                return;
            }

            Direction facing = player.getDirection();
            if (facing.getAxis() == Direction.Axis.Y) {
                facing = Direction.fromYRot(player.getYRot());
            }
            int removedPlants = PlantCleanupUtil.clearReplaceablePlantsArea(serverLevel, player, heldItem, clickedPos, facing, acreageLevel);
            if (removedPlants <= 0) {
                return;
            }

            PlantCleanupUtil.playCleanupSweepFeedback(serverLevel, player, facing, removedPlants);

            int cleanupDamage = PlantCleanupUtil.calculateCleanupDurabilityDamage(removedPlants);
            if (cleanupDamage > 0) {
                heldItem.hurtAndBreak(cleanupDamage, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(InteractionHand.MAIN_HAND));
            }

            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        boolean shiftHeld = player.isShiftKeyDown();

        int harvestedCount;
        if (shiftHeld) {
            Direction.Axis rowAxis = Direction.fromYRot(player.getYRot()).getAxis();
            harvestedCount = HarvestLogic.harvestRow(serverLevel, player, heldItem, clickedPos, rowAxis, acreageLevel);
        } else if (acreageLevel > 0) {
            harvestedCount = HarvestLogic.harvestAreaByAcreageLevel(serverLevel, player, heldItem, clickedPos, acreageLevel);
        } else {
            harvestedCount = HarvestLogic.harvestSingle(serverLevel, player, heldItem, clickedPos);
        }

        if (harvestedCount <= 0) {
            return;
        }

        int durabilityDamage = HarvestLogic.rollDurabilityDamage(serverLevel.random, harvestedCount);
        if (durabilityDamage > 0) {
            heldItem.hurtAndBreak(durabilityDamage, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
