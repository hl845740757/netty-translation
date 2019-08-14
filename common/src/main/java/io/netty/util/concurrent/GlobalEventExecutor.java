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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全局事件处理器。
 *
 * 一个单线程的{@link EventExecutor}。它会自动的启动和停止它的线程当1秒之内没有任务填充到它的任务队列时。
 * 请注意：该Executor不可扩展用于调度大量的任务，请使用专用的线程。
 *
 * 评价：个人觉得不应该继承AbstractScheduledEventExecutor，不应该支持schedule系列方法，因为GlobalEventExecutor的自动关闭实现很特殊，
 * 如果GlobalEventExecutor中存在周期性的任务，如果不手动取消这些任务，那么可能导致GlobalEventExecutor无法关闭！最终JVM也无法关闭！
 * 因此，我们使用的时候不要使用schedule系列的接口！！！否则可能导致JVM无法退出！！(应用代码其实最好不要使用GlobalEventExecutor)
 * 个人觉得代理JDK的{@link java.util.concurrent.ThreadPoolExecutor}，创建单线程的线程池更加安全，实现也很简单。
 * {@link java.util.concurrent.ThreadPoolExecutor#allowCoreThreadTimeOut(boolean)} 允许核心线程自动关闭即可。
 *
 * Single-thread singleton {@link EventExecutor}.  It starts the thread automatically and stops it when there is no
 * task pending in the task queue for 1 second.  Please note it is not scalable to schedule large number of tasks to
 * this executor; use a dedicated executor.
 */
public final class GlobalEventExecutor extends AbstractScheduledEventExecutor {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(GlobalEventExecutor.class);
    /**
     * 线程调度安静时间（指定时间内无任务提交时，将自动关闭线程）
     */
    private static final long SCHEDULE_QUIET_PERIOD_INTERVAL = TimeUnit.SECONDS.toNanos(1);
    /**
     * 单例对象
     */
    public static final GlobalEventExecutor INSTANCE = new GlobalEventExecutor();
    /**
     * 任务队列。
     * 包含{@link #execute(Runnable)}提交的任务和从{@link #scheduledTaskQueue}拉取过来的任务。
     */
    final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
    /**
     * 安静期任务，可看做一个监控任务，用于监控线程是否空闲的。
     * 它是一个周期性任务，固定间隔执行，会一直存在于{@link #scheduledTaskQueue()}中，
     * 由于它的存在，使得{@link #takeTask()}永远不会调用taskQueue.take()方法，不会陷入无限期等待，只会调用有时限的take(long, TimeUnit)方法。
     * 当且仅当 {@link #taskQueue} 和 {@link #scheduledTaskQueue()}只剩下quietPeriodTask时，如果它被调度到就表示着可以尝试关闭了。
     *
     * 平心而论：监控任务，这个设计还是很有启发意义的。不过这里的实现有风险，如果有人使用了schedule接口，可能导致JVM无法退出。
     */
    final ScheduledFutureTask<Void> quietPeriodTask = new ScheduledFutureTask<Void>(
            this, Executors.<Void>callable(new Runnable() {
        @Override
        public void run() {
            // NOOP
        }
    }, null), ScheduledFutureTask.deadlineNanos(SCHEDULE_QUIET_PERIOD_INTERVAL), -SCHEDULE_QUIET_PERIOD_INTERVAL);

    // because the GlobalEventExecutor is a singleton, tasks submitted to it can come from arbitrary threads and this
    // can trigger the creation of a thread from arbitrary thread groups; for this reason, the thread factory must not
    // be sticky about its thread group
    // visible for testing
    final ThreadFactory threadFactory =
            new DefaultThreadFactory(DefaultThreadFactory.toPoolName(getClass()), false, Thread.NORM_PRIORITY, null);

    /**
     * 线程运行逻辑，见名知意 -- 执行提交的任务
     */
    private final TaskRunner taskRunner = new TaskRunner();
    /**
     * 线程启动状态，它的维护其实挺复杂。{@link #execute(Runnable)} 与 检测关闭有复杂的冲突检测机制。
     */
    private final AtomicBoolean started = new AtomicBoolean();
    /**
     * EventExecutor持有的线程对象，它是可以安全访问指定数据(线程封闭数据)的线程。
     */
    volatile Thread thread;

    private final Future<?> terminationFuture = new FailedFuture<Object>(this, new UnsupportedOperationException());

    private GlobalEventExecutor() {
        // 填充安静期任务，用于
        scheduledTaskQueue().add(quietPeriodTask);
    }

    /**
     * 从taskQueue中取出下一个执行的任务，如果当前没有任务存在，则会阻塞。
     * (和SingleThreadEventExecutor一个样)
     *
     * Take the next {@link Runnable} from the task queue and so will block if no task is currently present.
     *
     * @return {@code null} if the executor thread has been interrupted or waken up.
     */
    Runnable takeTask() {
        // taskQueue 包含{@link #execute(Runnable)}提交的任务和从{@link #scheduledTaskQueue}拉取过来的任务。
        // 注意 taskQueue 中包含的任务，否则会看懵逼
        BlockingQueue<Runnable> taskQueue = this.taskQueue;
        for (;;) {
            // 查看是否有待执行的周期性调度的任务，如果有的话，不可以无限制的在taskQueue上等待 --- quietPeriodTask有这个效果。
            ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
            if (scheduledTask == null) {
                // 当前无等待调度的任务，因此可以采用take，阻塞直到taskQueue中提交新任务。
                Runnable task = null;
                try {
                    task = taskQueue.take();
                } catch (InterruptedException e) {
                    // Ignore
                }
                return task;
            } else {
                // 当前有等待调度的任务，不可以采用无限等待的take，必须在任务下次调度前醒来
                long delayNanos = scheduledTask.delayNanos();
                Runnable task;
                if (delayNanos > 0) {
                    // 有周期性调度任务，但还没到执行时间，这个时候需要尝试取出一个普通任务，
                    // 但是需要在周期性任务下次调度前醒来，因此是在taskQueue上等待delayNanos
                    try {
                        task = taskQueue.poll(delayNanos, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        // Waken up.
                        return null;
                    }
                } else {
                    // scheduledTask 可以执行了，这里为啥在taskQueue上poll？？？
                    // 因为可调度的任务必须先进入taskQueue，注意 fetchFromScheduledTaskQueue 方法。
                    // 如果 scheduledTask 可执行，那么taskQueue中可能已经存在比它优先级更高的任务。
                    task = taskQueue.poll();
                }

                // 如果taskQueue不包含可执行的任务，那么就会从scheduledTaskQueue中拉取可能执行的任务到taskQueue
                if (task == null) {
                    fetchFromScheduledTaskQueue();
                    task = taskQueue.poll();
                }

                // 获取到一个可执行任务，返回
                if (task != null) {
                    return task;
                }
            }
        }
    }

    /**
     * 获取最新的可执行任务到{@link #taskQueue}，这样所有可执行的任务都从taskQueue中获取。
     */
    private void fetchFromScheduledTaskQueue() {
        long nanoTime = AbstractScheduledEventExecutor.nanoTime();
        Runnable scheduledTask = pollScheduledTask(nanoTime);
        while (scheduledTask != null) {
            taskQueue.add(scheduledTask);
            scheduledTask = pollScheduledTask(nanoTime);
        }
    }

    /**
     * Return the number of tasks that are pending for processing.
     *
     * <strong>Be aware that this operation may be expensive as it depends on the internal implementation of the
     * SingleThreadEventExecutor. So use it was care!</strong>
     */
    public int pendingTasks() {
        return taskQueue.size();
    }

    /**
     * Add a task to the task queue, or throws a {@link RejectedExecutionException} if this instance was shutdown
     * before.
     */
    private void addTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        taskQueue.add(task);
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
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
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

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

    /**
     * Waits until the worker thread of this executor has no tasks left in its task queue and terminates itself.
     * Because a new worker thread will be started again when a new task is submitted, this operation is only useful
     * when you want to ensure that the worker thread is terminated <strong>after</strong> your application is shut
     * down and there's no chance of submitting a new task afterwards.
     *
     * @return {@code true} if and only if the worker thread has been terminated
     */
    public boolean awaitInactivity(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        final Thread thread = this.thread;
        if (thread == null) {
            throw new IllegalStateException("thread was not started");
        }
        thread.join(unit.toMillis(timeout));
        return !thread.isAlive();
    }

    @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }

        // 添加到任务队列，必须在startThread之前添加到队列
        addTask(task);

        // 另一个线程提交任务时需要启动EventLoop线程 --- 这时任务队列中可能有一个任务！
        if (!inEventLoop()) {
            startThread();
        }
    }

    /**
     * startThread 和 线程退出 是如何保证安全性的？
     * 先添加任务到队列 ---------> 那么在startThread()后，如果成功将{@link #started}设置为true，
     *                          那么taskQueue和{@link #scheduledTaskQueue()}中一定至少存在一个非 {@link #quietPeriodTask}任务。
     *
     * 在检测是否需要退出时 ------> 如果覆盖了startThread() 的值，那么一定有一个新的任务需要执行，就可以知道需要继续执行。
     */
    private void startThread() {
        if (started.compareAndSet(false, true)) {
            // 通过任务创建线程，由于任务是死循环任务，因此独占该线程
            final Thread t = threadFactory.newThread(taskRunner);
            // classLoader泄漏：
            // 如果创建线程的时候，未指定contextClassLoader,那么将会继承父线程(创建当前线程的线程)的contextClassLoader，见Thread.init()方法。
            // 如果创建线程的线程contextClassLoader是自定义类加载器，那么新创建的线程将继承(使用)该contextClassLoader，在线程未回收期间，将导致自定义类加载器无法回收。
            // 从而导致ClassLoader内存泄漏，基于自定义类加载器的某些设计可能失效。
            // 我们显式的将其设置为null，表示使用系统类加载器进行加载，避免造成内存泄漏。

            // Set to null to ensure we not create classloader leaks by holds a strong reference to the inherited
            // classloader.
            // See:
            // - https://github.com/netty/netty/issues/7290
            // - https://bugs.openjdk.java.net/browse/JDK-7008595
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    t.setContextClassLoader(null);
                    return null;
                }
            });

            // Set the thread before starting it as otherwise inEventLoop() may return false and so produce
            // an assert error.
            // See https://github.com/netty/netty/issues/4357
            // 先赋值再启动才具有happens-before关系，新线程才能看见thread属性为自己
            // 线程启动原则: start()之前的操作happens-before start()之后的所有操作(happens-before于新启动的线程中的所有逻辑)
            thread = t;
            t.start();
        }
    }

    final class TaskRunner implements Runnable {

        /**
         * 安全性维护见
         * @see #quietPeriodTask
         */
        @Override
        public void run() {
            for (;;) {
                // 不断的取出任务执行
                Runnable task = takeTask();
                if (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        logger.warn("Unexpected exception from the global event executor: ", t);
                    }
                    // 如果是quietPeriodTask，则需要检测是否只剩下该任务了，如果是，则表示可能需要关闭了
                    if (task != quietPeriodTask) {
                        continue;
                    }
                }

                Queue<ScheduledFutureTask<?>> scheduledTaskQueue = GlobalEventExecutor.this.scheduledTaskQueue;
                // 如果只存在quietPeriodTask，则表示没有新任务提交，需要关闭线程
                // Terminate if there is no task in the queue (except the noop task).
                if (taskQueue.isEmpty() && (scheduledTaskQueue == null || scheduledTaskQueue.size() == 1)) {
                    // Mark the current thread as stopped.
                    // The following CAS must always success and must be uncontended,
                    // because only one thread should be running at the same time.
                    boolean stopped = started.compareAndSet(true, false);
                    assert stopped;

                    // 如果只剩下了quietPeriodTask，那么表示当前线程可以退出了。
                    // Check if there are pending entries added by execute() or schedule*() while we do CAS above.
                    if (taskQueue.isEmpty() && (scheduledTaskQueue == null || scheduledTaskQueue.size() == 1)) {
                        // A) No new task was added and thus there's nothing to handle
                        //    -> safe to terminate because there's nothing left to do
                        // B) A new thread started and handled all the new tasks.
                        //    -> safe to terminate the new thread will take care the rest
                        break;
                    }

                    // 由于addTask在startThread之前，那么可以保证：如果有其它的任务，一定有人尝试启动线程，那么将started标记为true
                    // There are pending tasks added again.
                    if (!started.compareAndSet(false, true)) {
                        // startThread() started a new thread and set 'started' to true.
                        // -> terminate this thread so that the new thread reads from taskQueue exclusively.
                        break;
                    }

                    // New tasks were added, but this worker was faster to set 'started' to true.
                    // i.e. a new worker thread was not started by startThread().
                    // -> keep this thread alive to handle the newly added entries.
                }
            }
        }
    }
}
