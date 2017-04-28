package com.yeahmobi.lab.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
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

    public void start() {
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
            .handler(new ChannelInitializer<SocketChannel>() {
                protected void initChannel(SocketChannel channel) throws Exception {
                    ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast(new StressChannelHandler());
                }
            });

        started = true;
    }

    public void connect() {
        if (!started) {
            start();
        }

        ChannelFuture future =  bootstrap.connect("172.30.30.68", 1234);
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
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Client().connect();
    }

    static class StressChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

        public StressChannelHandler() {
            super(false);
        }

        @Override public void channelActive(final ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            System.out.println("Initiate writing");
            ctx.writeAndFlush(data.slice()).addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        System.out.println("Closing connection");
                        ctx.close();
                    }
                }
            });
        }

        protected void channelRead0(ChannelHandlerContext context, ByteBuf buf) throws Exception {
            context.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        System.out.println("Closing connection");
                        future.channel().close();
                    }
                }
            });
        }

        @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ctx.close();
        }
    }
}
