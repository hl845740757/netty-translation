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

import io.netty.buffer.ByteBufAllocator;
import io.netty.util.AbstractConstant;
import io.netty.util.ConstantPool;

import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * {@link ChannelOption}允许以一种类型安全的方式去配置{@link ChannelConfig}。
 * 支持的{@link ChannelOption}依赖于{@link ChannelConfig}的真正实现，并且可能依赖于它所属的
 * 传输(方式)的性质。
 *
 * ChannelOption本身不维护值，只用于约束值的类型。它作为key存取它对它的值。
 * <p>
 *
 * A {@link ChannelOption} allows to configure a {@link ChannelConfig} in a type-safe
 * way. Which {@link ChannelOption} is supported depends on the actual implementation
 * of {@link ChannelConfig} and may depend on the nature of the transport it belongs
 * to.
 *
 * @param <T> {@link ChannelOption}的有效值类型。
 *           the type of the value which is valid for the {@link ChannelOption}
 */
public class ChannelOption<T> extends AbstractConstant<ChannelOption<T>> {

    /**
     * ChannelOption的常量池
     */
    private static final ConstantPool<ChannelOption<Object>> pool = new ConstantPool<ChannelOption<Object>>() {
        @Override
        protected ChannelOption<Object> newConstant(int id, String name) {
            return new ChannelOption<Object>(id, name);
        }
    };

    /**
     * 返回指定名字的{@link ChannelOption}。
     * 是对{@link ConstantPool}的一个封装。
     *
     * Returns the {@link ChannelOption} of the specified name.
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(String name) {
        return (ChannelOption<T>) pool.valueOf(name);
    }

    /**
     * Shortcut of {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return (ChannelOption<T>) pool.valueOf(firstNameComponent, secondNameComponent);
    }

    /**
     * Returns {@code true} if a {@link ChannelOption} exists for the given {@code name}.
     */
    public static boolean exists(String name) {
        return pool.exists(name);
    }

    /**
     * Creates a new {@link ChannelOption} for the given {@code name} or fail with an
     * {@link IllegalArgumentException} if a {@link ChannelOption} for the given {@code name} exists.
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> newInstance(String name) {
        return (ChannelOption<T>) pool.newInstance(name);
    }

    /**
     * 用于绑定Channel的ByteBuf分配器。
     */
    public static final ChannelOption<ByteBufAllocator> ALLOCATOR = valueOf("ALLOCATOR");

    public static final ChannelOption<RecvByteBufAllocator> RCVBUF_ALLOCATOR = valueOf("RCVBUF_ALLOCATOR");
    public static final ChannelOption<MessageSizeEstimator> MESSAGE_SIZE_ESTIMATOR = valueOf("MESSAGE_SIZE_ESTIMATOR");

    public static final ChannelOption<Integer> CONNECT_TIMEOUT_MILLIS = valueOf("CONNECT_TIMEOUT_MILLIS");
    /**
     * @deprecated Use {@link MaxMessagesRecvByteBufAllocator}
     * and {@link MaxMessagesRecvByteBufAllocator#maxMessagesPerRead(int)}.
     */
    @Deprecated
    public static final ChannelOption<Integer> MAX_MESSAGES_PER_READ = valueOf("MAX_MESSAGES_PER_READ");
    public static final ChannelOption<Integer> WRITE_SPIN_COUNT = valueOf("WRITE_SPIN_COUNT");
    /**
     * @deprecated Use {@link #WRITE_BUFFER_WATER_MARK}
     */
    @Deprecated
    public static final ChannelOption<Integer> WRITE_BUFFER_HIGH_WATER_MARK = valueOf("WRITE_BUFFER_HIGH_WATER_MARK");
    /**
     * @deprecated Use {@link #WRITE_BUFFER_WATER_MARK}
     */
    @Deprecated
    public static final ChannelOption<Integer> WRITE_BUFFER_LOW_WATER_MARK = valueOf("WRITE_BUFFER_LOW_WATER_MARK");
    public static final ChannelOption<WriteBufferWaterMark> WRITE_BUFFER_WATER_MARK =
            valueOf("WRITE_BUFFER_WATER_MARK");

    public static final ChannelOption<Boolean> ALLOW_HALF_CLOSURE = valueOf("ALLOW_HALF_CLOSURE");
    public static final ChannelOption<Boolean> AUTO_READ = valueOf("AUTO_READ");

    /**
     * 如果为{@code true}则表示{@link Channel}会自动关闭，并在写入失败时立即关闭。
     *
     * If {@code true} then the {@link Channel} is closed automatically and immediately on write failure.
     * The default value is {@code true}.
     */
    public static final ChannelOption<Boolean> AUTO_CLOSE = valueOf("AUTO_CLOSE");

