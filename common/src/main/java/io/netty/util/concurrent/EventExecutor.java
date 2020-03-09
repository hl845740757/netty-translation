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
 * {@link EventExecutor}是{@link EventExecutorGroup}中的成员，它是真正执行事件处理的单元。
 * 它继承自{@link EventExecutorGroup}表示它可以作为一个只有单个成员的{@link EventExecutorGroup}进行服务。
 *
 * 它附带了一些简单方法去查看一个线程是否运行在EventLoop中。
 * 此外，它继承了{@link EventExecutorGroup}并且提供了访问{@link EventExecutorGroup}的通用方法。
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
     * {@link EventExecutor}表示持有一个{@link EventExecutor}的{@link EventExecutorGroup}， 因此它总是返回自身进行服务。
     *
     * Returns a reference to itself.
     */
    @Override
    EventExecutor next();

    /**
     * 返回EventExecutor的父节点，因为{@link EventExecutor} 是 {@link EventExecutorGroup}的一个内部组件。
     * (元素 与 容器的关系)
     * 它可能为null，因为它自己也表示一个{@link EventExecutorGroup}，因此可以独立存在。
     *
     * Return the {@link EventExecutorGroup} which is the parent of this {@link EventExecutor},
     */
    EventExecutorGroup parent();

    /**
     * 一个便捷方法，查询当前线程(调用方法的线程)是否是EventLoop线程。
     * 它暗示着：如果当前线程是EventLoop线程，那么可以访问一些线程封闭的数据。
     * <h3>时序问题</h3>
     * 以下代码可能产生时序问题:
     * <pre>
     * {@code
     * 		if(eventLoop.inEventLoop()) {
     * 	    	doSomething();
     *        } else{
     * 			eventLoop.execute(() -> doSomething());
     *        }
     * }
     * </pre>
     * Q: 产生的原因？
     * A: 单看任意一个线程，该线程的所有操作之间都是有序的，这个应该能理解。
     * 但是出现多个线程执行该代码块时：
     * 1. 所有的非EventLoop线程的操作会进入同一个队列，因此所有的非EventLoop线程之间的操作是有序的！
     * 2. 但是EventLoop线程是直接执行的，并没有进入队列，它是插队执行的。
     * 它有时候是无害的，有时候则可能有害的，因此需要慎重使用！
     * <p>
     * 这里我要批评下这个方法放的位置，这个方法放在{@link EventExecutor}接口下，带来的混乱甚至超过好处，它更适合定义在{@code EventLoop}下。
     * 即使按照{@link io.netty.util.ReferenceCountUtil}类中使用 instanceOf 和 inEventLoop判断，都会更好点。
     * <p>
     * Calls {@link #inEventLoop(Thread)} with {@link Thread#currentThread()} as argument
     */
    boolean inEventLoop();

    /**
     * 查询给定的线程是否是EventLoop使用的线程。
     * 该方法实现必须是线程安全的，也表明了存在用于比较的Thread属性。
     *
     * 目的：数据线程封闭。
     * 某些操作和数据只允许EventLoop线程自身操作和访问，不允许其它线程直接访问这些数据，否则将造成线程安全问题。
     *
     * 多线程的{@link EventExecutor}一定是返回false的，因为它不能提供线程封闭功能。
     * 单线程的{@link EventExecutor}才可能返回true。
     *
     * Return {@code true} if the given {@link Thread} is executed in the event loop,
     * {@code false} otherwise.
     */
    boolean inEventLoop(Thread thread);

    /**
     * 创建一个{@link Promise}(一个可写的Future)。
     * 用户提交一个任务之后，返回给客户端一个Promise，
     * 使得用户可以获取操作结果和添加监听器。
     *
     * Return a new {@link Promise}.
     */
    <V> Promise<V> newPromise();

    /**
     * 创建一个{@link ProgressivePromise}，可以监控任务的进度
     *
     * Create a new {@link ProgressivePromise}.
     */
    <V> ProgressivePromise<V> newProgressivePromise();

    /**
     * 创建一个{@link Future}，该future表示它关联的任务早已正常完成。因此{@link Future#isSuccess()}总是返回true。
     * 所有添加到该future上的{@link FutureListener}都会立即被通知。并且该future上的所有阻塞方法会立即返回而不会阻塞。
     *
     * Create a new {@link Future} which is marked as succeeded already. So {@link Future#isSuccess()}
     * will return {@code true}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    <V> Future<V> newSucceededFuture(V result);

    /**
     * 创建一个{@link Future}，该future表示它关联的任务早已失败。因此{@link Future#isSuccess()}总是返回false。
     * 所有添加到该future上的{@link FutureListener}都会立即被通知。并且该future上的所有阻塞方法会立即返回而不会阻塞。*
     *
     * Create a new {@link Future} which is marked as failed already. So {@link Future#isSuccess()}
     * will return {@code false}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    <V> Future<V> newFailedFuture(Throwable cause);
}
