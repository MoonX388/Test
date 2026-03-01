package com.moonx.numa_bridge_mod;

import com.moonx.numa_bridge_mod.network.CustomChannelHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import java.lang.reflect.Field;

public class NetworkEventHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        try {
            // Ambil Connection dari ServerGamePacketListenerImpl
            Connection connection = serverPlayer.connection.connection;

            // Ambil Netty Channel dari Connection
            // Field name "channel" di obfuscated env, pakai AT atau reflection
            Field channelField = Connection.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            Channel channel = (Channel) channelField.get(connection);

            // Inject custom handler SEBELUM "packet_handler" di pipeline
            // "packet_handler" adalah nama handler utama Minecraft di pipeline
            if (channel.pipeline().get("nettymod_handler") == null) {
                channel.pipeline().addBefore("packet_handler", "nettymod_handler",
                        new CustomChannelHandler(serverPlayer.getGameProfile().getName()));
                NUMABridgeMod.LOGGER.info("[NettyMod] Handler injected for player: {}",
                        serverPlayer.getGameProfile().getName());
            }

        } catch (NoSuchFieldException | IllegalAccessException e) {
            NUMABridgeMod.LOGGER.error("[NettyMod] Failed to inject Netty handler: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        try {
            Connection connection = serverPlayer.connection.connection;
            Field channelField = Connection.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            Channel channel = (Channel) channelField.get(connection);

            // Cleanup: remove handler saat player disconnect
            if (channel.pipeline().get("nettymod_handler") != null) {
                channel.pipeline().remove("nettymod_handler");
                NUMABridgeMod.LOGGER.info("[NettyMod] Handler removed for player: {}",
                        serverPlayer.getGameProfile().getName());
            }

        } catch (Exception e) {
            NUMABridgeMod.LOGGER.warn("[NettyMod] Failed to remove handler: {}", e.getMessage());
        }
    }
}