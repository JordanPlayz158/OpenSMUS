package net.sf.opensmus.io;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import io.netty.channel.ChannelHandlerContext;
import net.sf.opensmus.MUSLog;
import net.sf.opensmus.MUSUser;

public class Framer extends ByteToMessageDecoder {

  int messageSize = 0;

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    if (messageSize == 0) {
      // Start of new message
      // Grab the first 6 bytes of the incoming message so we can see how long this message is
      if (in.readableBytes() < 6) return; // Wait until the length prefix is available.

      // Check that the packet has the SMUS signature header
      if (in.readShort() != 0x7200) {
        MUSUser whatUser = ((SMUSPipeline) ctx.pipeline()).user; // Figure out what user this is
        MUSLog.Log("Invalid message format from " + whatUser + ": " + in + " " + ByteBufUtil.hexDump(in, in.readerIndex() - 2, 8), MUSLog.kMsgErr);
        whatUser.killMUSUser();
        in.clear(); // Consume everything in the buffer so that the FrameDecoder doesn't keep calling decode() while the user is being disconnected
        return;
      }

      messageSize = in.readInt(); // Next 4 bytes is full message size in bytes (rest of the data)
    }

    if (in.readableBytes() < messageSize) {
      // Not enough bytes available for the rest of the message
      return;
    }

    // We could have a max size check here...
    // if (messageSize > m_movie.getServer().m_props.getIntProperty("MaxMessageSize") )
    // MUSErrorCode.MessageTooLarge

    // Store the message bytes (stripped of the 2 header bytes and 4 length info bytes) in a new buffer
    // This also updates the readerIndex of the original buffer, so FrameDecoder will know we consumed the bytes in it.
    ByteBuf msgData = in.readBytes(messageSize);

    messageSize = 0; // Get ready for the next message
    out.add(msgData);
  }
}



