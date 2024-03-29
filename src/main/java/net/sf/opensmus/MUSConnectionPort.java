/*
  Part of OpenSMUS Source Code.
  OpenSMUS is licensed under a MIT License, compatible with both
  open source (GPL or not) and commercial development.

  Copyright (c) 2001-2008 Mauricio Piacentini <mauricio@tabuleiro.com>

  Permission is hereby granted, free of charge, to any person
  obtaining a copy of this software and associated documentation
  files (the "Software"), to deal in the Software without
  restriction, including without limitation the rights to use,
  copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the
  Software is furnished to do so, subject to the following
  conditions:

  The above copyright notice and this permission notice shall be
  included in all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
  OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
  OTHER DEALINGS IN THE SOFTWARE.
*/

package net.sf.opensmus;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import net.sf.opensmus.io.SMUSPipelineFactory;

/////////////////////////////////////////////////////////////
public class MUSConnectionPort {

  //private ChannelFactory factory;

  // Netty
  public ChannelGroup m_channels;
  // public ServerBootstrap bootstrap; // Stored as an instance variable in order to access the pipelinefactory, to facilitate dynamic changes to the floodfilter params

  public MUSServer m_server;
  boolean m_alive;

  /////////////////////////////////////////////////////////////
  public MUSConnectionPort(MUSServer srv, String ipaddress, int port) {
    try {
      m_server = srv;
      InetAddress iad;
      m_alive = true;

      if (ipaddress.equalsIgnoreCase("default"))
        iad = InetAddress.getLocalHost();
      else
        iad = InetAddress.getByName(ipaddress);

      m_channels = new DefaultChannelGroup("OpenSMUS", GlobalEventExecutor.INSTANCE); // The name can be duplicates, we use it by reference anyway.

      //factory = new NioServerSocketChannelFactory(
      //        Executors.newCachedThreadPool(),
      //        Executors.newCachedThreadPool());

      ServerBootstrap bootstrap = new ServerBootstrap();
      //bootstrap.channelFactory(factory);

      // Set up the event pipeline factory.
      bootstrap.childHandler(new SMUSPipelineFactory(srv, m_channels, false));
      //bootstrap.setPipelineFactory(new SMUSPipelineFactory(srv, m_channels, false));

      // The initial idletime will be for the login only. Once a user is logged in it will be changed to the normal idle.
      // this.bootstrap.getSessionConfig().setReaderIdleTime(m_server.m_props.getIntProperty("MaxLoginWait"));
      // @TODO: This is not yet implemented in Netty


      boolean tcpdelayflag = (m_server.m_props.getIntProperty("tcpNoDelay") == 1);
      bootstrap.childOption(ChannelOption.TCP_NODELAY, tcpdelayflag);

      int lingerTime = m_server.m_props.getIntProperty("soLingerTime");
      if (lingerTime != -1) {
        bootstrap.option(ChannelOption.SO_LINGER, lingerTime);
        MUSLog.Log("New Socket LingerTime > " + lingerTime, MUSLog.kDebWarn);
      }

      bootstrap.option(ChannelOption.SO_REUSEADDR, true); // Needed to fix the locking up of ports after shutdown
      // bootstrap.setOption("child.reuseAddress", true); // Is this needed?

      //Channel sc = bootstrap.bind(new InetSocketAddress(iad, port)); // Start listening to the port and accept connections
      EventLoopGroup group = new NioEventLoopGroup(); // (1)
      bootstrap.group(group);
      bootstrap.channel(NioServerSocketChannel.class);
      Channel sc = bootstrap.bind(new InetSocketAddress(iad, port)).channel(); // Start listening to the port and accept connections

      // Add the server socket to the global channel group.
      m_channels.add(sc);

      MUSLog.Log("Listening to: " + iad + ":" + port, MUSLog.kSys);

      // No exceptions thrown, add the port to the list on the server
      m_server.addConnectionPort(this);

    } catch (Exception e) {
      MUSLog.Log("Bind error : address in use", MUSLog.kSys);
      MUSLog.Log(e, MUSLog.kSys);
    }
  }


  public void killConnectionPort() {
    try {
      m_server.removeConnectionPort(this);

      // Close all connections and server sockets.
      m_channels.close().awaitUninterruptibly();
      // Shutdown the selector loop (boss and worker).
      // Netty 4
      //factory.releaseExternalResources();

      MUSLog.Log("Connection port stopped", MUSLog.kSys);
    } catch (Exception e) {
      MUSLog.Log("Error while stopping Connection Port", MUSLog.kSys);
    }
  }
} 