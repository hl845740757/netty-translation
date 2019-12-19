/*
 * Copyright 2016 The Netty Project
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

/**
 * Promise Combiner监视一些独立的future的结果。当所有的Future都进入完成状态后通知一个最终的、聚合的promise。
 * 当且仅当所有被聚合的Future都成功时，该聚合的Promise才表现为成功。任意一个Future失败，那么该聚合的Promise都将表现为失败。
 * 聚合的Promise失败的原因是这些失败的future中的某一个。确切地说，如果被聚合的future中有多个future失败，哪一个失败的原因将会被赋值给
 * 该聚合的promise将是不确定的。
 * <p>
 * 调用者可以通过{@link PromiseCombiner#add(Future)} and {@link PromiseCombiner#addAll(Future[])}添加任意数量的future，
 * 当所有要被合并的future被添加以后，调用者必须通过{@link PromiseCombiner#finish(Promise)}提供一个聚合的promise，当所有被合并的promise
 * 完成的时候被通知。
 * (有点生硬:其实就是说你在聚合完成之后，需要提供一个自己的promise，以便在所有被聚合的future进入完成状态之后得到一个通知)
 * <p>
 * 该工具类的应用场景一般是拆包，当将一个完整数据包拆分为多个小包时，只有当这些小包都传输完成时，该数据包才算传输完成。
 *
 * <p>A promise combiner monitors the outcome of a number of discrete futures, then notifies a final, aggregate promise
 * when all of the combined futures are finished. The aggregate promise will succeed if and only if all of the combined
 * futures succeed. If any of the combined futures fail, the aggregate promise will fail. The cause failure for the
 * aggregate promise will be the failure for one of the failed combined futures; if more than one of the combined
 * futures fails, exactly which cause of failure will be assigned to the aggregate promise is undefined.</p>
 *
 * <p>Callers may populate a promise combiner with any number of futures to be combined via the
 * {@link PromiseCombiner#add(Future)} and {@link PromiseCombiner#addAll(Future[])} methods. When all futures to be
 * combined have been added, callers must provide an aggregate promise to be notified when all combined promises have
 * finished via the {@link PromiseCombiner#finish(Promise)} method.</p>
 *
 * <p>This implementation is <strong>NOT</strong> thread-safe and all methods must be called
 * from the {@link EventExecutor} thread.</p>
 */
