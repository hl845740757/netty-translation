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

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.Thread.State;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * {@link SingleThreadEventExecutor}是Netty并发实现中非常重要的一个类，该类主要负责线程的生命周期管理(创建，唤醒，销毁)，任务调度。
 *
 * 此外，它实现了{@link OrderedEventExecutor}，表示它会在单线程下有序的执行完所有的事件。
 * <li>它真正的处理所有的提交的任务或事件。</li>
 * <li>与之相对的是{@link MultithreadEventExecutorGroup}</li>
 *
 * Abstract base class for {@link OrderedEventExecutor}'s that execute all its submitted tasks in a single thread.
 *
 */
public abstract class SingleThreadEventExecutor extends AbstractScheduledEventExecutor implements OrderedEventExecutor {

    /** 最少16个任务空间。默认无限制 */
    static final int DEFAULT_MAX_PENDING_EXECUTOR_TASKS = Math.max(16,
            SystemPropertyUtil.getInt("io.netty.eventexecutor.maxPendingTasks", Integer.MAX_VALUE));

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(SingleThreadEventExecutor.class);
    /**
     * 尚未启动状态。
     * EventLoop/线程的状态
     */
    private static final int ST_NOT_STARTED = 1;
    /**
     * 已启动状态
     */
    private static final int ST_STARTED = 2;
    /**
     * 正在关闭状态 {@link #shutdownGracefully(long, long, TimeUnit)}
     */
    private static final int ST_SHUTTING_DOWN = 3;
    /**
     * 已关闭状态 {@link #shutdown()}
     */
    private static final int ST_SHUTDOWN = 4;
    /**
     * 已终止状态 {@link #ensureThreadStarted(int)} {@link #doStartThread()}
     */
    private static final int ST_TERMINATED = 5;

    /**
     * 填充任务，提交给executor，用于唤醒正在关闭的线程
     */
    private static final Runnable WAKEUP_TASK = new Runnable() {
        @Override
        public void run() {
            // Do nothing.
        }
    };
    /**
     * 填充任务，提交给executor，用于启动EventLoop线程
     */
    private static final Runnable NOOP_TASK = new Runnable() {
        @Override
        public void run() {
            // Do nothing.
        }
    };

    // 用于更新线程的转托管
    private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");
    // 用于更新线程的数学
    private static final AtomicReferenceFieldUpdater<SingleThreadEventExecutor, ThreadProperties> PROPERTIES_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(
                    SingleThreadEventExecutor.class, ThreadProperties.class, "threadProperties");

    /**
     * 任务队列，如果子类不需要阻塞操作，则可以创建特定类型的队列。
     */
    private final Queue<Runnable> taskQueue;
    /**
     * 该executor持有的线程。
     *
     * 使用volatile的原因是：该属性由占有线程的任务赋值，但是可以被外部线程访问到。
     * {@link #inEventLoop(Thread)} 这里存在可见性问题，因此需要volatile
     */
    private volatile Thread thread;
    /**
     * 线程的属性(并没有深入研究)
     */
    @SuppressWarnings("unused")
    private volatile ThreadProperties threadProperties;
    /**
     * 依赖的{@link Executor},线程由executor创建。
     *
     * 线程获取方式：提交一个任务到给定Executor，由于提交的是一个死循环任务，因此可以占用这个线程，阅读的时候可能觉得有点别扭。
     * 注意：给定的Executor必须能创建足够多的线程，否则会出现异常。
     */
    private final Executor executor;
    /**
     * 是否有中断请求.
     * 就当前版本来看，是有点小bug的。
     */
    private volatile boolean interrupted;
    /**
     * 线程锁。
     * 默认资源为0的信号量，也就是说：除非先释放资源否则无法申请资源。
     * 不知是不是我还没捋清楚，用{@link #terminationFuture} 好像就可以了，这个写的好像还有点bug，
     * 在{@link #ensureThreadStarted(int)}中，并没有调用release。
     */
    private final Semaphore threadLock = new Semaphore(0);
    /**
     * 线程关闭钩子，当线程要退出时，执行这些关闭钩子
     * 它不是线程安全的，是通过{@link #execute(Runnable)}来实现安全性的
     */
    private final Set<Runnable> shutdownHooks = new LinkedHashSet<Runnable>();
    /**
     * 当且仅当{@link #addTask(Runnable)}能唤醒executor线程的时候，为true。
     * Q:什么意思呢？
     * A:线程可能阻塞在taskQueue上（等待任务），这种情况下，添加一个任务可以唤醒线程，但是线程也可能阻塞在其它地方！
     * 当阻塞在其它地方时，添加一个任务到taskQueue并不能唤醒线程，这时子类需要实现自己的唤醒机制。
     */
    private final boolean addTaskWakesUp;
    /**
     * 最大填充任务数。当任务数超过该数量时则会阻塞。
     * {@link #newTaskQueue(int)}
     */
    private final int maxPendingTasks;
    /**
     * 任务拒接策略
     */
    private final RejectedExecutionHandler rejectedExecutionHandler;
    /**
     * 上次执行任务的时间。
     */
    private long lastExecutionTime;

    /**
     * 线程的状态，使用{@link #STATE_UPDATER}进行更新。
     */
    @SuppressWarnings({ "FieldMayBeFinal", "unused" })
    private volatile int state = ST_NOT_STARTED;

