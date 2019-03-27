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

import java.util.EventListener;

/**
 *
 * 通用Future监听器。
 *
 * 监听{@link Future}的计算结果。
 * 一旦listener通过{@link Future#addListener(GenericFutureListener)}添加到ChannelFuture上，
 * 那么异步操作的结果将会被通知。
 *
 * 这里是 观察者模式(发布/订阅模式) 的一种运用。
 * 一般来说，异步操作的回调都是另一个线程执行，需要处理线程安全问题。
 *
 * Listens to the result of a {@link Future}.  The result of the asynchronous operation is notified once this listener
 * is added by calling {@link Future#addListener(GenericFutureListener)}.
 */
public interface GenericFutureListener<F extends Future<?>> extends EventListener {

    /**
     * 当关联的{@link Future}计算完成时，该方法将会被调用。
     * 如果回调方法阻塞或者过于耗时，将会影响到其它监听者。
     *
     * Invoked when the operation associated with the {@link Future} has been completed.
     *
     * @param future  the source {@link Future} which called this callback
     */
    void operationComplete(F future) throws Exception;
}
