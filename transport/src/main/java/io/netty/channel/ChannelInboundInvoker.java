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
package io.netty.channel;

/**
 * Channel入站事件调用者，它们调用channel的入站事件方法。
 * 即调用{@link ChannelInboundHandler}中的方法。
 * (入站事件传递)
 *
 * // {@link Channel}的{@link ChannelPipeline}包含的下一个{@link ChannelInboundHandler}
 *
 * // the next {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
 * // {@link Channel}
 */
public interface ChannelInboundInvoker {

    /**
     * 事件：一个{@link Channel}注册到了它的{@link EventLoop}.
     * 一个{@link Channel}会注册并且只会注册到一个{@link EventLoop}。<p>
     *
     * 这将导致{@link Channel}的{@link ChannelPipeline}包含的下一个{@link ChannelInboundHandler}的
     * {@link ChannelInboundHandler#channelRegistered(ChannelHandlerContext)}方法被调用。<p>
     *
     * A {@link Channel} was registered to its {@link EventLoop}.
     *
     * This will result in having the  {@link ChannelInboundHandler#channelRegistered(ChannelHandlerContext)} method
     * called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireChannelRegistered();

    /**
     * 一个{@link Channel}从它的 {@link EventLoop}上取消注册。
     * 这将导致{@link Channel}的{@link ChannelPipeline}包含的下一个{@link ChannelInboundHandler}的
     * {@link ChannelInboundHandler#channelUnregistered(ChannelHandlerContext)}方法被调用。<p>
     *
     * A {@link Channel} was unregistered from its {@link EventLoop}.
     *
     * This will result in having the  {@link ChannelInboundHandler#channelUnregistered(ChannelHandlerContext)} method
     * called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireChannelUnregistered();

    /**
     * 一个{@link Channel} 当前激活了，它意味着建立好了连接。<p></p>
     *
     * A {@link Channel} is active now, which means it is connected.
     *
     * This will result in having the  {@link ChannelInboundHandler#channelActive(ChannelHandlerContext)} method
     * called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireChannelActive();

    /**
     * 一个{@link Channel} 当前不再活动，它意味着channel关闭了(断开连接)。
     * A {@link Channel} is inactive now, which means it is closed。<p></p>
     *
     * This will result in having the  {@link ChannelInboundHandler#channelInactive(ChannelHandlerContext)} method
     * called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireChannelInactive();

    /**
     * 一个{@link Channel}在它的入站操作中接收了一个{@link Throwable}异常。<p></p>
     *
     * A {@link Channel} received an {@link Throwable} in one of its inbound operations.
     *
     * This will result in having the  {@link ChannelInboundHandler#exceptionCaught(ChannelHandlerContext, Throwable)}
     * method  called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireExceptionCaught(Throwable cause);

    /**
     * 一个{@link Channel}接收到了一个用户定义的事件。<p></p>
     *
     * A {@link Channel} received an user defined event.
     *
     * This will result in having the  {@link ChannelInboundHandler#userEventTriggered(ChannelHandlerContext, Object)}
     * method  called of the next  {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireUserEventTriggered(Object event);

    /**
     * 一个{@link Channel}接收到了一个信息。<p></p>
     *
     * A {@link Channel} received a message.
     *
     * This will result in having the {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)}
     * method  called of the next {@link ChannelInboundHandler} contained in the  {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelInboundInvoker fireChannelRead(Object msg);

    /**
     * {@link Channel}本次读操作读取完毕。<p></p>
     *
     * Triggers an {@link ChannelInboundHandler#channelReadComplete(ChannelHandlerContext)}
     * event to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     */
    ChannelInboundInvoker fireChannelReadComplete();

    /**
     * {@link Channel}的可写状态发生了改变。 <p></p>
     *
     * Triggers an {@link ChannelInboundHandler#channelWritabilityChanged(ChannelHandlerContext)}
     * event to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     */
    ChannelInboundInvoker fireChannelWritabilityChanged();
}
