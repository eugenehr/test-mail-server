/**
 * Copyright 2018-2020 (c) Eugene Khrustalev.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.eugenehr.testmailserver.event.SMTPSessionEvent;
import ru.eugenehr.testmailserver.event.SMTPSessionLogEvent;
import ru.eugenehr.testmailserver.event.SessionEvent;
import ru.eugenehr.testmailserver.event.SessionLogEvent.Direction;
import ru.eugenehr.testmailserver.ui.UIEventBus;

/**
 * SMTP server handler.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
@Sharable
public class SMTPHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SMTPHandler.class);
    private static final Pattern RCPT_PATTERN = Pattern.compile("^.*<([^>]+)>$");

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        final Channel channel = ctx.channel();
        logger.info("Client connected: {}", channel.remoteAddress());

        final Attribute<State> state = channel.attr(AttributeKey.valueOf("state"));
        state.set(new State());

        // Send greetings
        final String message = "220 Test Mail Server\r\n";
        logger.debug(">>: {}", message.trim());
        channel.writeAndFlush(message);

        // Notify UI
        UIEventBus.post(new SMTPSessionEvent(channel.id().toString(), SessionEvent.Type.CREATED));
        UIEventBus.post(new SMTPSessionLogEvent(channel.id().toString(), Direction.SERVER, message));
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        logger.info("Client disconnected: {}", ctx.channel().remoteAddress());

        final Channel channel = ctx.channel();
        final String channelId = channel.id().toString();

        // Remove temporary files
        final Attribute<State> attr = channel.attr(AttributeKey.valueOf("state"));
        final State state = attr.get();

        if (state.file != null && state.file.exists()) {
            logger.debug("Cleaning temporary files...");
            try {
                if (state.file.delete()) {
                    logger.debug("File '{}' deleted", state.file.getAbsolutePath());
                }
            } catch (Exception ex) {
                logger.error("Could not delete file '{}': {}", state.file.getAbsolutePath(), ex.getMessage());
            }
        }

        // Notify UI
        UIEventBus.post(new SMTPSessionEvent(channelId, SessionEvent.Type.CLOSED));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) event;
            if (idleStateEvent.state() == IdleState.READER_IDLE) {
                logger.info("Closing client connection {} because Keep-Alive timeout has expired",
                    ctx.channel().remoteAddress());
                ctx.close();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        final Channel channel = ctx.channel();
        final String channelId = channel.id().toString();
        logger.debug("<<: {}", msg);

        final String message = msg.toString();
        UIEventBus.post(new SMTPSessionLogEvent(channelId, Direction.CLIENT, message));

        final Attribute<State> attr = channel.attr(AttributeKey.valueOf("state"));
        final State state = attr.get();

        String response;
        boolean closeConnection = false;
        if (state.data) {
            // Reading data
            if (message.equals(".")) {
                state.data = false;
                final long length = state.close();
                if (state.file != null) {
                    final Set<String> files = MailServer.getInstance().getMailboxes()
                        .addMessage(state.from, state.to, state.file);
                    logger.info("Message saved to {}", files);
                }
                response = "250 " + length + " bytes accepted\r\n";
            } else {
                if (message.equals("..")) {
                    state.data(".\r\n");
                } else {
                    state.data(message + "\r\n");
                }
                return;
            }
        } else {
            if (message.matches("^(HELO|EHLO).*")) {
                response = "250 OK\r\n";
            } else if (message.startsWith("MAIL FROM:")) {
                final String from = message.substring(10).trim();
                if (from.isEmpty()) {
                    response = "550 no sender given\r\n";
                } else {
                    final Matcher matcher = RCPT_PATTERN.matcher(from);
                    state.from = (matcher.matches() ? matcher.group(1) : from).trim();
                    if (state.from.startsWith("<") && state.from.endsWith(">")) {
                        state.from = state.from.substring(1, state.from.length() - 1);
                    }
                    response = "250 sender " + state.from + " OK\r\n";
                }
            } else if (message.startsWith("RCPT TO:")) {
                String recipient = message.substring(8).trim();
                if (recipient.isEmpty()) {
                    response = "550 no recipient given\r\n";
                } else {
                    state.to.add(recipient);
                    response = "250 recipient " + recipient + " OK\r\n";
                }
            } else if (message.equals("DATA")) {
                if (state.from == null) {
                    response = "550 no sender given\r\n";
                } else if (state.to.isEmpty()) {
                    response = "554 no recipients given\r\n";
                } else {
                    state.data = true;
                    response = "354 enter mail, end with line containing only \".\"\r\n";
                }
            } else if (message.equals("NOOP")) {
                response = "250 OK\r\n";
            } else if (message.equals("QUIT")) {
                response = "221 Closing connection\r\n";
                closeConnection = true;
            } else {
                response = "500 ERROR\r\n";
            }
        }
        logger.debug(">>: {}", response.trim());
        channel.writeAndFlush(response);
        UIEventBus.post(new SMTPSessionLogEvent(channelId, Direction.SERVER, response));

        if (closeConnection) {
            ctx.close();
        }
    }

    /**
     * SMTP session state.
     */
    private static class State implements Serializable {
        private final List<String> to = new ArrayList<>();
        private String from;
        private File file;
        private FileOutputStream stream;
        private boolean data;

        /**
         * Append data to temporary file.
         *
         * @param message data to append
         */
        private void data(String message) {
            try {
                if (file == null) {
                    file = File.createTempFile("mail", ".msg");
                    stream = new FileOutputStream(file);
                }
                stream.write(message.getBytes());
            } catch (IOException ex) {
                logger.error("Could not write data to file '{}': {}", file.getAbsolutePath(), ex.getMessage());
                throw new RuntimeException(ex);
            }
        }

        /**
         * Close temporary file.
         *
         * @return the length in bytes of file.
         */
        private long close() {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    logger.error("Could not close file '{}': {}", file.getAbsolutePath(), ex.getMessage());
                    throw new RuntimeException(ex);
                }
            }
            stream = null;
            if (file != null) {
                return file.length();
            }
            return 0;
        }
    }
}
