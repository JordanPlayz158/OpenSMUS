package net.sf.opensmus.io;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import java.util.concurrent.TimeUnit;
import net.sf.opensmus.MUSServerProperties;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.timeout.IdleStateHandler;
import net.sf.opensmus.MUSServer;
import net.sf.opensmus.MUSLog;

import java.util.ArrayList;

public class SMUSPipelineFactory extends ChannelInitializer<Channel> {

  public static final ChannelHandler HANDLER = new IOHandler();
  // Stateless, singleton handler instances. Re-used across connections.
  private static final ChannelHandler ENCODER = new SMUSEncoder();
  public final ChannelHandler LOGGER;
//    private ExecutionHandler EXECUTOR;
  final ArrayList<ArrayList> antiFloodSettings = new ArrayList<>();
  final boolean useUDP;
  private final MUSServer server;
  private final ChannelGroup channels;
  //private HashedWheelTimer timer;
  private final int idleTimeout;
  private final boolean useLogging;
  //private int threadPoolSize;
  private final boolean allEncryptionEnabled;
  private boolean useFloodProtection = false;

  public SMUSPipelineFactory(MUSServer srv, ChannelGroup cg, boolean udp) {

    server = srv;
    channels = cg;
    useUDP = udp;

    LOGGER = new LogFilter(server);

//        threadPoolSize = m_server.m_props.getIntProperty("ThreadPoolSize");
//        if (threadPoolSize > 0) {
//            int maxMem = m_server.m_props.getIntProperty("MaxUserMemorySize");
//            int maxTotalMem = m_server.m_props.getIntProperty("MaxTotalMemorySize");
//            EXECUTOR = new ExecutionHandler(new OrderedMemoryAwareThreadPoolExecutor(threadPoolSize, maxMem, maxTotalMem));
//        }

    // Look up these properties once instead of every pipeline creation
    idleTimeout = server.m_props.getIntProperty("IdleTimeOut");
    allEncryptionEnabled = server.encryptionKey.startsWith("#All");
    useLogging = server.m_props.getIntProperty("ServerStatusReportInterval") != 0;

    // Parse and setup antiflood
    String[] af = server.m_props.getStringListProperty("AntiFlood");
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

  public ChannelPipeline getPipeline() {
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
      pipeline.addLast("handler", new UDPIOHandler(server, channels));
    } else {
      // (LogonHandler gets replaced with IOHandler once the login message has been handled)
      pipeline.addLast("logonhandler", new LogonHandler(server, channels));
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
  protected void initChannel(Channel ch) {
    channels.add(ch);
  }
}