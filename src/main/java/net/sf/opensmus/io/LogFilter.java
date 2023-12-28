package net.sf.opensmus.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import net.sf.opensmus.MUSServer;

@ChannelHandler.Sharable
public class LogFilter extends ChannelDuplexHandler {
    MUSServer m_server;

    public LogFilter(MUSServer srv) {
        m_server = srv;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            m_server.logInBytes(((ByteBuf) msg).readableBytes());
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            m_server.logOutBytes(((ByteBuf) msg).readableBytes());
        }

        super.write(ctx, msg, promise);
    }
}
