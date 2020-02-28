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
 * 在事件循环线程中执行一个阻塞操作，该阻塞操作可能导致线程进入死锁状态，因此在检测到可能死锁时，抛出该异常。
 *
 * 死锁分析：
 * 如果EventExecutor是单线程的，线程一次只能执行一个任务，如果在执行任务的时候等待该线程上的另一个任务完成，将死锁。
 * --
 * 我在我的实现中，添加了另一个异常{@code GuardedException}，当尝试访问受保护的资源时抛出，即一个操作只允许指定线程操作。
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
