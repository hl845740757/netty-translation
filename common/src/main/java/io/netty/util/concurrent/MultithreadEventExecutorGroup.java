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
 * <p>
 *  用多线程同时处理任务的{@link EventExecutorGroup}的基本抽象实现。它是一个很重要的实现。
 * <p>
 *  它的实现逻辑也很简单，它持有多个{@link EventExecutor}，本身不负责业务/事件的处理，
 *  而是单纯将任务分派给它的子节点。然后负责管理子节点的生命周期即可。
 * <p>
 *  与之对应的是单线程的事件处理器 {@link SingleThreadEventExecutor}
 * <p>
 *
 * Abstract base class for {@link EventExecutorGroup} implementations that handles their tasks with multiple threads at
 * the same time.
 */
public abstract class MultithreadEventExecutorGroup extends AbstractEventExecutorGroup {

    /**
     * 包含的子节点们，用数组，方便分配下一个EventExecutor(通过计算索引来分配)
     */
    private final EventExecutor[] children;
    /**
     * 只读的子节点集合，封装为一个集合，方便迭代，用于实现{@link Iterable}接口
     */
    private final Set<EventExecutor> readonlyChildren;
    /**
     * 进入终止状态的子节点数量
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
     *                          是真正创建线程的Executor，它用于创建EventLoop线程。必须保证能创建足够的线程。
     *                          (因为每一个IO任务(EventLoop)都是死循环，每一个EventLoop需要一个独立的线程，
     *                          如果不能创建足够的线程，则会引发异常)
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

        // 如果未指定Executor 则创建的是无界线程池(每一个任务创建一个线程)。
        // 这个一定要注意，在Netty中是安全的，它用于真正的创建线程。必须能保证创建足够的线程。
        // 线程数由执行IO的线程数决定，也就是EventLoop的个数(nThreads)。每一个EventLoop可看做一个死循环的Runnable

        if (executor == null) {
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }

        // 创建指定数目的Child，其实就是线程
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
                    // 等待所有创建的线程关闭
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

        // 到这里所有的线程创建成功。
        // 创建EventExecutor选择器
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

        // 在所有的子节点上监听 它们的关闭事件，当所有的child关闭时，可以获得通知
        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }

        // 将子节点数组封装为不可变集合，方便迭代(不允许外部改变持有的线程)
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

    /**
     * 将算法委托给{@link #chooser}，可以实现自定义的选择策略。
     */
    @Override
    public EventExecutor next() {
        return chooser.next();
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return readonlyChildren.iterator();
    }

    /**
     * 返回包含的executor数，其实也就是child数量，也是线程的数量。
     *
     * Return the number of {@link EventExecutor} this implementation uses. This number is the maps
     * 1:1 to the threads it use.
     */
    public final int executorCount() {
        return children.length;
    }

    /**
     * 创建一个新的EventExecutor，稍后可以被后面的{@link #next()}方法访问。*
     * 其实就是创建线程，只不过是将线程封装为{@link EventExecutor}。
     *
     * @apiNote
     * 注意：这里是超类构建的时候调用的，此时子类属性都是null，因此newChild需要的数据必须在args中，使用子类的属性会导致NPE。
     *
     * Create a new EventExecutor which will later then accessible via the {@link #next()}  method. This method will be
     * called for each thread that will serve this {@link MultithreadEventExecutorGroup}.
     */
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        // 它不真正的管理线程内逻辑，只是管理持有的线程的生命周期，由child自己管理自己的生命周期。
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
     * 是否正在关闭。
     * @return 所有节点都至少进入shuttingdown状态才返回true
     */
    @Override
    public boolean isShuttingDown() {
        for (EventExecutor l: children) {
            // 只要有child不处于正在关闭状态则返回false
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        // all match
        return true;
    }

    /**
     * 是否已进入关闭状态
     * @return 所有节点都至少进入shutdown状态才返回true
     */
    @Override
    public boolean isShutdown() {
        for (EventExecutor l: children) {
            // 只要有child未进入到关闭状态则返回false
            if (!l.isShutdown()) {
                return false;
            }
        }
        // all match
        return true;
    }

    /**
     * 是否已进入终止状态
     * @return 所有节点都进入terminated状态才返回true
     */
    @Override
    public boolean isTerminated() {
        for (EventExecutor l: children) {
            // 只要有节点未处于终止状态则返回false
            if (!l.isTerminated()) {
                return false;
            }
        }
        // all match
        return true;
    }

    /**
     * 有时限的等待EventExecutorGroup关闭。
     *
     * @param timeout 时间数值
     * @param unit 时间单位
     * @return 如果它包含的所有子节点都进入了终止状态则返回true。
     * @throws InterruptedException
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        // 虽然这里能看明白，但是：为何不直接在{@link #terminationFuture}上await？？？
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
        // 可能是超时了，可能时限内成功终止了，最后再尝试一次
        return isTerminated();
    }
}
