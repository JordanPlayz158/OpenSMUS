package net.sf.opensmus.io;

import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import net.sf.opensmus.MUSMessage;

@ChannelHandler.Sharable
public class SMUSEncoder extends MessageToMessageEncoder<MUSMessage> {
  @Override
  protected void encode(ChannelHandlerContext ctx, MUSMessage msg, List<Object> out) {
    // Return the constructed raw message bytes.
    out.add(msg.getBytes());
  }
}
