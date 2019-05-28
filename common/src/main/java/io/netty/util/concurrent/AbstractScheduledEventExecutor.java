/*
 * Copyright 2015 The Netty Project
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
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PriorityQueue;

import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 继承AbstractEventExecutor，对它的含义进一步具体化。
 * 实现了{@link java.util.concurrent.ScheduledExecutorService}中的方法，支持事件的调度处理。
 *
 * Abstract base class for {@link EventExecutor}s that want to support scheduling.
 */
public abstract class AbstractScheduledEventExecutor extends AbstractEventExecutor {
    /**
     * 默认的任务比较器
     */
    private static final Comparator<ScheduledFutureTask<?>> SCHEDULED_FUTURE_TASK_COMPARATOR =
            new Comparator<ScheduledFutureTask<?>>() {
                @Override
                public int compare(ScheduledFutureTask<?> o1, ScheduledFutureTask<?> o2) {
                    return o1.compareTo(o2);
                }
            };

    /**
     * 可调度的任务队列，底层的实现多为优先检查周期性调度的任务，其次执行提交的普通任务。
     *
     * 它本身不是线程安全的，但它是线程封闭的，它只由执行任务的线程访问{@link #inEventLoop(Thread)}。
     * 当不是执行任务的线程时，任务不直接插入到队列，而是提交一个任务，由执行逻辑的线程插入到队列，并最终执行。
     * 这样的好处是啥呢？没有线程安全的优先级队列，使用这种方式可以减少对锁的使用，否则需要锁整个对象。
     */
    PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue;

    protected AbstractScheduledEventExecutor() {
    }

    protected AbstractScheduledEventExecutor(EventExecutorGroup parent) {
        super(parent);
    }

    /**
     * 获取过去的纳秒数
     * @return long
     */
    protected static long nanoTime() {
        return ScheduledFutureTask.nanoTime();
    }

    /**
     * 获取默认的周期性任务队列，它不是线程安全的
     * @return
     */
    PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue() {
        if (scheduledTaskQueue == null) {
            scheduledTaskQueue = new DefaultPriorityQueue<ScheduledFutureTask<?>>(
                    SCHEDULED_FUTURE_TASK_COMPARATOR,
                    // Use same initial capacity as java.util.PriorityQueue
                    11);
        }
        return scheduledTaskQueue;
    }

    private static boolean isNullOrEmpty(Queue<ScheduledFutureTask<?>> queue) {
        return queue == null || queue.isEmpty();
    }

    /**
     * 取消当前线程的所有任务。
     * (必须保证当前线程是EventExecutor中的线程)
     *
     * Cancel all scheduled tasks.
     *
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     */
    protected void cancelScheduledTasks() {
        assert inEventLoop();
        PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (isNullOrEmpty(scheduledTaskQueue)) {
            return;
        }

        final ScheduledFutureTask<?>[] scheduledTasks =
                scheduledTaskQueue.toArray(new ScheduledFutureTask<?>[0]);

        for (ScheduledFutureTask<?> task: scheduledTasks) {
            task.cancelWithoutRemove(false);
        }

        scheduledTaskQueue.clearIgnoringIndexes();
    }

    /**
     * 弹出一个已提交的任务
     *
     * @see #pollScheduledTask(long)
     */
    protected final Runnable pollScheduledTask() {
        return pollScheduledTask(nanoTime());
    }

    /**
     * 弹出可执行的优先级最高的任务，如果没有可执行的任务则返回null。
     *
     * Return the {@link Runnable} which is ready to be executed with the given {@code nanoTime}.
     * You should use {@link #nanoTime()} to retrieve the correct {@code nanoTime}.
     */
    protected final Runnable pollScheduledTask(long nanoTime) {
        assert inEventLoop();

        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        // peek只检测队列的首元素，不对队列结构造成影响，是一个消耗很低的操作。
        ScheduledFutureTask<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        if (scheduledTask == null) {
            return null;
        }
        // 确定优先级最高的任务需要执行时，才真正从队列中删除。
        // 如果先删除再插入，就是一个相对高消耗的操作，而先peek再删除可以有更好的性能。
        if (scheduledTask.deadlineNanos() <= nanoTime) {
            scheduledTaskQueue.remove();
            return scheduledTask;
        }
        return null;
    }

