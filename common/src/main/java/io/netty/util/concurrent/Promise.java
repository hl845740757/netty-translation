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

/**
 * Promise模式的本质就是为Future模式提供可写功能。
 * (和FutureTask的不同在于FutureTask是内部设置结果，而Promise是外部设置结果,为外部提供接口)
 * Promise和{@link Runnable}之间是组合关系，而FutureTask和{@link Runnable}是继承关系。
 *
 * Special {@link Future} which is writable.
 */
public interface Promise<V> extends Future<V> {

    /**
     * 标记该Future操作成功，并且通知所有的监听者。
     * 如果早已成功或失败，该方法将会抛出{@link IllegalStateException}异常。（即只允许调用一次）
     *
     * Marks this future as a success and notifies all
     * listeners.
     *
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setSuccess(V result);

    /**
     * 尝试标记该Future操作成功，并且通知所有的监听者。
     * 当且仅当成功标记该future为成功时返回true。如果早已成功或失败，该方法将返回false。
     *
     * Marks this future as a success and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a success. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean trySuccess(V result);

    /**
     * 标记该Future操作失败，并且通知所有的监听者。
     * 如果早已成功或失败，该方法将会抛出{@link IllegalStateException}异常。
     *
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * If it is success or failed already it will throw an {@link IllegalStateException}.
     */
    Promise<V> setFailure(Throwable cause);

    /**
     * 尝试标记该Future操作失败，并且通知所有的监听者。
     * 当且仅当成功标记该future为成功时返回true。如果早已成功或失败，该方法将返回false。
     *
     * Marks this future as a failure and notifies all
     * listeners.
     *
     * @return {@code true} if and only if successfully marked this future as
     *         a failure. Otherwise {@code false} because this future is
     *         already marked as either a success or a failure.
     */
    boolean tryFailure(Throwable cause);

    /**
     * 标记当前future为不可取消。
     * 当前仅当成功标记该future为不可取消或该future未被取消且已经完成则返回true。
     * 如果该future早已被取消则返回false。
     *
     * Make this future impossible to cancel.
     *
     * @return {@code true} if and only if successfully marked this future as uncancellable or it is already done
     *         without being cancelled.  {@code false} if this future has been cancelled already.
     */
    boolean setUncancellable();

    // 这些返回值转换为子类型的代码 能否优雅的解决呢？泛型的话，应该会好点

    @Override
    Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    @Override
    Promise<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    @Override
    Promise<V> await() throws InterruptedException;

    @Override
    Promise<V> awaitUninterruptibly();

    @Override
    Promise<V> sync() throws InterruptedException;

    @Override
    Promise<V> syncUninterruptibly();
}
