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

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;


/**
 * Future通常代表的是异步、支持取消、获取执行结果的任务的凭证。
 *
 * Future模式:
 *
 * 1.简单的Future模式是没有回调的，如JDK的Future。无回调的Future简单安全，没有线程安全问题。
 *
 * 2.Future模式也可以提供回调机制，提供回调的话，是存在线程安全问题的，因此监听器的实现呢一定要保证线程安全；
 * 此外，由于添加了回调，如果某一个回调方法执行时间过长或阻塞，或抛出异常等都将威胁到系统的安全性(如：其它监听者丢失信号)
 *
 * 3.添加回调机制其实就是观察者模式，涉及到使用观察者模式和不使用观察者模式的优劣议题。不使用观察者模式，就需要使用轮询的方式
 * 去获取类似的功能，其缺点是：轮询间隔时间过长；则响应性低，若轮询时间过短，则消耗大量的CPU资源。
 *
 *  怎么设计以及使用都需要仔细斟酌,毕竟有得必有失。
 * (简单一般容易保证安全性，对开发者要求不高。便捷的方法往往潜在着危险，对开发者素质要求较高。)
 *
 * JDK的Channel最大的问题就是不知道任务何时完成。
 * Netty对JDK的Future进行了扩展，添加了事件完成的回调机制。Netty也推荐大家使用回调机制监听计算的完成事件。
 *
 * Netty的Future提供了一些方便开发者使用的接口。
 *
 * The result of an asynchronous operation.
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Future<V> extends java.util.concurrent.Future<V> {

    /**
     * Returns {@code true} if and only if the I/O operation was completed
     * successfully.
     */
    boolean isSuccess();

    /**
     * returns {@code true} if and only if the operation can be cancelled via {@link #cancel(boolean)}.
     */
    boolean isCancellable();

    /**
     * Returns the cause of the failed I/O operation if the I/O operation has
     * failed.
     *
     * @return the cause of the failure.
     *         {@code null} if succeeded or this future is not
     *         completed yet.
     */
    Throwable cause();

    /**
     * 添加一个监听者到当前Future。传入的特定的Listener将会在Future计算完成时(isDown)被通知。
     * 如果当前Future已经计算完成，那么将立即被通知。
     *
     * Adds the specified listener to this future.  The
     * specified listener is notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listener is notified immediately.
     */
    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * Adds the specified listeners to this future.  The
     * specified listeners are notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listeners are notified immediately.
     */
    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * 从该Future中移除第一个出现的特定的Listener(传入的Listener)
     * Removes the first occurrence of the specified listener from this future.
     * The specified listener is no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listener is not associated with this future, this method
     * does nothing and returns silently.
     */
    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * Removes the first occurrence for each of the listeners from this future.
     * The specified listeners are no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listeners are not associated with this future, this method
     * does nothing and returns silently.
     */
    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * 等待 直到当前Future计算完成 或者 当前Future由于已经失败重新抛出异常。其实相当于get,换了个语法糖
     * 返回的是自己。
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     */
    Future<V> sync() throws InterruptedException;

    /**
     * 在等待计算完成之前，不响应中断(应该就是内部捕获中断，在返回之前恢复中断状态)
     *
     * 中断是针对线程的，即使任务可以处理中断，在返回之前也应该恢复线程的中断状态，使得线程的拥有者可以处理中断。
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     */
    Future<V> syncUninterruptibly();

    /**
     * 等待当前Future计算完成。
     * 有{@link #sync()}方法为啥还要await()方法呢？
     *
     * await()不会查询任务的结果,在任务完成之后就返回。而 sync()方法会在任务失败的情况下抛出异常。
     *
     * Waits for this future to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    Future<V> await() throws InterruptedException;

    /**
     * Waits for this future to be completed without
     * interruption.  This method catches an {@link InterruptedException} and
     * discards it silently.
     */
    Future<V> awaitUninterruptibly();

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Waits for this future to be completed within the
     * specified time limit without interruption.  This method catches an
     * {@link InterruptedException} and discards it silently.
     *
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeoutMillis);

    /**
     * 尝试非阻塞的获取结果，如果Future的计算没有完成的话，那么将会返回null。
     *
     * (一般是获取一下保护结果的锁，查询结果立即返回，或者是volatile变量。) Netty采用的是volatile。
     *
     * Return the result without blocking. If the future is not done yet this will return {@code null}.
     *
     * As it is possible that a {@code null} value is used to mark the future as successful you also need to check
     * if the future is really done with {@link #isDone()} and not relay on the returned {@code null} value.
     */
    V getNow();

    /**
     * {@inheritDoc}
     *
     * If the cancellation was successful it will fail the future with an {@link CancellationException}.
     */
    @Override
    boolean cancel(boolean mayInterruptIfRunning);
}
