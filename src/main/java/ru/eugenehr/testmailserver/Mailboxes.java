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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.eugenehr.testmailserver.event.MailboxEvent;
import ru.eugenehr.testmailserver.event.MailboxEvent.Type;
import ru.eugenehr.testmailserver.ui.UIEventBus;

/**
 * Mailboxes manager.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
public class Mailboxes {

    private static final Logger logger = LoggerFactory.getLogger(Mailboxes.class);
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyyMMddHHmmssSSS");

    private File root;

    /**
     * Creates a mailboxes manager.
     *
     * @param root path to root directory to store user mailboxes
     */
    public Mailboxes(File root) {
        this.root = root;
    }

    public File getRoot() {
        return root;
    }

    public void setRoot(File root) {
        logger.info("Using '{}' directory to store user mailboxes", root.getAbsolutePath());
        this.root = root;
    }

    /**
     * Lookup for all mailboxes that containing mails.
     *
     * @return the list of mailboxes
     */
    public Set<String> getMailboxes() {
        try {
            return Files.walk(getRoot().toPath(), 1)
                .map(Path::toFile)
                .filter(File::isDirectory)
                .filter(file -> getRoot().equals(file.getParentFile()))
                .filter(file -> {
                    final File[] files = file.listFiles((dir, name) -> name != null && name.endsWith(".msg"));
                    return files != null && files.length > 0;
                })
                .map(File::getName)
                .collect(Collectors.toSet());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Lookup for all messages in the given mailbox.
     *
     * @param mailbox mailbox to lookup
     * @return the list of mail messages
     */
    public Set<String> getMessages(String mailbox) {
        final Set<String> mails = new TreeSet<>();
        final File dir = new File(getRoot(), mailbox);
        if (dir.exists() && dir.isDirectory()) {
            final Iterator<File> iterator = FileUtils.iterateFiles(dir, new String[]{"msg"}, false);
            while (iterator.hasNext()) {
                mails.add(iterator.next().getName());
            }
        }
        return mails;
    }

    /**
     * Adds a message to the users mailboxes.
     *
     * @param from    message sender
     * @param to      message recipients
     * @param message the message
     * @return the list of files in users mailboxes
     */
    public Set<String> addMessage(String from, List<String> to, File message) {
        final String fileName = SDF.format(new Date()) + ".msg";

        final Set<String> destinations;
        if (MailServer.getInstance().isRedirectToSender()) {
            destinations = Collections.singleton(stripRecipient(from));
        } else {
            destinations = to.stream().map(this::stripRecipient).collect(Collectors.toSet());
        }
        final Set<File> destinationFiles = destinations.stream()
            .map(dest -> new File(new File(getRoot(), dest), fileName))
            .collect(Collectors.toSet());

        destinationFiles.forEach(dest -> copyFile(message, dest));

        // Notify UI
        destinations.stream()
            .map(dest -> new MailboxEvent(dest, fileName, Type.CREATED))
            .forEach(UIEventBus::post);

        return destinationFiles.stream().map(File::getAbsolutePath).collect(Collectors.toSet());
    }

    /**
     * Gets a message from the mailbox.
     *
     * @param mailbox mailbox
     * @param message message filename
     * @return the message
     */
    public File getMessage(String mailbox, String message) {
        return new File(new File(getRoot(), mailbox), message);
    }

    /**
     * Removes "<" and ">" from recipient name.
     *
     * @param recipient recipient to strip
     * @return stripped recipient
     */
    private String stripRecipient(String recipient) {
        recipient = recipient.trim();
        while (recipient.startsWith("<")) {
            recipient = recipient.substring(1);
        }
        while (recipient.endsWith(">")) {
            recipient = recipient.substring(0, recipient.length() - 1);
        }
        return recipient;
    }

    private void copyFile(File source, File dest) {
        try {
            dest.getParentFile().mkdirs();
            FileUtils.copyFile(source, dest);
        } catch (IOException ex) {
            logger.error("Could not copy file '{}' to '{}': {}", source, dest, ex.getMessage());
            throw new RuntimeException(ex);
        }
    }
}
