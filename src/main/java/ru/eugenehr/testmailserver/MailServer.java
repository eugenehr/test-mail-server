/**
 * Copyright 2018 (c) Eugene Khrustalev
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.eugenehr.testmailserver;

import java.io.File;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.CharsetUtil;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.eugenehr.testmailserver.ui.Application;

/**
 * Mail server.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
public class MailServer {

    private static final Logger logger = LoggerFactory.getLogger(MailServer.class);
    static MailServer INSTANCE; // package visible for testing purpose
    //
    private final NioEventLoopGroup bossGroup = new DaemonEventLoopGroup("listener-");
    private final NioEventLoopGroup workerGroup = new DaemonEventLoopGroup("worker-");
    private final Mailboxes mailboxes;
    //
    private int smtpPort = 2500;
    private Channel smtpChannel;
    private int pop3Port = 1100;
    private Channel pop3Channel;
    /**
     * {@code true} if all incoming SMTP messages must be redirected to the senders mailbox.
     */
    private boolean redirectToSender;

    /**
     * Creates a server channel manager.
     *
     * @param mailboxes user mailboxes manager
     */
    public MailServer(Mailboxes mailboxes) {
        this.mailboxes = mailboxes;
    }

    public static MailServer getInstance() {
        return INSTANCE;
    }

    /**
     * Application entry-point.
     *
     * @param args command line arguments
     * @throws Exception if any
     */
    public static void main(String[] args) throws Exception {
        File mailboxesDir = new File(
            System.getProperty("user.dir", System.getProperty("user.home")), ".test-mail-server");

        // Parse command line arguments
        final Options options = new Options();
        options.addOption("h", "help", false, "This help");
        options.addOption("s", "smtp-port", true,
            "SMTP port to listen to");
        options.addOption("sr", "smtp-redirect", false,
            "Redirect all incoming messages to sender");
        options.addOption("p", "pop3-port", true,
            "POP3 port to listen to");
        options.addOption("c", "console", false, "Start application in console mode with no GUI");
        options.addOption("m", "mail-dir", true,
            "Directory to store mailboxes. Default is " + mailboxesDir.getPath());

        final CommandLine cmdLine;
        try {
            cmdLine = new DefaultParser().parse(options, args);
        } catch (Exception ex) {
            new HelpFormatter().printHelp("test-mail-server", options);
            return;
        }

        if (cmdLine.hasOption("h")) {
            new HelpFormatter().printHelp("test-mail-server", options);
            return;
        }

        mailboxesDir = new File(cmdLine.getOptionValue("m", mailboxesDir.getPath()));
        if (!mailboxesDir.exists()) {
            if (!mailboxesDir.mkdirs()) {
                logger.error("Could not create directory '{}'", mailboxesDir.getAbsolutePath());
                return;
            }
        }
        INSTANCE = new MailServer(new Mailboxes(mailboxesDir));
        INSTANCE.redirectToSender = cmdLine.hasOption("sr");

        int port = Integer.valueOf(cmdLine.getOptionValue("s", "0"));
        if (port > 0) {
            INSTANCE.startSMTP(port);
        }

        port = Integer.valueOf(cmdLine.getOptionValue("p", "0"));
        if (port > 0) {
            INSTANCE.startPOP3(port);
        }

        if (INSTANCE.smtpChannel == null && INSTANCE.pop3Channel == null && cmdLine.hasOption("c")) {
            new HelpFormatter().printHelp("test-mail-server", options);
            return;
        }

        if (!cmdLine.hasOption("c")) {
            // Start the GUI
            Application.launch(Application.class, args);
        } else {
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(INSTANCE::shutdown));
            // Wait for channels
            if (INSTANCE.smtpChannel != null) {
                INSTANCE.smtpChannel.closeFuture().sync();
            }
            if (INSTANCE.pop3Channel != null) {
                INSTANCE.pop3Channel.closeFuture().sync();
            }
        }
    }

    /**
     * Tests if SMTP server started.
     *
     * @return {@code true} if SMTP started and alive
     */
    public boolean isSMTPStarted() {
        return smtpChannel != null && smtpChannel.isActive();
    }

    /**
     * Starts the SMTP server on the given port.
     *
     * @param port port to listen to
     * @throws Exception if any
     */
    public Channel startSMTP(int port) throws Exception {
        stopSMTP();
        logger.info("Starting SMTP server on port {}...", port);
        smtpPort = port;
        return smtpChannel = startChannel(port, new SMTPHandler());
    }

    /**
     * Stops the SMTP server.
     */
    public void stopSMTP() {
        if (isSMTPStarted()) {
            logger.info("Stopping SMTP server...");
            stopChannel(smtpChannel);
            smtpChannel = null;
        }
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    /**
     * Tests if POP3 server started.
     *
     * @return {@code true} if POP3 started and alive
     */
    public boolean isPOP3Started() {
        return pop3Channel != null && pop3Channel.isActive();
    }

    /**
     * Starts the POP3 server on the given port.
     *
     * @param port port to listen to
     * @throws Exception if any
     */
    public Channel startPOP3(int port) throws Exception {
        stopPOP3();
        logger.info("Starting POP3 server on port {}...", port);
        pop3Port = port;
        return pop3Channel = startChannel(port, new POP3Handler());
    }

    /**
     * Stops the POP3 server.
     */
    public void stopPOP3() {
        if (isPOP3Started()) {
            logger.info("Stopping SMTP server...");
            stopChannel(pop3Channel);
            pop3Channel = null;
        }
    }

    public int getPop3Port() {
        return pop3Port;
    }

    private void stopChannel(Channel channel) {
        if (channel != null && channel.isActive()) {
            channel.close();
        }
    }

    public boolean isRedirectToSender() {
        return redirectToSender;
    }

    public void setRedirectToSender(boolean redirectToSender) {
        this.redirectToSender = redirectToSender;
    }

    public Mailboxes getMailboxes() {
        return mailboxes;
    }

    /**
     * Shuts down all servers and close all listening sockets.
     */
    public void shutdown() {
        stopChannel(smtpChannel);
        stopChannel(pop3Channel);

        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    /**
     * Adds and starts a new server.
     *
     * @param port    TCP port to listen to
     * @param handler server handler
     * @return server channel
     */
    private Channel startChannel(int port, ChannelInboundHandler handler) throws Exception {
        final Channel channel = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) throws Exception {
                    // Keep alive timeout
                    channel.pipeline().addLast("keepAliveHandler",
                        new IdleStateHandler(60, 30, 0));
                    // Line delimeter based decoder
                    channel.pipeline().addLast("frameDecoder",
                        new DelimiterBasedFrameDecoder(16384, Delimiters.lineDelimiter()));
                    // String decoder
                    channel.pipeline().addLast("stringDecoder",
                        new StringDecoder(CharsetUtil.US_ASCII));
                    // Line delimeter based decoder
                    channel.pipeline().addLast("stringEncoder", new StringEncoder(CharsetUtil.US_ASCII));
                    // Protocol handler
                    channel.pipeline().addLast(handler);
                }
            })
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .bind(port)
            .channel();
        syncChannel(channel);
        return channel;
    }

    /**
     * Starts a background thread that syncs the given channel.
     *
     * @param channel channel to sync
     */
    private void syncChannel(Channel channel) {
        final Runnable task = () -> {
            try {
                channel.closeFuture().sync();
            } catch (InterruptedException ex) {
                /* do nothing */
            }
        };
        final Thread thread = new Thread(task);
        thread.setName("sync-channel-" + channel.id());
        thread.setDaemon(true);
        thread.start();
    }
}
