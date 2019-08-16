/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * 立即执行的EventExecutor，有些地方也叫SameThreadExecutor。
 * 含义可参考{@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}。
 * 提交的任务的线程立即执行它所提交的任务。如果{@link #execute(Runnable)}是可重入的，
 * 那么该任务将会压入队列直到原始的{@link Runnable}完成执行。
 * <p>
 * {@link #execute(Runnable)} 抛出的所有异常都将被吞没并记录一个日志。目的是确保所有压入队列的任务都有机会执行。
 * </p>
 *
 * Executes {@link Runnable} objects in the caller's thread. If the {@link #execute(Runnable)} is reentrant it will be
 * queued until the original {@link Runnable} finishes execution.
 * <p>
 * All {@link Throwable} objects thrown from {@link #execute(Runnable)} will be swallowed and logged. This is to ensure
 * that all queued {@link Runnable} objects have the chance to be run.
 */
public final class ImmediateEventExecutor extends AbstractEventExecutor {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ImmediateEventExecutor.class);
    public static final ImmediateEventExecutor INSTANCE = new ImmediateEventExecutor();

    /**
     * 任务队列。
     * 必须是ThreadLocal的，因为使用ImmediateEventExecutor的时候，并不是创建一个对象，而是立即捕获当前线程进行执行。
     * 因此要保证数据的隔离，必须是ThreadLocal的。
     * A Runnable will be queued if we are executing a Runnable. This is to prevent a {@link StackOverflowError}.
     */
    private static final FastThreadLocal<Queue<Runnable>> DELAYED_RUNNABLES = new FastThreadLocal<Queue<Runnable>>() {
        @Override
        protected Queue<Runnable> initialValue() throws Exception {
            return new ArrayDeque<Runnable>();
        }
    };

    /**
     * 运行状态。提交第一个任务的时候进入运行状态，执行完本批次任务后进入非运行状态。
     * 必须是ThreadLocal，理由同上。
     * Set to {@code true} if we are executing a runnable.
     */
    private static final FastThreadLocal<Boolean> RUNNING = new FastThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() throws Exception {
            return false;
        }
    };

    /**
     * 因为它并没有创建新线程，而是捕获了当前线程，将当前线程伪装成为一个EventExecutor，因此也不支持关闭。
     */
    private final Future<?> terminationFuture = new FailedFuture<Object>(
            GlobalEventExecutor.INSTANCE, new UnsupportedOperationException());

    private ImmediateEventExecutor() { }

    @Override
    public boolean inEventLoop() {
        return true;
    }

    /** 因为该EventExecutor就是基于当前线程的，因此返回true */
    @Override
    public boolean inEventLoop(Thread thread) {
        return true;
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    @Deprecated
    public void shutdown() { }

    @Override
    public boolean isShuttingDown() {
        return false;
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return false;
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (!RUNNING.get()) {
            // 如果提交任务的时候，当前EventExecutor处于非活动状态，那么需要先标记为运行状态，使得当前任务执行期间提交的新任务进入队列
            RUNNING.set(true);
            // 立即执行提交的任务
            try {
                command.run();
            } catch (Throwable cause) {
                logger.info("Throwable caught while executing Runnable {}", command, cause);
            } finally {
                Queue<Runnable> delayedRunnables = DELAYED_RUNNABLES.get();
                Runnable runnable;
                // 检查该线程(任务)提交的所有任务，直到所有任务执行完毕
                while ((runnable = delayedRunnables.poll()) != null) {
                    try {
                        runnable.run();
                    } catch (Throwable cause) {
                        // 必须捕获异常，使得所有任务都可以被执行
                        logger.info("Throwable caught while executing Runnable {}", runnable, cause);
                    }
                }
                RUNNING.set(false);
            }
        } else {
            // 如果该线程提交任务的时候，有任务正在执行，则将任务压入队列，什么情况下会出现呢？
            // 当正在执行的任务会提交新的任务时就会产生。
            DELAYED_RUNNABLES.get().add(command);
        }
    }

    @Override
    public <V> Promise<V> newPromise() {
        return new ImmediatePromise<V>(this);
    }

    @Override
    public <V> ProgressivePromise<V> newProgressivePromise() {
        return new ImmediateProgressivePromise<V>(this);
    }

    static class ImmediatePromise<V> extends DefaultPromise<V> {
        ImmediatePromise(EventExecutor executor) {
            super(executor);
        }

        @Override
        protected void checkDeadLock() {
            // No check
            // 为何不检查死锁？ 因为检查死锁一定会抛出BlockingOperateExeception
            // 因为检查死锁过程中，获取到的Executor就是ImmediateEventExecutor，inEventLoop始终返回true。
        }
    }

    static class ImmediateProgressivePromise<V> extends DefaultProgressivePromise<V> {
        ImmediateProgressivePromise(EventExecutor executor) {
            super(executor);
        }

        @Override
        protected void checkDeadLock() {
            // No check
        }
    }
}
