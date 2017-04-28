package com.yeahmobi.lab.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
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
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.Arrays;

public class Server {
    private ServerBootstrap serverBootstrap;
    private final EventLoopGroup boss;
    private final EventLoopGroup workers;

    public static ByteBuf data;

    static {
        byte[] randomData = new byte[1024 * 1024 * 4];
        Arrays.fill(randomData, (byte)'x');
        data = Unpooled.wrappedBuffer(randomData);
    }

    public Server() {
        boss = new NioEventLoopGroup(1);
        workers = new NioEventLoopGroup();
    }

    public void start() {
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
                    pipeline.addLast(new StressChannelHandler());
                }
            });
        try {
            ChannelFuture future = serverBootstrap.bind(1234).sync();
            System.out.println("Server starts OK");
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            boss.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        new Server().start();
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
