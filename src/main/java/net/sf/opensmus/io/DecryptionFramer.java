package net.sf.opensmus.io;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import net.sf.opensmus.MUSLog;
import net.sf.opensmus.MUSUser;
import net.sf.opensmus.MUSBlowfish;


//public class DecryptionFramer extends FrameDecoder {
public class DecryptionFramer extends ByteToMessageDecoder {

  int messageSize = 0;
  final MUSBlowfish cipher = new MUSBlowfish();

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    if (messageSize == 0) {
      // We haven't read the size info yet. = start of new message

      // Check the content length to see if we can decode the entire message
      // We only need the first 6 bytes (header + message length info) to check this
      // but since Blowfish works on 8-byte blocks, we need 8 bytes.
      if (in.readableBytes() < 8) {
        return;
      }

      // Store the start of the message in the buffer.
      // We can NOT assume that the readerIndex of an incoming buffer is 0.
      int msgStart = in.readerIndex();

      // Decrypt the first 8 bytes
      cipher.decode(in, 8);
      // Buffer reader index will be +8 now!

      // Check that the packet has the SMUS signature header
      if (in.getShort(msgStart) != 0x7200) {
        MUSUser whatUser = ((SMUSPipeline) ctx.pipeline()).user; // Figure out what user this is
        MUSLog.Log("Invalid message format from " + whatUser + ": " + in + " " + ByteBufUtil.hexDump(in, msgStart, 8), MUSLog.kMsgErr);
        whatUser.killMUSUser(); // Has caused NullPointerException
        in.clear();
        return;
      }

      messageSize = in.getInt(msgStart + 2); // The following 4 bytes is the full message size in bytes

      if (in.readableBytes() + 2 < messageSize) { // +2 = reader index is 8, so we have read 2 bytes of the content already
        // Not enough data available, we have to wait.

        // Rewind the reader index to prevent the FrameDecoder from getting rid of the 8 bytes
        // while we wait for more data to come in. This seems to have happened!
        in.readerIndex(msgStart);
        return;
      } else {
        // Enough data available
        // Continue the decryption
        cipher.decode(in, messageSize - 2); // -2 = we have already decrypted 2 bytes from the content
        cipher.reset();
      }

    } else  // Fragmented data. New data has arrived, check if enough.
      if (in.readableBytes() - 6 < messageSize) { // 6 bytes are header data and not counted.
        // Not enough bytes available for the rest of the message
        return; // Wait until more data arrives
      } else {
        // We have enough data to decode the current message
        // Restore the reader index past the initial 8 bytes we have already decrypted
        in.skipBytes(8);

        // Continue decryption of the rest of the message
        cipher.decode(in, messageSize - 2); // 2 bytes of the data already decrypted
        // We're all done with this message, reset the cipher.
        cipher.reset();
      }

    // The data is now fully decrypted and ready to be passed along

    // We could have a max size check here...
    // if (messageSize > m_movie.getServer().m_props.getIntProperty("MaxMessageSize") )
    // MUSErrorCode.MessageTooLarge

    in.readerIndex(in.readerIndex() - messageSize); // Rewind to start of message data.

    // Trying to optimize and use buffer.readSlice(messageSize) instead will work most of the time
    // but occasionally generate corrupt messages.
    ByteBuf msgData = in.readBytes(messageSize);

    messageSize = 0; // Get ready for the next message
    // (The original buffer's reader index is now consumed properly so the FrameDecoder is happy.)
    out.add(msgData);
  }
}
