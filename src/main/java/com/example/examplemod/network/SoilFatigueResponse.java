package com.example.examplemod.network;

import com.example.examplemod.client.SoilFatigueClientOverlay;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class SoilFatigueResponse {
    private final BlockPos pos;
    private final int fatigue;

    public SoilFatigueResponse(BlockPos pos, int fatigue) {
        this.pos = pos;
        this.fatigue = fatigue;
    }

    public static void encode(SoilFatigueResponse message, net.minecraft.network.FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeVarInt(message.fatigue);
    }

    public static SoilFatigueResponse decode(net.minecraft.network.FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int fatigue = buffer.readVarInt();
        return new SoilFatigueResponse(pos, fatigue);
    }

    public static void handle(SoilFatigueResponse message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SoilFatigueClientOverlay.updateFatigue(message.pos, message.fatigue)));
        context.setPacketHandled(true);
    }
}