    /**
     * Return the nanoseconds when the next scheduled task is ready to be run or {@code -1} if no task is scheduled.
     */
    protected final long nextScheduledTaskNano() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        ScheduledFutureTask<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        if (scheduledTask == null) {
            return -1;
        }
        return Math.max(0, scheduledTask.deadlineNanos() - nanoTime());
    }

    /**
     * 查看周期性调度任务中优先级最高的任务。
     * 周期性调度的任务优先级高于普通任务队列中的任务。
     * (底层的实现多为优先检查周期性调度的任务，其次执行提交的普通任务).
     *
     * 如果不存在则返回null，否则返回当前优先级最高的任务
     *
     * @return null/ScheduledFutureTask
     */
    final ScheduledFutureTask<?> peekScheduledTask() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (scheduledTaskQueue == null) {
            return null;
        }
        return scheduledTaskQueue.peek();
    }

    /**
     * Returns {@code true} if a scheduled task is ready for processing.
     */
    protected final boolean hasScheduledTasks() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        ScheduledFutureTask<?> scheduledTask = scheduledTaskQueue == null ? null : scheduledTaskQueue.peek();
        return scheduledTask != null && scheduledTask.deadlineNanos() <= nanoTime();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        ObjectUtil.checkNotNull(command, "command");
        ObjectUtil.checkNotNull(unit, "unit");
        if (delay < 0) {
            delay = 0;
        }
        validateScheduled0(delay, unit);

        return schedule(new ScheduledFutureTask<Void>(
                this, command, null, ScheduledFutureTask.deadlineNanos(unit.toNanos(delay))));
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        ObjectUtil.checkNotNull(callable, "callable");
        ObjectUtil.checkNotNull(unit, "unit");
        if (delay < 0) {
            delay = 0;
        }
        validateScheduled0(delay, unit);

        return schedule(new ScheduledFutureTask<V>(
                this, callable, ScheduledFutureTask.deadlineNanos(unit.toNanos(delay))));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        ObjectUtil.checkNotNull(command, "command");
        ObjectUtil.checkNotNull(unit, "unit");
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (period <= 0) {
            throw new IllegalArgumentException(
                    String.format("period: %d (expected: > 0)", period));
        }
        validateScheduled0(initialDelay, unit);
        validateScheduled0(period, unit);

        return schedule(new ScheduledFutureTask<Void>(
                this, Executors.<Void>callable(command, null),
                ScheduledFutureTask.deadlineNanos(unit.toNanos(initialDelay)), unit.toNanos(period)));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        ObjectUtil.checkNotNull(command, "command");
        ObjectUtil.checkNotNull(unit, "unit");
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (delay <= 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: > 0)", delay));
        }

        validateScheduled0(initialDelay, unit);
        validateScheduled0(delay, unit);

        return schedule(new ScheduledFutureTask<Void>(
                this, Executors.<Void>callable(command, null),
                ScheduledFutureTask.deadlineNanos(unit.toNanos(initialDelay)), -unit.toNanos(delay)));
    }

    @SuppressWarnings("deprecation")
    private void validateScheduled0(long amount, TimeUnit unit) {
        validateScheduled(amount, unit);
    }

    /**
     * Sub-classes may override this to restrict the maximal amount of time someone can use to schedule a task.
     *
     * @deprecated will be removed in the future.
     */
    @Deprecated
    protected void validateScheduled(long amount, TimeUnit unit) {
        // NOOP
    }

    <V> ScheduledFuture<V> schedule(final ScheduledFutureTask<V> task) {
        if (inEventLoop()) {
            // EventLoop线程添加的任务，EventLoop线程可以安全的访问线程封闭的数据
            scheduledTaskQueue().add(task);
        } else {
            // netty中充满了大量的这种设计，当不是同一个线程时，提交一个任务，
            // 由执行线程插入对象，而不是当前线程插入到队列，以实现线程安全。
            execute(new Runnable() {
                @Override
                public void run() {
                    scheduledTaskQueue().add(task);
                }
            });
        }

        return task;
    }

    final void removeScheduled(final ScheduledFutureTask<?> task) {
        if (inEventLoop()) {
            scheduledTaskQueue().removeTyped(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    removeScheduled(task);
                }
            });
        }
    }
}