    // region SO —— Socket Option
    // 可参考 - https://blog.csdn.net/zero__007/article/details/51723434
    /**
     * {@link java.net.StandardSocketOptions#SO_BROADCAST}
     * 是否启用数据报广播,默认值为false(不启用)。
     *
     * 该选项特定于发送到ipv4广播地址的面向数据报的套接字。
     * 启用socket选项后，可以使用socket发送广播数据报。
     */
    public static final ChannelOption<Boolean> SO_BROADCAST = valueOf("SO_BROADCAST");
    /**
     * {@link java.net.StandardSocketOptions#SO_KEEPALIVE}
     * 是否启用TCP保活机制，默认值为false(不启用)。
     * 当启用该选项后，操作系统可以使用一个keep alive
     * 机制在连接处于空闲状态时定期探测连接的另一端。
     * Keep-Alive机制的确切语义依赖于系统，因此未明确。
     * (由于默认心跳间隔太长（2小时），因此一般不使用系统自带的保活机制，而是使用应用层自己的心跳机制)
     */
    public static final ChannelOption<Boolean> SO_KEEPALIVE = valueOf("SO_KEEPALIVE");
    /**
     * {@link java.net.StandardSocketOptions#SO_SNDBUF}
     * socket发送缓冲区的大小。
     * 此套接字选项的值是{@code Integer}，它是套接字发送缓冲区的大小（以字节为单位）。
     * 套接字发送缓冲区是网络实现使用的输出缓冲区。 对于大容量连接，可能需要增加。
     * 该选项的值是实现的缓冲区大小的一个<em>提示</em>值，实际大小可能不同。
     * 可以查询套接字选项以检索实际大小。
     *
     * 对于面向数据报的套接字，发送缓冲区的大小可能会限制套接字可能发送的数据报的大小。
     * 能否发送大于缓冲区大小的数据报依赖于操作系统。
     *
     * 初始值大小 以及 数值范围 以及 是否允许动态设置该值 依赖于操作系统。
     */
    public static final ChannelOption<Integer> SO_SNDBUF = valueOf("SO_SNDBUF");
    /**
     * {@link java.net.StandardSocketOptions#SO_RCVBUF}
     * socket接收缓冲区的大小。
     * 此套接字选项的值是{@code Integer}，它是套接字接收缓冲区的大小（以字节为单位）。
     * 套接字接收缓冲区是网络实现使用的输入缓冲区。 可能需要增加高容量连接或减少以限制可能的输入数据积压。
     * 该选项的值是实现的缓冲区大小的一个<em>提示</em>值，实际大小可能不同。
     *
     * 对于面向数据报的套接字，接收缓冲区的大小可能会限制可以接收的数据报的大小。
     * 是否可以接收大于缓冲区大小的数据报依赖于操作系统。
     * 增加套接字接收缓冲区对于数据报以比可处理的更快的速度到达突发的情况可能是重要的。
     *
     * 在面向流的套接字和TCP/IP协议的情况下，当向远程对等体通告TCP接收窗口的大小时，
     * 可以使用套接字接收缓冲区的大小（也就是接收缓冲区大小就是滑动窗口大小）。
     *
     * 初始值大小 以及 数值范围 以及 是否允许动态设置该值 依赖于操作系统。
     */
    public static final ChannelOption<Integer> SO_RCVBUF = valueOf("SO_RCVBUF");
    /**
     * {@link java.net.StandardSocketOptions#SO_REUSEADDR}
     * 是否启用地址重用。
     * 此套接字选项的确切语义是依赖于套接字的类型和操作系统。
     *
     * 对于面向流的套接字，当涉及该套接字地址的先前连接处于<em>TIME_WAIT</em>状态时，
     * 此套接字选项通常将确定套接字是否可以绑定到套接字地址。(常说的tcp四次挥手过程)
     *
     * 对于面向数据报的套接字，套接字选项用于允许多个程序绑定到同一地址。
     * 当套接字用于Internet协议（IP）多播时，应启用此选项。(UDP)
     *
     * 使用SO_REUSEADDR选项时有两点需要注意：
     *    1. 必须在调用bind方法之前使用setReuseAddress方法来打开SO_REUSEADDR选项。
     *    因此，要想使用SO_REUSEADDR选项，就不能通过Socket类的构造方法来绑定端口。
     *    2. 必须将绑定同一个端口的所有的Socket对象的SO_REUSEADDR选项都打开才能起作用
     */
    public static final ChannelOption<Boolean> SO_REUSEADDR = valueOf("SO_REUSEADDR");
    /**
     * {@link java.net.StandardSocketOptions#SO_LINGER}
     * 如果在关闭的时候有数据发送，等待的延迟时间（秒）。
     *
     * 在默认情况下，当调用close方法后，将立即返回；如果这时仍然有未被送出的数据包，那么这些数据包将被丢弃。
     * 如果将linger参数设为一个正整数n时（n的值最大是65，535），在调用close方法后，将最多被阻塞n秒。
     * 在这n秒内，系统将尽量将未送出的数据包发送出去；如果超过了n秒，如果还有未发送的数据包，这些数据包将全部被丢弃；
     * 而close方法会立即返回。如果将linger设为0，和关闭SO_LINGER选项的作用是一样的。
     *
     * 套接字选项的值是一个<em>提示</em>值。 实现可以忽略该值，或忽略特定值。
     */
    public static final ChannelOption<Integer> SO_LINGER = valueOf("SO_LINGER");
    /**
     * backlog，一个整数，表示可以在任何时间排队的挂起连接数。 操作系统通常会对此值设置上限。
     *
     * BACKLOG用于构造服务端套接字ServerSocket对象，标识当服务器请求处理线程全满时，
     * 用于临时存放已完成三次握手的请求的队列的最大长度。如果未设置或所设置的值小于1，Java将使用默认值50。
     *
     * - https://en.wikipedia.org/wiki/Berkeley_sockets#listen
     * - https://www.jianshu.com/p/e6f2036621f4
     */
    public static final ChannelOption<Integer> SO_BACKLOG = valueOf("SO_BACKLOG");
    /**
     * socket读操作的超时时间。（netty实现Oio好像是基于此）。
     *
     *  这个Socket选项用来设置读取数据超时。当输入流的read方法被阻塞时，如果设置timeout（timeout的单位是毫秒），
     *  那么系统在等待了timeout毫秒后会抛出一个InterruptedIOException例外。
     *  在抛出例外后，输入流并未关闭，可以继续通过read方法读取数据。
     *  如果将timeout设为0，就意味着read将会无限等待下去，直到服务端程序关闭这个Socket.这也是timeout的默认值。
     *  如下面的语句将读取数据超时设为30秒：socket1.setSoTimeout( 30 * 1000 );
     */
    public static final ChannelOption<Integer> SO_TIMEOUT = valueOf("SO_TIMEOUT");


