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

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FutureListener;

import java.net.ConnectException;
import java.net.SocketAddress;

/**
 * Channel出站事件调用者。
 * (出站事件传播)
 * 也就是它调用{@link ChannelOutboundHandler}中的方法
 */
public interface ChannelOutboundInvoker {

    /**
     * 请求绑定到给定的{@link SocketAddress} (socket地址)上，并且一旦操作完成时通知返回的{@link ChannelFuture}。
     * (操作完成是指：要么成功，要么由于错误失败)
     *
     * 这将导致该{@link Channel}的{@link ChannelPipeline}包含的下一个{@link ChannelOutboundHandler}的
     * {@link ChannelOutboundHandler#bind(ChannelHandlerContext, SocketAddress, ChannelPromise)}方法被调用。
     *
     * Request to bind to the given {@link SocketAddress} and notify the {@link ChannelFuture} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#bind(ChannelHandlerContext, SocketAddress, ChannelPromise)} method
     * called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture bind(SocketAddress localAddress);

    /**
     * 请求连接到给定的{@link SocketAddress}(socket地址)，并且一旦操作完成时通知返回的{@link ChannelFuture}。
     * (操作完成是指：要么成功，要么由于错误失败)
     * <p>
     * 如果由于连接超时而失败，{@link ChannelFuture}的get()方法将会失败返回一个{@link ConnectTimeoutException}(连接超时异常)、
     * 如果由于连接被拒失败，get()方法将会返回{@link ConnectException}异常。
     * <p>
     *
     * Request to connect to the given {@link SocketAddress} and notify the {@link ChannelFuture} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * If the connection fails because of a connection timeout, the {@link ChannelFuture} will get failed with
     * a {@link ConnectTimeoutException}. If it fails because of connection refused a {@link ConnectException}
     * will be used.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, ChannelPromise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture connect(SocketAddress remoteAddress);

    /**
     * 请求连接到给定的{@link SocketAddress}，同时绑定到给定本地地址，并且一旦操作完成时通知返回的{@link ChannelFuture}。
     *
     * Request to connect to the given {@link SocketAddress} while bind to the localAddress and notify the
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, ChannelPromise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress);

    /**
     * 请求从远程对等端断开连接，并且一个操作完成时通知返回的 {@link ChannelFuture}。
     *
     * Request to disconnect from the remote peer and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#disconnect(ChannelHandlerContext, ChannelPromise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture disconnect();

    /**
     * 请求关闭{@link Channel}并且一旦操作完成时通知返回的 {@link ChannelFuture}。
     *
     * Request to close the {@link Channel} and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of an error.
     *
     * After it is closed it is not possible to reuse it again.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#close(ChannelHandlerContext, ChannelPromise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture close();

    /**
     * 请求从之前赋值的{@link EventExecutor}中取消注册，并且一旦操作完成时通知返回的{@link ChannelFuture}.
     *
     * Request to deregister from the previous assigned {@link EventExecutor} and notify the
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#deregister(ChannelHandlerContext, ChannelPromise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     */
    ChannelFuture deregister();

    /**
     * 注释参考{@link #bind(SocketAddress)},给定{@link ChannelPromise}将会被通知(其实返回的就是它)。
     * <p>
     *
     * Request to bind to the given {@link SocketAddress} and notify the {@link ChannelFuture} once the operation
     * completes, either because the operation was successful or because of an error.
     *
     * The given {@link ChannelPromise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#bind(ChannelHandlerContext, SocketAddress, ChannelPromise)} method
     * called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise);

    /**
     * 注释参考{@link #connect(SocketAddress)},给定{@link ChannelPromise}将会被通知(其实返回的就是它)。
     *
     * Request to connect to the given {@link SocketAddress} and notify the {@link ChannelFuture} once the operation
     * completes, either because the operation was successful or because of an error.
     *
     * The given {@link ChannelFuture} will be notified.
     *
     * <p>
     * If the connection fails because of a connection timeout, the {@link ChannelFuture} will get failed with
     * a {@link ConnectTimeoutException}. If it fails because of connection refused a {@link ConnectException}
     * will be used.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, ChannelPromise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise);

    /**
     * 注释参考{@link #connect(SocketAddress,SocketAddress)},给定{@link ChannelPromise}将会被通知(其实返回的就是它)。
     *
     * Request to connect to the given {@link SocketAddress} while bind to the localAddress and notify the
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     *
     * The given {@link ChannelPromise} will be notified and also returned.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, ChannelPromise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

    /**
     * 注释参考{@link #disconnect()},给定{@link ChannelPromise}将会被通知(其实返回的就是它)。
     *
     * Request to disconnect from the remote peer and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of an error.
     *
     * The given {@link ChannelPromise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#disconnect(ChannelHandlerContext, ChannelPromise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture disconnect(ChannelPromise promise);

    /**
     * 注释参考{@link #close()},给定{@link ChannelPromise}将会被通知(其实返回的就是它)。
     *
     * Request to close the {@link Channel} and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     *
     * After it is closed it is not possible to reuse it again.
     * The given {@link ChannelPromise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#close(ChannelHandlerContext, ChannelPromise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture close(ChannelPromise promise);

    /**
     * 注释参考{@link #deregister()},给定{@link ChannelPromise}将会被通知(其实返回的就是它)。
     *
     * Request to deregister from the previous assigned {@link EventExecutor} and notify the
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     *
     * The given {@link ChannelPromise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#deregister(ChannelHandlerContext, ChannelPromise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelFuture deregister(ChannelPromise promise);

    /**
     * 请求从{@link Channel}中读取数据到它的第一个inbound buffer，并且如果读取到了数据，则触发
     * {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)}事件，并且
     * 触发{@link ChannelInboundHandler#channelReadComplete(ChannelHandlerContext) channelReadComplete}事件，这样
     * handler能够决定继续读。吐过这里已经有一个挂起(等待)的读操作，该方法什么也不会做。
     *
     * Request to Read data from the {@link Channel} into the first inbound buffer, triggers an
     * {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)} event if data was
     * read, and triggers a
     * {@link ChannelInboundHandler#channelReadComplete(ChannelHandlerContext) channelReadComplete} event so the
     * handler can decide to continue reading.  If there's a pending read operation already, this method does nothing.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#read(ChannelHandlerContext)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelOutboundInvoker read();

    /**
     * 请求通过当前的 {@link ChannelHandlerContext} 通过{@link ChannelPipeline}写入一个消息。
     * 该方法并不好请求真正的flush操作，因此当你希望请求flush所有填充的数据进行真正的传输时，一定要调用一次{@link #flush()}方法.
     * 返回的{@link ChannelFuture}会在操作完成时(发送出去或失败)收到通知。
     *
     * Request to write a message via this {@link ChannelHandlerContext} through the {@link ChannelPipeline}.
     * This method will not request to actual flush, so be sure to call {@link #flush()}
     * once you want to request to flush all pending data to the actual transport.
     */
    ChannelFuture write(Object msg);

