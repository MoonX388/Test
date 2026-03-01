package com.moonx.numa_bridge_mod;

import com.moonx.numa_bridge_mod.network.ClientHandshakeSender;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.network.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = NUMABridgeMod.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onClientConnecting(ClientPlayerNetworkEvent.LoggingIn event) {
        // Inject client-side handler saat mulai connect ke server
        ClientHandshakeSender sender = ClientHandshakeSender.injectIntoClientPipeline();
        if (sender != null) {
            NUMABridgeMod.LOGGER.info("[NettyMod] Client handler ready, waiting for server ACK...");
        }
    }
}