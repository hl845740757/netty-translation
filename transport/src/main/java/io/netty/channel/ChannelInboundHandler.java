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

/**
 *
 * {@link ChannelInboundHandler} 是一个特殊的{@link ChannelHandler}，
 * 它添加了许多状态改变的回调方法。这允许用户轻松的挂钩到状态的改变上。
 *
 * channel的生命周期为：注册-->激活-->关闭-->取消注册
 * {@link #channelRegistered} --> {@link #channelActive} --> {@link #channelInactive} --> {@link #channelUnregistered}
 *
 * {@link ChannelHandler} which adds callbacks for state changes. This allows the user
 * to hook in to state changes easily.
 */
public interface ChannelInboundHandler extends ChannelHandler {

    /**
     * 当{@link ChannelHandlerContext}中的{@link Channel}注册到它的{@link EventLoop}线程的时候。
     *
     * The {@link Channel} of the {@link ChannelHandlerContext} was registered with its {@link EventLoop}
     */
    void channelRegistered(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当{@link ChannelHandlerContext}中的{@link Channel}从它的{@link EventLoop}线程移除的时候。
     *
     * The {@link Channel} of the {@link ChannelHandlerContext} was unregistered from its {@link EventLoop}
     */
    void channelUnregistered(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当{@link ChannelHandlerContext}中的{@link Channel}激活的时候。
     *
     * The {@link Channel} of the {@link ChannelHandlerContext} is now active
     */
    void channelActive(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当{@link ChannelHandlerContext}中的{@link Channel}关闭的时候。
     *
     * The {@link Channel} of the {@link ChannelHandlerContext} was registered is now inactive and reached its
     * end of lifetime.
     */
    void channelInactive(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当{@link Channel}从对等方读取到一个消息的时候调用
     * Invoked when the current {@link Channel} has read a message from the peer.
     */
    void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception;

    /**
     * 当当前读操作的最后一个消息被{@link #channelRead(ChannelHandlerContext, Object)}消费以后调用。
     * (本波消息读完时调用)
     * 如果{@link ChannelOption#AUTO_READ}(自动读)被关闭了。那么在{@link ChannelHandlerContext#read()}调用之前
     * 不会再尝试从当前{@link Channel}读取入站数据。
     *
     * Invoked when the last message read by the current read operation has been consumed by
     * {@link #channelRead(ChannelHandlerContext, Object)}.  If {@link ChannelOption#AUTO_READ} is off, no further
     * attempt to read an inbound data from the current {@link Channel} will be made until
     * {@link ChannelHandlerContext#read()} is called.
     */
    void channelReadComplete(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当一个用户事件触发的时候会被调用。
     * eg: IdleStateEvent
     *
     * Gets called if an user event was triggered.
     */
    void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception;

    /**
     * 当channel的可写状态发生改变的时候会被调用。你可以调用{@link Channel#isWritable()}检查当前的状态。
     *
     * Gets called once the writable state of a {@link Channel} changed. You can check the state with
     * {@link Channel#isWritable()}.
     */
    void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception;

    /**
     * 当一个异常被抛出的时候被调用
     * Gets called if a {@link Throwable} was thrown.
     */
    @Override
    @SuppressWarnings("deprecation")
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;
}