    /** 安静期最小时长 */
    private volatile long gracefulShutdownQuietPeriod;
    /** 安静期的最大时长，避免无法退出 */
    private volatile long gracefulShutdownTimeout;
    /** 进入安静期的开始时间 */
    private long gracefulShutdownStartTime;
    /** 终止状态future */
    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);

    /**
     * 详细注释见{@link #SingleThreadEventExecutor(EventExecutorGroup, Executor, boolean, int, RejectedExecutionHandler)}
     *
     * Create a new instance
     *
     * @param parent            the {@link EventExecutorGroup} which is the parent of this instance and belongs to it
     * @param threadFactory     the {@link ThreadFactory} which will be used for the used {@link Thread}
     * @param addTaskWakesUp    {@code true} if and only if invocation of {@link #addTask(Runnable)} will wake up the
     *                          executor thread
     */
    protected SingleThreadEventExecutor(
            EventExecutorGroup parent, ThreadFactory threadFactory, boolean addTaskWakesUp) {
        this(parent, new ThreadPerTaskExecutor(threadFactory), addTaskWakesUp);
    }

    /**
     * 详细注释见{@link #SingleThreadEventExecutor(EventExecutorGroup, Executor, boolean, int, RejectedExecutionHandler)}
     *
     * Create a new instance
     *
     * @param parent            the {@link EventExecutorGroup} which is the parent of this instance and belongs to it
     * @param threadFactory     the {@link ThreadFactory} which will be used for the used {@link Thread}
     * @param addTaskWakesUp    {@code true} if and only if invocation of {@link #addTask(Runnable)} will wake up the
     *                          executor thread
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     */
    protected SingleThreadEventExecutor(
            EventExecutorGroup parent, ThreadFactory threadFactory,
            boolean addTaskWakesUp, int maxPendingTasks, RejectedExecutionHandler rejectedHandler) {
        this(parent, new ThreadPerTaskExecutor(threadFactory), addTaskWakesUp, maxPendingTasks, rejectedHandler);
    }

    /**
     * 详细注释见{@link #SingleThreadEventExecutor(EventExecutorGroup, Executor, boolean, int, RejectedExecutionHandler)}
     *
     * Create a new instance
     *
     * @param parent            the {@link EventExecutorGroup} which is the parent of this instance and belongs to it
     * @param executor          the {@link Executor} which will be used for executing
     *                          用于创建线程的executor，注意：给定的Executor必须能创建足够多的线程，否则会出现异常。
     * @param addTaskWakesUp    {@code true} if and only if invocation of {@link #addTask(Runnable)} will wake up the
     *                          executor thread
     *                          当且仅当{@link #addTask(Runnable)}可以唤醒executor线程时为true。
     */
    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor, boolean addTaskWakesUp) {
        this(parent, executor, addTaskWakesUp, DEFAULT_MAX_PENDING_EXECUTOR_TASKS, RejectedExecutionHandlers.reject());
    }

    /**
     * Create a new instance
     *
     * @param parent            the {@link EventExecutorGroup} which is the parent of this instance and belongs to it
     *                          EventExecutor所属的父节点
     * @param executor          the {@link Executor} which will be used for executing
     *                          用于创建线程的executor，注意：给定的Executor必须能创建足够多的线程，否则会出现异常。
     *
     * @param addTaskWakesUp    {@code true} if and only if invocation of {@link #addTask(Runnable)} will wake up the
     *                          executor thread
     *                          当且仅当{@link #addTask(Runnable)}可以唤醒executor线程时为true。
     *                          更多信息看{@link #addTaskWakesUp}。
     *
     * @param maxPendingTasks   the maximum number of pending tasks before new tasks will be rejected.
     *                          允许的最大任务数，当队列中任务数到达该值时，新提交的任务将被拒绝。
     * @param rejectedHandler   the {@link RejectedExecutionHandler} to use.
     *                          任务被拒绝时的处理策略。
     */
    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor,
                                        boolean addTaskWakesUp, int maxPendingTasks,
                                        RejectedExecutionHandler rejectedHandler) {
        super(parent);
        this.addTaskWakesUp = addTaskWakesUp;
        // 至少允许压入16个任务
        this.maxPendingTasks = Math.max(16, maxPendingTasks);
        this.executor = ObjectUtil.checkNotNull(executor, "executor");
        taskQueue = newTaskQueue(this.maxPendingTasks);
        rejectedExecutionHandler = ObjectUtil.checkNotNull(rejectedHandler, "rejectedHandler");
    }

    /**
     * @deprecated Please use and override {@link #newTaskQueue(int)}.
     */
    @Deprecated
    protected Queue<Runnable> newTaskQueue() {
        return newTaskQueue(maxPendingTasks);
    }

    /**
     * 创建一个管理要执行的任务的队列。默认的实现返回一个{@link LinkedBlockingQueue}，如果子类并不会在任务队列上执行任何阻塞调用，
     * 那么可以覆盖该方法并返回一个更高性能的不支持阻塞操作的实现。
     *
     * 这是一个工厂方法，子类可以覆盖它以创建不同类型的队列。
     * @param maxPendingTasks 如果是有界队列，那么会需要该值，任务到达上限时会阻塞。
     *
     * Create a new {@link Queue} which will holds the tasks to execute. This default implementation will return a
     * {@link LinkedBlockingQueue} but if your sub-class of {@link SingleThreadEventExecutor} will not do any blocking
     * calls on the this {@link Queue} it may make sense to {@code @Override} this and return some more performant
     * implementation that does not support blocking operations at all.
     */
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return new LinkedBlockingQueue<Runnable>(maxPendingTasks);
    }

    /**
     * 中断当前运行的线程，唤醒线程用的
     * Interrupt the current running {@link Thread}.
     */
    protected void interruptThread() {
        Thread currentThread = thread;
        if (currentThread == null) {
            // 当前还没有新建线程，添加中断标记
            // 讲道理这是一个先检查后执行的操作，这不安全，主要是只在startThread的时候进行了检测
            // eg: 1.检测到null 2.线程启动(未检测到中断) 3.这里设置为true  结果什么也没干，这个标记便失效了
            interrupted = true;
        } else {
            // 当前已新建线程，那么中断它使用的线程
            currentThread.interrupt();
        }
    }

    /**
     * 轮询返回一个有效的任务。
     * @see Queue#poll()
     */
    protected Runnable pollTask() {
        assert inEventLoop();
        return pollTaskFrom(taskQueue);
    }

    /**
     * 从指定任务队列中弹出一个有效任务
     * @param taskQueue 任务队列
     * @return nullable 如果不存在则返回null
     */
    protected static Runnable pollTaskFrom(Queue<Runnable> taskQueue) {
        for (;;) {
            Runnable task = taskQueue.poll();
            // 如果唤醒当前线程的任务，跳过
            if (task == WAKEUP_TASK) {
                continue;
            }
            return task;
        }
    }

    /**
     * 从任务队列中取出下一个执行的{@link Runnable}，并且如果当前没有任务存在的话则会阻塞。
     * 注意：如果{@link #newTaskQueue()}返回的对象没有实现{@link BlockingQueue}则会抛出一个
     * {@link UnsupportedOperationException}异常。
     *
     * 和{@code GlobalEventExecutor#taskTask}是一样的逻辑，注意那边的注释。
     * <p>
     *
     * Take the next {@link Runnable} from the task queue and so will block if no task is currently present.
     * <p>
     * Be aware that this method will throw an {@link UnsupportedOperationException} if the task queue, which was
     * created via {@link #newTaskQueue()}, does not implement {@link BlockingQueue}.
     * </p>
     *
     * @return {@code null} if the executor thread has been interrupted or waken up.
     */
    protected Runnable takeTask() {
        // 调用该方法的线程必须是EventLoop线程自己
        assert inEventLoop();
        // 只支持阻塞队列
        if (!(taskQueue instanceof BlockingQueue)) {
            throw new UnsupportedOperationException();
        }
        // taskQueue包含当前可执行的所有任务，包括周期性调度的任务和execute提交的任务。
        BlockingQueue<Runnable> taskQueue = (BlockingQueue<Runnable>) this.taskQueue;
        for (;;) {
            // 查看是否有待执行的周期性调度的任务，如果有的话，不可以无限制的在taskQueue上等待
            ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
            if (scheduledTask == null) {
                // 当前无等待调度的任务，因此可以采用take，阻塞直到taskQueue中提交新任务。
                Runnable task = null;
                try {
                    task = taskQueue.take();
                    if (task == WAKEUP_TASK) {
                        task = null;
                    }
                } catch (InterruptedException e) {
                    // Ignore
                }
                return task;
            } else {
                // 当前有等待调度的任务，不可以采用无限等待的take，必须在任务下次调度前醒来
                long delayNanos = scheduledTask.delayNanos();
                Runnable task = null;
                if (delayNanos > 0) {
                    try {
                        task = taskQueue.poll(delayNanos, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        // Waken up.
                        return null;
                    }
                }
                // 难怪没有重复代码提示，这里的else去掉了 -- 比较GlobalEventExecutor

                // 如果taskQueue不包含可执行的任务，那么就会从scheduledTaskQueue中拉取可能执行的任务到taskQueue
                // fetchFromScheduledTaskQueue 这个方法很重要
                if (task == null) {
                    // We need to fetch the scheduled tasks now as otherwise there may be a chance that
                    // scheduled tasks are never executed if there is always one task in the taskQueue.
                    // This is for example true for the read task of OIO Transport
                    // See https://github.com/netty/netty/issues/1614
                    fetchFromScheduledTaskQueue();
                    task = taskQueue.poll();
                }

                if (task != null) {
                    return task;
                }
            }
        }
    }

    /**
     * 拉取可以执行的周期性调度任务到taskQueue，这样taskQueue中就包含了所有可执行的任务，
     * @return 是否拉取了所有的可执行的周期性任务
     */
    private boolean fetchFromScheduledTaskQueue() {
        long nanoTime = AbstractScheduledEventExecutor.nanoTime();
        Runnable scheduledTask  = pollScheduledTask(nanoTime);
        while (scheduledTask != null) {
            if (!taskQueue.offer(scheduledTask)) {
                // No space left in the task queue add it back to the scheduledTaskQueue so we pick it up again.
                scheduledTaskQueue().add((ScheduledFutureTask<?>) scheduledTask);
                return false;
            }
            scheduledTask  = pollScheduledTask(nanoTime);
        }
        return true;
    }

    /**
     * @see Queue#peek()
     */
    protected Runnable peekTask() {
        assert inEventLoop();
        return taskQueue.peek();
    }

    /**
     * @see Queue#isEmpty()
     */
    protected boolean hasTasks() {
        assert inEventLoop();
        return !taskQueue.isEmpty();
    }

    /**
     * Return the number of tasks that are pending for processing.
     *
     * <strong>Be aware that this operation may be expensive as it depends on the internal implementation of the
     * SingleThreadEventExecutor. So use it with care!</strong>
     */
    public int pendingTasks() {
        return taskQueue.size();
    }

    /**
     * Add a task to the task queue, or throws a {@link RejectedExecutionException} if this instance was shutdown
     * before.
     */
    protected void addTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (!offerTask(task)) {
            // 添加任务失败，执行拒绝策略。
            reject(task);
        }
    }

    /**
     * 将任务压入队列
     * @param task 任务
     * @return true/false，如果线程已关闭则抛出异常，否则压入成功则返回true，失败false
     */
    final boolean offerTask(Runnable task) {
        // 注意：这里是 shutdown状态才拒绝，也就是说在shuttingdown的时候是接受任务的，也就是所谓的安静期期间会接受任务
        if (isShutdown()) {
            reject();
        }
        return taskQueue.offer(task);
    }

    /**
     * @see Queue#remove(Object)
     */
    protected boolean removeTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        return taskQueue.remove(task);
    }

    /**
     * 弹出所有的任务并执行(调用他们的run方法)
     *
     * Poll all tasks from the task queue and run them via {@link Runnable#run()} method.
     *
     * @return {@code true} if and only if at least one task was run
     */
    protected boolean runAllTasks() {
        assert inEventLoop();
        boolean fetchedAll;
        boolean ranAtLeastOne = false;

        do {
            // 拉取可执行的周期性任务到taskQueue
            fetchedAll = fetchFromScheduledTaskQueue();
            // 执行taskQueue中的所有任务
            if (runAllTasksFrom(taskQueue)) {
                ranAtLeastOne = true;
            }
            // 如果并没有成功拉取到所有课执行任务，那么继续拉取执行，直到Schedule队列中没有课执行的任务
        } while (!fetchedAll); // keep on processing until we fetched all scheduled tasks.

        if (ranAtLeastOne) {
            // 如果至少执行了一个任务，更新上次执行任务的时间戳
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }
        // 批量执行任务之后，执行一个回调 - 类似在Disruptor中的BatchEventProcessor的endOfBatch
        afterRunningAllTasks();
        return ranAtLeastOne;
    }

    /**
     * 执行任务队列中的所有任务。
     *
     * Runs all tasks from the passed {@code taskQueue}.
     *
     * @param taskQueue To poll and execute all tasks.
     *
     * @return {@code true} if at least one task was executed.
     *          当至少执行了一个任务的时候返回true。
     */
    protected final boolean runAllTasksFrom(Queue<Runnable> taskQueue) {
        Runnable task = pollTaskFrom(taskQueue);
        if (task == null) {
            return false;
        }
        for (;;) {
            // 安全的执行，避免出现异常打断其它任务的执行
            safeExecute(task);
            task = pollTaskFrom(taskQueue);
            if (task == null) {
                // 没有课执行的任务了，结束
                return true;
            }
        }
    }

    /**
     * 在限定的时间内执行可执行的任务。
     *
     * 轮询taskQueue中的所有任务，并通过{@link Runnable#run()}方法执行它们。
     * 如果执行时间长于指定时间，该方法将会停止执行队列中的任务并返回。
     * (该方法的主要作用是协调时间的分配比例，执行任务占用的时间要达到某一个设定的比例)
     *
     * Poll all tasks from the task queue and run them via {@link Runnable#run()} method.  This method stops running
     * the tasks in the task queue and returns if it ran longer than {@code timeoutNanos}.
     *
     * @param timeoutNanos 超时时间，若执行完当前任务，超过该时间，则返回
     * @return 如果执行了至少一个任务，则返回true。
     */
    protected boolean runAllTasks(long timeoutNanos) {
        // 先拉取可以执行的周期任务到taskQueue
        fetchFromScheduledTaskQueue();
        Runnable task = pollTask();
        if (task == null) {
            afterRunningAllTasks();
            return false;
        }
        // 计算截止时间
        final long deadline = ScheduledFutureTask.nanoTime() + timeoutNanos;
        long runTasks = 0;
        long lastExecutionTime;
        for (;;) {
            safeExecute(task);

            runTasks ++;

            // 没有实时检测是否需要退出，而是每执行64个任务检测一次，并不需要那么精确的控制。
            // Check timeout every 64 tasks because nanoTime() is relatively expensive.
            // XXX: Hard-coded value - will make it configurable if it is really a problem.
            if ((runTasks & 0x3F) == 0) {
                lastExecutionTime = ScheduledFutureTask.nanoTime();
                if (lastExecutionTime >= deadline) {
                    break;
                }
            }

            task = pollTask();
            if (task == null) {
                // 没有任务了，退出
                lastExecutionTime = ScheduledFutureTask.nanoTime();
                break;
            }
        }

        afterRunningAllTasks();
        this.lastExecutionTime = lastExecutionTime;
        return true;
    }

    /**
     * 一个钩子方法，执行完一批任务之后，执行一个回调。
     * Invoked before returning from {@link #runAllTasks()} and {@link #runAllTasks(long)}.
     */
    @UnstableApi
    protected void afterRunningAllTasks() { }

    /**
     * 获取最近的下一个任务执行的延迟。
     *
     * Returns the amount of time left until the scheduled task with the closest dead line is executed.
     */
    protected long delayNanos(long currentTimeNanos) {
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        // 进行了一个保护，如果当前scheduledQueue中没有任务，则最多返回1秒，不至于线程阻塞过久。
        if (scheduledTask == null) {
            return SCHEDULE_PURGE_INTERVAL;
        }

        return scheduledTask.delayNanos(currentTimeNanos);
    }

    /**
     * Returns the absolute point in time (relative to {@link #nanoTime()}) at which the the next
     * closest scheduled task should run.
     */
    @UnstableApi
    protected long deadlineNanos() {
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            return nanoTime() + SCHEDULE_PURGE_INTERVAL;
        }
        return scheduledTask.deadlineNanos();
    }

    /**
     * 更新上一次执行任务的时间。
     * (为子类提供的口)
     *
     * Updates the internal timestamp that tells when a submitted task was executed most recently.
     * {@link #runAllTasks()} and {@link #runAllTasks(long)} updates this timestamp automatically, and thus there's
     * usually no need to call this method.  However, if you take the tasks manually using {@link #takeTask()} or
     * {@link #pollTask()}, you have to call this method at the end of task execution loop for accurate quiet period
     * checks.
     */
    protected void updateLastExecutionTime() {
        lastExecutionTime = ScheduledFutureTask.nanoTime();
    }

    // 不过没有配一个init方法

    /**
     * 真正的逻辑代码。
     */
    protected abstract void run();

    /**
     * 在线程退出前需要执行什么清理操作可以覆盖该方法。
     * Do nothing, sub-classes may override
     */
    protected void cleanup() {
        // NOOP
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    /**
     * 唤醒线程。
     * 线程当前可能阻塞在等待任务的过程中(take)，如果阻塞在任务队列的take方法上，那么压入一个任务往往可以唤醒线程。
     * 但线程更有可能阻塞在其它地方，因此子类需要重写该方法以实现真正的唤醒操作，超类仅仅是处理一些简单的情况。
     *
     * Q: 为什么默认实现是向taskQueue中插入一个任务，而不是中断线程{@link #interruptThread()} ?
     * A: 我先不说这里能不能唤醒线程这个问题。
     *    中断最致命的一点是：向目标线程发出中断请求以后，你并不知道目标线程接收到中断信号的时候正在做什么！！！
     *    因此它并不是一种唤醒/停止目标线程的最佳方式，它可能导致一些需要原子执行的操作失败，也可能导致其它的问题。
     *    因此最好是对症下药，默认实现里认为线程可能阻塞在taskQueue上，因此我们尝试压入一个任务以尝试唤醒它。
     *
     * @param inEventLoop 当前是否是EventLoop线程，正常情况下只有不在当前线程的时候才需要唤醒，才需要调用该方法。
     *                    但是在关闭期间自己唤醒自己是为了改变takeTask的行为，避免线程阻塞太久。
     */
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop || state == ST_SHUTTING_DOWN) {
            // 使用offer，因为我们只是需要唤醒线程，并且offer失败也没有影响。
            // Use offer as we actually only need this to unblock the thread and if offer fails we do not care as there
            // is already something in the queue.
            taskQueue.offer(WAKEUP_TASK);
        }
    }

    /**
     * 添加一个线程退出时的钩子
     * Hook -- 钩子 -- 将自己的行为添加到某个流程中，常用在模板方法模式 或 回调中。
     *
     * Add a {@link Runnable} which will be executed on shutdown of this instance
     */
    public void addShutdownHook(final Runnable task) {
        if (inEventLoop()) {
            // 如果在同一个线程下，则可以安全的直接添加
            shutdownHooks.add(task);
        } else {
            // 如果在不同的线程下，则通过发消息(提交任务)来添加 ---- 当有大量这样的代码时，需要保证队列不能太小
            execute(new Runnable() {
                @Override
                public void run() {
                    shutdownHooks.add(task);
                }
            });
        }
    }

    /**
     * 删除一个之前添加的线程退出钩子
     *
     * Remove a previous added {@link Runnable} as a shutdown hook
     */
    public void removeShutdownHook(final Runnable task) {
        if (inEventLoop()) {
            // 如果在同一个线程下，则可以安全的直接删除
            shutdownHooks.remove(task);
        } else {
            // 如果在不同的线程下，则通过发消息(提交任务)来删除
            execute(new Runnable() {
                @Override
                public void run() {
                    shutdownHooks.remove(task);
                }
            });
        }
    }

    /**
     * 执行所有的线程退出钩子 -- 线程准备退出时
     * @return 是否至少执行了一个钩子
     */
    private boolean runShutdownHooks() {
        boolean ran = false;
        // Note shutdown hooks can add / remove shutdown hooks.
        // 因为关闭钩子在执行的过程中可能被删除，因此需要进行拷贝
        // 如果钩子在执行的过程中，调用remove，会满足inEventLoop为true，会直接从列表中删除，从而造成异常。(遍历的过程中删除元素)
        while (!shutdownHooks.isEmpty()) {
            List<Runnable> copy = new ArrayList<Runnable>(shutdownHooks);
            shutdownHooks.clear();
            for (Runnable task: copy) {
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.warn("Shutdown hook raised an exception.", t);
                } finally {
                    ran = true;
                }
            }
        }

        if (ran) {
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }

        return ran;
    }

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        if (quietPeriod < 0) {
            throw new IllegalArgumentException("quietPeriod: " + quietPeriod + " (expected >= 0)");
        }
        if (timeout < quietPeriod) {
            throw new IllegalArgumentException(
                    "timeout: " + timeout + " (expected >= quietPeriod (" + quietPeriod + "))");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        // executor正在关闭，可以直接开始等待
        if (isShuttingDown()) {
            return terminationFuture();
        }

        boolean inEventLoop = inEventLoop();
        boolean wakeup;
        int oldState;
        for (;;) {
            if (isShuttingDown()) {
                return terminationFuture();
            }
            int newState;
            wakeup = true;
            oldState = state;
            if (inEventLoop) {
                // 因为只有EventExecutor线程自己能够将state切换到SHUTDOWN 和 TERMINATED状态，其它线程最多只能将state切换为ST_SHUTTING_DOWN
                // 因此EventExecutor线程也只需要切换到ST_SHUTTING_DOWN就可以了，该冲突是没有影响的
                newState = ST_SHUTTING_DOWN;
            } else {
                // 如果是其它线程关闭executor，EventExecutor可能切换到了更靠后的状态。
                // 它如果检测到了更靠后的状态，那么表示该EventExecutor已开始关闭，不需要改变它的状态，而且不需要唤醒。
                switch (oldState) {
                    case ST_NOT_STARTED:
                    case ST_STARTED:
                        newState = ST_SHUTTING_DOWN;
                        break;
                    default:
                        // 目标线程已经开始关闭了，不需要唤醒，维持当前状态
                        newState = oldState;
                        wakeup = false;
                }
            }
            // 强制切换到正在关闭状态，或维持当前的状态，更新成功则跳出循环
            if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
                break;
            }
        }
        // 更新安静期时间
        gracefulShutdownQuietPeriod = unit.toNanos(quietPeriod);
        gracefulShutdownTimeout = unit.toNanos(timeout);

        // 确保线程已启动，否则无法进入终止状态
        if (ensureThreadStarted(oldState)) {
            // 已终止，不能唤醒
            return terminationFuture;
        }
        // 如果需要唤醒线程的话，则进行唤醒（关闭期间需要唤醒线程，改变takeTask行为）
        if (wakeup) {
            wakeup(inEventLoop);
        }

        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    @Deprecated
    public void shutdown() {
        if (isShutdown()) {
            return;
        }

        // 存为临时变量，减少volatile读消耗（有for循环），因为在一次方法调用中 inEventLoop结果是不会变化的。
        boolean inEventLoop = inEventLoop();
        boolean wakeup;
        int oldState;
        for (;;) {
            if (isShuttingDown()) {
                return;
            }
            int newState;
            wakeup = true;
            oldState = state;
            if (inEventLoop) {
                // 因为只有EventExecutor线程自己能够将state切换到TERMINATED状态，其它线程最多只能将state切换为ST_SHUTDOWN
                // 因此EventExecutor线程也只需要切换到ST_SHUTDOWN就可以了，该冲突是没有影响的
                newState = ST_SHUTDOWN;
            } else {
                // 如果是其它线程关闭executor，EventExecutor可能切换到了更靠后的状态。
                // 它如果检测到了更靠后的状态，那么表示该EventExecutor已关闭，不需要改变它的状态，而且不需要唤醒。
                switch (oldState) {
                    case ST_NOT_STARTED:
                    case ST_STARTED:
                    case ST_SHUTTING_DOWN:
                        newState = ST_SHUTDOWN;
                        break;
                    default:
                        // 已经是SHUTDOWN 或者 TERMINAL了。不需要唤醒
                        newState = oldState;
                        wakeup = false;
                }
            }
            // 强制切换到正在关闭状态，或维持当前的状态，更新成功则跳出循环
            if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
                break;
            }
        }
        // 确保线程已启动 -- 也是确保线程可关闭
        if (ensureThreadStarted(oldState)) {
            return;
        }
        // 如果需要唤醒线程的话，则进行唤醒（关闭期间需要唤醒线程，改变takeTask行为）
        if (wakeup) {
            wakeup(inEventLoop);
        }
    }

    @Override
    public boolean isShuttingDown() {
        return state >= ST_SHUTTING_DOWN;
    }

    @Override
    public boolean isShutdown() {
        return state >= ST_SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {
        return state == ST_TERMINATED;
    }

    /**
     * Confirm that the shutdown if the instance should be done now!
     */
    protected boolean confirmShutdown() {
        if (!isShuttingDown()) {
            return false;
        }

        if (!inEventLoop()) {
            throw new IllegalStateException("must be invoked from an event loop");
        }

        // 取消所有的周期性调度任务
        cancelScheduledTasks();

        // 还未初始化安静期的开始时间
        if (gracefulShutdownStartTime == 0) {
            gracefulShutdownStartTime = ScheduledFutureTask.nanoTime();
        }

        if (runAllTasks() || runShutdownHooks()) {
            // 如果执行了任务或关闭钩子，则可能需要重新进入安静期
            if (isShutdown()) {
                // 已经完全关闭，则退出
                // Executor shut down - no new tasks anymore.
                return true;
            }

            // 安静期为0，立即关闭
            // There were tasks in the queue. Wait a little bit more until no tasks are queued for the quiet period or
            // terminate if the quiet period is 0.
            // See https://github.com/netty/netty/issues/4241
            if (gracefulShutdownQuietPeriod == 0) {
                return true;
            }
            // 如果需要唤醒线程的话，则进行唤醒（关闭期间需要唤醒线程，改变takeTask行为）
            wakeup(true);
            return false;
        }

        final long nanoTime = ScheduledFutureTask.nanoTime();

        // 没有执行新任务时，如果已进入关闭状态或安静期超时，则可以立即退出
        if (isShutdown() || nanoTime - gracefulShutdownStartTime > gracefulShutdownTimeout) {
            return true;
        }

        // 安静期还未结束，避免线程take超时
        if (nanoTime - lastExecutionTime <= gracefulShutdownQuietPeriod) {
            // 如果需要唤醒线程的话，则进行唤醒（关闭期间需要唤醒线程，改变takeTask行为）
            // Check if any tasks were added to the queue every 100ms.
            // TODO: Change the behavior of takeTask() so that it returns on timeout.
            wakeup(true);
            // sleep一会儿，避免在安静期内不停的插入任务，这个间隔也不好确定啊。 太长不能及时响应任务，太短老是干无用功。
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }

            return false;
        }

        // No tasks were added for last quiet period - hopefully safe to shut down.
        // (Hopefully because we really cannot make a guarantee that there will be no execute() calls by a user.)
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        if (inEventLoop()) {
            throw new IllegalStateException("cannot await termination of the current thread");
        }
        // 为何不在 terminationFuture 上等待？
//        return terminationFuture.await(timeout, unit);

        // 目前看了一下：在ensureThreadStarted(int)的时候并没有调用threadLock.release()，是否存在问题？
        if (threadLock.tryAcquire(timeout, unit)) {
            threadLock.release();
        }

        return isTerminated();
    }

    /**
     * 提交一个任务
     * @param task 提交的任务
     */
    @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }

        boolean inEventLoop = inEventLoop();
        addTask(task);
        if (!inEventLoop) {
            // 如果不在当前线程，那么添加任务之后，必须保证线程已启动
            startThread();
            if (isShutdown()) {
                // 压入队列是一个过程！在压入队列的过程中，executor的状态可能改变，因此必须再次校验 - 以判断线程是否在任务压入队列之后已经开始关闭了
                // 如果在添加任务之后，EventExecutor已关闭，那么需要尝试移除任务，如果移除成功，则执行拒绝策略
                boolean reject = false;
                try {
                    if (removeTask(task)) {
                        reject = true;
                    }
                } catch (UnsupportedOperationException e) {
                    // The task queue does not support removal so the best thing we can do is to just move on and
                    // hope we will be able to pick-up the task before its completely terminated.
                    // In worst case we will log on termination.
                }
                // 移除成功，执行拒绝策略
                if (reject) {
                    reject();
                }
            }
        }
        // 如果 addTask(task) 并不能唤醒线程（线程可能阻塞在其它地方，taskQueue只是其中之一），但是提交的任务需要唤醒线程时，则唤醒线程
        if (!addTaskWakesUp && wakesUpForTask(task)) {
            // 只有子类自己知道如何唤醒线程
            wakeup(inEventLoop);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        throwIfInEventLoop("invokeAny");
        return super.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        throwIfInEventLoop("invokeAny");
        return super.invokeAny(tasks, timeout, unit);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        throwIfInEventLoop("invokeAll");
        return super.invokeAll(tasks);
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        throwIfInEventLoop("invokeAll");
        return super.invokeAll(tasks, timeout, unit);
    }

    private void throwIfInEventLoop(String method) {
        if (inEventLoop()) {
            throw new RejectedExecutionException("Calling " + method + " from within the EventLoop is not allowed");
        }
    }

    /**
     * Returns the {@link ThreadProperties} of the {@link Thread} that powers the {@link SingleThreadEventExecutor}.
     * If the {@link SingleThreadEventExecutor} is not started yet, this operation will start it and block until
     * it is fully started.
     */
    public final ThreadProperties threadProperties() {
        ThreadProperties threadProperties = this.threadProperties;
        if (threadProperties == null) {
            Thread thread = this.thread;
            if (thread == null) {
                assert !inEventLoop();
                submit(NOOP_TASK).syncUninterruptibly();
                thread = this.thread;
                assert thread != null;
            }

            threadProperties = new DefaultThreadProperties(thread);
            if (!PROPERTIES_UPDATER.compareAndSet(this, null, threadProperties)) {
                threadProperties = this.threadProperties;
            }
        }

        return threadProperties;
    }

    /**
     * 任务是否可以用来唤醒线程，如果返回true，意味着会调用{@link #wakeup(boolean)}
     * (提交的任务是否需要唤醒线程时)
     *
     * @param task 新任务
     * @return true/false 如果可以唤醒线程则返回true，如果不可以唤醒线程则返回false
     */
    @SuppressWarnings("unused")
    protected boolean wakesUpForTask(Runnable task) {
        return true;
    }

    protected static void reject() {
        throw new RejectedExecutionException("event executor terminated");
    }

    /**
     * Offers the task to the associated {@link RejectedExecutionHandler}.
     *
     * @param task to reject.
     */
    protected final void reject(Runnable task) {
        rejectedExecutionHandler.rejected(task, this);
    }

    // ScheduledExecutorService implementation

    private static final long SCHEDULE_PURGE_INTERVAL = TimeUnit.SECONDS.toNanos(1);

    /**
     * 如果未启动的话，启动线程
     */
    private void startThread() {
        // 如果当前处理未启动状态，则尝试启动线程
        if (state == ST_NOT_STARTED) {
            // compareAndSet进行保护
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                try {
                    doStartThread();
                } catch (Throwable cause) {
                    // 启动失败，恢复状态
                    STATE_UPDATER.set(this, ST_NOT_STARTED);
                    PlatformDependent.throwException(cause);
                }
            }
        }
    }

    /**
     * 确认线程已启动 --------- 有个疑问？如果shutdown的时候，executor尚未启动过，是否有必要启动里面的线程？
     *
     * @param oldState 关闭前的状态
     * @return 如果线程已终止，则返回true
     */
    private boolean ensureThreadStarted(int oldState) {
        if (oldState == ST_NOT_STARTED) {
            try {
                doStartThread();
            } catch (Throwable cause) {
                // 这里没有调用 threadLock.release()方法，是否会存在问题
                // 启动失败，更新状态为终止状态，并更新future
                STATE_UPDATER.set(this, ST_TERMINATED);
                terminationFuture.tryFailure(cause);

                if (!(cause instanceof Exception)) {
                    // Also rethrow as it may be an OOME for example
                    PlatformDependent.throwException(cause);
                }
                return true;
            }
        }
        return false;
    }

    private void doStartThread() {
        assert thread == null;
        // 在这里提交一个任务，回调到自身的run逻辑,通过死循环占有线程。
        executor.execute(new Runnable() {
            @Override
            public void run() {
                // 在这里赋值，EventExecutor之外可以被访问，所以需要为volatile变量。
                thread = Thread.currentThread();
                if (interrupted) {
                    // 这块代码其实不安全，注意看我在interruptThread方法中的注释
                    // 如果在启动前有人请求中断线程，则执行中断逻辑。
                    thread.interrupt();
                }

                boolean success = false;
                updateLastExecutionTime();
                try {
                    SingleThreadEventExecutor.this.run();
                    success = true;
                } catch (Throwable t) {
                    logger.warn("Unexpected exception from an event executor: ", t);
                } finally {
                    // 如果是非正常退出，需要切换到正在关闭状态
                    for (;;) {
                        int oldState = state;
                        if (oldState >= ST_SHUTTING_DOWN || STATE_UPDATER.compareAndSet(
                                SingleThreadEventExecutor.this, oldState, ST_SHUTTING_DOWN)) {
                            break;
                        }
                    }

                    // Check if confirmShutdown() was called at the end of the loop.
                    if (success && gracefulShutdownStartTime == 0) {
                        if (logger.isErrorEnabled()) {
                            logger.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " +
                                    SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must " +
                                    "be called before run() implementation terminates.");
                        }
                    }

                    try {
                        // 尝试执行所有剩余的任务和关闭钩子，然后才能安全的退出 - 当然这也有风险，在错误的状态下继续运行，是有风险的
                        // Run all remaining tasks and shutdown hooks.
                        for (;;) {
                            if (confirmShutdown()) {
                                break;
                            }
                        }
                    } finally {
                        try {
                            // 退出前进行必要的清理
                            cleanup();
                        } finally {
                            // Lets remove all FastThreadLocals for the Thread as we are about to terminate and notify
                            // the future. The user may block on the future and once it unblocks the JVM may terminate
                            // and start unloading classes.
                            // See https://github.com/netty/netty/issues/6596.
                            FastThreadLocal.removeAll();

                            // 标记为已进入终止状态
                            STATE_UPDATER.set(SingleThreadEventExecutor.this, ST_TERMINATED);
                            // 释放信号量，使得等待终止的线程能够醒来
                            threadLock.release();
                            if (!taskQueue.isEmpty()) {
                                if (logger.isWarnEnabled()) {
                                    logger.warn("An event executor terminated with " +
                                            "non-empty task queue (" + taskQueue.size() + ')');
                                }
                            }
                            // 设置future结果，通知在该future上等待的线程
                            terminationFuture.setSuccess(null);
                        }
                    }
                }
            }
        });
    }

    private static final class DefaultThreadProperties implements ThreadProperties {
        private final Thread t;

        DefaultThreadProperties(Thread t) {
            this.t = t;
        }

        @Override
        public State state() {
            return t.getState();
        }

        @Override
        public int priority() {
            return t.getPriority();
        }

        @Override
        public boolean isInterrupted() {
            return t.isInterrupted();
        }

        @Override
        public boolean isDaemon() {
            return t.isDaemon();
        }

        @Override
        public String name() {
            return t.getName();
        }

        @Override
        public long id() {
            return t.getId();
        }

        @Override
        public StackTraceElement[] stackTrace() {
            return t.getStackTrace();
        }

        @Override
        public boolean isAlive() {
            return t.isAlive();
        }
    }
}
