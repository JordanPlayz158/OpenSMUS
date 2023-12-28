package net.sf.opensmus.io;

import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import net.sf.opensmus.*;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;


@ChannelHandler.Sharable
public class SMUSEncoder extends MessageToMessageEncoder<MUSMessage> {

//    @Override
//    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object message) throws Exception {
//
//        MUSMessage msg = (MUSMessage) message;
//        // Return the constructed raw message bytes.
//        return (msg.getBytes());
//    }

    @Override
    protected void encode(ChannelHandlerContext ctx, MUSMessage msg, List<Object> out) throws Exception {
        // Return the constructed raw message bytes.
        out.add(msg.getBytes());
    }
}
