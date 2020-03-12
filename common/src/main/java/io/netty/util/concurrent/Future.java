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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


/**
 * Future通常代表的是异步的、支持取消的、可获取执行结果的任务的凭证（Future是并发模式中的一种）。
 *
 * 1.简单的Future模式是没有回调的，如JDK的Future。无回调的Future简单安全。
 *
 * 2.Future模式也可以提供回调机制，提供回调的话，监听器的实现呢一定要保证线程安全。因为调用回调方法的线程通常不是我们的逻辑线程/业务线程。
 * 此外，由于添加了回调，如果某一个回调方法执行时间过长或阻塞，或抛出异常等都将威胁到系统的安全性(如：其它监听者丢失信号)，通知操作在实现上必须保证安全。
 *
 * 3.添加回调机制其实就是观察者模式，涉及到使用观察者模式和不使用观察者模式的优劣议题。不使用观察者模式，就需要使用轮询的方式
 * 去获取类似的功能，其缺点是：轮询间隔时间过长；则响应性低，若轮询时间过短，则消耗大量的CPU资源。
 *
 *  怎么设计以及使用都需要仔细斟酌,毕竟有得必有失。
 * (简单一般容易保证安全性，对开发者要求不高。便捷的方法往往潜在着危险，对开发者素质要求较高。)
 *
 * JDK的Future最大的问题就是不知道任务何时完成。{@link java.util.concurrent.CompletableFuture}接口又不是那么的好用（API过多）。
 * Netty对JDK的Future进行了扩展，添加了事件完成的回调机制。Netty也推荐大家使用回调机制监听计算的完成事件，此外还提供了一些方便开发者使用的接口。
 * Netty的监听器实现的不友好的一点是不能指定监听器运行的环境。 缺少这样的方法{@code addListener(FutureListener, Executor)}支持（需要自己进行封装）。
 *
 * Netty的Future实现了流式语法，方法返回{@link Future}的都是返回的this，并发组件实现这个还是有点不容易的，因为涉及大量的子类重写这些方法，
 * 一旦某个子类重写未按规则来，就GG了。
 *
 * The result of an asynchronous operation.
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
public interface Future<V> extends java.util.concurrent.Future<V> {

    /**
     * 查询future关联的操作是否顺利完成了。当且仅当Future关联的IO操作已成功完成时返回true。
     *
     * Returns {@code true} if and only if the I/O operation was completed
     * successfully.
     */
    boolean isSuccess();

    /**
     * 查询future关联的操作是否可以取消，当且仅当关联的操作可以通过{@link #cancel(boolean)}取消时返回true。
     *
     * returns {@code true} if and only if the operation can be cancelled via {@link #cancel(boolean)}.
     */
    boolean isCancellable();

    /**
     * 获取导致任务失败的原因。
     * 当future关联的任务被取消或由于异常进入完成状态后，该方法将返回操作失败的原因。
     *
     * Returns the cause of the failed I/O operation if the I/O operation has
     * failed.
     *
     * @return the cause of the failure.
     *         {@code null} if succeeded or this future is not completed yet.
     *
     *          失败的原因。{@link CancellationException} 或 {@link ExecutionException}。
     * 		    如果future关联的task已正常完成，则返回null。
     * 		    如果future关联的task还未进入完成状态，则返回null。
     */
    Throwable cause();

    /**
     * 添加一个监听者到当前Future。传入的特定的Listener将会在Future计算完成时{@link #isDone() true}被通知。
     * 如果当前Future已经计算完成，那么将立即被通知。
     * <p>
     * 就接口层面来说，并没有说明回调的执行时序和执行环境，因此listener需要自己管理线程安全问题。
     *
     * Adds the specified listener to this future.  The
     * specified listener is notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listener is notified immediately.
     */
    Future<V> addListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * 添加一组监听者到当前Future。传入的特定的Listener将会在Future计算完成时{@link #isDone() true}被通知。
     * 如果当前Future已经计算完成，那么将立即被通知。
     *
     * Adds the specified listeners to this future.  The
     * specified listeners are notified when this future is
     * {@linkplain #isDone() done}.  If this future is already
     * completed, the specified listeners are notified immediately.
     */
    Future<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * 移除监听器中第一个与指定Listener匹配的监听器，如果该Listener没有进行注册，那么什么也不会做。
     *
     * Removes the first occurrence of the specified listener from this future.
     * The specified listener is no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listener is not associated with this future, this method
     * does nothing and returns silently.
     */
    Future<V> removeListener(GenericFutureListener<? extends Future<? super V>> listener);

    /**
     * @see #removeListener(GenericFutureListener)
     *
     * Removes the first occurrence for each of the listeners from this future.
     * The specified listeners are no longer notified when this
     * future is {@linkplain #isDone() done}.  If the specified
     * listeners are not associated with this future, this method
     * does nothing and returns silently.
     */
    Future<V> removeListeners(GenericFutureListener<? extends Future<? super V>>... listeners);

    /**
     * 等待直到当前Future计算完成 或者 当前Future由于已经失败重新抛出异常。
     * 某些情况下必须等待操作已完成才能继续时，sync方法会很有用。
     *
     * 注意：sync方法没有声明任何异常(不算中断)，但是却可能抛出异常！sync的语义更贴近于等待任务完成，但是其实现会在任务失败后抛出异常，一不小心会着道的，
     * 更建议使用{@link #await()} 和 {@link #isSuccess()}进行处理。
     * (如果在线上要是出现异常，会让你蛋疼的)
     *
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     */
    Future<V> sync() throws InterruptedException;

    /**
     * 在等待计算完成之前，不响应中断。
     * 注意：syncUninterruptibly方法没有声明任何异常，但是却可能抛出异常！sync的语义更贴近于等待任务完成，但是其实现会在任务失败后抛出异常，一不小心会着道的，
     * 更建议使用{@link #await()} 和 {@link #isSuccess()}进行处理。
     *
     * 关于Uninterruptibly，一般实现都是：内部捕获中断，在返回之前恢复中断状态。
     * 中断是针对线程的，即使任务可以处理中断，在返回之前也应该恢复线程的中断状态，使得线程的拥有者可以处理中断。
     *
     * Waits for this future until it is done, and rethrows the cause of the failure if this future
     * failed.
     */
    Future<V> syncUninterruptibly();

    /**
     * 等待当前Future计算完成。
     * await()不会查询任务的结果，在Future进入完成状态之后就返回，方法返回后，接下来的{@link #isDone()}调用都将返回true。
     * sync()方法会在任务失败的情况下抛出异常。
     *
     * Waits for this future to be completed.
     *
     * @throws InterruptedException
     *         if the current thread was interrupted
     */
    Future<V> await() throws InterruptedException;

    /**
     * 等待当前Future计算完成，在等待期间不响应中断。
     * await()不会查询任务的结果，在Future进入完成状态之后就返回，方法返回后，接下来的{@link #isDone()}调用都将返回true。
     * sync()方法会在任务失败的情况下抛出异常。
     *
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
     * 这个方法的命名不好，其更确切的名字是{@code getNowIfSuccess}。
     *
     * 尝试非阻塞的获取当前结果，当前仅当任务正常完成时返回期望的结果，否则返回null，即：
     * 1. 如果future关联的task还未完成 {@link #isDone() false}，则返回null。
     * 2. 如果任务被取消或失败，则返回null。
     *
     * 注意：
     * 如果future关联的task没有返回值(操作完成返回null)，此时不能根据返回值做任何判断。对于这种情况，
     * 你可以使用{@link #isSuccess()},作为更好的选择。
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
