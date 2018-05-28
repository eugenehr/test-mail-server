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

package ru.eugenehr.testmailserver.event;

/**
 * Mailbox notification event.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
public class MailboxEvent {

    public final String mailbox;
    public final String message;
    public final Type type;

    /**
     * Constructor.
     *
     * @param mailbox mailbox
     * @param message message
     * @param type    event type
     */
    public MailboxEvent(String mailbox, String message, Type type) {
        this.mailbox = mailbox;
        this.message = message;
        this.type = type;
    }

    public enum Type {
        CREATED,
        DELETED,
        BLOCKED,
        UNBLOCKED
    }
}
