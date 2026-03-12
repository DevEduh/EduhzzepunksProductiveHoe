package com.example.examplemod.network;

import com.example.examplemod.farming.SoilFatigueManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class SoilFatigueRequest {
    private final BlockPos pos;

    public SoilFatigueRequest(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(SoilFatigueRequest message, net.minecraft.network.FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
    }

    public static SoilFatigueRequest decode(net.minecraft.network.FriendlyByteBuf buffer) {
        return new SoilFatigueRequest(buffer.readBlockPos());
    }

    public static void handle(SoilFatigueRequest message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            ServerLevel level = player.serverLevel();
            BlockPos pos = message.pos;
            if (!level.getBlockState(pos).is(Blocks.FARMLAND)) {
                return;
            }

            int fatigue = SoilFatigueManager.get(level).getFatigue(pos);
            ModNetworking.sendToPlayer(player, new SoilFatigueResponse(pos, fatigue));
        });
        context.setPacketHandled(true);
    }
}
