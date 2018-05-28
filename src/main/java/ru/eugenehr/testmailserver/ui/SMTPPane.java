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
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.eugenehr.testmailserver.MailServer;
import ru.eugenehr.testmailserver.event.SMTPSessionEvent;
import ru.eugenehr.testmailserver.event.SMTPSessionLogEvent;
import ru.eugenehr.testmailserver.event.SessionEvent;

/**
 * SMTP server pane.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
public class SMTPPane extends ServerPane {

    private static final Logger logger = LoggerFactory.getLogger(SMTPPane.class);

    /**
     * Creates a SMTP server pane.
     */
    public SMTPPane() {
        startedProperty.setValue(MailServer.getInstance().isSMTPStarted());
        portField.setText(Integer.toString(MailServer.getInstance().getSmtpPort()));

        CheckBox redirectToSender = new CheckBox();
        redirectToSender.setTooltip(new Tooltip(getString("redirectToSender.tooltip")));
        redirectToSender.setSelected(MailServer.getInstance().isRedirectToSender());
        redirectToSender.selectedProperty().addListener((observable, oldValue, newValue) ->
            MailServer.getInstance().setRedirectToSender(newValue));

        Label redirectToSenderTitle = new Label(getString("redirectToSender.title"));
        redirectToSenderTitle.setLabelFor(redirectToSender);
        redirectToSenderTitle.setMinWidth(Region.USE_PREF_SIZE);
        redirectToSenderTitle.setTooltip(redirectToSender.getTooltip());

        titlePane.getChildren().add(1, redirectToSenderTitle);
        titlePane.getChildren().add(2, redirectToSender);
    }

    /**
     * Starts the server.
     *
     * @param port TPC port to listen to
     */
    @Override
    protected Channel startServer(int port) throws Exception {
        return MailServer.getInstance().startSMTP(port);
    }

    /**
     * Stops the server.
     */
    @Override
    protected void stopServer() {
        MailServer.getInstance().stopSMTP();
    }

    /**
     * SMTPSession CREATED and CLOSED events listener.
     *
     * @param event the event
     */
    @Subscribe
    public void onSessionEvent(SMTPSessionEvent event) {
        if (event.type == SessionEvent.Type.CREATED) {
            onSessionCreated(event.sessionId);
        } else {
            onSessionClosed(event.sessionId);
        }
    }

    /**
     * SMTPSession message events listener.
     *
     * @param event the event
     */
    @Subscribe
    public void onSessionLogEvent(SMTPSessionLogEvent event) {
        onSessionMessage(event);
    }
}