    /**
     * 注释参考{@link #write(Object)}。给定{@link ChannelPromise}将会被通知(其实返回的就是它)。
     *
     * Request to write a message via this {@link ChannelHandlerContext} through the {@link ChannelPipeline}.
     * This method will not request to actual flush, so be sure to call {@link #flush()}
     * once you want to request to flush all pending data to the actual transport.
     */
    ChannelFuture write(Object msg, ChannelPromise promise);

    /**
     * 通过该ChannelOutboundInvoker请求flush所有填充的消息(进行真正的传输)
     *
     * Request to flush all pending messages via this ChannelOutboundInvoker.
     */
    ChannelOutboundInvoker flush();

    /**
     * Shortcut for call {@link #write(Object, ChannelPromise)} and {@link #flush()}.
     */
    ChannelFuture writeAndFlush(Object msg, ChannelPromise promise);

    /**
     * Shortcut for call {@link #write(Object)} and {@link #flush()}.
     */
    ChannelFuture writeAndFlush(Object msg);

    /**
     * 返回一个新创建的{@link ChannelPromise}.用于接收IO操作通知。
     *
     * Return a new {@link ChannelPromise}.
     */
    ChannelPromise newPromise();

    /**
     * 返回一个新创建的{@link ChannelProgressivePromise}。
     * 
     * Return an new {@link ChannelProgressivePromise}
     */
    ChannelProgressivePromise newProgressivePromise();

    /**
     * 创建一个{@link ChannelFuture}，它标记它关联的操作早已成功。因此{@link ChannelFuture#isSuccess()}总是返回成功。
     * 所有添加到它的{@link FutureListener}将直接被通知，此外每一个阻塞方法的调用将非阻塞的立即返回。
     * 
     * Create a new {@link ChannelFuture} which is marked as succeeded already. So {@link ChannelFuture#isSuccess()}
     * will return {@code true}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    ChannelFuture newSucceededFuture();

    /**
     * 创建一个{@link ChannelFuture}，它标记它关联的操作早已失败。因此{@link ChannelFuture#isSuccess()}总是返回失败。
     * 所有添加到它的{@link FutureListener}将直接被通知，此外每一个阻塞方法的调用将非阻塞的立即返回。
     *
     * Create a new {@link ChannelFuture} which is marked as failed already. So {@link ChannelFuture#isSuccess()}
     * will return {@code false}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    ChannelFuture newFailedFuture(Throwable cause);

    /**
     * 返回一个特殊的ChannelPromise，它可以被不同的操作重用。
     * <p>
     * 它仅支持用于{@link ChannelOutboundInvoker#write(Object, ChannelPromise)}.
     * <p>
     * 请注意，返回的{@link ChannelPromise}将不支持大多数操作，并且只应在你想保存一个对象为每个写入操作分配时使用。
     * 如果操作失败，您将无法检测操作是否完成，因为在这种情况下实现将调用{@link ChannelPipeline #fireExceptionCaught（Throwable）}。
     * <p>
     *
     * Return a special ChannelPromise which can be reused for different operations.
     * <p>
     * It's only supported to use
     * it for {@link ChannelOutboundInvoker#write(Object, ChannelPromise)}.
     * </p>
     * <p>
     * Be aware that the returned {@link ChannelPromise} will not support most operations and should only be used
     * if you want to save an object allocation for every write operation. You will not be able to detect if the
     * operation  was complete, only if it failed as the implementation will call
     * {@link ChannelPipeline#fireExceptionCaught(Throwable)} in this case.
     * </p>
     * <strong>Be aware this is an expert feature and should be used with care!</strong>
     */
    ChannelPromise voidPromise();
}
