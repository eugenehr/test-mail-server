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

import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.google.common.eventbus.Subscribe;
import io.netty.util.CharsetUtil;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.apache.commons.io.FileUtils;

import ru.eugenehr.testmailserver.MailServer;
import ru.eugenehr.testmailserver.Mailboxes;
import ru.eugenehr.testmailserver.event.MailboxEvent;

/**
 * Users mailboxes pane.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
public class MailboxesPane extends BorderPane {

    private final ResourceBundle bundle = ResourceBundle.getBundle(MailboxesPane.class.getName());
    private final Mailboxes mailboxes;

    private final ListView<String> mailboxesView;
    private final ListView<String> messagesView;
    private final TextArea messagePane;

    /**
     * Creates a mailboxes pane.
     */
    public MailboxesPane() {
        mailboxes = MailServer.getInstance().getMailboxes();

        // Title pane
        final HBox titlePane = new HBox();
        titlePane.getStyleClass().add("title-pane");
        final Label title = new Label(bundle.getString("title"));
        title.getStyleClass().add("title");
        titlePane.getChildren().add(title);
        setTop(titlePane);

        final SplitPane centerPane = new SplitPane();
        centerPane.setOrientation(Orientation.HORIZONTAL);
        centerPane.setDividerPositions(0.2, 0.45);

        mailboxesView = new ListView<>();
        mailboxesView.getItems().addAll(this.mailboxes.getMailboxes());
        mailboxesView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
            selectMailbox(newValue));
        centerPane.getItems().add(mailboxesView);

        messagesView = new ListView<>();
        messagesView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            String mailbox = mailboxesView.getSelectionModel().getSelectedItem();
            selectMessage(mailbox, newValue);
        });
        centerPane.getItems().add(messagesView);

        messagePane = new TextArea();
        messagePane.setEditable(false);
        centerPane.getItems().add(messagePane);

        setCenter(centerPane);

        if (!mailboxesView.getItems().isEmpty()) {
            mailboxesView.getSelectionModel().select(mailboxesView.getItems().get(0));
        }

        UIEventBus.register(this);
    }

    /**
     * Selects a mailbox.
     *
     * @param mailbox mailbox to select
     */
    private void selectMailbox(String mailbox) {
        messagesView.getItems().clear();
        messagesView.getItems().addAll(mailboxes.getMessages(mailbox));
        if (!messagesView.getItems().isEmpty()) {
            messagesView.getSelectionModel().select(messagesView.getItems().get(0));
        } else {
            messagePane.clear();
        }
    }

    /**
     * Selects a message in a mailbox.
     *
     * @param mailbox mailbox
     * @param message message
     */
    private void selectMessage(String mailbox, String message) {
        messagePane.clear();
        if (mailbox != null && message != null) {
            final File file = mailboxes.getMessage(mailbox, message);
            if (file.exists()) {
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return FileUtils.readLines(file, CharsetUtil.US_ASCII)
                            .stream()
                            .collect(Collectors.joining("\n"));
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }).exceptionally(Throwable::getMessage)
                    .thenAccept(text -> Platform.runLater(() -> messagePane.setText(text)));
            }
        }
    }

    /**
     * Mailbox event listener.
     *
     * @param event mailbox event
     */
    @Subscribe
    public void onMailboxEvent(MailboxEvent event) {
        if (event.type == MailboxEvent.Type.CREATED) {
            if (!mailboxesView.getItems().contains(event.mailbox)) {
                mailboxesView.getItems().add(event.mailbox);
            }
            if (mailboxesView.getItems().size() == 1) {
                mailboxesView.getSelectionModel().select(event.mailbox);
            }
            if (event.mailbox.equals(mailboxesView.getSelectionModel().getSelectedItem())) {
                selectMailbox(event.mailbox);
            }
        } else if (event.type == MailboxEvent.Type.DELETED) {
            if (messagesView.getItems().contains(event.message)) {
                boolean selected = event.message.equals(messagesView.getSelectionModel().getSelectedItem());
                messagesView.getItems().remove(event.message);
                // Restore selection
                if (selected && messagesView.getItems().size() > 0) {
                    messagesView.getSelectionModel().select(0);
                }
            }
        }
    }
}
