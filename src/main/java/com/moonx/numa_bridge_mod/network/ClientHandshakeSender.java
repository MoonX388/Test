package com.moonx.numa_bridge_mod.network;

import com.moonx.numa_bridge_mod.NUMA;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.lang.reflect.Field;

/**
 * ClientHandshakeSender
 * 
 * Ini adalah sisi CLIENT dari protokol kita.
 * Saat client connect ke server, mod ini akan:
 * 1. Inject handler ke pipeline client
 * 2. Kirim MAGIC_HANDSHAKE ke server
 * 3. Tunggu ACK dari server
 * 
 * Kalau ACK datang → server juga punya mod kita ✓
 * Kalau tidak → server vanilla, skip
 */
@OnlyIn(Dist.CLIENT)
public class ClientHandshakeSender extends ChannelInboundHandlerAdapter {

    private boolean handshakeSent = false;
    private boolean serverHasMod = false;

    /**
     * Dipanggil saat channel pertama kali aktif (connected)
     * Ini timing yang tepat untuk kirim handshake
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!handshakeSent) {
            sendHandshake(ctx);
            handshakeSent = true;
        }
        super.channelActive(ctx);
    }

    /**
     * Intercept response dari server
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf && !serverHasMod) {
            if (isHandshakeAck(buf)) {
                serverHasMod = true;
                NettyMod.LOGGER.info("[NettyMod] Server has mod! Handshake complete.");
                buf.release();
                return;
            }
            buf.resetReaderIndex();
        }
        super.channelRead(ctx, msg);
    }

    private void sendHandshake(ChannelHandlerContext ctx) {
        ByteBuf buf = Unpooled.buffer(CustomChannelHandler.MAGIC_HANDSHAKE.length);
        buf.writeBytes(CustomChannelHandler.MAGIC_HANDSHAKE);
        ctx.writeAndFlush(buf);
        NettyMod.LOGGER.info("[NettyMod] Sent handshake to server");
    }

    private boolean isHandshakeAck(ByteBuf buf) {
        if (buf.readableBytes() < CustomChannelHandler.HANDSHAKE_ACK.length) return false;
        buf.markReaderIndex();
        for (byte b : CustomChannelHandler.HANDSHAKE_ACK) {
            if (buf.readByte() != b) {
                buf.resetReaderIndex();
                return false;
            }
        }
        return true;
    }

    public boolean isServerHasMod() {
        return serverHasMod;
    }

    /**
     * Static helper: inject handler ini ke pipeline client Minecraft
     * Panggil ini dari ClientPlayerNetworkEvent.LoggingIn
     */
    public static ClientHandshakeSender injectIntoClientPipeline() {
        try {
            Minecraft mc = Minecraft.getInstance();
            ClientPacketListener listener = mc.getConnection();
            if (listener == null) return null;

            Field connField = ClientPacketListener.class.getDeclaredField("connection");
            connField.setAccessible(true);
            Connection connection = (Connection) connField.get(listener);

            Field channelField = Connection.class.getDeclaredField("channel");
            channelField.setAccessible(true);
            Channel channel = (Channel) channelField.get(connection);

            ClientHandshakeSender sender = new ClientHandshakeSender();
            if (channel.pipeline().get("nettymod_client_handler") == null) {
                channel.pipeline().addFirst("nettymod_client_handler", sender);
                NettyMod.LOGGER.info("[NettyMod] Client handler injected");
            }
            return sender;

        } catch (Exception e) {
            NettyMod.LOGGER.error("[NettyMod] Failed to inject client handler: {}", e.getMessage());
            return null;
        }
    }
}