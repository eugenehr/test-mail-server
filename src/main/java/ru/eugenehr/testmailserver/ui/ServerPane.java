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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.ResourceBundle;

import io.netty.channel.Channel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import ru.eugenehr.testmailserver.event.SessionLogEvent;
import ru.eugenehr.testmailserver.event.SessionLogEvent.Direction;

/**
 * Base class for SMTP and POP3 server pane.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
abstract class ServerPane extends BorderPane {

    /**
     * Maximum inactive sessions in sessions list.
     */
    private static final int SESSION_VIEW_MAXSIZE = 100;
    /**
     * Maximum log records in log view.
     */
    private static final int LOG_VIEW_MAXSIZE = 10000;

    protected final ResourceBundle superBundle = ResourceBundle.getBundle(ServerPane.class.getName());
    protected final ResourceBundle bundle;

    protected final BooleanProperty startedProperty = new SimpleBooleanProperty();

    protected final HBox titlePane;
    protected final Label title;
    protected final TextField portField;
    protected final Hyperlink startStopButton;
    protected final ListView<SessionViewItem> sessionsView;
    protected final ListView<SessionLogEvent> logView;

    /**
     * Creates a server pane.
     */
    public ServerPane() {
        this.bundle = ResourceBundle.getBundle(getClass().getName());
        getStyleClass().add("server-pane");

        // Top pane
        titlePane = new HBox();
        titlePane.getStyleClass().add("title-pane");

        title = new Label(getString("title"));
        title.getStyleClass().add("title");
        title.prefWidthProperty().bind(titlePane.widthProperty());
        titlePane.getChildren().add(title);

        portField = new TextField();
        portField.getStyleClass().add("port-field");
        portField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("[0-9]*")) {
                portField.setText(newValue.replaceAll("\\D", ""));
            }
        });
        portField.setPrefWidth(60);
        portField.minWidthProperty().bind(portField.prefWidthProperty());
        portField.setTooltip(new Tooltip(getString("port.tooltip")));
        portField.disableProperty().bind(startedProperty);
        titlePane.getChildren().add(portField);

        final Image startImage = new Image(ServerPane.class.getResource("/start.png").toExternalForm());
        final Tooltip startTooltip = new Tooltip(getString("startButton.tooltip"));

        final Image stopImage = new Image(ServerPane.class.getResource("/stop.png").toExternalForm());
        final Tooltip stopTooltip = new Tooltip(getString("stopButton.tooltip"));

        startStopButton = new Hyperlink();
        startStopButton.setGraphic(new ImageView(startImage));
        startStopButton.setTooltip(startTooltip);
        startStopButton.getStyleClass().add("start-stop-button");
        startStopButton.setMinSize(startImage.getWidth(), startImage.getHeight());
        startStopButton.setOnAction(event -> startStopServer(!startedProperty.get()));
        startedProperty.addListener((observable, oldValue, newValue) -> {
            final Image image = newValue ? stopImage : startImage;
            startStopButton.setGraphic(new ImageView(image));
            startStopButton.setTooltip(newValue ? stopTooltip : startTooltip);
        });
        titlePane.getChildren().add(startStopButton);

        Label gap = new Label();
        titlePane.getChildren().add(gap);

        setTop(titlePane);

        // Sessions list and session log
        logView = new ListView<>();
        logView.setCellFactory(item -> new LogViewCell());
        setCenter(logView);

        sessionsView = new ListView<>();
        sessionsView.setCellFactory(item -> new SessionViewCell());
        sessionsView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            logView.getItems().clear();
            logView.getItems().addAll(newValue.events);
        });
        sessionsView.setPrefWidth(150);
        setLeft(sessionsView);

        UIEventBus.register(this);
    }

    /**
     * Starts or stops the server.
     *
     * @param start {@code true} to start the server
     */
    protected void startStopServer(boolean start) {
        if (start) {
            final String port = portField.getText();
            if (!port.isEmpty()) {
                try {
                    startServer(Integer.valueOf(port));
                } catch (Exception ex) {
                    final Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(getString("errorAlert.title"));
                    alert.setHeaderText(getString("errorAlert.headerText"));
                    alert.setContentText(ex.getMessage());
                    alert.showAndWait();
                }
            }
        } else {
            stopServer();
        }
        startedProperty.set(start);
    }

    /**
     * Gets a string from derived class resource bundle or from self resource bundle.
     *
     * @param key string key
     * @return string value
     */
    protected String getString(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception ex) {
            return superBundle.getString(key);
        }
    }

    protected void onSessionCreated(String sessionId) {
        final ObservableList<SessionViewItem> sessions = sessionsView.getItems();
        final SessionViewItem session = new SessionViewItem(sessionId);
        if (!sessions.contains(session)) {
            sessions.add(0, new SessionViewItem(sessionId));
            // remove old closed sessions
            if (sessions.size() > SESSION_VIEW_MAXSIZE) {
                final ListIterator<SessionViewItem> it = sessions.listIterator(sessions.size());
                while (sessions.size() > SESSION_VIEW_MAXSIZE && it.hasPrevious()) {
                    final SessionViewItem item = it.previous();
                    if (!item.active) {
                        it.remove();
                    }
                }
            }
            if (sessionsView.getSelectionModel().isEmpty()) {
                sessionsView.getSelectionModel().select(session);
            }
        }
    }

    protected void onSessionClosed(String sessionId) {
        SessionViewItem session = new SessionViewItem(sessionId);
        int index = sessionsView.getItems().indexOf(session);
        if (index >= 0) {
            session = sessionsView.getItems().get(index);
            if (session.events.isEmpty()) {
                sessionsView.getItems().remove(session);
            } else {
                session.active = false;
                sessionsView.refresh();
            }
        }
    }

    protected void onSessionMessage(SessionLogEvent event) {
        SessionViewItem session = new SessionViewItem(event.sessionId);
        int index = sessionsView.getItems().indexOf(session);
        if (index >= 0) {
            session = sessionsView.getItems().get(index);
            // Append log to tail
            session.events.add(event);
            while (session.events.size() > LOG_VIEW_MAXSIZE) {
                session.events.remove(0);
            }
            // Update log view if the current session is selected
            if (session.equals(sessionsView.getSelectionModel().getSelectedItem())) {
                logView.getItems().clear();
                logView.getItems().addAll(session.events);
                logView.scrollTo(event);
            }
        }
    }

    /**
     * Starts the server. Must be implemented in derived classes.
     *
     * @param port TPC port to listen to
     */
    protected abstract Channel startServer(int port) throws Exception;

    /**
     * Stops the server.
     */
    protected abstract void stopServer();

    /**
     * Session ListView item element.
     */
    private static class SessionViewItem {
        private final List<SessionLogEvent> events;
        private String sessionId;
        private boolean active;

        public SessionViewItem(String sessionId) {
            this.sessionId = sessionId;
            this.active = true;
            events = new ArrayList<>();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof SessionViewItem) {
                SessionViewItem that = (SessionViewItem) other;
                return Objects.equals(sessionId, that.sessionId);
            } else if (other instanceof String) {
                return Objects.equals(sessionId, other);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionId);
        }
    }

    private static class SessionViewCell extends ListCell<SessionViewItem> {

        private static final Image active = new Image(
            ServerPane.class.getResource("/greenbull.png").toExternalForm());
        private static final Image closed = new Image(
            ServerPane.class.getResource("/redbull.png").toExternalForm());

        private final ImageView imageView = new ImageView();

        @Override
        protected void updateItem(SessionViewItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                imageView.setImage(item.active ? active : closed);
                setText(item.sessionId);
                setGraphic(imageView);
            }
        }
    }

    private static class LogViewCell extends ListCell<SessionLogEvent> {

        private static final Image server = new Image(
            ServerPane.class.getResource("/right.png").toExternalForm());
        private static final Image client = new Image(
            ServerPane.class.getResource("/left.png").toExternalForm());

        private final ImageView imageView = new ImageView();

        @Override
        protected void updateItem(SessionLogEvent item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.log.trim());
                imageView.setImage(item.direction == Direction.SERVER ? server : client);
                setGraphic(imageView);
            }
        }
    }
}
