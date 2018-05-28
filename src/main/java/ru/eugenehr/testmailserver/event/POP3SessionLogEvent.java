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
 * POP3 session log event.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
public class POP3SessionLogEvent extends SessionLogEvent {

    public POP3SessionLogEvent(String sessionId, Direction direction, String log) {
        super(sessionId, direction, log);
    }
}
