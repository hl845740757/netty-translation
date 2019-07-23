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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * EventExecutorGroup是事件执行器组，同时是是一种特殊的支持任务调度的执行服务。
 *
 * {@link EventExecutorGroup} 有两个作用：
 * 1.通过它的 {@link #next()} 方法提供 {@link EventExecutor}
 * 2. 此外，还负责 {@link EventExecutor} 的生命周期，允许使用全局统一的方式关闭他们。--- fashion(时尚，方法，方式)
 *
 * 总的来说：它并不直接处理用户请求，而是将请求转交给它所持有的执行单元。它管理它持有的执行单元的生命周期。
 * 它代表的是一组线程服务的高级封装。
 *
 * EventExecutorGroup是Netty中的事件服务(事件处理器你)的顶层接口。
 * Netty中从 EventExecutor 渐渐地演化到 EventLoop，赋予它更明确的意义。(归纳与演进)
 *
 * Netty是异步的，是基于事件的，它需要不停的处理各种各样的事件，因此定义了EventExecutor。
 * 而Netty又在此基础上实现了事件循环，即：EventLoop.
 *
 * PS:
 * EventExecutorGroup 与 EventExecutor 之间的关系就是容器与容器内的元素这样的关系。
 * 可以看做是设计模式中的组合模式的，EventExecutorGroup 既是顶层的 Component，也是容器节点，而EventExecutor是叶子节点。
 *
 * 在Netty的设计中：带Group/Multi的是多线程(容器)，而不带的基本都是单线程(执行单元)。
 * {@link EventExecutorGroup}就是多线程的顶层接口，{@link EventExecutor}就是单线程的顶层接口。
 *
 * The {@link EventExecutorGroup} is responsible for providing the {@link EventExecutor}'s to use
 * via its {@link #next()} method. Besides this, it is also responsible for handling their
 * life-cycle and allows shutting them down in a global fashion.
 *
 */
public interface EventExecutorGroup extends ScheduledExecutorService, Iterable<EventExecutor> {

    /**
     * 分配下一个执行任务的{@link EventExecutor}。
     * {@link EventExecutorGroup}管理着一组{@link EventExecutor}，由它们真正的执行任务(请求)。
     * 在返回{@link EventExecutor}时需要尽可能的负载均衡。
     *
     * (该方法比较重要，因此我挪动到最上面)
     *
     * Returns one of the {@link EventExecutor}s managed by this {@link EventExecutorGroup}.
     */
    EventExecutor next();

    /**
     * 查询{@link EventExecutorGroup}是否处于正在关闭状态。
     *
     * 如果该{@link EventExecutorGroup}管理的所有{@link EventExecutor}正在优雅的关闭或已关闭则返回true
     *
     * Returns {@code true} if and only if all {@link EventExecutor}s managed by this {@link EventExecutorGroup}
     * are being {@linkplain #shutdownGracefully() shut down gracefully} or was {@linkplain #isShutdown() shut down}.
     */
    boolean isShuttingDown();

    /**
     * {@link #shutdownGracefully(long, long, TimeUnit)}的快捷调用方式，参数为合理的默认值。
     * (该方法就不详细解释了，见带参方法)
     * Shortcut method for {@link #shutdownGracefully(long, long, TimeUnit)} with sensible default values.
     *
     * @return the {@link #terminationFuture()}
     */
    Future<?> shutdownGracefully();

    /**
     * 通知当前{@link EventExecutorGroup} 关闭。
     * 一旦该方法被调用，{@link #isShuttingDown()}将开始返回true,并且当前 executor准备开始关闭自己。
     * 和{@link #shutdown()}方法不同的是，优雅的关闭将保证在关闭前的安静期没有任务提交。
     * 如果在安静期提交了一个任务，那么它一定会接受它并重新进入安静期。
     * (也就是说不推荐使用 {@link ExecutorService#shutdown()} 和 {@link ExecutorService#shutdownNow()}方法。
     *
     * Signals this executor that the caller wants the executor to be shut down.  Once this method is called,
     * {@link #isShuttingDown()} starts to return {@code true}, and the executor prepares to shut itself down.
     * Unlike {@link #shutdown()}, graceful shutdown ensures that no tasks are submitted for <i>'the quiet period'</i>
     * (usually a couple seconds) before it shuts itself down.  If a task is submitted during the quiet period,
     * it is guaranteed to be accepted and the quiet period will start over.
     *
     * @param quietPeriod the quiet period as described in the documentation 默认的安静时间(秒)
     * @param timeout     the maximum amount of time to wait until the executor is {@linkplain #shutdown()}
     *                    regardless if a task was submitted during the quiet period
     *                    等待当前executor成功关闭的超时时间，而不管是否有任务在关闭前的安静期提交。
     * @param unit        the unit of {@code quietPeriod} and {@code timeout}
     *                    quietPeriod 和 timeout 的时间单位。
     *
     * @return the {@link #terminationFuture()}
     */
    Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit);

    /**
     * 返回等待线程终止的future。
     * 返回的{@link Future}会在该Group管理的所有{@link EventExecutor}终止后收到一个通知
     *
     * Returns the {@link Future} which is notified when all {@link EventExecutor}s managed by this
     * {@link EventExecutorGroup} have been terminated.
     */
    Future<?> terminationFuture();

    /**
     * shutdown方法是 {@link java.util.concurrent.ExecutorService}中的方法。netty并不推荐使用它，
     * 而是使用netty定义的优雅关闭方法{@link #shutdownGracefully}
     *
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    void shutdown();

    /**
     * shutdownNow方法是 {@link java.util.concurrent.ExecutorService}中的方法。netty并不推荐使用它，
     * 而是使用netty定义的优雅关闭方法{@link #shutdownGracefully}
     * @deprecated {@link #shutdownGracefully(long, long, TimeUnit)} or {@link #shutdownGracefully()} instead.
     */
    @Override
    @Deprecated
    List<Runnable> shutdownNow();

    @Override
    Iterator<EventExecutor> iterator();

    @Override
    Future<?> submit(Runnable task);

    @Override
    <T> Future<T> submit(Runnable task, T result);

    @Override
    <T> Future<T> submit(Callable<T> task);

    @Override
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    @Override
    <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    @Override
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
