package com.yeahmobi.lab.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.ReferenceCountUtil;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Server {
    private ServerBootstrap serverBootstrap;
    private final EventLoopGroup boss;
    private final EventLoopGroup workers;

    private final AtomicLong receivedBytes;

    private final ScheduledExecutorService scheduledExecutorService;

    public Server() {
        boss = new NioEventLoopGroup(1);
        workers = new NioEventLoopGroup();
        receivedBytes = new AtomicLong(0L);
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            public void run() {
                long bytes = receivedBytes.getAndSet(0);
                System.out.println((bytes >> 20) / 10  + " MB/s");
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    public void start(int port) {
        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(boss, workers)
            .channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_REUSEADDR, true)
            .option(ChannelOption.SO_BACKLOG, 128)
            .handler(new LoggingHandler(LogLevel.INFO))
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_SNDBUF, 16 * 1024 * 1024)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                protected void initChannel(SocketChannel channel) throws Exception {
                    ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast(new ChannelInboundHandlerAdapter() {
                        @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (msg instanceof ByteBuf) {
                                receivedBytes.addAndGet(((ByteBuf) msg).readableBytes());
                            }

                            ReferenceCountUtil.release(msg);
                        }

                        @Override public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                            ctx.close();
                            cause.printStackTrace();
                        }

                        @Override public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            System.out.println("Channel inactive");
                        }

                        @Override public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            System.out.println("Channel active");
                        }

                        @Override public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                            System.out.println("Channel registered");
                        }

                        @Override public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                            System.out.println("Channel unregistered");
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                            System.out.println("Event: " + evt);
                        }

                        @Override public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                            System.out.println("Channel writable state changed");
                        }
                    });
                }
            });

        try {
            ChannelFuture future = serverBootstrap.bind(port).sync();
            System.out.println("Server starts OK");
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            boss.shutdownGracefully();
            workers.shutdownGracefully();
            scheduledExecutorService.shutdown();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("command port");
        }
        new Server().start(Integer.parseInt(args[0]));
    }


}
