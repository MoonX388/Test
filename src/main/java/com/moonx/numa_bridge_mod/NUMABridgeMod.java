package com.moonx.numa_bridge_mod;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(NUMABridgeMod.MOD_ID)
public class NUMABridgeMod {
    public static final String MOD_ID = "nettymod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NUMABridgeMod(IEventBus modEventBus) {
        // Register event handlers ke NeoForge event bus
        NeoForge.EVENT_BUS.register(NetworkEventHandler.class);
        LOGGER.info("[NettyMod] Mod loaded.");
    }
}