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

import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 *
 * 任务状态迁移：
 * <pre>
 *       (setUncancellabl) (result == UNCANCELLABLE)     (异常/成功)
 *                   --------> 不可取消状态 ------------------------|
 *                   |         (未完成)                            |
 *  初始状态 ---------|                                            | ----> 完成状态(isDown() == true)
 * (result == null)  |                                            |
 *  (未完成)          |--------------------------------------------|
 *                                 (取消/异常/成功)
 *                 (cancel, tryFailure,setFailure,trySuccess,setSuccess)
 * </pre>
 * @param <V>
 */
public class DefaultPromise<V> extends AbstractFuture<V> implements Promise<V> {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultPromise.class);
    private static final InternalLogger rejectedExecutionLogger =
            InternalLoggerFactory.getInstance(DefaultPromise.class.getName() + ".rejectedExecution");
    private static final int MAX_LISTENER_STACK_DEPTH = Math.min(8,
            SystemPropertyUtil.getInt("io.netty.defaultPromise.maxListenerStackDepth", 8));

    /** 我个人的理解上，AtomicReferenceFieldUpdater最大的好处就是单例，使用便利程度，以及可理解度要难于原子变量。 */
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultPromise, Object> RESULT_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DefaultPromise.class, Object.class, "result");
    /**
     * 它是一个占位符，当运行成功而没有结果时（结果为null时），赋值给{@link #result}，使用该对象表示已完成。
     */
    private static final Object SUCCESS = new Object();
    /**
     * 它表示一个占位符，赋值给{@link #result}，表示当前处于不可取消状态
     */
    private static final Object UNCANCELLABLE = new Object();
    /**
     * 它表示一个占位符，赋值给{@link #result}，表示已取消，减少堆栈填充。
     */
    private static final CauseHolder CANCELLATION_CAUSE_HOLDER = new CauseHolder(ThrowableUtil.unknownStackTrace(
            new CancellationException(), DefaultPromise.class, "cancel(...)"));

    /**
     * 执行结果，和{@link java.util.concurrent.FutureTask}不同的是，它不是使用一个int值来表示状态并保护结果的可见性，
     * 而是自身是volatile，将不同的实例结果赋值给result表示状态和结果。
     */
    private volatile Object result;
    /**
     * 默认的通知用的executor，一般是创建Promise的EventLoop。
     * 如果任务执行期间可能改变executor，那么需要重写{@link #executor()}，以返回最新的executor。
     */
    private final EventExecutor executor;
    /**
     * 一个或多个监听器。可能是{@link GenericFutureListener}或者 {@link DefaultFutureListeners}。
     * (它使用一个对象来表示一个或多个监听器)。
     * 如果它为null，存在两种情况：
     * 1.还没有监听器添加到该promise。
     * 2.所有的监听器都已经被通知了。（也就是说在通知完毕和移除某个监听器后，如果size为0，那么会置为null）
     * Netty的这个实现我没有深入的研究，我个人实现的时候使用了LinkedList...
     *
     * 线程安全 - 通过synchronized(this)保护。我们必须支持在没有{@link EventExecutor}时添加监听器。
     *
     * One or more listeners. Can be a {@link GenericFutureListener} or a {@link DefaultFutureListeners}.
     * If {@code null}, it means either 1) no listeners were added yet or 2) all listeners were notified.
     *
     * Threading - synchronized(this). We must support adding listeners when there is no EventExecutor.
     */
    private Object listeners;
    /**
     * 在当前对象上等待的线程数。这些线程请求持有当前对象的监视器以使用 wait()/notifyAll() 进行通信.
     * 如果该值大于0，那么在Future进入完成状态的时候需要调用{@link #notifyAll()}，使用该标记可以减少{@link #notifyAll()}的调用。
     *
     * 线程安全 - 通过synchronized(this)保护。
     *
     * Threading - synchronized(this). We are required to hold the monitor to use Java's underlying wait()/notifyAll().
     */
    private short waiters;

    /**
     * 表示当前是否有线程正在通知监听器们。我们必须阻止并发的通知 和 保持监听器的先入先出顺序(先添加的先被通知)。
     * 如果有该值为{@code true}，那么表示有线程正在通知监听器，那么我可以放弃通知。
     *
     * 线程安全 - 通过synchronized(this)保护。
     *
     * Threading - synchronized(this). We must prevent concurrent notification and FIFO listener notification if the
     * executor changes.
     */
    private boolean notifyingListeners;

    /**
     * Creates a new instance.
     *
     * It is preferable to use {@link EventExecutor#newPromise()} to create a new promise
     *
     * @param executor
     *        当future关联的操作完成时，用于通知监听器的线程。这里假设executor将会防止{@link StackOverflowError}异常(栈溢出异常)。
     *        如果executor的堆栈超过阈值，将会提交{@link Runnable}执行以避免堆栈异常。
     *        the {@link EventExecutor} which is used to notify the promise once it is complete.
     *        It is assumed this executor will protect against {@link StackOverflowError} exceptions.
     *        The executor may be used to avoid {@link StackOverflowError} by executing a {@link Runnable} if the stack
     *        depth exceeds a threshold.
     *
     */
    public DefaultPromise(EventExecutor executor) {
        this.executor = checkNotNull(executor, "executor");
    }

    /**
     * See {@link #executor()} for expectations of the executor.
     */
    protected DefaultPromise() {
        // only for subclasses
        executor = null;
    }

    @Override
    public Promise<V> setSuccess(V result) {
        if (setSuccess0(result)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this);
    }

    @Override
    public boolean trySuccess(V result) {
        return setSuccess0(result);
    }

    @Override
    public Promise<V> setFailure(Throwable cause) {
        if (setFailure0(cause)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this, cause);
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return setFailure0(cause);
    }

    @Override
    public boolean setUncancellable() {
        // 尝试置为不可取消状态
        if (RESULT_UPDATER.compareAndSet(this, null, UNCANCELLABLE)) {
            return true;
        }
        // 到这里 result一定不可为null，表示 不可取消状态 或 完成状态
        // !isDone0(result) 其实等价于 result == UNCANCELLABLE
        // !isCancelled0(result) 表示非取消进入的完成状态
        Object result = this.result;
        return !isDone0(result) || !isCancelled0(result);
    }

    /**
     * 解释一下为什么多线程的代码喜欢将volatile变量存为临时变量或传递给某一个静态方法做判断。
     * 原因：保证数据的前后一致性。
     * 当对volatile涉及多个操作时，如果不把volatile变量保存下来，每次读取的结果可能是不一样的！！！
     */
    @Override
    public boolean isSuccess() {
        Object result = this.result;
        return result != null && result != UNCANCELLABLE && !(result instanceof CauseHolder);
    }

    @Override
    public boolean isCancellable() {
        return result == null;
    }

    @Override
    public Throwable cause() {
        Object result = this.result;
        return (result instanceof CauseHolder) ? ((CauseHolder) result).cause : null;
    }

    @Override
    public Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        checkNotNull(listener, "listener");
        // 先添加监听器
        synchronized (this) {
            addListener0(listener);
        }
        // 必须检查是否已经进入完成状态，避免信号丢失 - 因为进入完成状态与锁没有关系
        if (isDone()) {
            notifyListeners();
        }

        return this;
    }

    @Override
    public Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        checkNotNull(listeners, "listeners");
        // 先添加监听器
        synchronized (this) {
            for (GenericFutureListener<? extends Future<? super V>> listener : listeners) {
                if (listener == null) {
                    break;
                }
                addListener0(listener);
            }
        }
        // 必须检查是否已经进入完成状态，避免信号丢失。
        if (isDone()) {
            notifyListeners();
        }

        return this;
    }

    @Override
    public Promise<V> removeListener(final GenericFutureListener<? extends Future<? super V>> listener) {
        checkNotNull(listener, "listener");
        // 监听器对象由this的监视器锁保护，先获得锁才可以修改
        synchronized (this) {
            removeListener0(listener);
        }

        return this;
    }

    @Override
    public Promise<V> removeListeners(final GenericFutureListener<? extends Future<? super V>>... listeners) {
        checkNotNull(listeners, "listeners");

        synchronized (this) {
            for (GenericFutureListener<? extends Future<? super V>> listener : listeners) {
                if (listener == null) {
                    break;
                }
                removeListener0(listener);
            }
        }

        return this;
    }

    /**
     * <pre> {@code
     *         // synchronized的标准模式
     *         synchronized (this) {
     *             while(!condition()) {
     *                 this.wait();
     *             }
     *         }
     *
     *         // 显式锁的标准模式
     *         lock.lock();
     *         try {
     *             while (!isOK()) {
     *                 condition.await();
     *             }
     *         } finally {
     *             lock.unlock();
     *         }
     * }
     * </pre>
     */
    @Override
    public Promise<V> await() throws InterruptedException {
        // 先检查一次是否已完成，减小锁竞争，同时在完成的情况下，等待不会死锁。
        if (isDone()) {
            return this;
        }
        // 检查中断 --- 在执行一个耗时操作之前检查中断是有必要的
        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        // 检查死锁可能
        checkDeadLock();

        synchronized (this) {
            while (!isDone()) {
                incWaiters();
                try {
                    wait();
                } finally {
                    decWaiters();
                }
            }
        }
        return this;
    }

    @Override
    public Promise<V> awaitUninterruptibly() {
        // 先检查一次是否已完成，减小锁竞争，同时在完成的情况下，等待不会死锁。
        if (isDone()) {
            return this;
        }

        // 检查死锁可能
        checkDeadLock();

        boolean interrupted = false;
        synchronized (this) {
            while (!isDone()) {
                incWaiters();
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Interrupted while waiting.
                    interrupted = true;
                } finally {
                    decWaiters();
                }
            }
        }
        // 这代码有bug，应该放在finally块中执行，如果在等待中出现其他异常，会导致中断丢失。
        // 虽然 incWaiters 出现异常的可能性很低，但是严格的来说是不安全的。
        // 码多必失... 相似代码/重复代码写多了就容易出现点问题。
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(MILLISECONDS.toNanos(timeoutMillis), true);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            // Should not be raised at all.
            throw new InternalError();
        }
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            // Should not be raised at all.
            throw new InternalError();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public V getNow() {
        Object result = this.result;
        // 如果当前对象表示 异常、成功执行但没有结果、不可取消，则返回null
        if (result instanceof CauseHolder || result == SUCCESS || result == UNCANCELLABLE) {
            return null;
        }
        // 否则返回真实的值
        return (V) result;
    }

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 尝试将结果从初始状态(null)变为取消完成状态
        if (RESULT_UPDATER.compareAndSet(this, null, CANCELLATION_CAUSE_HOLDER)) {
            // 取消成功，则坚持是否需要通知监听器，并进行相应的通知
            if (checkNotifyWaiters()) {
                notifyListeners();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled0(result);
    }

    @Override
    public boolean isDone() {
        return isDone0(result);
    }

    @Override
    public Promise<V> sync() throws InterruptedException {
        // 等待进入完成状态
        await();
        // 如果任务失败了，则抛出对应的异常（所以使用sync系列方法要注意）
        rethrowIfFailed();
        return this;
    }

    @Override
    public Promise<V> syncUninterruptibly() {
        // 等待进入完成状态
        awaitUninterruptibly();
        // 如果任务失败了，则抛出对应的异常（所以使用sync系列方法要注意）
        rethrowIfFailed();
        return this;
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    protected StringBuilder toStringBuilder() {
        StringBuilder buf = new StringBuilder(64)
                .append(StringUtil.simpleClassName(this))
                .append('@')
                .append(Integer.toHexString(hashCode()));

        Object result = this.result;
        if (result == SUCCESS) {
            buf.append("(success)");
        } else if (result == UNCANCELLABLE) {
            buf.append("(uncancellable)");
        } else if (result instanceof CauseHolder) {
            buf.append("(failure: ")
                    .append(((CauseHolder) result).cause)
                    .append(')');
        } else if (result != null) {
            buf.append("(success: ")
                    .append(result)
                    .append(')');
        } else {
            buf.append("(incomplete)");
        }

        return buf;
    }

    /**
     * 获取在promise关联的任务完成的时候用于通知的executor。
     *
     * 这里假设该executor会防止 {@link StackOverflowError}(堆栈溢出)。
     * 当该executor的栈深度超过一个阈值之后，该executor可能通过使用 {@link java.util.concurrent.Executor#execute(Runnable)}
     * 以避免出现堆栈异常。
     *
     * Get the executor used to notify listeners when this promise is complete.
     * <p>
     * It is assumed this executor will protect against {@link StackOverflowError} exceptions.
     * The executor may be used to avoid {@link StackOverflowError} by executing a {@link Runnable} if the stack
     * depth exceeds a threshold.
     * @return The executor used to notify listeners when this promise is complete.
     */
    protected EventExecutor executor() {
        return executor;
    }

    /**
     * 检查死锁。
     */
    protected void checkDeadLock() {
        // 默认情况下，通知用的executor就是任务的执行环境
        // EventLoop是单线程的，不可以在当前线程上等待另一个任务完成，会导致死锁。
        EventExecutor e = executor();
        if (e != null && e.inEventLoop()) {
            throw new BlockingOperationException(toString());
        }
    }

    /**
     * Notify a listener that a future has completed.
     * <p>
     * This method has a fixed depth of {@link #MAX_LISTENER_STACK_DEPTH} that will limit recursion to prevent
     * {@link StackOverflowError} and will stop notifying listeners added after this threshold is exceeded.
     * @param eventExecutor the executor to use to notify the listener {@code listener}.
     * @param future the future that is complete.
     * @param listener the listener to notify.
     */
    protected static void notifyListener(
            EventExecutor eventExecutor, final Future<?> future, final GenericFutureListener<?> listener) {
        checkNotNull(eventExecutor, "eventExecutor");
        checkNotNull(future, "future");
        checkNotNull(listener, "listener");
        notifyListenerWithStackOverFlowProtection(eventExecutor, future, listener);
    }

    private void notifyListeners() {
        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();
            final int stackDepth = threadLocals.futureListenerStackDepth();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                threadLocals.setFutureListenerStackDepth(stackDepth + 1);
                try {
                    notifyListenersNow();
                } finally {
                    threadLocals.setFutureListenerStackDepth(stackDepth);
                }
                return;
            }
        }

        safeExecute(executor, new Runnable() {
            @Override
            public void run() {
                notifyListenersNow();
            }
        });
    }

    /**
     * The logic in this method should be identical to {@link #notifyListeners()} but
     * cannot share code because the listener(s) cannot be cached for an instance of {@link DefaultPromise} since the
     * listener(s) may be changed and is protected by a synchronized operation.
     */
    private static void notifyListenerWithStackOverFlowProtection(final EventExecutor executor,
                                                                  final Future<?> future,
                                                                  final GenericFutureListener<?> listener) {
        if (executor.inEventLoop()) {
            final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();
            final int stackDepth = threadLocals.futureListenerStackDepth();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                threadLocals.setFutureListenerStackDepth(stackDepth + 1);
                try {
                    notifyListener0(future, listener);
                } finally {
                    threadLocals.setFutureListenerStackDepth(stackDepth);
                }
                return;
            }
        }

        safeExecute(executor, new Runnable() {
            @Override
            public void run() {
                notifyListener0(future, listener);
            }
        });
    }

    private void notifyListenersNow() {
        // 用于拉取最新的监听器，避免长时间的占有锁
        Object listeners;
        synchronized (this) {
            // 有线程正在进行通知 或当前 没有监听器，则不需要当前线程进行通知
            // Only proceed if there are listeners to notify and we are not already notifying listeners.
            if (notifyingListeners || this.listeners == null) {
                return;
            }
            // 标记为正在通知(每一个正在通知的线程都会将所有的监听器通知一遍)
            notifyingListeners = true;
            listeners = this.listeners;
            this.listeners = null;
        }
        for (;;) {
            // 通知当前批次的监听器(此时不需要获得锁)
            // 通知某一个监听器的时候必须捕获异常，否则可能导致某些监听器丢失完成信号（notifyingListeners标记无法清除）
            if (listeners instanceof DefaultFutureListeners) {
                notifyListeners0((DefaultFutureListeners) listeners);
            } else {
                notifyListener0(this, (GenericFutureListener<?>) listeners);
            }
            // 通知完当前批次后，检查是否有新的监听器加入
            synchronized (this) {
                // 如果在通知完当前的监听器之后，没有新的监听器加入，那么表示通知完成，否则需要通知新加入的监听器
                if (this.listeners == null) {
                    // Nothing can throw from within this method, so setting notifyingListeners back to false does not
                    // need to be in a finally block.
                    notifyingListeners = false;
                    return;
                }
                listeners = this.listeners;
                this.listeners = null;
            }
        }
    }

    /**
     * 通知一组监听器
     * @param listeners 多个监听器的组合对象
     */
    private void notifyListeners0(DefaultFutureListeners listeners) {
        GenericFutureListener<?>[] a = listeners.listeners();
        int size = listeners.size();
        for (int i = 0; i < size; i ++) {
            notifyListener0(this, a[i]);
        }
    }

    /**
     * 通知单个监听器
     * @param future future对象
     * @param l future上注册的某一个监听器
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void notifyListener0(Future future, GenericFutureListener l) {
        try {
            l.operationComplete(future);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("An exception was thrown by " + l.getClass().getName() + ".operationComplete()", t);
            }
        }
    }

    /**
     * 真正添加监听器的方法，运行在锁的保护之下，以实现线程安全。
     */
    private void addListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listeners == null) {
            // 可以直接赋值
            listeners = listener;
        } else if (listeners instanceof DefaultFutureListeners) {
            // 如果是多个监听器的组合对象，则直接添加该监听器
            ((DefaultFutureListeners) listeners).add(listener);
        } else {
            // 这是第二个监听器，则创建和前一个监听器构成的组合对象
            listeners = new DefaultFutureListeners((GenericFutureListener<?>) listeners, listener);
        }
    }

    /**
     * 移除一个监听器
     * @param listener 期望移除的监听器
     */
    private void removeListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listeners instanceof DefaultFutureListeners) {
            // 如果是多个监听器的组合对象，则调用对应的移除方法
            ((DefaultFutureListeners) listeners).remove(listener);
        } else if (listeners == listener) {
            // 如果是前面注册的监听器，则直接将引用置为null
            listeners = null;
        }
        // else 该监听器未在该future上注册
    }

    /**
     * Q:为什么要这么写？
     * A:为了支持子类重写 {@link #setSuccess(Object)} {@link #setFailure(Throwable)}
     * {@link #trySuccess(Object)} {@link #tryFailure(Throwable)}
     */
    private boolean setSuccess0(V result) {
        return setValue0(result == null ? SUCCESS : result);
    }

    /**
     * Q:为什么要这么写？
     * A:为了支持子类重写 {@link #setSuccess(Object)} {@link #setFailure(Throwable)}
     * @see #setSuccess0(Object)
     */
    private boolean setFailure0(Throwable cause) {
        return setValue0(new CauseHolder(checkNotNull(cause, "cause")));
    }

    /**
     * 尝试赋值结果(从未完成状态进入完成状态)
     * @param objResult 要赋的值，一定不为null
     * @return 如果赋值成功，则返回true，否则返回false。
     */
    private boolean setValue0(Object objResult) {
        // 正常金乌完成状态，可以是从初始状态进入完成状态，也可以是从不可取消状态变为完成状态
        if (RESULT_UPDATER.compareAndSet(this, null, objResult) ||
            RESULT_UPDATER.compareAndSet(this, UNCANCELLABLE, objResult)) {

            if (checkNotifyWaiters()) {
                // 如果需要通知监听器，则通知监听器
                notifyListeners();
            }

            return true;
        }
        return false;
    }

    /**
     * 检查需要通知的等待线程。
     *
     * Q:为什么可以先检查listener是否为null，再通知监听器？
     * A:代码执行到这里的时候，结果(volatile)已经对其它线程可见。如果这里认为没有监听器，那么新加入的监听器一定会在添加完之后进行通知。
     *
     * Q: 为什么不在checkNotifyWaiters里面进行通知呢？
     * A: 那会使得{@link #notifyListeners()}整个方法都处于同步代码块中！
     *
     * Check if there are any waiters and if so notify these.
     * @return {@code true} if there are any listeners attached to the promise, {@code false} otherwise.
     *          如果有listener在该promise上监听则返回true，否则返回false
     */
    private synchronized boolean checkNotifyWaiters() {
        if (waiters > 0) {
            notifyAll();
        }
        return listeners != null;
    }

    /**
     * 在该对象上等待的线程数+1
     */
    private void incWaiters() {
        // 进行了一个保护，但是awaitUninterruptibly方法写的并不安全(虽然基本不会出现，但是不够规范)
        if (waiters == Short.MAX_VALUE) {
            throw new IllegalStateException("too many waiters: " + this);
        }
        ++waiters;
    }

    /**
     * 在该对象上等待的线程数-1
     */
    private void decWaiters() {
        --waiters;
    }

    /**
     * 如果future关联的任务执行失败了的话，重新抛出对应的异常。
     * {@link #sync()} {@link #syncUninterruptibly()}会进行调用。。
     */
    private void rethrowIfFailed() {
        Throwable cause = cause();
        if (cause == null) {
            return;
        }
        // 抛出一个异常，利用泛型绕过了编译检查
        PlatformDependent.throwException(cause);
    }

    /**
     * 这块我觉得netty写的不太好，
     * 1. 获取锁需要时间，因此应该在获取锁之后计算剩余时间
     * 2. synchronized应该放在外层，如果被错误的唤醒，那么会立即释放锁，又立即竞争锁。
     * 3. 可读性不好，不易理解 - 与锁的标准模式差别较大
     *
     * 我的实现大致如下：
     * <pre>{@code
     *         boolean interrupted = Thread.interrupted();
     *         final long endTime = System.nanoTime() + unit.toNanos(timeout);
     *         try {
     *             synchronized (this) {
     *                 while (!isDone()) {
     *                     // 获取锁需要时间，因此应该在获取锁之后计算剩余时间
     *                     final long remainNano = endTime - System.nanoTime();
     *                     if (remainNano <= 0) {
     *                          // 再尝试一下
     *                         return isDone();
     *                     }
     *                     incWaiters();
     *                     try {
     *                         this.wait(remainNano / 1000000, (int) (remainNano % 1000000));
     *                     } catch (InterruptedException e) {
     *                         interrupted = true;
     *                     } finally {
     *                         decWaiters();
     *                     }
     *                 }
     *                 return true;
     *             }
     *         } finally {
     *             // 恢复中断状态
     *             if (interrupted) {
     *                 Thread.currentThread().interrupt();
     *             }
     *         }
     * }</pre>
     */
    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        // 已完成，检查一次已完成，可以避免在完成的状态下的死锁问题。
        if (isDone()) {
            return true;
        }
        // 小于等于0表示不阻塞（其实可以和上面一句调换下顺序）
        if (timeoutNanos <= 0) {
            return isDone();
        }
        // 如果可被中断且现场当前被中断的情况下，抛出中断异常
        // 如果不可被中断，那么现场的中断状态也不会被清除
        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException(toString());
        }
        // 检查死锁可能
        checkDeadLock();

        long startTime = System.nanoTime();
        long waitTime = timeoutNanos;
        boolean interrupted = false;

        // 限时等待
        try {
            for (;;) {
                synchronized (this) {
                    if (isDone()) {
                        return true;
                    }
                    incWaiters();
                    try {
                        wait(waitTime / 1000000, (int) (waitTime % 1000000));
                    } catch (InterruptedException e) {
                        // 如果可中断，那么在中断之后直接退出
                        if (interruptable) {
                            throw e;
                        } else {
                            // 否则捕获中断标记
                            interrupted = true;
                        }
                    } finally {
                        decWaiters();
                    }
                }
                if (isDone()) {
                    return true;
                } else {
                    waitTime = timeoutNanos - (System.nanoTime() - startTime);
                    if (waitTime <= 0) {
                        return isDone();
                    }
                }
            }
        } finally {
            // 这儿又是对的，这是同一个人写的吗？？？？
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 通知所有的Future进度观察者，有关进度类型的监听器我一点都没研究，暂时对其不感兴趣。
     *
     * Notify all progressive listeners.
     * <p>
     * No attempt is made to ensure notification order if multiple calls are made to this method before
     * the original invocation completes.
     * <p>
     * This will do an iteration over all listeners to get all of type {@link GenericProgressiveFutureListener}s.
     * @param progress the new progress.
     * @param total the total progress.
     */
    @SuppressWarnings("unchecked")
    void notifyProgressiveListeners(final long progress, final long total) {
        final Object listeners = progressiveListeners();
        if (listeners == null) {
            return;
        }

        final ProgressiveFuture<V> self = (ProgressiveFuture<V>) this;

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            if (listeners instanceof GenericProgressiveFutureListener[]) {
                notifyProgressiveListeners0(
                        self, (GenericProgressiveFutureListener<?>[]) listeners, progress, total);
            } else {
                notifyProgressiveListener0(
                        self, (GenericProgressiveFutureListener<ProgressiveFuture<V>>) listeners, progress, total);
            }
        } else {
            if (listeners instanceof GenericProgressiveFutureListener[]) {
                final GenericProgressiveFutureListener<?>[] array =
                        (GenericProgressiveFutureListener<?>[]) listeners;
                safeExecute(executor, new Runnable() {
                    @Override
                    public void run() {
                        notifyProgressiveListeners0(self, array, progress, total);
                    }
                });
            } else {
                final GenericProgressiveFutureListener<ProgressiveFuture<V>> l =
                        (GenericProgressiveFutureListener<ProgressiveFuture<V>>) listeners;
                safeExecute(executor, new Runnable() {
                    @Override
                    public void run() {
                        notifyProgressiveListener0(self, l, progress, total);
                    }
                });
            }
        }
    }

    /**
     * Returns a {@link GenericProgressiveFutureListener}, an array of {@link GenericProgressiveFutureListener}, or
     * {@code null}.
     */
    private synchronized Object progressiveListeners() {
        Object listeners = this.listeners;
        if (listeners == null) {
            // No listeners added
            return null;
        }

        if (listeners instanceof DefaultFutureListeners) {
            // Copy DefaultFutureListeners into an array of listeners.
            DefaultFutureListeners dfl = (DefaultFutureListeners) listeners;
            int progressiveSize = dfl.progressiveSize();
            switch (progressiveSize) {
                case 0:
                    return null;
                case 1:
                    for (GenericFutureListener<?> l: dfl.listeners()) {
                        if (l instanceof GenericProgressiveFutureListener) {
                            return l;
                        }
                    }
                    return null;
            }

            GenericFutureListener<?>[] array = dfl.listeners();
            GenericProgressiveFutureListener<?>[] copy = new GenericProgressiveFutureListener[progressiveSize];
            for (int i = 0, j = 0; j < progressiveSize; i ++) {
                GenericFutureListener<?> l = array[i];
                if (l instanceof GenericProgressiveFutureListener) {
                    copy[j ++] = (GenericProgressiveFutureListener<?>) l;
                }
            }

            return copy;
        } else if (listeners instanceof GenericProgressiveFutureListener) {
            return listeners;
        } else {
            // Only one listener was added and it's not a progressive listener.
            return null;
        }
    }

    private static void notifyProgressiveListeners0(
            ProgressiveFuture<?> future, GenericProgressiveFutureListener<?>[] listeners, long progress, long total) {
        for (GenericProgressiveFutureListener<?> l: listeners) {
            if (l == null) {
                break;
            }
            notifyProgressiveListener0(future, l, progress, total);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void notifyProgressiveListener0(
            ProgressiveFuture future, GenericProgressiveFutureListener l, long progress, long total) {
        try {
            l.operationProgressed(future, progress, total);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("An exception was thrown by " + l.getClass().getName() + ".operationProgressed()", t);
            }
        }
    }

    /**
     * 查询结果是否表示已经取消
     * @param result 结果的缓存变量，将volatile变量存为本地变量，避免在查询过程中数据发生变更。
     * @return
     */
    private static boolean isCancelled0(Object result) {
        // CauseHolder 表示执行失败，CancellationException表示取消
        return result instanceof CauseHolder && ((CauseHolder) result).cause instanceof CancellationException;
    }

    /**
     * 查询结果是否表示已完成。
     *
     * @param result 结果的缓存变量，将volatile变量存为本地变量，避免在查询过程中数据发生变更。
     * @return true or flase
     */
    private static boolean isDone0(Object result) {
        // 当result不为null，且不是不可取消占位符的时候表示已进入完成状态
        return result != null && result != UNCANCELLABLE;
    }

    /**
     * 异常holder，只有该类型表示失败。
     * 否则无法区分{@link #setSuccess(Object) exception}。（拿异常当结果就无法区分了）
     */
    private static final class CauseHolder {
        final Throwable cause;
        CauseHolder(Throwable cause) {
            this.cause = cause;
        }
    }

    /**
     * 安全的提交一个任务，避免提交任务打断当前运行。
     * @param executor 执行器
     * @param task 待提交的任务
     */
    private static void safeExecute(EventExecutor executor, Runnable task) {
        try {
            executor.execute(task);
        } catch (Throwable t) {
            rejectedExecutionLogger.error("Failed to submit a listener notification task. Event loop shut down?", t);
        }
    }
}
