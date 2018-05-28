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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.eugenehr.testmailserver.event.MailboxEvent;
import ru.eugenehr.testmailserver.event.POP3SessionEvent;
import ru.eugenehr.testmailserver.event.POP3SessionLogEvent;
import ru.eugenehr.testmailserver.event.SessionEvent;
import ru.eugenehr.testmailserver.event.SessionLogEvent.Direction;
import ru.eugenehr.testmailserver.ui.UIEventBus;

/**
 * POP3 server handler.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
@Sharable
public class POP3Handler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(POP3Handler.class);

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        final Channel channel = ctx.channel();
        logger.info("Client connected: {}", channel.remoteAddress());

        final Attribute<State> state = channel.attr(AttributeKey.valueOf("state"));
        state.set(new State());

        // Send greetings
        final String message = "+OK Test Mail Server\r\n";
        logger.debug(">>: {}", message.trim());
        channel.writeAndFlush(message);

        // Notify UI
        UIEventBus.post(new POP3SessionEvent(channel.id().toString(), SessionEvent.Type.CREATED));
        UIEventBus.post(new POP3SessionLogEvent(channel.id().toString(), Direction.SERVER, message));
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        logger.info("Client disconnected: {}", ctx.channel().remoteAddress());

        final Channel channel = ctx.channel();
        final String channelId = channel.id().toString();

        // Remove temporary files
        final Attribute<State> attr = channel.attr(AttributeKey.valueOf("state"));
        final State state = attr.get();

        // Notify UI
        UIEventBus.post(new POP3SessionEvent(channelId, SessionEvent.Type.CLOSED));
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
        UIEventBus.post(new POP3SessionLogEvent(channelId, Direction.CLIENT, message));

        final Attribute<State> attr = channel.attr(AttributeKey.valueOf("state"));
        final State state = attr.get();

        final Mailboxes mailboxes = MailServer.getInstance().getMailboxes();

        String response;
        boolean closeConnection = false;
        if (message.matches("^(USER|APOP)\\s.+")) {
            state.mailbox = message.substring(5).split("\\s")[0];
            state.messages = new ArrayList<>(mailboxes.getMessages(state.mailbox));
            state.deleted = new ArrayList<>();
            response = "+OK\r\n";
        } else if (message.startsWith("PASS ")) {
            if (state.mailbox == null) {
                response = "-ERR No user given\r\n";
            } else {
                response = "+OK\r\n";
            }
        } else if (message.equals("CAPA")) {
            response = "+OK\r\nUIDL\r\n.\r\n";
        } else if (message.equals("STAT")) {
            if (state.mailbox == null) {
                response = "-ERR No user given\r\n";
            } else {
                response = "+OK "
                    + state.messages.size() + " "
                    + state.messages.stream()
                    .map(file -> mailboxes.getMessage(state.mailbox, file))
                    .filter(File::exists).mapToLong(File::length).sum() + "\r\n";
            }
        } else if (message.startsWith("LIST")) {
            if (state.mailbox == null) {
                response = "-ERR No user given\r\n";
            } else {
                response = "+OK\r\n";
                if (message.equals("LIST")) {
                    for (int i = 0; i < state.messages.size(); i++) {
                        final String mail = state.messages.get(i);
                        if (!state.deleted.contains(mail)) {
                            response += (i + 1) + " "
                                + mailboxes.getMessage(state.mailbox, mail).length() + "\r\n";
                        }
                    }
                    response += ".\r\n";
                } else {
                    String index = message.substring(5);
                    if (index.matches("\\d+")) {
                        int num = Integer.valueOf(index);
                        if (num > state.messages.size()) {
                            response = "-ERR Invalid message number\r\n";
                        } else {
                            final String mail = state.messages.get(num - 1);
                            if (!state.deleted.contains(mail)) {
                                response += num + " " + mailboxes.getMessage(state.mailbox,
                                    state.messages.get(num - 1)).length() + "\r\n";
                            }
                            response += ".\r\n";
                        }
                    } else {
                        response = "-ERR Invalid message number\r\n";
                    }
                }
            }
        } else if (message.equals("UIDL")) {
            if (state.mailbox == null) {
                response = "-ERR no user given\r\n";
            } else {
                response = "+OK\r\n";
                for (int i = 0; i < state.messages.size(); i++) {
                    response += (i + 1) + " " + state.messages.get(i) + "\r\n";
                }
                response += ".\r\n";
            }
        } else if (message.startsWith("RETR ")) {
            if (state.mailbox == null) {
                response = "-ERR no user given\r\n";
            } else {
                String index = message.substring(5);
                if (index.matches("\\d+")) {
                    int num = Integer.valueOf(index);
                    if (num > state.messages.size()) {
                        response = "-ERR Invalid message number\r\n";
                    } else {
                        final String mail = state.messages.get(num - 1);
                        if (!state.deleted.contains(mail)) {
                            final File file = mailboxes.getMessage(state.mailbox, state.messages.get(num - 1));
                            if (file.exists() && file.canRead()) {
                                response = "+OK " + file.length() + "\r\n"
                                    + FileUtils.readLines(file, CharsetUtil.US_ASCII)
                                    .stream().collect(Collectors.joining("\r\n"))
                                    + "\r\n.\r\n";
                            } else {
                                response = "-ERR Message deleted\r\n";
                            }
                        } else {
                            response = "-ERR Message deleted\r\n";
                        }
                    }
                } else {
                    response = "-ERR Invalid message number\r\n";
                }
            }
        } else if (message.startsWith("TOP ")) {
            if (state.mailbox == null) {
                response = "-ERR no user given\r\n";
            } else {
                String[] parts = message.substring(4).split("\\s", 2);
                String index = parts[0];
                if (index.matches("\\d+")) {
                    int num = Integer.valueOf(index);
                    if (num > state.messages.size()) {
                        response = "-ERR Invalid message number\r\n";
                    } else {
                        final String mail = state.messages.get(num - 1);
                        if (!state.deleted.contains(mail)) {
                            final File file = mailboxes.getMessage(state.mailbox, state.messages.get(num - 1));
                            if (file.exists() && file.canRead()) {
                                index = parts[1];
                                if (index.matches("\\d+")) {
                                    num = Integer.valueOf(index);
                                    response = "+OK\r\n";
                                    // Read message headers
                                    final ListIterator<String> it = FileUtils
                                        .readLines(file, CharsetUtil.US_ASCII).listIterator();
                                    while (it.hasNext()) {
                                        final String line = it.next();
                                        response += line + "\r\n";
                                        if (line.isEmpty()) {
                                            break;
                                        }
                                    }
                                    // Read message body
                                    for (int i = 0; i < num; i++) {
                                        if (it.hasNext()) {
                                            final String line = it.next();
                                            response += line + "\r\n";
                                        } else {
                                            break;
                                        }
                                    }
                                    response += ".\r\n";
                                } else {
                                    response = "-ERR Invalid lines count format\r\n";
                                }
                            } else {
                                response = "-ERR Message deleted\r\n";
                            }
                        } else {
                            response = "-ERR Message deleted\r\n";
                        }
                    }
                } else {
                    response = "-ERR Invalid message number\r\n";
                }
            }
        } else if (message.startsWith("DELE ")) {
            if (state.mailbox == null) {
                response = "-ERR no user given\r\n";
            } else {
                String index = message.substring(5);
                if (index.matches("\\d+")) {
                    int num = Integer.valueOf(index);
                    if (num > state.messages.size()) {
                        response = "-ERR Invalid message number\r\n";
                    } else {
                        final String mail = state.messages.get(num - 1);
                        if (!state.deleted.contains(mail)) {
                            state.deleted.add(mail);
                            response = "+OK\r\n";
                        } else {
                            response = "-ERR Message deleted\r\n";
                        }
                    }
                } else {
                    response = "-ERR Invalid message number\r\n";
                }
            }
        } else if (message.equals("RSET")) {
            if (state.mailbox == null) {
                response = "-ERR no user given\r\n";
            } else {
                state.deleted.clear();
                response = "+OK\r\n";
            }
        } else if (message.equals("NOOP")) {
            response = "+OK\r\n";
        } else if (message.equals("QUIT")) {
            response = "+OK\r\n";
            // Remove deleted files
            state.deleted.stream()
                .map(mail -> mailboxes.getMessage(state.mailbox, mail))
                .filter(File::exists)
                .forEach(file -> {
                    if (file.delete()) {
                        UIEventBus.post(new MailboxEvent(state.mailbox, file.getName(), MailboxEvent.Type.DELETED));
                    }
                });
            closeConnection = true;
        } else {
            response = "-ERR Not implemented\r\n";
        }
        logger.debug(">>: {}", response.trim());
        channel.writeAndFlush(response);
        UIEventBus.post(new POP3SessionLogEvent(channelId, Direction.SERVER, response));

        if (closeConnection) {
            ctx.close();
        }
    }

    /**
     * POP3 session state.
     */
    private static class State implements Serializable {
        private String mailbox;
        private List<String> messages;
        private List<String> deleted;
    }
}
