package com.moonx.numa_bridge_mod.network;

import com.moonx.numa_bridge_mod.NUMABridgeMod;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.FriendlyByteBuf;

/**
 * CustomChannelHandler
 * 
 * Ini adalah inti sistem: handler yang numpang di Netty pipeline Minecraft.
 * 
 * Cara kerjanya:
 * - channelRead()  → intercept packet MASUK dari client ke server
 * - write()        → intercept packet KELUAR dari server ke client
 * 
 * Untuk deteksi "apakah client punya mod kita", kita pakai pendekatan:
 * Client mod akan kirim custom "magic bytes" di awal koneksi.
 * Kalau bytes itu tidak datang dalam X detik → vanilla client.
 */
public class CustomChannelHandler extends ChannelDuplexHandler {

    // Magic identifier: 8 bytes unik untuk protokol kita
    // Ini yang dikirim client mod untuk "jabat tangan"
    public static final byte[] MAGIC_HANDSHAKE = {
        (byte) 0x4E, (byte) 0x45, (byte) 0x54, (byte) 0x54,  // "NETT"
        (byte) 0x59, (byte) 0x4D, (byte) 0x4F, (byte) 0x44   // "YMOD"
    };

    // Response server ke client setelah handshake berhasil
    public static final byte[] HANDSHAKE_ACK = {
        (byte) 0x41, (byte) 0x43, (byte) 0x4B, (byte) 0x21   // "ACK!"
    };

    private final String playerName;
    private boolean handshakeReceived = false;
    private boolean isModdedClient = false;

    public CustomChannelHandler(String playerName) {
        this.playerName = playerName;
    }

    /**
     * Intercept packet MASUK (client → server)
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // msg di Minecraft adalah FriendlyByteBuf (wrapped ByteBuf)
        if (msg instanceof ByteBuf buf && !handshakeReceived) {
            // Cek apakah ini magic handshake dari client mod kita
            if (isCustomHandshake(buf)) {
                handshakeReceived = true;
                isModdedClient = true;
                NettyMod.LOGGER.info("[NettyMod] Modded client detected: {}", playerName);

                // Kirim ACK balik ke client
                sendHandshakeAck(ctx);

                // Jangan forward packet ini ke Minecraft (sudah kita consume)
                buf.release();
                return;
            }
            // Bukan magic kita → reset reader index, forward ke Minecraft seperti biasa
            buf.resetReaderIndex();
        }

        // Forward ke handler berikutnya di pipeline (Minecraft normal)
        super.channelRead(ctx, msg);
    }

    /**
     * Intercept packet KELUAR (server → client)
     * Kamu bisa tambah logic di sini untuk inject data ke outbound packets
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // Untuk sekarang, kita cuma pass-through
        // Di sini kamu bisa intercept packet tertentu, modifikasi, atau tambah metadata
        super.write(ctx, msg, promise);
    }

    /**
     * Dipanggil saat channel aktif (koneksi established)
     * Di sinilah kamu bisa kirim "challenge" ke client
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NettyMod.LOGGER.debug("[NettyMod] Channel active for: {}", playerName);
        // Optional: bisa kirim "challenge" ke client di sini
        // Client mod yang ngerti akan reply dengan MAGIC_HANDSHAKE
        super.channelActive(ctx);
    }

    /**
     * Cek apakah ByteBuf berisi magic handshake kita
     */
    private boolean isCustomHandshake(ByteBuf buf) {
        if (buf.readableBytes() < MAGIC_HANDSHAKE.length) return false;

        buf.markReaderIndex();
        for (byte b : MAGIC_HANDSHAKE) {
            if (buf.readByte() != b) {
                buf.resetReaderIndex();
                return false;
            }
        }
        return true;
    }

    /**
     * Kirim ACK response ke client
     */
    private void sendHandshakeAck(ChannelHandlerContext ctx) {
        ByteBuf ackBuf = Unpooled.buffer(HANDSHAKE_ACK.length);
        ackBuf.writeBytes(HANDSHAKE_ACK);
        ctx.writeAndFlush(ackBuf);
        NettyMod.LOGGER.info("[NettyMod] Sent ACK to: {}", playerName);
    }

    /**
     * Getter: apakah client ini punya mod kita?
     */
    public boolean isModdedClient() {
        return isModdedClient;
    }

    /**
     * Exception handler
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        NettyMod.LOGGER.warn("[NettyMod] Exception in handler for {}: {}", playerName, cause.getMessage());
        // PENTING: jangan close channel di sini kecuali kamu yakin
        // Kalau close, player akan di-disconnect
        super.exceptionCaught(ctx, cause);
    }
}