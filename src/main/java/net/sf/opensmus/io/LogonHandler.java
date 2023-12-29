package net.sf.opensmus.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.group.ChannelGroup;

import java.net.InetSocketAddress;
import net.sf.opensmus.MUSLog;
import net.sf.opensmus.MUSLogonMessage;
import net.sf.opensmus.MUSServer;
import net.sf.opensmus.MUSUser;

@ChannelHandler.Sharable
public class LogonHandler extends IOHandler {

  final MUSServer m_server;
  final ChannelGroup channels;

  public LogonHandler(MUSServer srv, ChannelGroup cg) {
    m_server = srv;
    channels = cg;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    Channel channel = ctx.channel();

    // Add all open channels to the global group so that they are closed on shutdown.
    // If the added channel is closed before shutdown, it will be removed from the group automatically.
    channels.add(channel);

    String ip = ((InetSocketAddress) channel.remoteAddress()).getAddress().getHostAddress();

    MUSLog.Log("Client connection initialized : " + (m_server.m_clientlist.size() + 1) + " (" + ip + ")", MUSLog.kSrv);
    // Create a user object for this new connection
    // Store the user object in the session so we will know who it is when data arrive
    ((SMUSPipeline) ctx.pipeline()).user = new MUSUser(m_server, channel);

    super.channelActive(ctx);
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
    // Figure out what user this is
    MUSUser whatUser = ((SMUSPipeline) ctx.pipeline()).user;

    MUSLogonMessage msg = new MUSLogonMessage();

    // Decode the message
    msg.extractMUSMessage(buffer);  // The incoming buffer does NOT have the 6 headerbytes (ID & length info)

    m_server.queueLogonMessage(msg, whatUser);

    super.channelRead(ctx, buffer);
  }
}