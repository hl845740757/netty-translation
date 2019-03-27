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
package io.netty.channel;

import io.netty.util.concurrent.EventExecutorGroup;

/**
 * EventLoopGroup是允许在事件循环期间(during event loop) 注册channel 以便能够在接下来的
 * select操作得到处理的特殊EventExecutorGroup。
 *
 * 说人话就是：
 * 1.允许在事件循环期间动态的注册channel
 * 2.这些新注册的channel会在下一次selection操作的时候生效。(selector 的某一种select操作)
 *
 * {@link EventLoopGroup} 实现{@link EventExecutorGroup}，使其更加具体化。
 * {@link EventExecutorGroup}是{@link io.netty.util.concurrent.EventExecutor}的容器，
 * 而{@link EventLoopGroup} 是 {@link EventLoop}的容器。并定义了{@link Channel}的注册接口。
 *
 * Special {@link EventExecutorGroup} which allows registering {@link Channel}s that get
 * processed for later selection during the event loop.
 *
 */
public interface EventLoopGroup extends EventExecutorGroup {
    /**
     * EventLoopGroup是EventLoop的组，获取下一个事件循环。
     *
     * Return the next {@link EventLoop} to use
     */
    @Override
    EventLoop next();

    /**
     * 在事件循环期间注册一个{@link Channel}到{@link EventLoop}对象，该方法返回一个{@link ChannelFuture},
     * 返回的ChannelFuture会在注册完成时收到通知。
     *
     * 这就是接口定义的最重要的方法。
     * Register a {@link Channel} with this {@link EventLoop}. The returned {@link ChannelFuture}
     * will get notified once the registration was complete.
     */
    ChannelFuture register(Channel channel);

    /**
     * 使用{@link ChannelFuture}注册一个channel到{@link EventLoop}。该{@link ChannelFuture}将会被返回，
     * 并且ChannelFuture会在注册完成时收到通知。
     * {@link ChannelPromise} 是 {@link ChannelFuture}的子类
     *
     * Register a {@link Channel} with this {@link EventLoop} using a {@link ChannelFuture}. The passed
     * {@link ChannelFuture} will get notified once the registration was complete and also will get returned.
     */
    ChannelFuture register(ChannelPromise promise);

    /**
     * 已过时了，不翻译。
     * 觉得过时的主要原因是 {@link ChannelPromise} 和 {@link ChannelFuture} 都包含了指定的Channel,多传入反而容易造成错误。
     * Register a {@link Channel} with this {@link EventLoop}. The passed {@link ChannelFuture}
     * will get notified once the registration was complete and also will get returned.
     *
     * @deprecated Use {@link #register(ChannelPromise)} instead.
     */
    @Deprecated
    ChannelFuture register(Channel channel, ChannelPromise promise);
}
