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

package ru.eugenehr.testmailserver.ui;

import com.google.common.eventbus.Subscribe;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.eugenehr.testmailserver.MailServer;
import ru.eugenehr.testmailserver.event.POP3SessionEvent;
import ru.eugenehr.testmailserver.event.POP3SessionLogEvent;
import ru.eugenehr.testmailserver.event.SessionEvent;

/**
 * POP3 server pane.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
public class POP3Pane extends ServerPane {

    private static final Logger logger = LoggerFactory.getLogger(POP3Pane.class);

    /**
     * Creates a POP3 server pane.
     */
    public POP3Pane() {
        startedProperty.setValue(MailServer.getInstance().isPOP3Started());
        portField.setText(Integer.toString(MailServer.getInstance().getPop3Port()));
    }

    /**
     * Starts the server.
     *
     * @param port TPC port to listen to
     */
    @Override
    protected Channel startServer(int port) throws Exception {
        return MailServer.getInstance().startPOP3(port);
    }

    /**
     * Stops the server.
     */
    @Override
    protected void stopServer() {
        MailServer.getInstance().stopPOP3();
    }

    /**
     * POP3Session CREATED and CLOSED events listener.
     *
     * @param event the event
     */
    @Subscribe
    public void onSessionEvent(POP3SessionEvent event) {
        if (event.type == SessionEvent.Type.CREATED) {
            onSessionCreated(event.sessionId);
        } else {
            onSessionClosed(event.sessionId);
        }
    }

    /**
     * POP3Session message events listener.
     *
     * @param event the event
     */
    @Subscribe
    public void onSessionLogEvent(POP3SessionLogEvent event) {
        onSessionMessage(event);
    }
}
