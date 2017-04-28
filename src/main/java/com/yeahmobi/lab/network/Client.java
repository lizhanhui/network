package com.yeahmobi.lab.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Client {
    private final EventLoopGroup workers;
    private final Bootstrap bootstrap;

    private static final int BUF_2M = 2 * 1024 * 1024;
    private static final int BUF_8M = 8 * 1024 * 1024;
    private static final int BUF_16M = 16 * 1024 * 1024;
    public static ByteBuf data;

    static {
        byte[] randomData = new byte[1024 * 1024 * 4];
        Arrays.fill(randomData, (byte)'x');
        data = Unpooled.wrappedBuffer(randomData);
    }

    private volatile boolean started;

    public Client() {
        workers = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
    }

    public void start(int sendBufferSize) {
        if (started) {
            return;
        }

        bootstrap.group(workers)
            .channel(NioSocketChannel.class)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .option(ChannelOption.RCVBUF_ALLOCATOR,
                new AdaptiveRecvByteBufAllocator(BUF_2M, BUF_8M, BUF_16M))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .option(ChannelOption.SO_SNDBUF, sendBufferSize > 0 ? sendBufferSize : 16 * 1024 * 1024)
            .handler(new ChannelInitializer<SocketChannel>() {
                protected void initChannel(SocketChannel channel) throws Exception {
                    ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast(new TestChannelHandler());
                }
            });

        started = true;
    }

    public Channel connect(int sendBufferSize, String ip) {
        if (!started) {
            start(sendBufferSize);
        }

        ChannelFuture future =  bootstrap.connect(ip, 1234);
        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    System.out.println("Should be stressing network");
                }
            }
        });

        try {
            if (!future.sync().await(10, TimeUnit.SECONDS)) {
                future.channel().close();
                return null;
            }
        } catch (InterruptedException e) {
            return null;
        }

        return future.channel();
    }

    static class TestChannelHandler extends ChannelInboundHandlerAdapter {
        @Override public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Channel active");
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.close();
            cause.printStackTrace();
            System.out.println("Channel closed");
        }

        @Override public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Channel inactive");
        }

        @Override public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            System.out.println("Event: " + evt);
        }

        @Override public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Channel registered");
        }

        @Override public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Channel unregistered");
        }

        @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            System.out.println("channel read");
        }

        @Override public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            System.out.println("channel read complete");
        }

        @Override public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {

        }
    }

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("command sndBuf IP");
            return;
        }

        Client client = new Client();
        Channel channel = client.connect(Integer.parseInt(args[0]), args[1]);
        while (channel.isActive()) {
            try {
                if (channel.isWritable()) {
                    channel.writeAndFlush(data.slice().retain());
                } else {
                    Thread.sleep(0);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
