/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel;

import io.netty.util.IntSupplier;

import java.nio.channels.Selector;

/**
 * 选择策略接口。
 * 用于指示EventLoop下一步该干什么。
 *
 * 提供控制select loop行为的功能。 例如，如果有事件要立即处理，则可以延迟或完全跳过阻塞选择操作
 *
 * Select strategy interface.
 *
 * Provides the ability to control the behavior of the select loop. For example a blocking select
 * operation can be delayed or skipped entirely if there are events to process immediately.
 */
public interface SelectStrategy {

    /**
     * 指示下一步应该执行阻塞的select操作 {@link Selector#select()} {@link Selector#select(long)}
     * Indicates a blocking select should follow.
     */
    int SELECT = -1;
    /**
     * 指示IO循环应该重试，下一步应该执行非阻塞的select操作 {@link Selector#selectNow()}
     * Indicates the IO loop should be retried, no blocking select to follow directly.
     */
    int CONTINUE = -2;
    /**
     * 指示IO循环下一步应该非阻塞地轮询新事件。
     * Indicates the IO loop to poll for new events without blocking.
     */
    int BUSY_WAIT = -3;

    /**
     * 计算选择策略。
     * {@link SelectStrategy}可用于指导潜在选择调用的结果。
     *
     * The {@link SelectStrategy} can be used to steer the outcome of a potential select
     * call.
     *
     * @param selectSupplier The supplier with the result of a select result.
     * @param hasTasks true if tasks are waiting to be processed.
     * @return {@link #SELECT} if the next step should be blocking select {@link #CONTINUE} if
     *         the next step should be to not select but rather jump back to the IO loop and try
     *         again. Any value >= 0 is treated as an indicator that work needs to be done.
     *         返回{@link #SELECT}表示下一步应该执行阻塞的select操作
     *         返回{@link #CONTINUE}表示下一步不应该执行选择操作，而是调回IO循环再试一次。
     *         返回任何 >= 0 的值都被视为需要完成工作的指标。
     */
    int calculateStrategy(IntSupplier selectSupplier, boolean hasTasks) throws Exception;
}
