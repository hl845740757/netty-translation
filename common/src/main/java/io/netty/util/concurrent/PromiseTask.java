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
 * 它既是Promise，也是Task，所有的结果都应该由自己赋值，不允许外部进行赋值操作。
 * <p>
 * 这里我要批评下这里的实现，这里继承{@link DefaultPromise}简直不能再糟糕了，它本不需要实现{@link Promise}接口，
 * 它本身只需要实现{@link Future}和{@link RunnableFuture}接口。
 * 使用{@link EventExecutor#newPromise()}创建一个{@link Promise}，通过代理{@link Promise}的部分方法实现接口，
 * 要好的多。
 *
 * <pre>{@code
 *
 * protected final Promise promise;
 * protected final Callable task;
 *
 * PromiseTask(EventExecutor executor, Callable<V> callable) {
 *      promise = executor.newPromise();
 *      task = callable;
 *     }
 *
 * public void run() {
 *      try{
 *          if(promise.setUncancellable()) {
 *              V result = task.call();
 *              promise.trySuccess(result);
 *          }
 *      } catch(Throwable e) {
 *          promise.tryFailure(e);
 *      }
 * }
 * }
 * </pre>
 *
 * @param <V>
 */
class PromiseTask<V> extends DefaultPromise<V> implements RunnableFuture<V> {

    static <T> Callable<T> toCallable(Runnable runnable, T result) {
        return new RunnableAdapter<T>(runnable, result);
    }

    /**
     * 这个和Executors中的RunnableAdapter基本一样。
     * {@link java.util.concurrent.Executors#callable(Runnable)}
     *
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
            // 标记为不可取消(因为PromiseTask本身并不清楚任务内部是什么逻辑，如果盲目的支持中断，可能导致需要原子执行的任务出现异常)
            if (setUncancellableInternal()) {
                // 执行任务逻辑
                V result = task.call();
                // 标记为成功
                setSuccessInternal(result);
            }
        } catch (Throwable e) {
            // 执行出现异常，标记为失败
            setFailureInternal(e);
        }
    }

    // --------------- 禁用这些public方法，因为PromiseTask既是Promise，也是Task，结果由它自己来赋值
    // --------------- 由protected方法替代（需要支持子类调用）

    /**
     * {@link PromiseTask}的结果由自己赋值，因此外部赋值抛出异常
     */
    @Override
    public final Promise<V> setFailure(Throwable cause) {
        throw new IllegalStateException();
    }

    /**
     * 这是内部赋值失败的方法，不是对外的
     */
    protected final Promise<V> setFailureInternal(Throwable cause) {
        super.setFailure(cause);
        return this;
    }

    /**
     * {@link PromiseTask}的结果由自己赋值，因此外部赋值总是失败
     */
    @Override
    public final boolean tryFailure(Throwable cause) {
        return false;
    }

    /**
     * 内部赋值失败的方法
     */
    protected final boolean tryFailureInternal(Throwable cause) {
        return super.tryFailure(cause);
    }

    /**
     * {@link PromiseTask}的结果由自己赋值，因此外部赋值抛出异常。
     */
    @Override
    public final Promise<V> setSuccess(V result) {
        throw new IllegalStateException();
    }

    /**
     * 内部赋值成功的方法
     */
    protected final Promise<V> setSuccessInternal(V result) {
        super.setSuccess(result);
        return this;
    }

    /**
     * {@link PromiseTask}的结果由自己赋值，因此外部赋值总是失败
     */
    @Override
    public final boolean trySuccess(V result) {
        return false;
    }

    /**
     * 内部尝试赋值成功的方法
     */
    protected final boolean trySuccessInternal(V result) {
        return super.trySuccess(result);
    }

    /**
     * 任务只有自己才能设置为不可取消，外部不可以进行设置
     */
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
