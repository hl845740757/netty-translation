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
package io.netty.util.concurrent;

import io.netty.util.internal.UnstableApi;

/**
 * EventExecutorChooser工厂。
 * 负责创建EventExecutor的选择器。
 *
 * Factory that creates new {@link EventExecutorChooser}s.
 */
@UnstableApi
public interface EventExecutorChooserFactory {

    /**
     * 为这些{@link EventExecutor}创建一个选择器，负责它们之间的负载均衡。
     *
     * Returns a new {@link EventExecutorChooser}.
     */
    EventExecutorChooser newChooser(EventExecutor[] executors);

    /**
     * 当调用{@link EventExecutorGroup#next()}的时候，由 Chooser负责真正的选择。
     * 主要是实现Executor的负载均衡。
     *
     * 是一种策略模式(或者说桥接模式)的运用
     * Chooses the next {@link EventExecutor} to use.
     */
    @UnstableApi
    interface EventExecutorChooser {

        /**
         * 返回下一个使用的{@link EventExecutor}
         * Returns the new {@link EventExecutor} to use.
         */
        EventExecutor next();
    }
}
