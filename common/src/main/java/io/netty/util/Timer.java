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

import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * (这句话不好翻译，也不太容易直接表述timer的功能。)
 * Schedules {@link TimerTask}s for one-time future execution in a background
 * thread.
 */
public interface Timer {

    /**
     * 在指定延迟之后执行(调度)给定的{@link TimerTask}。
     *
     * Schedules the specified {@link TimerTask} for one-time execution after
     * the specified delay.
     *
     * @return a handle which is associated with the specified task.
     *         与该任务关联的{@link Timeout}
     *
     * @throws IllegalStateException       if this timer has been {@linkplain #stop() stopped} already
     * @throws RejectedExecutionException if the pending timeouts are too many and creating new timeout
     *                                    can cause instability in the system.
     */
    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);

    /**
     * 释放该{@link Timer}持有的所有资源，并取消所有的已提交但还未执行的任务。
     *
     * Releases all resources acquired by this {@link Timer} and cancels all
     * tasks which were scheduled but not executed yet.
     *
     * @return the handles associated with the tasks which were canceled by
     *         this method
     */
    Set<Timeout> stop();
}
