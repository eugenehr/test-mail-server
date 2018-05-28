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

import java.util.concurrent.ThreadFactory;

import io.netty.channel.nio.NioEventLoopGroup;

/**
 * {@link NioEventLoopGroup} with daemon threads.
 *
 * @author <a href="mailto:eugene.khrustalev@gmail.com">Eugene Khrustalev</a>
 */
public class DaemonEventLoopGroup extends NioEventLoopGroup {

    /**
     * Constructor.
     *
     * @param namePrefix threads name prefix
     */
    public DaemonEventLoopGroup(String namePrefix) {
        super(0, new DaemonThreadFactory(namePrefix));
    }

    private static class DaemonThreadFactory implements ThreadFactory {

        private final String namePrefix;
        private int counter;

        public DaemonThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            final Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName(namePrefix + (counter++));
            return thread;
        }
    }
}
