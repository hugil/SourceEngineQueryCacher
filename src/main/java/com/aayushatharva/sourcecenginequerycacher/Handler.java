package com.aayushatharva.sourcecenginequerycacher;

import com.aayushatharva.sourcecenginequerycacher.utils.CacheHub;
import com.aayushatharva.sourcecenginequerycacher.utils.Config;
import com.aayushatharva.sourcecenginequerycacher.utils.Packets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.SplittableRandom;

@ChannelHandler.Sharable
final class Handler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LogManager.getLogger(Handler.class);
    private static final SplittableRandom RANDOM = new SplittableRandom();

    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket datagramPacket) {

        if (Config.Stats_PPS) {
            Stats.PPS.incrementAndGet();
        }

        if (Config.Stats_bPS) {
            Stats.BPS.addAndGet(datagramPacket.content().readableBytes());
        }

        /*
         * If A2S_INFO or A2S_PLAYER or A2S_RULES is not readable, drop request because we've nothing to reply.
         */
        if (!CacheHub.A2S_INFO.isReadable() || !CacheHub.A2S_PLAYER.isReadable() || !CacheHub.A2S_RULES.isReadable()) {
            logger.error("Dropping query request because Cache is not ready. A2S_INFO: {}, A2S_PLAYER: {}, A2S_RULES: {}",
                    CacheHub.A2S_INFO, CacheHub.A2S_PLAYER, CacheHub.A2S_RULES);
            return;
        }

        int pckLength = datagramPacket.content().readableBytes();

        /*
         * Packet size of 25, 29 bytes and 9 bytes only will be processed rest will dropped.
         *
         * A2S_INFO = 25 Bytes, 29 bytes with padded challenge code
         * A2S_Player = 9 Bytes
         * A2S_RULES = 9 Bytes
         */
        if (pckLength == 9 || pckLength == 25 || pckLength == 29) {

            if (ByteBufUtil.equals(Packets.A2S_RULES_REQUEST_HEADER, datagramPacket.content().slice(0, Packets.A2S_RULES_REQUEST_HEADER_LEN))) {

                /* 1. Packet equals `A2S_RULES_CHALLENGE_REQUEST_1` or `A2S_RULES_CHALLENGE_REQUEST_2`
                 * then we'll send response of A2S_Challenge Packet.
                 */
                if (ByteBufUtil.equals(datagramPacket.content(), Packets.A2S_RULES_CHALLENGE_REQUEST_2) ||
                        ByteBufUtil.equals(datagramPacket.content(), Packets.A2S_RULES_CHALLENGE_REQUEST_1)) {
                    sendA2SChallenge(ctx, datagramPacket);
                } else {
                    //2. Validate A2S_RULES Challenge Response and send A2S_Rules Packet.
                    sendA2SRulesResponse(ctx, datagramPacket);
                }
                return;
            } else if (ByteBufUtil.equals(Packets.A2S_PLAYER_REQUEST_HEADER, datagramPacket.content().slice(0, Packets.A2S_PLAYER_REQUEST_HEADER_LEN))) {

                /* 1. Packet equals to `A2S_PLAYER_CHALLENGE_REQUEST_1` or `A2S_PLAYER_CHALLENGE_REQUEST_2`
                 * then we'll send response of A2S_Player Challenge Packet.
                 */
                if (ByteBufUtil.equals(datagramPacket.content(), Packets.A2S_PLAYER_CHALLENGE_REQUEST_2) ||
                        ByteBufUtil.equals(datagramPacket.content(), Packets.A2S_PLAYER_CHALLENGE_REQUEST_1)) {
                    sendA2SChallenge(ctx, datagramPacket);
                } else {
                    //2. Validate A2S_Player Challenge Response and send A2S_Player Packet.
                    sendA2SPlayerResponse(ctx, datagramPacket);
                }
                return;
            }
            if (ByteBufUtil.equals(Packets.A2S_INFO_REQUEST, datagramPacket.content().slice(0, Packets.A2S_INFO_REQUEST_LEN))) {

                /*
                 * 1. Packet equals to `A2S_INFO_REQUEST` with length==25 (A2S_INFO without challenge code)
                 * then we'll send response of A2S_Challenge Packet.
                 *
                 * 2. Validate A2S_INFO Challenge Response (length==29) and send A2S_INFO Packet.
                 */
                if (datagramPacket.content().readableBytes() == Packets.A2S_INFO_REQUEST_LEN) {
                    sendA2SChallenge(ctx, datagramPacket);
                } else if (datagramPacket.content().readableBytes() == Packets.A2S_INFO_REQUEST_LEN + Packets.LEN_CODE) { // 4 Byte padded challenge Code
                    sendA2SInfoResponse(ctx, datagramPacket);
                }
                return;
            }
        }

        dropLog(datagramPacket);
    }

    private void sendA2SChallenge(ChannelHandlerContext ctx, DatagramPacket datagramPacket) {
        byte[] challenge = CacheHub.CHALLENGE_MAP.computeIfAbsent(datagramPacket.sender().getAddress().getAddress(), key -> {
            // Generate Random Data of 4 Bytes only if there is no challenge code for this IP
            byte[] challengeCode = new byte[Packets.LEN_CODE];
            RANDOM.nextBytes(challengeCode);
            return challengeCode;
        });

        // Send A2S CHALLENGE Packet
        ByteBuf byteBuf = ctx.alloc().buffer();
        byteBuf.writeBytes(Packets.A2S_CHALLENGE_RESPONSE_HEADER.retainedDuplicate());
        byteBuf.writeBytes(challenge);
        ctx.writeAndFlush(new DatagramPacket(byteBuf, datagramPacket.sender()), ctx.voidPromise());
    }

    private void sendA2SPlayerResponse(ChannelHandlerContext ctx, DatagramPacket datagramPacket) {
        if (isIPValid(datagramPacket, Arrays.copyOfRange(ByteBufUtil.getBytes(datagramPacket.content()),
                Packets.A2S_PLAYER_CODE_POS, Packets.A2S_PLAYER_CODE_POS + Packets.LEN_CODE), "A2S_PLAYER")) {
            ctx.writeAndFlush(new DatagramPacket(CacheHub.A2S_PLAYER.retainedDuplicate(), datagramPacket.sender()), ctx.voidPromise());
        }
    }

    private void sendA2SRulesResponse(ChannelHandlerContext ctx, DatagramPacket datagramPacket) {
        if (isIPValid(datagramPacket, Arrays.copyOfRange(ByteBufUtil.getBytes(datagramPacket.content()),
                Packets.A2S_RULES_CODE_POS, Packets.A2S_RULES_CODE_POS + Packets.LEN_CODE), "A2S_RULES")) {
            ctx.writeAndFlush(new DatagramPacket(CacheHub.A2S_RULES.retainedDuplicate(), datagramPacket.sender()), ctx.voidPromise());
        }
    }

    private void sendA2SInfoResponse(ChannelHandlerContext ctx, DatagramPacket datagramPacket) {
        if (isIPValid(datagramPacket, Arrays.copyOfRange(ByteBufUtil.getBytes(datagramPacket.content()),
                Packets.A2S_INFO_CODE_POS, Packets.A2S_INFO_CODE_POS + Packets.LEN_CODE), "A2S_INFO")) {
            ctx.writeAndFlush(new DatagramPacket(CacheHub.A2S_INFO.retainedDuplicate(), datagramPacket.sender()), ctx.voidPromise());
        }
    }

    private boolean isIPValid(DatagramPacket datagramPacket, byte[] challengeCode, String logTrace) {
        // Look for  Client IP Address in Cache and load Challenge Code Value from it.
        byte[] storedChallengeCode = CacheHub.CHALLENGE_MAP.remove(datagramPacket.sender().getAddress().getAddress());

        // If Cache Value is not NULL it means we found the IP, and now we'll validate it.
        if (storedChallengeCode != null) {
            // Match received challenge code against Cache Stored challenge code
            if (Arrays.equals(storedChallengeCode, challengeCode)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Valid Challenge Code ({}) received from {}:{} [{}][REQUEST ACCEPTED]", toHexString(challengeCode),
                            datagramPacket.sender().getAddress().getHostAddress(), datagramPacket.sender().getPort(), logTrace);
                }
                return true;
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Invalid Challenge Code ({}) received from {}:{} Expected Code: {} [{}][REQUEST DROPPED]", toHexString(challengeCode),
                            datagramPacket.sender().getAddress().getHostAddress(), datagramPacket.sender().getPort(), storedChallengeCode, logTrace);
                }
                return false;
            }
        } else {
            if (logger.isDebugEnabled()) {
                // If you see lots of messages like this in the log, try raising the ChallengeCodeTTL (best practise is 2000)
                logger.debug("Unknown (Old?) Challenge Code ({}) received from {}:{} [{}][REQUEST DROPPED]", toHexString(challengeCode),
                        datagramPacket.sender().getAddress().getHostAddress(), datagramPacket.sender().getPort(), logTrace);
            }
            return false;
        }
    }

    private void dropLog(DatagramPacket packet) {
        if (logger.isDebugEnabled()) {
            logger.debug("Dropping Packet of Length {} bytes from {}:{}", packet.content().readableBytes(),
                    packet.sender().getAddress().getHostAddress(), packet.sender().getPort());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Caught Error", cause);
    }

    /**
     * Convert Byte Array into Hex String
     *
     * @param bytes Byte Array
     * @return Hex String
     */
    private String toHexString(final byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = "0123456789ABCDEF".toCharArray()[v >>> 4];
            hexChars[j * 2 + 1] = "0123456789ABCDEF".toCharArray()[v & 0x0F];
        }
        return new String(hexChars);
    }
}
