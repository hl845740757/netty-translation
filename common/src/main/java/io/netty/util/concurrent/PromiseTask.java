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

import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;

/**
 * 该类的含义可对比{@link java.util.concurrent.FutureTask}
 * @param <V>
 */
class PromiseTask<V> extends DefaultPromise<V> implements RunnableFuture<V> {

    static <T> Callable<T> toCallable(Runnable runnable, T result) {
        return new RunnableAdapter<T>(runnable, result);
    }

    /**
     * 这个和Executors中的RunnableAdapter基本一样
     * @param <T>
     */
    private static final class RunnableAdapter<T> implements Callable<T> {
        /**
         * 任务逻辑
         */
        final Runnable task;
        /**
         * 结果对象，其实是结果容器。
         * 如果需要结果的话，task内部会持有该对象并在完成任务时将结果存在该结果容器对象。
         * 只有task本身知道如何使用该对象。
         */
        final T result;

        RunnableAdapter(Runnable task, T result) {
            this.task = task;
            this.result = result;
        }

        @Override
        public T call() {
            task.run();
            // 结果已写入result，如果需要返回结果的话，这是task内部的逻辑。
            return result;
        }

        @Override
        public String toString() {
            return "Callable(task: " + task + ", result: " + result + ')';
        }
    }

    protected final Callable<V> task;

    PromiseTask(EventExecutor executor, Runnable runnable, V result) {
        this(executor, toCallable(runnable, result));
    }

    PromiseTask(EventExecutor executor, Callable<V> callable) {
        super(executor);
        task = callable;
    }

    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public void run() {
        try {
            if (setUncancellableInternal()) {
                V result = task.call();
                setSuccessInternal(result);
            }
        } catch (Throwable e) {
            setFailureInternal(e);
        }
    }

    // --------------- 禁用这些public方法，因为PromiseTask既是Promise，也是Task，结果由它自己来赋值
    // --------------- 由protected方法替代（需要支持子类调用）

    /**
     * {@link PromiseTask}的结果由自己赋值，因此外部赋值抛出异常
     * @param cause
     * @return
     */
    @Override
    public final Promise<V> setFailure(Throwable cause) {
        throw new IllegalStateException();
    }

    /**
     * 这是内部赋值失败的方法，不是对外的
     * @param cause
     * @return
     */
    protected final Promise<V> setFailureInternal(Throwable cause) {
        super.setFailure(cause);
        return this;
    }

    /**
     * {@link PromiseTask}的结果由自己赋值，因此外部赋值总是失败
     * @param cause
     * @return
     */
    @Override
    public final boolean tryFailure(Throwable cause) {
        return false;
    }

    /**
     * 内部赋值失败的方法
     * @param cause
     * @return
     */
    protected final boolean tryFailureInternal(Throwable cause) {
        return super.tryFailure(cause);
    }

    /**
     * {@link PromiseTask}的结果由自己赋值，因此外部赋值抛出异常。
     * @param result
     * @return
     */
    @Override
    public final Promise<V> setSuccess(V result) {
        throw new IllegalStateException();
    }

    /**
     * 内部赋值成功的方法
     * @param result
     * @return
     */
    protected final Promise<V> setSuccessInternal(V result) {
        super.setSuccess(result);
        return this;
    }

    /**
     * {@link PromiseTask}的结果由自己赋值，因此外部赋值总是失败
     * @param result
     * @return
     */
    @Override
    public final boolean trySuccess(V result) {
        return false;
    }

    /**
     * 内部尝试赋值成功的方法
     * @param result
     * @return
     */
    protected final boolean trySuccessInternal(V result) {
        return super.trySuccess(result);
    }

    @Override
    public final boolean setUncancellable() {
        throw new IllegalStateException();
    }

    protected final boolean setUncancellableInternal() {
        return super.setUncancellable();
    }

    @Override
    protected StringBuilder toStringBuilder() {
        StringBuilder buf = super.toStringBuilder();
        buf.setCharAt(buf.length() - 1, ',');

        return buf.append(" task: ")
                  .append(task)
                  .append(')');
    }
}
