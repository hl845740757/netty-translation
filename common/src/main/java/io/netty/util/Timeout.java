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
package io.netty.util;

/**
 * 由{@link Timer}返回的关联{@link TimerTask}的句柄。
 *
 * A handle associated with a {@link TimerTask} that is returned by a
 * {@link Timer}.
 */
public interface Timeout {

    /**
     * 返回创建该{@link Timeout}的{@link Timer}.
     * Returns the {@link Timer} that created this handle.
     */
    Timer timer();

    /**
     * 返回该{@link Timeout}关联的 {@link TimerTask}.
     * Returns the {@link TimerTask} which is associated with this handle.
     */
    TimerTask task();

    /**
     * 关联的{@link TimerTask}是否已过期
     *
     * Returns {@code true} if and only if the {@link TimerTask} associated
     * with this handle has been expired.
     */
    boolean isExpired();

    /**
     * 关联的{@link TimerTask}是否已取消。
     * Returns {@code true} if and only if the {@link TimerTask} associated
     * with this handle has been cancelled.
     */
    boolean isCancelled();

    /**
     * 尝试取消关联的{@link TimerTask}。
     * 如果关联的task已经执行，或者取消，那么它将返回true而不造成任何副作用(什么也不干).
     *
     * Attempts to cancel the {@link TimerTask} associated with this handle.
     * If the task has been executed or cancelled already, it will return with
     * no side effect.
     *
     * @return True if the cancellation completed successfully, otherwise false
     */
    boolean cancel();
}
