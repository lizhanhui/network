package com.yeahmobi.lab.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.AdaptiveRecvByteBufAllocator;
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
import java.util.concurrent.TimeUnit;

public class Client {
    private final EventLoopGroup workers;
    private final Bootstrap bootstrap;

    private static final int BUF_2M = 2 * 1024 * 1024;
    private static final int BUF_8M = 8 * 1024 * 1024;
    private static final int BUF_16M = 16 * 1024 * 1024;

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
                    pipeline.addLast(new ChannelInboundHandlerAdapter() {
                        @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            ctx.write(msg);
                        }

                        @Override public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                            super.channelReadComplete(ctx);
                            ctx.flush();
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            super.exceptionCaught(ctx, cause);
                            ctx.close();
                        }
                    });
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
}
