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

import java.util.concurrent.TimeUnit;

/**
 * 在指定延迟之后执行的任务。
 *
 * A task which is executed after the delay specified with
 * {@link Timer#newTimeout(TimerTask, long, TimeUnit)}.
 */
public interface TimerTask {

    /**
     * 到了指定延迟，执行任务。
     * Executed after the delay specified with
     * {@link Timer#newTimeout(TimerTask, long, TimeUnit)}.
     *
     * @param timeout a handle which is associated with this task.
     *                与该任务关联的{@link Timeout}
     */
    void run(Timeout timeout) throws Exception;
}
