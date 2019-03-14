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
 * 事件执行器，
 *
 * {@link EventExecutor}是一个特殊的{@link EventExecutorGroup}。
 * 它附带了一些简单方法去查看一个线程是否在一个EventLoop中。
 *
 * 此外，它继承了{@link EventExecutorGroup}并且提供了{@link EventExecutorGroup}的通用方法。
 *
 * handy：简单
 *
 * The {@link EventExecutor} is a special {@link EventExecutorGroup} which comes
 * with some handy methods to see if a {@link Thread} is executed in a event loop.
 * Besides this, it also extends the {@link EventExecutorGroup} to allow for a generic
 * way to access methods.
 *
 */
public interface EventExecutor extends EventExecutorGroup {

    /**
     * 返回它自身的一个引用。
     * Returns a reference to itself.
     */
    @Override
    EventExecutor next();

    /**
     * 返回EventExecutor的父节点，因为{@link EventExecutor} 是 {@link EventExecutorGroup}的一个内部组件。
     * (元素 与 容器的关系)
     * Return the {@link EventExecutorGroup} which is the parent of this {@link EventExecutor},
     */
    EventExecutorGroup parent();

    /**
     * 一个便捷方法，查询当前先去是否是当前 {@link EventExecutor}中的线程
     * Calls {@link #inEventLoop(Thread)} with {@link Thread#currentThread()} as argument
     */
    boolean inEventLoop();

    /**
     * 查询给定的线程是否EventExecutor中的线程
     *
     * Return {@code true} if the given {@link Thread} is executed in the event loop,
     * {@code false} otherwise.
     */
    boolean inEventLoop(Thread thread);

    /**
     * 创建一个{@link Promise}
     * Return a new {@link Promise}.
     */
    <V> Promise<V> newPromise();

    /**
     * 创建一个{@link ProgressivePromise}
     * Create a new {@link ProgressivePromise}.
     */
    <V> ProgressivePromise<V> newProgressivePromise();

    /**
     * Create a new {@link Future} which is marked as succeeded already. So {@link Future#isSuccess()}
     * will return {@code true}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    <V> Future<V> newSucceededFuture(V result);

    /**
     * Create a new {@link Future} which is marked as failed already. So {@link Future#isSuccess()}
     * will return {@code false}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    <V> Future<V> newFailedFuture(Throwable cause);
}
