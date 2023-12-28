package net.sf.opensmus.io;

import java.util.concurrent.TimeUnit;
import net.sf.opensmus.MUSServerProperties;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.timeout.IdleStateHandler;
import net.sf.opensmus.MUSServer;
import net.sf.opensmus.MUSLog;

import java.util.ArrayList;

public class SMUSPipelineFactory extends ChannelInitializer {

    // Stateless, singleton handler instances. Re-used across connections.
    private static final ChannelHandler ENCODER = new SMUSEncoder();
    public static final ChannelHandler HANDLER = new IOHandler();
    public final ChannelHandler LOGGER;
//    private ExecutionHandler EXECUTOR;

    private MUSServer m_server;
    private ChannelGroup channels;
    boolean useUDP = false;
    //private HashedWheelTimer timer;
    private int idleTimeout;
    private boolean useLogging = false;
    private boolean allEncryptionEnabled = false;
    //private int threadPoolSize;

    private boolean useFloodProtection = false;
    final ArrayList<ArrayList> antiFloodSettings = new ArrayList<ArrayList>();

    public SMUSPipelineFactory(MUSServer srv, ChannelGroup cg, boolean udp) {

        m_server = srv;
        channels = cg;
        useUDP = udp;

        LOGGER = new LogFilter(m_server);

//        threadPoolSize = m_server.m_props.getIntProperty("ThreadPoolSize");
//        if (threadPoolSize > 0) {
//            int maxMem = m_server.m_props.getIntProperty("MaxUserMemorySize");
//            int maxTotalMem = m_server.m_props.getIntProperty("MaxTotalMemorySize");
//            EXECUTOR = new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(threadPoolSize, maxMem, maxTotalMem));
//        }

        // Look up these properties once instead of every pipeline creation
        idleTimeout = m_server.m_props.getIntProperty("IdleTimeOut");
        allEncryptionEnabled = m_server.encryptionKey.startsWith("#All");
        useLogging = m_server.m_props.getIntProperty("ServerStatusReportInterval") != 0;

        // Parse and setup antiflood
        String[] af = m_server.m_props.getStringListProperty("AntiFlood");
        if (af != null) {
            for (String v : af) {
                ArrayList settings = MUSServerProperties.parseAntiFloodSettings(v);
                if (settings != null) {
                    antiFloodSettings.add(settings);
                }
            }

            if (!antiFloodSettings.isEmpty()) useFloodProtection = true;
        }

        // Set up the timer to use for scheduling timeouts
        //timer = new HashedWheelTimer(5, TimeUnit.SECONDS); // @TODO: Are the defaults (100ms / 512 wheels) good for SMUS usage?
    }

    public ChannelPipeline getPipeline() throws Exception {
        // Create default pipeline from static method
        ChannelPipeline pipeline = new SMUSPipeline(channels.stream().findFirst().get()); // Channels.pipeline();

        if (idleTimeout > 0)
            //pipeline.addLast("idlehandler", new IdleStateHandler(timer, idleTimeout, 0, 0));
            pipeline.addLast("idlehandler", new IdleStateHandler(5, idleTimeout, 0, TimeUnit.SECONDS));

        if (allEncryptionEnabled) {
            pipeline.addLast("decryption", new DecryptionFramer()); // Handles upstream (incoming) messages
            pipeline.addLast("encryption", new Encrypter()); // Handles downstream (outgoing) messages
        } else {
            pipeline.addLast("framer", new Framer()); // Frames upstream (incoming) messages
        }

        if (useLogging)
            pipeline.addLast("logging", LOGGER); // new LogFilter(m_server)

        if (useFloodProtection)
            pipeline.addLast("floodfilter", new FloodFilter(antiFloodSettings));

        // Add encoder (downstream)
        pipeline.addLast("encoder", ENCODER);

        // It's not strictly necessary to use an executor, but will help prevent lag caused by slow/blocking business logic (e.g. server side scripts)
        //if (threadPoolSize > 0)
        //    pipeline.addLast("executor", EXECUTOR);
        
        // Add business logic (upstream) ALSO INCLUDES decoder
        if (useUDP) {
            // This is a pipeline for a UDP channel.
            pipeline.addLast("handler", new UDPIOHandler(m_server, channels));
        } else {
            // (LogonHandler gets replaced with IOHandler once the login message has been handled)
            pipeline.addLast("logonhandler", new LogonHandler(m_server, channels));
        }

        // Return pipeline instance
        return pipeline;
    }


    // Test to allow for dynamically changing the flood params
    public void setFloodParameters(int minTime, int tol, int repeat) {
        // floodMinMsgTime = minTime;
        // floodMsgTolerance = tol;
        // floodMaxRepeat = repeat;
        MUSLog.Log("Flood filter parameters updated", MUSLog.kDeb);
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        channels.add(ch);
    }
}