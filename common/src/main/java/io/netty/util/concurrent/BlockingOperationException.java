/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

/**
 * 当用户在事件循环线程中执行了一个阻塞操作时将会抛出一个{@link BlockingOperationException}异常。
 * 在事件循环线程中执行一个阻塞操作，该阻塞操作很可能导致进入死锁状态，因此抛出该异常。
 *
 * An {@link IllegalStateException} which is raised when a user performed a blocking operation
 * when the user is in an event loop thread.  If a blocking operation is performed in an event loop
 * thread, the blocking operation will most likely enter a dead lock state, hence throwing this
 * exception.
 */
public class BlockingOperationException extends IllegalStateException {

    private static final long serialVersionUID = 2462223247762460301L;

    public BlockingOperationException() { }

    public BlockingOperationException(String s) {
        super(s);
    }

    public BlockingOperationException(Throwable cause) {
        super(cause);
    }

    public BlockingOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