    public static final ChannelOption<Integer> IP_TOS = valueOf("IP_TOS");
    public static final ChannelOption<InetAddress> IP_MULTICAST_ADDR = valueOf("IP_MULTICAST_ADDR");
    public static final ChannelOption<NetworkInterface> IP_MULTICAST_IF = valueOf("IP_MULTICAST_IF");
    public static final ChannelOption<Integer> IP_MULTICAST_TTL = valueOf("IP_MULTICAST_TTL");
    public static final ChannelOption<Boolean> IP_MULTICAST_LOOP_DISABLED = valueOf("IP_MULTICAST_LOOP_DISABLED");

    /**
     * {@link java.net.StandardSocketOptions#TCP_NODELAY}
     * 该选项用于启用/关闭Nagle算法。
     *
     * 套接字选项特定于使用TCP/IP协议的面向流的套接字。 TCP/IP使用称为<em>Nagle算法</em>
     * 来合并小的数据块以此提高网络效率。
     *
     * 默认值为false，也就是启用Nagle算法，也就是有延迟的。
     * 在游戏等要求及时响应的应用中最好关闭Nagle算法，也就是启用NoDelay.
     *
     *  在默认情况下，客户端向服务器发送数据时，会根据数据包的大小决定是否立即发送。
     *  当数据包中的数据很少时，如只有1个字节，而数据包的头却有几十个字节（IP头+TCP头）时，
     *  系统会在发送之前先将较小的包合并到较大的包后，一起将数据发送出去。在发送下一个数据包时，
     *  系统会等待服务器对前一个数据包的响应，当收到服务器的响应后，再发送下一个数据包，这就是所谓的Nagle算法；
     *  在默认情况下，Nagle算法是开启的。这种算法虽然可以有效地改善网络传输的效率，但对于网络速度比较慢，
     *  而且对实现性的要求比较高的情况下（如游戏、Telnet等），使用这种方式传输数据会使得客户端有明显的停顿现象。
     *  因此，最好的解决方案就是需要Nagle算法时就使用它，不需要时就关闭它。
     *  而使用setTcpToDelay正好可以满足这个需求。当使用setTcpNoDelay（true）将Nagle算法关闭后，
     *  客户端每发送一次数据，无论数据包的大小都会将这些数据发送出去。
     */
    public static final ChannelOption<Boolean> TCP_NODELAY = valueOf("TCP_NODELAY");

    // endregion

    @Deprecated
    public static final ChannelOption<Boolean> DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION =
            valueOf("DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION");

    public static final ChannelOption<Boolean> SINGLE_EVENTEXECUTOR_PER_GROUP =
            valueOf("SINGLE_EVENTEXECUTOR_PER_GROUP");

    /**
     * Creates a new {@link ChannelOption} with the specified unique {@code name}.
     */
    private ChannelOption(int id, String name) {
        super(id, name);
    }

    @Deprecated
    protected ChannelOption(String name) {
        this(pool.nextId(), name);
    }

    /**
     * Validate the value which is set for the {@link ChannelOption}. Sub-classes
     * may override this for special checks.
     */
    public void validate(T value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
    }
}
