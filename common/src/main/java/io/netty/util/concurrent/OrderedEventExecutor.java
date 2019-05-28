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

/**
 * 这是一个标记接口。
 *
 * 它的作用是标记它的所有子类 按照有序(串行)的方式处理所有提交的任务，
 * 也就意味着它必须是单线程的{@link EventExecutor}
 *
 * Marker interface for {@link EventExecutor}s that will process all submitted tasks in an ordered / serial fashion.
 */
public interface OrderedEventExecutor extends EventExecutor {
}
