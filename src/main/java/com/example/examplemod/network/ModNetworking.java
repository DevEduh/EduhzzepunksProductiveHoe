package com.example.examplemod.network;

import com.example.examplemod.ProductiveHoeMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ProductiveHoeMod.MODID, "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    private ModNetworking() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, SoilFatigueRequest.class, SoilFatigueRequest::encode, SoilFatigueRequest::decode, SoilFatigueRequest::handle);
        CHANNEL.registerMessage(nextId++, SoilFatigueResponse.class, SoilFatigueResponse::encode, SoilFatigueResponse::decode, SoilFatigueResponse::handle);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
