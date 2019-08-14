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

import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.PriorityQueueNode;

import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可周期性调度的任务。
 * {@link #getDelay(TimeUnit)}
 *
 * @param <V> the type of value
 */
@SuppressWarnings("ComparableImplementedButEqualsNotOverridden")
final class ScheduledFutureTask<V> extends PromiseTask<V> implements ScheduledFuture<V>, PriorityQueueNode {

    private static final AtomicLong nextTaskId = new AtomicLong();
    /**
     * 全局起始时间偏移量
     */
    private static final long START_TIME = System.nanoTime();
    /**
     * 获取过去的纳秒数（相对时间）
     * @return long
     */
    static long nanoTime() {
        return System.nanoTime() - START_TIME;
    }

    /**
     * 计算任务过期时间。
     * @param delay 任务的延迟(第一次执行的延迟)
     * @return deadline 纳秒
     */
    static long deadlineNanos(long delay) {
        long deadlineNanos = nanoTime() + delay;
        // Guard against overflow
        return deadlineNanos < 0 ? Long.MAX_VALUE : deadlineNanos;
    }

    private final long id = nextTaskId.getAndIncrement();
    /**
     * 下次执行的时间戳
     * （相对时间戳，相对于{@link #START_TIME}的时间戳）
     * 它不是volatile的，导致{@link #getDelay(TimeUnit)}会出现线程安全问题(其它线程可能无法获取到真实延迟)。
     * 不是volatile的可以提高排序顺序，减少消耗。
     */
    private long deadlineNanos;

    /**
     * 0 - no repeat, —— 0表示不重复执行，只执行一次
     * >0 - repeat at fixed rate, —— >0 表示固定频率执行，固定频率执行会尽量使得满足执行频率(比如1秒10次)，下次执行时间 = deadlineNanos + p，
     * <0 - repeat with fixed delay —— <0 表示固定延迟执行，固定延迟着重与保持两次执行之间的间隔，而不保证频率，下次执行时间 = nanoTime() - p，
     * 总的来说：这个设定很巧妙，但不是很方便使用。一定需要对调用参数进行校验，否则可能出错。
     */
    private final long periodNanos;

    private int queueIndex = INDEX_NOT_IN_QUEUE;

    ScheduledFutureTask(
            AbstractScheduledEventExecutor executor,
            Runnable runnable, V result, long nanoTime) {

        this(executor, toCallable(runnable, result), nanoTime);
    }

    ScheduledFutureTask(
            AbstractScheduledEventExecutor executor,
            Callable<V> callable, long nanoTime, long period) {

        super(executor, callable);
        if (period == 0) {
            throw new IllegalArgumentException("period: 0 (expected: != 0)");
        }
        deadlineNanos = nanoTime;
        periodNanos = period;
    }

    ScheduledFutureTask(
            AbstractScheduledEventExecutor executor,
            Callable<V> callable, long nanoTime) {

        super(executor, callable);
        deadlineNanos = nanoTime;
        periodNanos = 0;
    }

    @Override
    protected EventExecutor executor() {
        return super.executor();
    }

    /**
     * 获取截止时间，这是相对时间，非系统时间
     * @return long
     */
    public long deadlineNanos() {
        return deadlineNanos;
    }

    /**
     * 获取下次执行延迟时间
     * @return long
     */
    public long delayNanos() {
        // deadlineNanos() - nanoTime() 表示剩余延迟
        return Math.max(0, deadlineNanos() - nanoTime());
    }

    /**
     * 获取相对于当前系统时间戳的延迟时间
     * @param currentTimeNanos 系统当前纳秒时间戳
     * @return 延迟时间，大于等于0
     */
    public long delayNanos(long currentTimeNanos) {
        return Math.max(0, deadlineNanos() - (currentTimeNanos - START_TIME));
    }

    @Override
    public long getDelay(TimeUnit unit) {
        // 注意：Netty的该实现不是线程安全的，其它线程查询该值将产生问题，不准确。
        return unit.convert(delayNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
        if (this == o) {
            return 0;
        }
        // 先比较执行间隔，再比较id --- 用于调度排序 deadline是非volatile的，可以提高性能，但是使得getDelay就有线程安全问题了。
        ScheduledFutureTask<?> that = (ScheduledFutureTask<?>) o;
        long d = deadlineNanos() - that.deadlineNanos();
        if (d < 0) {
            return -1;
        } else if (d > 0) {
            return 1;
        } else if (id < that.id) {
            return -1;
        } else if (id == that.id) {
            throw new Error();
        } else {
            return 1;
        }
    }

    @Override
    public void run() {
        assert executor().inEventLoop();
        try {
            // 只执行一次的任务
            if (periodNanos == 0) {
                if (setUncancellableInternal()) {
                    V result = task.call();
                    setSuccessInternal(result);
                }
            } else {
                // 检查任务是否已经被取消
                // check if is done as it may was cancelled
                if (!isCancelled()) {
                    task.call();
                    if (!executor().isShutdown()) {
                        long p = periodNanos;
                        if (p > 0) {
                            // 固定频率执行，固定频率执行会尽量使得满足执行频率(比如1秒10次)
                            // - 我的timer系统中，这里使用的是while循环，因为有可能压入队列之后又需要被弹出，当然这样更标准点
                            deadlineNanos += p;
                        } else {
                            // 固定延迟执行，固定延迟着重与保持两次执行之间的间隔，而不保证频率
                            deadlineNanos = nanoTime() - p;
                        }
                        // 任务可能在执行之后被取消，如果没有被取消，那么重新压入队列
                        if (!isCancelled()) {
                            // scheduledTaskQueue一定不会为null，因为在提交任务之前已经进行了延迟初始化。
                            // 是这样的：该任务就是从任务队列弹出的，因此走到该代码块的时候，队列一定存在。

                            // scheduledTaskQueue can never be null as we lazy init it before submit the task!
                            Queue<ScheduledFutureTask<?>> scheduledTaskQueue =
                                    ((AbstractScheduledEventExecutor) executor()).scheduledTaskQueue;
                            assert scheduledTaskQueue != null;

                            // 执行完毕之后重新压入队列，重新调整堆结构
                            scheduledTaskQueue.add(this);
                        }
                    }
                }
            }
        } catch (Throwable cause) {
            // 一旦执行错误，结束执行
            setFailureInternal(cause);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean canceled = super.cancel(mayInterruptIfRunning);
        if (canceled) {
            ((AbstractScheduledEventExecutor) executor()).removeScheduled(this);
        }
        return canceled;
    }

    /**
     * 取消任务，但是不从队列中移除。
     */
    boolean cancelWithoutRemove(boolean mayInterruptIfRunning) {
        return super.cancel(mayInterruptIfRunning);
    }

    @Override
    protected StringBuilder toStringBuilder() {
        StringBuilder buf = super.toStringBuilder();
        buf.setCharAt(buf.length() - 1, ',');

        return buf.append(" id: ")
                  .append(id)
                  .append(", deadline: ")
                  .append(deadlineNanos)
                  .append(", period: ")
                  .append(periodNanos)
                  .append(')');
    }

    @Override
    public int priorityQueueIndex(DefaultPriorityQueue<?> queue) {
        return queueIndex;
    }

    @Override
    public void priorityQueueIndex(DefaultPriorityQueue<?> queue, int i) {
        queueIndex = i;
    }
}
