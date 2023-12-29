package net.sf.opensmus.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.sf.opensmus.MUSServer;

@ChannelHandler.Sharable
public class LogFilter extends ChannelDuplexHandler {
  final MUSServer m_server;

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