public final class PromiseCombiner {
    // 这些数据都不是volatile的，也不是锁保护的，是因为只允许创建PromiseCombiner的当前线程访问。
    /**
     * 总的future数量
     */
    private int expectedCount;
    /**
     * 已完成的future，当已完成的数量等于{@link #expectedCount}的时候，表示PromiseCombiner进入完成状态。
     */
    private int doneCount;
    /**
     * 指定的通知器
     */
    private Promise<Void> aggregatePromise;
    /**
     * 造成失败的原因，随意捕获的一个。
     * 也并没有使用{@link Throwable#addSuppressed(Throwable)}合并所有的异常。
     */
    private Throwable cause;
    /**
     * 用于监听所管理的future的完成事件。
     */
    private final GenericFutureListener<Future<?>> listener = new GenericFutureListener<Future<?>>() {
        @Override
        public void operationComplete(final Future<?> future) {
            if (executor.inEventLoop()) {
                // 就在当前线程下，直接执行
                operationComplete0(future);
            } else {
                // 否则将操作提交到EventLoop线程，消除回调代码中的同步逻辑
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        operationComplete0(future);
                    }
                });
            }
        }

        private void operationComplete0(Future<?> future) {
            assert executor.inEventLoop();
            // 增加已完成future计数
            ++doneCount;
            if (!future.isSuccess() && cause == null) {
                // 默认取了第一个失败的future的cause，但该future的顺序并没有保证
                cause = future.cause();
            }
            // 如果所有的future都已经完成，并且设置了promise，那么在所有future完成之后，进行通知
            if (doneCount == expectedCount && aggregatePromise != null) {
                tryPromise();
            }
        }
    };

    /**
     * 监听器的执行环境（其实也是当前线程）。
     */
    private final EventExecutor executor;

    /**
     * Deprecated use {@link PromiseCombiner#PromiseCombiner(EventExecutor)}.
     */
    @Deprecated
    public PromiseCombiner() {
        // 默认捕获了当前线程进行通知...应该使用真正的Executor对象。
        this(ImmediateEventExecutor.INSTANCE);
    }

    /**
     * The {@link EventExecutor} to use for notifications. You must call {@link #add(Future)}, {@link #addAll(Future[])}
     * and {@link #finish(Promise)} from within the {@link EventExecutor} thread.
     *
     * @param executor the {@link EventExecutor} to use for notifications.
     */
    public PromiseCombiner(EventExecutor executor) {
        this.executor = ObjectUtil.checkNotNull(executor, "executor");
    }

    /**
     * Adds a new promise to be combined. New promises may be added until an aggregate promise is added via the
     * {@link PromiseCombiner#finish(Promise)} method.
     *
     * @param promise the promise to add to this promise combiner
     *
     * @deprecated Replaced by {@link PromiseCombiner#add(Future)}.
     */
    @Deprecated
    public void add(Promise promise) {
        add((Future) promise);
    }

    /**
     * Adds a new future to be combined. New futures may be added until an aggregate promise is added via the
     * {@link PromiseCombiner#finish(Promise)} method.
     *
     * @param future the future to add to this promise combiner
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void add(Future future) {
        checkAddAllowed();
        // 线程封闭，数据保护
        checkInEventLoop();
        // 期望的future数加1
        ++expectedCount;
        // 添加完成监听
        future.addListener(listener);
    }

    /**
     * Adds new promises to be combined. New promises may be added until an aggregate promise is added via the
     * {@link PromiseCombiner#finish(Promise)} method.
     *
     * @param promises the promises to add to this promise combiner
     *
     * @deprecated Replaced by {@link PromiseCombiner#addAll(Future[])}
     */
    @Deprecated
    public void addAll(Promise... promises) {
        addAll((Future[]) promises);
    }

    /**
     * Adds new futures to be combined. New futures may be added until an aggregate promise is added via the
     * {@link PromiseCombiner#finish(Promise)} method.
     *
     * @param futures the futures to add to this promise combiner
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addAll(Future... futures) {
        for (Future future : futures) {
            this.add(future);
        }
    }

    /**
     * 设置最终的promise，当它管理的所有future都进入完成的时候，该promise将进入完成状态。
     * 这部分注释和类文档有重复，这里不再注释。
     *
     * <p>Sets the promise to be notified when all combined futures have finished. If all combined futures succeed,
     * then the aggregate promise will succeed. If one or more combined futures fails, then the aggregate promise will
     * fail with the cause of one of the failed futures. If more than one combined future fails, then exactly which
     * failure will be assigned to the aggregate promise is undefined.</p>
     *
     * <p>After this method is called, no more futures may be added via the {@link PromiseCombiner#add(Future)} or
     * {@link PromiseCombiner#addAll(Future[])} methods.</p>
     *
     * @param aggregatePromise the promise to notify when all combined futures have finished
     */
    public void finish(Promise<Void> aggregatePromise) {
        ObjectUtil.checkNotNull(aggregatePromise, "aggregatePromise");
        checkInEventLoop();
        if (this.aggregatePromise != null) {
            throw new IllegalStateException("Already finished");
        }
        this.aggregatePromise = aggregatePromise;
        // 如果所有的future都已经完成，那么直接进行通知
        if (doneCount == expectedCount) {
            tryPromise();
        }
    }

    private void checkInEventLoop() {
        if (!executor.inEventLoop()) {
            throw new IllegalStateException("Must be called from EventExecutor thread");
        }
    }

    private boolean tryPromise() {
        // 如果cause不为null，证明有future关联的操作失败了，那么聚合的promise也表现为失败
        return (cause == null) ? aggregatePromise.trySuccess(null) : aggregatePromise.tryFailure(cause);
    }

    private void checkAddAllowed() {
        if (aggregatePromise != null) {
            throw new IllegalStateException("Adding promises is not allowed after finished adding");
        }
    }
}
