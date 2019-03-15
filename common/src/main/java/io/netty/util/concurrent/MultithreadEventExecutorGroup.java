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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  用多线程同时处理任务的{@link EventExecutor}的基本抽象实现。
 *  它继承了{@link AbstractEventExecutorGroup}，也就是说，多线程的EventExecutor是容器节点，
 *  本身不负责业务/事件的处理，而是单纯将事件分派给它的子节点。
 *  它的主要工作就是分派事件和管理子节点的生命周期。
 *
 *  对应的应该是单线程的事件处理器 {@link SingleThreadEventExecutor}
 *
 * Abstract base class for {@link EventExecutorGroup} implementations that handles their tasks with multiple threads at
 * the same time.
 */
public abstract class MultithreadEventExecutorGroup extends AbstractEventExecutorGroup {

    /**
     * 包含的子节点们，用数组，方便分配下一个EventExecutor
     */
    private final EventExecutor[] children;
    /**
     * 只读的子节点集合，封装为一个集合，方便迭代，用于实现{@link Iterable}接口
     */
    private final Set<EventExecutor> readonlyChildren;
    /**
     * 已关闭的子节点的数量
     */
    private final AtomicInteger terminatedChildren = new AtomicInteger();
    /**
     * 监听所有子节点关闭的Listener，当所有的子节点关闭时，会收到关闭成功事件
     */
    private final Promise<?> terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE);
    /**
     * 选择下一个EventExecutor的方式，策略模式的运用。将选择算法交给Chooser
     * 目前看见两种： 与操作计算 和 取模操作计算。
     */
    private final EventExecutorChooserFactory.EventExecutorChooser chooser;

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the ThreadFactory to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory), args);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     *
     * @param chooserFactory    the {@link EventExecutorChooserFactory} to use.
     *                          创建child选择器的工厂
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     *                          用于创建子节点的参数
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                            EventExecutorChooserFactory chooserFactory, Object... args) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
        }

        // 如果未指定Executor 则创建的是无界线程池(每一个任务创建一个线程)。这个一定要注意
        if (executor == null) {
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }

        // 以下是创建EventExecutorGroup的子节点
        children = new EventExecutor[nThreads];

        for (int i = 0; i < nThreads; i ++) {
            // 当前索引的child是否创建成功
            boolean success = false;
            try {
                children[i] = newChild(executor, args);
                success = true;
            } catch (Exception e) {
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                if (!success) {
                    // 一旦出现某个创建失败，则移除所有创建的child
                    for (int j = 0; j < i; j ++) {
                        children[j].shutdownGracefully();
                    }

                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
                        try {
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        // 选择下一个EventExecutor的方式
        chooser = chooserFactory.newChooser(children);

        // 监听子节点关闭的Listener，可以看做回调式的CountDownLatch.
        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                if (terminatedChildren.incrementAndGet() == children.length) {
                    terminationFuture.setSuccess(null);
                }
            }
        };

        // 在所有的子节点上监听 它们的关闭事件
        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }

        // 将子节点数组封装为不可变集合
        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }

    /**
     * 创建默认的线程工厂
     * @return
     */
    protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass());
    }

    @Override
    public EventExecutor next() {
        return chooser.next();
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return readonlyChildren.iterator();
    }

    /**
     * Return the number of {@link EventExecutor} this implementation uses. This number is the maps
     * 1:1 to the threads it use.
     */
    public final int executorCount() {
        return children.length;
    }

    /**
     * Create a new EventExecutor which will later then accessible via the {@link #next()}  method. This method will be
     * called for each thread that will serve this {@link MultithreadEventExecutorGroup}.
     *
     */
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        for (EventExecutor l: children) {
            l.shutdownGracefully(quietPeriod, timeout, unit);
        }
        return terminationFuture();
    }

    /**
     * @see EventExecutorGroup#terminationFuture()
     * @return
     */
    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    @Deprecated
    public void shutdown() {
        for (EventExecutor l: children) {
            l.shutdown();
        }
    }

    /**
     * 是否正在关闭。 -------- 两阶段终止模式
     * @return
     */
    @Override
    public boolean isShuttingDown() {
        for (EventExecutor l: children) {
            // 只要有child未进入到正在关闭状态则返回false
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否已进入关闭状态
     * @return
     */
    @Override
    public boolean isShutdown() {
        for (EventExecutor l: children) {
            // 只要有child未进入到关闭状态则返回false
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否已进入终止状态
     * @return
     */
    @Override
    public boolean isTerminated() {
        for (EventExecutor l: children) {
            // 只要有节点未处于终止状态则返回false
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 有时限的等待EventExecutorGroup关闭。
     * @param timeout 时间数值
     * @param unit 时间单位
     * @return
     * @throws InterruptedException
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        loop: for (EventExecutor l: children) {
            for (;;) {
                long timeLeft = deadline - System.nanoTime();
                // 超时了，跳出loop标签对应的循环，即查询是否所有child都终止了
                if (timeLeft <= 0) {
                    break loop;
                }
                // 当前child进入了终止状态，跳出死循环检查下一个child
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        // 可能是超时了，可能时限内成功终止了
        return isTerminated();
    }
}
