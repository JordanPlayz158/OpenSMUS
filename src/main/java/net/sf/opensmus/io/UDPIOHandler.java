package net.sf.opensmus.io;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import net.sf.opensmus.*;


@ChannelHandler.Sharable
public class UDPIOHandler extends IOHandler {

    MUSServer m_server;
    ChannelGroup channels;  // @TODO: Not used for udp...

    public UDPIOHandler(MUSServer srv, ChannelGroup cg) {
        m_server = srv;
        channels = cg;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Add all open channels to the global group so that they are closed on shutdown.
        // If the added channel is closed before shutdown, it will be removed from the group automatically.
        channels.add(ctx.channel());

        super.channelActive(ctx);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        // Figure out what user this is
        MUSUser whatUser = ((SMUSPipeline) ctx.pipeline()).user;

        MUSLog.Log("UDP message arrived from " + whatUser.name(), MUSLog.kDeb);

        MUSMessage msg = new MUSLogonMessage();

        // Decode the message
        msg.extractMUSMessage(buffer);  // Main entry of incoming messages  <-- The incoming buffer does NOT have the 6 headerbytes (ID & length info)

        // Prevent spoofing of userid by ignoring the parsed senderID
        // ALWAYS do this on incoming messages.
        // Only serverside scripts might want to send under a different name so that's only for outgoing messages
        // remotely connected users are not supposed to be able to change their senderID!
        msg.m_senderID = new MUSMsgHeaderString(whatUser.name());

        // ^--- All above from SMUSDecoder


        // This is the only difference in the UDP handler compared to the normal IOHandler
        /////////////////////////////////
        msg.m_udp = true;
        // Check if udpcookie is correct in the timestamp slot
        if (whatUser.m_udpcookie != msg.m_timeStamp) {
            MUSLog.Log("UDP cookie mismatch for " + whatUser.name(), MUSLog.kDeb);
            return;
        }
        /////////////////////////////////

        whatUser.postMessage(msg);

        super.channelRead(ctx, buffer);
    }
}