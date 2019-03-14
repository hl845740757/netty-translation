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
package io.netty.channel.nio;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.DefaultSelectStrategyFactory;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.SelectStrategyFactory;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorChooserFactory;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.concurrent.RejectedExecutionHandlers;

import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

/**
 * {@link NioEventLoopGroup}是用于基于Selector的channel的{@link MultithreadEventLoopGroup}的实现。
 *
 * 说人话：
 * 1.{@link NioEventLoopGroup}是{@link MultithreadEventLoopGroup}的实现。
 * 2.{@link NioEventLoopGroup}用于基于{@link Selector} 的 {@link Channel}。
 *
 * 这个类的构造方法是个糟糕的设计。参数太多，应该采用Builder模式代替过多的方法重载。
 * Builder的两种解决方法：1.直接Builder构建 2.Builder构建一个Options对象，构造方法传入Options对象。
 *
 * {@link MultithreadEventLoopGroup} implementations which is used for NIO {@link Selector} based {@link Channel}s.
 */
public class NioEventLoopGroup extends MultithreadEventLoopGroup {

    /**
     * 使用默认的线程数、线程工厂、{@link SelectorProvider} 创建一个实例。
     * 这些简单的构造方法就不再过多解释了。
     *
     * Create a new instance using the default number of threads, the default {@link ThreadFactory} and
     * the {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup() {
        this(0);
    }

    /**
     * Create a new instance using the specified number of threads, {@link ThreadFactory} and the
     * {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup(int nThreads) {
        this(nThreads, (Executor) null);
    }

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the
     * {@link SelectorProvider} which is returned by {@link SelectorProvider#provider()}.
     */
    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory) {
        this(nThreads, threadFactory, SelectorProvider.provider());
    }

    public NioEventLoopGroup(int nThreads, Executor executor) {
        this(nThreads, executor, SelectorProvider.provider());
    }

    /**
     *
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory} and the given
     * {@link SelectorProvider}.
     */
    public NioEventLoopGroup(
            int nThreads, ThreadFactory threadFactory, final SelectorProvider selectorProvider) {
        this(nThreads, threadFactory, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }

    public NioEventLoopGroup(int nThreads, ThreadFactory threadFactory,
        final SelectorProvider selectorProvider, final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, threadFactory, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    public NioEventLoopGroup(
            int nThreads, Executor executor, final SelectorProvider selectorProvider) {
        this(nThreads, executor, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
    }

    /**
     * 创建一个实例
     * @param nThreads 线程数
     * @param executor 任务执行器(执行单元
     * @param selectorProvider selector提供者
     * @param selectStrategyFactory selector的选择策略工厂(创建选择策略--Netty支持多种select方式)
     */
    public NioEventLoopGroup(int nThreads, Executor executor, final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
    }

    /**
     * 创建一个实例
     * @param nThreads 线程数
     * @param executor 任务执行器(执行单元)
     * @param chooserFactory EventExecutor选择器工厂
     *        作用见{@link EventExecutorChooserFactory.EventExecutorChooser#next()}
     *
     * @param selectorProvider Selector的提供者
     * @param selectStrategyFactory selector的选择策略工厂(创建选择策略--Netty支持多种select方式)
     */
    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory,
                RejectedExecutionHandlers.reject());
    }

    /**
     *
     * @param nThreads 线程数
     * @param executor 任务执行器(执行单元)
     * @param chooserFactory EventExecutor选择器工厂
     *        作用见{@link EventExecutorChooserFactory.EventExecutorChooser#next()}
     * @param selectorProvider Selector的提供者
     * @param selectStrategyFactory selector的选择策略工厂(创建选择策略--Netty支持多种select方式)
     * @param rejectedExecutionHandler executor拒绝策略。当提交的任务不能执行时执行的策略。()
     */
    public NioEventLoopGroup(int nThreads, Executor executor, EventExecutorChooserFactory chooserFactory,
                             final SelectorProvider selectorProvider,
                             final SelectStrategyFactory selectStrategyFactory,
                             final RejectedExecutionHandler rejectedExecutionHandler) {
        super(nThreads, executor, chooserFactory, selectorProvider, selectStrategyFactory, rejectedExecutionHandler);
    }

    /**
     * 设置花费在 child event loops IO操作上的期望的时间比例。
     * 默认值为50，也就是说 event loop 将会尝试在 IO任务和非IO任务上花费相同的时间。
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.  The default value is
     * {@code 50}, which means the event loop will try to spend the same amount of time for I/O as for non-I/O tasks.
     */
    public void setIoRatio(int ioRatio) {
        for (EventExecutor e: this) {
            ((NioEventLoop) e).setIoRatio(ioRatio);
        }
    }

    /**
     * 创建新的Selector 替换{@link NioEventLoopGroup}中所有的 EventLoop当前的Selector，
     * 以解决 Epoll模式下 CPU 使用率 100%的bug
     *
     * Replaces the current {@link Selector}s of the child event loops with newly created {@link Selector}s to work
     * around the  infamous epoll 100% CPU bug.
     */
    public void rebuildSelectors() {
        for (EventExecutor e: this) {
            ((NioEventLoop) e).rebuildSelector();
        }
    }

    /**
     * 创建一个Child--->EventLoop
     * @param executor
     * @param args 参数。这是一种很不好的面向对象设计。应该使用明确的对象代替数组。
     *             按顺序解释数组，可读性很差，也任意产生错误。
     *             参数1: selectorProvider 参数2: SelectStrategyFactory 参数3: RejectedExecutionHandler
     * @return
     * @throws Exception
     */
    @Override
    protected EventLoop newChild(Executor executor, Object... args) throws Exception {
        return new NioEventLoop(this, executor, (SelectorProvider) args[0],
            ((SelectStrategyFactory) args[1]).newSelectStrategy(), (RejectedExecutionHandler) args[2]);
    }
}
