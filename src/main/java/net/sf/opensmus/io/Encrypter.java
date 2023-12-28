package net.sf.opensmus.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import net.sf.opensmus.MUSBlowfish;

public class Encrypter extends ChannelOutboundHandlerAdapter {

    MUSBlowfish cipher = new MUSBlowfish();

    // Server sending a message to a user (raw bytes)
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        // Always assume only one complete message at a time will be in the buffer.
        ByteBuf inBuffer = (ByteBuf) msg;

        inBuffer.markReaderIndex(); // @TODO: Maybe we can assume that the reader index will alwyas be 0?
        cipher.encode(inBuffer);

        inBuffer.resetReaderIndex(); // Rewind the reader index since it was changed by the encode()
        // Forward the now encrypted message along the pipeline
        //Channels.write(ctx, e.getFuture(), inBuffer);
        ctx.write(inBuffer, promise);
    }
}
