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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AttributeMap;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


/**
 *
 * <p>
 * Channel是一个网络Socket的纽带，或者说是一个支持如读、写、连接、绑定(监听)IO操作的组件。。
 * </p>
 * A nexus to a network socket or a component which is capable of I/O
 * operations such as read, write, connect, and bind.
 *
 * <p>
 * Channel为用户提供了：
 * <ul>
 * <li>当前Channel的状态。如：是否处于打开状态，是否已连接</li>
 * <li>允许使用{@linkplain ChannelConfig}配置Channel的属性(如：接收缓冲区大小)</li>
 * <li>Channel支持IO操作，(如：读、写、连接、绑定(监听))</li>
 * <li>{@link ChannelPipeline} 处理与它关联的channel的所有IO事件和请求</li>
 * </ul>
 * </p>
 *
 * <p>
 * A channel provides a user:
 * <ul>
 * <li>the current state of the channel (e.g. is it open? is it connected?),</li>
 * <li>the {@linkplain ChannelConfig configuration parameters} of the channel (e.g. receive buffer size),</li>
 * <li>the I/O operations that the channel supports (e.g. read, write, connect, and bind), and</li>
 * <li>the {@link ChannelPipeline} which handles all I/O events and requests
 *     associated with the channel.</li>
 * </ul>
 *
 * <h3>所有的IO操作都是异步的</h3>
 * <p>
 * 所有的IO操作在Netty中都是异步的。它意味着任何IO操作都会立即返回，并且不保证请求的IO操作
 * 在方法调用之后已完成。替代方案是，你将会被返回一个{@link ChannelFuture}实例，当请求的
 * IO操作完成时(成功，失败，或取消)该{@link ChannelFuture}实例会通知你。。
 *
 * <h3>All I/O operations are asynchronous.</h3>
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which will notify you when the requested I/O
 * operation has succeeded, failed, or canceled.
 *
 * <h3>Channel是分级的/层次化的</h3>
 * <p>
 * 一个{@link Channel}可以拥有一个{@linkplain #parent() parent}，依赖于它的创建时机。
 * 例如，一个被{@link ServerSocketChannel}接收的{@link SocketChannel}，它的parent()方法
 * 将会返回{@link ServerSocketChannel}作为它的父亲(parent)
 * <p>
 * 分级结构的语义依赖于{@link Channel}所属的传输实现。例如，你可以写一个新的{@link Channel}实现，
 * 它创建的子Channel共享一个Socket链接。如<a href="http://beepcore.org/">BEEP</a> 和
 * <a href="http://en.wikipedia.org/wiki/Secure_Shell">SSH</a>所做的。
 *
 * <h3>Channels are hierarchical</h3>
 * <p>
 * A {@link Channel} can have a {@linkplain #parent() parent} depending on
 * how it was created.  For instance, a {@link SocketChannel}, that was accepted
 * by {@link ServerSocketChannel}, will return the {@link ServerSocketChannel}
 * as its parent on {@link #parent()}.
 *
 * <p>
 * The semantics of the hierarchical structure depends on the transport
 * implementation where the {@link Channel} belongs to.  For example, you could
 * write a new {@link Channel} implementation that creates the sub-channels that
 * share one socket connection, as <a href="http://beepcore.org/">BEEP</a> and
 * <a href="http://en.wikipedia.org/wiki/Secure_Shell">SSH</a> do.
 *
 * <h3>向下转型以访问特定的传输操作</h3>
 * <p>
 * 一些传输暴露(导出)额外的特定于传输的操作。将{@link Channel}向下转型为具体的子类型以调用这些操作。
 * 例如：在旧的IO数据包传输中，多播的加入/离开操作由{@link DatagramChannel}提供。
 *
 * <h3>Downcast to access transport-specific operations</h3>
 * <p>
 * Some transports exposes additional operations that is specific to the
 * transport.  Down-cast the {@link Channel} to sub-type to invoke such
 * operations.  For example, with the old I/O datagram transport, multicast
 * join / leave operations are provided by {@link DatagramChannel}.
 *
 * <h3>资源释放</h3>
 * <p>
 * 一旦你使用完{@link Channel}时，调用{@link #close()} 或 {@link #close(ChannelPromise)}方法去
 * 释放所有的资源是非常重要的。它保证了所有的资源以一种正确的方式释放所有的资源。比如：文件句柄。
 *
 * <h3>Release resources</h3>
 * <p>
 * It is important to call {@link #close()} or {@link #close(ChannelPromise)} to release all
 * resources once you are done with the {@link Channel}. This ensures all resources are
 * released in a proper way, i.e. filehandles.
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {

    /**
     * 返回{@link Channel}的全局唯一识别码{@link ChannelId}。
     * 每一个Channel有且仅有一个全局唯一的识别码，并且是不会变化的，由ChannelId表示
     * Returns the globally unique identifier of this {@link Channel}.
     */
    ChannelId id();

    /**
     * 该方法返回{@link Channel}注册到的{@link EventLoop}。
     * 每一个Channel会注册并且只会注册到一个EventLoop。
     * Return the {@link EventLoop} this {@link Channel} was registered to.
     */
    EventLoop eventLoop();

    /**
     * 返回该Channel的父亲(父节点)。
     *
     * Returns the parent of this channel.
     *
     * @return 如果该channel没有父亲的话返回{@code null}.
     *          the parent channel.
     *         {@code null} if this channel does not have a parent channel.
     *
     */
    Channel parent();

    /**
     * 返回该Channel的配置信息。ChannelConfig可对Channel进行配置
     *
     * Returns the configuration of this channel.
     */
    ChannelConfig config();

    /**
     * 如果该{@link Channel}处于打开状态并且稍后可能激活则返回{@code true}.
     *
     * Returns {@code true} if the {@link Channel} is open and may get active later
     */
    boolean isOpen();

    /**
     * 如果该{@link Channel}已经注册到它的{@link EventLoop}上时则返回{@code true}
     *
     * Returns {@code true} if the {@link Channel} is registered with an {@link EventLoop}.
     */
    boolean isRegistered();

    /**
     * 如果{@link Channel}已激活并且已连接则返回{@code true}。
     *
     * Return {@code true} if the {@link Channel} is active and so connected.
     */
    boolean isActive();

    /**
     * 返回描述{@link Channel}属性的{@link ChannelMetadata}。
     *
     * Return the {@link ChannelMetadata} of the {@link Channel} which describe the nature of the {@link Channel}.
     */
    ChannelMetadata metadata();

    /**
     * 返回该channel绑定到的本地地址。返回的{@link SocketAddress}执行向下转型为更多的
     * 具体子类型以获取更多的信息。如{@link InetSocketAddress}，
     *
     * Returns the local address where this channel is bound to.  The returned
     * {@link SocketAddress} is supposed to be down-cast into more concrete
     * type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * @return 返回当前Channel的本地地址，如果该channel未绑定则返回null。
     *          the local address of this channel.
     *         {@code null} if this channel is not bound.
     */
    SocketAddress localAddress();

    /**
     * 返回该channel连接到的远程地址。返回的{@link SocketAddress}执行向下转型为更多的
     * 具体子类型以获取更多的信息。如{@link InetSocketAddress}，
     *
     * Returns the remote address where this channel is connected to.  The
     * returned {@link SocketAddress} is supposed to be down-cast into more
     * concrete type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * @return 返回当前Channel的本地地址，如果该channel未绑定则返回null。
     *         如果当前channel未连接，但是可以从任意的远程接收信息(如：{@link DatagramChannel}),
     *         请使用{@link DatagramPacket#recipient()}方法去确定接收到的消息的来源，
     *         因此该方法也将返回null.
     *
     *         the remote address of this channel.
     *         {@code null} if this channel is not connected.
     *         If this channel is not connected but it can receive messages
     *         from arbitrary remote addresses (e.g. {@link DatagramChannel},
     *         use {@link DatagramPacket#recipient()} to determine
     *         the origination of the received message as this method will
     *         return {@code null}.
     */
    SocketAddress remoteAddress();

    /**
     * 返回的{@link ChannelFuture}将会在该channel关闭的时候被通知。
     * 该方法总是返回相同的实例！！！
     *
     * Returns the {@link ChannelFuture} which will be notified when this
     * channel is closed.  This method always returns the same future instance.
     */
    ChannelFuture closeFuture();

    /**
     * 当且仅当IO线程能立即执行请求的写操作时返回true。当该方法返回false时，任何的写操作请求都被
     * 排队，直到(它所属的)IO线程准备好执行排队的写操作时。
     *
     * Returns {@code true} if and only if the I/O thread will perform the
     * requested write operation immediately.  Any write requests made when
     * this method returns {@code false} are queued until the I/O thread is
     * ready to process the queued write requests.
     */
    boolean isWritable();

    /**
     * 在不可写之前还能写多少字节。
     * 获取在{@link #isWritable()}返回False之前可以写入多少字节。(疑问：Channel可写入还是线程可写入)
     * (返回的)数量总是非负的。如果{@link #isWritable()}返回false，则数量为0。
     *
     *
     * Get how many bytes can be written until {@link #isWritable()} returns {@code false}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code false} then 0.
     */
    long bytesBeforeUnwritable();

    /**
     * Get how many bytes must be drained from underlying buffers until {@link #isWritable()} returns {@code true}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code true} then 0.
     */
    long bytesBeforeWritable();

    /**
     * 返回一个仅内部使用的对象以提供一些非安全的操作。
     *
     * Returns an <em>internal-use-only</em> object that provides unsafe operations.
     */
    Unsafe unsafe();

    /**
     * 返回赋值给channel的 Pipeline。
     * 每一个Channel有且仅有一个{@link ChannelPipeline}，且不会改变。
     *
     * Return the assigned {@link ChannelPipeline}.
     */
    ChannelPipeline pipeline();

    /**
     * 返回赋值给该Channel的{@link ByteBufAllocator}，{@link ByteBufAllocator}一般用于分配{@link ByteBuf}。
     *
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     */
    ByteBufAllocator alloc();

    @Override
    Channel read();

    @Override
    Channel flush();

    /**
     * <em>Unsafe</em> operations that should <em>never</em> be called from user-code. These methods
     * are only provided to implement the actual transport, and must be invoked from an I/O thread except for the
     * following methods:
     * <ul>
     *   <li>{@link #localAddress()}</li>
     *   <li>{@link #remoteAddress()}</li>
     *   <li>{@link #closeForcibly()}</li>
     *   <li>{@link #register(EventLoop, ChannelPromise)}</li>
     *   <li>{@link #deregister(ChannelPromise)}</li>
     *   <li>{@link #voidPromise()}</li>
     * </ul>
     */
    interface Unsafe {

        /**
         * Return the assigned {@link RecvByteBufAllocator.Handle} which will be used to allocate {@link ByteBuf}'s when
         * receiving data.
         */
        RecvByteBufAllocator.Handle recvBufAllocHandle();

        /**
         * 返回Channel绑定到的本地{@link SocketAddress}。
         * 如果未绑定的话返回null
         *
         * Return the {@link SocketAddress} to which is bound local or
         * {@code null} if none.
         */
        SocketAddress localAddress();

        /**
         * 返回Channel连接到的远程{@link SocketAddress}。
         * 如果未连接的话返回null
         *
         * Return the {@link SocketAddress} to which is bound remote or
         * {@code null} if none is bound yet.
         */
        SocketAddress remoteAddress();

        /**
         * 将{@link ChannelPromise}中的{@link Channel}注册到{@link EventLoop}。
         * 并且在注册完成时通知{@link ChannelFuture}/{@link ChannelPromise}
         *
         * Register the {@link Channel} of the {@link ChannelPromise} and notify
         * the {@link ChannelFuture} once the registration was complete.
         */
        void register(EventLoop eventLoop, ChannelPromise promise);

        /**
         * 将{@link ChannelPromise}中的{@link Channel}绑定到本地{@link SocketAddress}。
         * 并且在操作完成时通知{@link ChannelFuture}/{@link ChannelPromise}
         *
         * Bind the {@link SocketAddress} to the {@link Channel} of the {@link ChannelPromise} and notify
         * it once its done.
         */
        void bind(SocketAddress localAddress, ChannelPromise promise);

        /**
         * 将{@link ChannelPromise}中的{@link Channel}连接到远端的{@link SocketAddress}。
         * 如果必须绑定到指定的本地{@link SocketAddress}，那么需要作为参数传入，其它情况传入null。
         *
         * 在连接完成一旦将会通知{@link ChannelFuture}/{@link ChannelPromise}。
         *
         * Connect the {@link Channel} of the given {@link ChannelFuture} with the given remote {@link SocketAddress}.
         * If a specific local {@link SocketAddress} should be used it need to be given as argument. Otherwise just
         * pass {@code null} to it.
         *
         * The {@link ChannelPromise} will get notified once the connect operation was complete.
         */
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

        /**
         * Disconnect the {@link Channel} of the {@link ChannelFuture} and notify the {@link ChannelPromise} once the
         * operation was complete.
         */
        void disconnect(ChannelPromise promise);

        /**
         * 关闭{@link ChannelPromise}中的{@link Channel}并且通知{@link ChannelPromise}IO操作已完成。
         * Close the {@link Channel} of the {@link ChannelPromise} and notify the {@link ChannelPromise} once the
         * operation was complete.
         */
        void close(ChannelPromise promise);

        /**
         * 立即关闭channel而不触发任何事件。可能只在channel注册操作失败的时候有用(注册到它的EventLoop线程)。
         *
         * Closes the {@link Channel} immediately without firing any events.  Probably only useful
         * when registration attempt failed.
         */
        void closeForcibly();

        /**
         * 将{@link ChannelPromise}中的{@link Channel}从{@link EventLoop}取消注册，并且通知{@link ChannelPromise}取消操作已完成。
         *
         * Deregister the {@link Channel} of the {@link ChannelPromise} from {@link EventLoop} and notify the
         * {@link ChannelPromise} once the operation was complete.
         */
        void deregister(ChannelPromise promise);

        /**
         * Schedules a read operation that fills the inbound buffer of the first {@link ChannelInboundHandler} in the
         * {@link ChannelPipeline}.  If there's already a pending read operation, this method does nothing.
         */
        void beginRead();

        /**
         * Schedules a write operation.
         */
        void write(Object msg, ChannelPromise promise);

        /**
         * Flush out all write operations scheduled via {@link #write(Object, ChannelPromise)}.
         */
        void flush();

        /**
         * Return a special ChannelPromise which can be reused and passed to the operations in {@link Unsafe}.
         * It will never be notified of a success or error and so is only a placeholder for operations
         * that take a {@link ChannelPromise} as argument but for which you not want to get notified.
         */
        ChannelPromise voidPromise();

        /**
         * Returns the {@link ChannelOutboundBuffer} of the {@link Channel} where the pending write requests are stored.
         */
        ChannelOutboundBuffer outboundBuffer();
    }
}
