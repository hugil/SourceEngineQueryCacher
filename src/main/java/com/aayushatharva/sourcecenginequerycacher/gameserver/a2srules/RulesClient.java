package com.aayushatharva.sourcecenginequerycacher.gameserver.a2srules;

import com.aayushatharva.sourcecenginequerycacher.Main;
import com.aayushatharva.sourcecenginequerycacher.utils.Config;
import com.aayushatharva.sourcecenginequerycacher.utils.Packets;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RulesClient extends Thread {

    private static final Logger logger = LogManager.getLogger(RulesClient.class);
    private boolean keepRunning = true;

    public RulesClient(String name) {
        super(name);
    }

    @SuppressWarnings("BusyWait")
    public void run() {
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(Main.eventLoopGroup)
                    .channelFactory(EpollDatagramChannel::new)
                    .option(ChannelOption.ALLOCATOR, Main.BYTE_BUF_ALLOCATOR)
                    .option(ChannelOption.SO_SNDBUF, Config.SendBufferSize)
                    .option(ChannelOption.SO_RCVBUF, Config.ReceiveBufferSize)
                    .option(ChannelOption.RCVBUF_ALLOCATOR, new AdaptiveRecvByteBufAllocator(Config.ReceiveAllocatorBufferSizeMin, Config.ReceiveAllocatorBufferSize, Config.ReceiveAllocatorBufferSizeMax))
                    .handler(new RulesHandler());

            Channel channel = bootstrap.connect(Config.GameServer).sync().channel();

            while (keepRunning) {
                channel.writeAndFlush(Packets.A2S_RULES_CHALLENGE_REQUEST_2.retainedDuplicate()).sync();
                try{
                    sleep(Config.GameUpdateInterval);
                } catch(InterruptedException e){
                    logger.error("Error at RulesClient During Interval Sleep ", e);
                    break;
                }

            }

            channel.closeFuture().sync();
        } catch (Exception ex) {
            logger.atError().withThrowable(ex).log("Error occurred");
        }
    }

    public void shutdown() {
        this.keepRunning = false;
        this.interrupt();
    }
}
