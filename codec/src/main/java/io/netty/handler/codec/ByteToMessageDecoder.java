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
package io.netty.handler.codec;

import static io.netty.util.internal.ObjectUtil.checkPositive;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.internal.StringUtil;

import java.util.List;

/**
 * {@link ByteToMessageDecoder}以一种流形式的方式从一个{@link ByteBuf}解码字节到另外一个消息类型。
 * (这里为什么是ByteBuf？因为Netty进行真正IO操作时使用的是ByteBuf)。
 * <b>这是一个很重要的解码器</b>
 *
 * <li>该类不是泛型的，是因为：从网络中读取消息时，可能读取到多个消息的帧，而这些消息的类型并不一定相同。</li>
 * <li>该类对应的编码器{@link MessageToByteEncoder}</li>
 * <p></p>
 *
 * {@link ChannelInboundHandlerAdapter} which decodes bytes in a stream-like fashion from one {@link ByteBuf} to an
 * other Message type.
 *
 * For example here is an implementation which reads all readable bytes from
 * the input {@link ByteBuf} and create a new {@link ByteBuf}.
 *
 * <pre>
 *     public class SquareDecoder extends {@link ByteToMessageDecoder} {
 *         {@code @Override}
 *         public void decode({@link ChannelHandlerContext} ctx, {@link ByteBuf} in, List&lt;Object&gt; out)
 *                 throws {@link Exception} {
 *             out.add(in.readBytes(in.readableBytes()));
 *         }
 *     }
 * </pre>
 *
 * <h3>帧检测</h3>
 * <p>
 * 在pipeline中，帧检测通常应该通过添加 {@link DelimiterBasedFrameDecoder}, {@link FixedLengthFrameDecoder},
 * {@link LengthFieldBasedFrameDecoder},或 {@link LineBasedFrameDecoder} 更早地处理。
 * <p>
 * 如果需要一个自定义的帧解码器，那么在实现{@link ByteToMessageDecoder}时需要小心。
 * 通过检查 {@link ByteBuf#readableBytes()}确保在buffer中有足够的字节构成一个完整的帧。如果没有足够的字节
 * 构成一个完整的帧，返回时不要修改readerIndex以允许更多的字节到达。
 * <p>
 * 为了检查完整的帧时不修改readerIndex，使用{@link ByteBuf#getInt(int)}这样的方法。
 * 当使用{@link ByteBuf#getInt(int)}类似的方法时<strong>必须</strong>必须使用readerIndex。
 * 比如，调用<tt>in.getInt(0)</tt>是假设帧从buffer的起始位置开始，但并不总是这样。
 * 请使用<tt>in.getInt(in.readerIndex())</tt>。
 *
 * <h3>Frame detection</h3>
 * <p>
 * Generally frame detection should be handled earlier in the pipeline by adding a
 * {@link DelimiterBasedFrameDecoder}, {@link FixedLengthFrameDecoder}, {@link LengthFieldBasedFrameDecoder},
 * or {@link LineBasedFrameDecoder}.
 * <p>
 * If a custom frame decoder is required, then one needs to be careful when implementing
 * one with {@link ByteToMessageDecoder}. Ensure there are enough bytes in the buffer for a
 * complete frame by checking {@link ByteBuf#readableBytes()}. If there are not enough bytes
 * for a complete frame, return without modifying the reader index to allow more bytes to arrive.
 * <p>
 * To check for complete frames without modifying the reader index, use methods like {@link ByteBuf#getInt(int)}.
 * One <strong>MUST</strong> use the reader index when using methods like {@link ByteBuf#getInt(int)}.
 * For example calling <tt>in.getInt(0)</tt> is assuming the frame starts at the beginning of the buffer, which
 * is not always the case. Use <tt>in.getInt(in.readerIndex())</tt> instead.
 *
 * <h3>陷阱</h3>
 * <p>
 * 请注意：{@link ByteToMessageDecoder}的子类<strong>一定不能</strong>使用{@link @Sharable}进行注解。
 * <p>
 * 部分方法如果返回的buffer没有被release或者被添加<tt>out</tt> {@link List}，将造成内存泄漏。比如{@link ByteBuf#readBytes(int)}。
 * 使用类似{@link ByteBuf#readSlice(int)}的派生buffer避免内存泄漏(使用衍生的Buffer不会阻止原Buffer的释放操作)。
 *
 * Some methods such as {@link ByteBuf#readBytes(int)} will cause a memory leak if the returned buffer
 * is not released or added to the <tt>out</tt> {@link List}. Use derived buffers like {@link ByteBuf#readSlice(int)}
 * to avoid leaking memory.
 *
 * <h3>Pitfalls</h3>
 * <p>
 * Be aware that sub-classes of {@link ByteToMessageDecoder} <strong>MUST NOT</strong>
 * annotated with {@link @Sharable}.
 * <p>
 * Some methods such as {@link ByteBuf#readBytes(int)} will cause a memory leak if the returned buffer
 * is not released or added to the <tt>out</tt> {@link List}. Use derived buffers like {@link ByteBuf#readSlice(int)}
 * to avoid leaking memory.
 */
public abstract class ByteToMessageDecoder extends ChannelInboundHandlerAdapter {

    /**
     * 使用内存复制的方法合并ByteBuf到一个新的ByteBuf中。
     * 它更加简单，但是存在内存复制，频繁的合并可能产生大量的垃圾。
     * Cumulate {@link ByteBuf}s by merge them into one {@link ByteBuf}'s, using memory copies.
     */
    public static final Cumulator MERGE_CUMULATOR = new Cumulator() {
        @Override
        public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
            try {
                final ByteBuf buffer;
                if (cumulation.writerIndex() > cumulation.maxCapacity() - in.readableBytes()
                    || cumulation.refCnt() > 1 || cumulation.isReadOnly()) {
                    // Expand cumulation (by replace it) when either there is not more room in the buffer
                    // or if the refCnt is greater then 1 which may happen when the user use slice().retain() or
                    // duplicate().retain() or if its read-only.
                    //
                    // See:
                    // - https://github.com/netty/netty/issues/2327
                    // - https://github.com/netty/netty/issues/1764
                    buffer = expandCumulation(alloc, cumulation, in.readableBytes());
                } else {
                    buffer = cumulation;
                }
                buffer.writeBytes(in);
                return buffer;
            } finally {
                // We must release in in all cases as otherwise it may produce a leak if writeBytes(...) throw
                // for whatever release (for example because of OutOfMemoryError)
                in.release();
            }
        }
    };

    /**
     * 使用{@link CompositeByteBuf}实现byteBuf之间的累积，只要有可能就不会产生内存复制。
     * 注意{@link CompositeByteBuf}使用多个复杂的索引实现，因此依赖你的使用情况，
     * 并且你的decoder实现使用该聚合器可能比使用{@link #MERGE_CUMULATOR}更慢。
     *
     * Cumulate {@link ByteBuf}s by add them to a {@link CompositeByteBuf} and so do no memory copy whenever possible.
     * Be aware that {@link CompositeByteBuf} use a more complex indexing implementation so depending on your use-case
     * and the decoder implementation this may be slower then just use the {@link #MERGE_CUMULATOR}.
     */
    public static final Cumulator COMPOSITE_CUMULATOR = new Cumulator() {
        @Override
        public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
            ByteBuf buffer;
            try {
                if (cumulation.refCnt() > 1) {
                    // Expand cumulation (by replace it) when the refCnt is greater then 1 which may happen when the
                    // user use slice().retain() or duplicate().retain().
                    //
                    // See:
                    // - https://github.com/netty/netty/issues/2327
                    // - https://github.com/netty/netty/issues/1764
                    buffer = expandCumulation(alloc, cumulation, in.readableBytes());
                    buffer.writeBytes(in);
                } else {
                    CompositeByteBuf composite;
                    if (cumulation instanceof CompositeByteBuf) {
                        composite = (CompositeByteBuf) cumulation;
                    } else {
                        composite = alloc.compositeBuffer(Integer.MAX_VALUE);
                        composite.addComponent(true, cumulation);
                    }
                    composite.addComponent(true, in);
                    in = null;
                    buffer = composite;
                }
                return buffer;
            } finally {
                if (in != null) {
                    // We must release if the ownership was not transferred as otherwise it may produce a leak if
                    // writeBytes(...) throw for whatever release (for example because of OutOfMemoryError).
                    in.release();
                }
            }
        }
    };

    private static final byte STATE_INIT = 0;
    private static final byte STATE_CALLING_CHILD_DECODE = 1;
    private static final byte STATE_HANDLER_REMOVED_PENDING = 2;

    /**
     * 累积的未完成读取的消息，表示所有的待解码的数据
     */
    ByteBuf cumulation;
    /**
     * 消息聚合器，将未读取的消息和新消息合并到一起，一共一个简单的ByteBuf视图。
     */
    private Cumulator cumulator = MERGE_CUMULATOR;
    /**
     * 是否在每次调用{@link #channelRead(ChannelHandlerContext, Object)}只解码一个消息。
     * 默认为false，因为存在性能影响。
     */
    private boolean singleDecode;
    private boolean decodeWasNull;
    /**
     * 是否是在{@link #cumulation}进行第一次读操作，意味着旧数据已读取完毕，新数据到来。
     */
    private boolean first;
    /**
     * A bitmask where the bits are defined as
     * <ul>
     *     <li>{@link #STATE_INIT}</li>
     *     <li>{@link #STATE_CALLING_CHILD_DECODE}</li>
     *     <li>{@link #STATE_HANDLER_REMOVED_PENDING}</li>
     * </ul>
     */
    private byte decodeState = STATE_INIT;
    /**
     * 在在{@link #cumulation}上执行多少次读操作之后，进行一次丢弃操作
     */
    private int discardAfterReads = 16;
    /**
     * 在{@link #cumulation}上进行了多少次读操作，读操作过多时，需要适当的释放内存，避免内存溢出
     */
    private int numReads;

    protected ByteToMessageDecoder() {
        ensureNotSharable();
    }

    /**
     * 设置是否每次decode只解码一个对象。
     * 如果您需要进行一些协议升级并希望确保没有任何混淆，这可能很有用。
     *
     * If set then only one message is decoded on each {@link #channelRead(ChannelHandlerContext, Object)}
     * call. This may be useful if you need to do some protocol upgrade and want to make sure nothing is mixed up.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public void setSingleDecode(boolean singleDecode) {
        this.singleDecode = singleDecode;
    }

    /**
     * 是否在每次调用{@link #channelRead(ChannelHandlerContext, Object)}只解码一个消息。
     * 默认为false，因为存在性能影响。
     *
     * If {@code true} then only one message is decoded on each
     * {@link #channelRead(ChannelHandlerContext, Object)} call.
     *
     * Default is {@code false} as this has performance impacts.
     */
    public boolean isSingleDecode() {
        return singleDecode;
    }

    /**
     * 设置ByteBuf的聚合器，按需使用。
     * 默认值为：{@link #MERGE_CUMULATOR}
     *
     * Set the {@link Cumulator} to use for cumulate the received {@link ByteBuf}s.
     */
    public void setCumulator(Cumulator cumulator) {
        if (cumulator == null) {
            throw new NullPointerException("cumulator");
        }
        this.cumulator = cumulator;
    }

    /**
     * Set the number of reads after which {@link ByteBuf#discardSomeReadBytes()} are called and so free up memory.
     * The default is {@code 16}.
     */
    public void setDiscardAfterReads(int discardAfterReads) {
        checkPositive(discardAfterReads, "discardAfterReads");
        this.discardAfterReads = discardAfterReads;
    }

    /**
     * Returns the actual number of readable bytes in the internal cumulative
     * buffer of this decoder. You usually do not need to rely on this value
     * to write a decoder. Use it only when you must use it at your own risk.
     * This method is a shortcut to {@link #internalBuffer() internalBuffer().readableBytes()}.
     */
    protected int actualReadableBytes() {
        return internalBuffer().readableBytes();
    }

    /**
     * 返回此解码器的内部累积ByteBuf。您通常不需要直接访问内部缓冲区来编写解码器。
     * 仅当你必须使用它时使用它，必须自己承担风险。
     *
     * Returns the internal cumulative buffer of this decoder. You usually
     * do not need to access the internal buffer directly to write a decoder.
     * Use it only when you must use it at your own risk.
     */
    protected ByteBuf internalBuffer() {
        if (cumulation != null) {
            return cumulation;
        } else {
            return Unpooled.EMPTY_BUFFER;
        }
    }

    /**
     * handler对应的context从{@link io.netty.channel.ChannelPipeline}中移除。
     * 主要完成清理工作。
     *
     * 这里实现为模板方法，父类先清理自身的状态，然后调用子类对应的方法。
     *
     * @param ctx 它所属的{@link ChannelHandlerContext}
     * @throws Exception
     */
    @Override
    public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (decodeState == STATE_CALLING_CHILD_DECODE) {
            decodeState = STATE_HANDLER_REMOVED_PENDING;
            return;
        }
        // 释放持有的byteBuf，重置状态
        ByteBuf buf = cumulation;
        if (buf != null) {
            // Directly set this to null so we are sure we not access it in any other method here anymore.
            cumulation = null;

            int readable = buf.readableBytes();
            if (readable > 0) {
                ByteBuf bytes = buf.readBytes(readable);
                buf.release();
                ctx.fireChannelRead(bytes);
            } else {
                buf.release();
            }

            numReads = 0;
            ctx.fireChannelReadComplete();
        }
        handlerRemoved0(ctx);
    }

    /**
     * 调用子类的逻辑。
     *
     * Gets called after the {@link ByteToMessageDecoder} was removed from the actual context and it doesn't handle
     * events anymore.
     */
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception { }

    /**
     * 这里进行解码的模板实现
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 该类只负责读取字节数组(ByteBuf)消息
        if (msg instanceof ByteBuf)
        {
            // 获取一个解码列表缓存对象
            CodecOutputList out = CodecOutputList.newInstance();
            try {
                ByteBuf data = (ByteBuf) msg;
                // 初始化读状态
                first = cumulation == null;
                // 是否是第一次读操作(旧数据已读完)
                if (first) {
                    cumulation = data;
                } else {
                    cumulation = cumulator.cumulate(ctx.alloc(), cumulation, data);
                }
                // 尝试解码操作
                callDecode(ctx, cumulation, out);
            } catch (DecoderException e) {
                throw e;
            } catch (Exception e) {
                throw new DecoderException(e);
            } finally {
                if (cumulation != null && !cumulation.isReadable()) {
                    // 累积的数据恰好读取完毕，重置所有相关状态标记
                    numReads = 0;
                    cumulation.release();
                    cumulation = null;
                } else if (++ numReads >= discardAfterReads) {
                    // 在cumulation进行了一定次数的读操作之后，需要丢弃掉已读数据，释放内存，避免内存溢出。
                    // We did enough reads already try to discard some bytes so we not risk to see a OOME.
                    // See https://github.com/netty/netty/issues/4275
                    numReads = 0;
                    discardSomeReadBytes();
                }

                int size = out.size();
                decodeWasNull = !out.insertSinceRecycled();
                fireChannelRead(ctx, out, size);
                out.recycle();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * 将读取到的消息传递给{@link io.netty.channel.ChannelPipeline}中的下一个handler
     *
     * Get {@code numElements} out of the {@link List} and forward these through the pipeline.
     */
    static void fireChannelRead(ChannelHandlerContext ctx, List<Object> msgs, int numElements) {
        if (msgs instanceof CodecOutputList) {
            fireChannelRead(ctx, (CodecOutputList) msgs, numElements);
        } else {
            for (int i = 0; i < numElements; i++) {
                ctx.fireChannelRead(msgs.get(i));
            }
        }
    }

    /**
     * 将读取到的消息传递给{@link io.netty.channel.ChannelPipeline}中的下一个handler
     *
     * Get {@code numElements} out of the {@link CodecOutputList} and forward these through the pipeline.
     */
    static void fireChannelRead(ChannelHandlerContext ctx, CodecOutputList msgs, int numElements) {
        for (int i = 0; i < numElements; i ++) {
            ctx.fireChannelRead(msgs.getUnsafe(i));
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        numReads = 0;
        discardSomeReadBytes();
        if (decodeWasNull) {
            decodeWasNull = false;
            if (!ctx.channel().config().isAutoRead()) {
                ctx.read();
            }
        }
        ctx.fireChannelReadComplete();
    }

    /**
     * 当前仅当refCnt == 1时，如果可能的话，丢弃一些字节以在ByteBuf中腾出更多的空间。
     * 如果refCnt != 1，则表示用户可能调用了slice().retain() or duplicate().retain()这些方法。
     */
    protected final void discardSomeReadBytes() {
        if (cumulation != null && !first && cumulation.refCnt() == 1) {
            // discard some bytes if possible to make more room in the
            // buffer but only if the refCnt == 1  as otherwise the user may have
            // used slice().retain() or duplicate().retain().
            //
            // See:
            // - https://github.com/netty/netty/issues/2327
            // - https://github.com/netty/netty/issues/1764
            cumulation.discardSomeReadBytes();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channelInputClosed(ctx, true);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof ChannelInputShutdownEvent) {
            // The decodeLast method is invoked when a channelInactive event is encountered.
            // This method is responsible for ending requests in some situations and must be called
            // when the input has been shutdown.
            channelInputClosed(ctx, false);
        }
        super.userEventTriggered(ctx, evt);
    }

    private void channelInputClosed(ChannelHandlerContext ctx, boolean callChannelInactive) throws Exception {
        CodecOutputList out = CodecOutputList.newInstance();
        try {
            channelInputClosed(ctx, out);
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            try {
                if (cumulation != null) {
                    cumulation.release();
                    cumulation = null;
                }
                int size = out.size();
                fireChannelRead(ctx, out, size);
                if (size > 0) {
                    // Something was read, call fireChannelReadComplete()
                    ctx.fireChannelReadComplete();
                }
                if (callChannelInactive) {
                    ctx.fireChannelInactive();
                }
            } finally {
                // Recycle in all cases
                out.recycle();
            }
        }
    }

    /**
     * Called when the input of the channel was closed which may be because it changed to inactive or because of
     * {@link ChannelInputShutdownEvent}.
     */
    void channelInputClosed(ChannelHandlerContext ctx, List<Object> out) throws Exception {
        if (cumulation != null) {
            callDecode(ctx, cumulation, out);
            decodeLast(ctx, cumulation, out);
        } else {
            decodeLast(ctx, Unpooled.EMPTY_BUFFER, out);
        }
    }

    /**
     * 该方法的含义是：每收到一个网络包则尝试解码操作
     * <p>
     * 一旦需要从{@link ByteBuf}中解码数据时进行调用。
     * 只要解码操作应该发生的时候该方法就会调用{@link #decode(ChannelHandlerContext, ByteBuf, List)}。
     * <p>
     * channelRead等方法没有直接调用decode方法，而是添加了一个中间方法，它的好处是：
     * 子类可以自己决定调用decode方法的时机
     * </p>
     *
     * Called once data should be decoded from the given {@link ByteBuf}. This method will call
     * {@link #decode(ChannelHandlerContext, ByteBuf, List)} as long as decoding should take place.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     *                      剩余的未完成解码的旧消息和新消息合并之后的ByteBuf，应该从该byteBuf中读取消息。
     * @param out           the {@link List} to which decoded messages should be added
     *                      解码后的消息应该放入该list
     */
    protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        try {
            while (in.isReadable()) {
                int outSize = out.size();

                // 有解码出的消息，传递给下一个handler处理
                if (outSize > 0) {
                    fireChannelRead(ctx, out, outSize);
                    out.clear();

                    // 检查该handler在继续解码之前是否移除了，如果已经移除，继续操作该buffer是不安全的。
                    // 因此brea跳出循环

                    // Check if this handler was removed before continuing with decoding.
                    // If it was removed, it is not safe to continue to operate on the buffer.
                    //
                    // See:
                    // - https://github.com/netty/netty/issues/4635
                    if (ctx.isRemoved()) {
                        break;
                    }
                    outSize = 0;
                }

                // 解码操作前的可读长度
                int oldInputLength = in.readableBytes();

                // 真正的进行读操作
                decodeRemovalReentryProtection(ctx, in, out);

                // Check if this handler was removed before continuing the loop.
                // If it was removed, it is not safe to continue to operate on the buffer.
                //
                // See https://github.com/netty/netty/issues/1664
                if (ctx.isRemoved()) {
                    break;
                }
                // 解码前后的消息个数未改变
                if (outSize == out.size()) {
                    if (oldInputLength == in.readableBytes()) {
                        // 解码前后的buffer的可读字节数也没改变。 两者都为true的情况下表示什么也没读到
                        break;
                    } else {
                        // 未能成功解码消息（或解码的消息未放入out），但是读取了部分数据，则继续
                        continue;
                    }
                }
                // 可以读取数据不放入out，但是不能为读取数据的情况下将对象放入out
                // 如果解码得到消息，但是buffer的可读字节未修改，证明解码操作有异常
                if (oldInputLength == in.readableBytes()) {
                    throw new DecoderException(
                            StringUtil.simpleClassName(getClass()) +
                                    ".decode() did not read anything but decoded a message.");
                }
                // 如果设置了每次只读取一个消息，则返回(默认false，存在性能问题)
                // 而且break之后没有将解码后的消息发送出去呢
                if (isSingleDecode()) {
                    break;
                }
            }
        } catch (DecoderException e) {
            throw e;
        } catch (Exception cause) {
            throw new DecoderException(cause);
        }
    }

    /**
     * 从{@link ByteBuf}中解码消息到其它类型。
     * 该方法将被调用直到 输入{@link ByteBuf}在该方法返回后没有数据了，或者直到没有从输入{@link ByteBuf}读取到任何消息。
     * <p>
     *
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read when return from this method or till nothing was read from the input
     * {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     *                      <p>解码时从该{@link ByteBuf}中读取数据，超类不会对它调用release操作；
     * @param out           the {@link List} to which decoded messages should be added
     *                      <p>解码的消息需要放入该{@link List}，放入out中的对象会传递给下一个handler。
     *                      也可以不放入out，自己决定发布的地方。
     * @throws Exception    is thrown if an error occurs
     */
    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception;

    /**
     * 从{@link ByteBuf}中解码消息到其它类型。
     * 该方法将被调用直到 输入{@link ByteBuf}在该方法返回后没有数据了，或者直到没有从输入{@link ByteBuf}读取到任何消息。
     * <p>
     *
     * Decode the from one {@link ByteBuf} to an other. This method will be called till either the input
     * {@link ByteBuf} has nothing to read when return from this method or till nothing was read from the input
     * {@link ByteBuf}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link List} to which decoded messages should be added
     * @throws Exception    is thrown if an error occurs
     */
    final void decodeRemovalReentryProtection(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
            throws Exception {
        decodeState = STATE_CALLING_CHILD_DECODE;
        try {
            decode(ctx, in, out);
        } finally {
            boolean removePending = decodeState == STATE_HANDLER_REMOVED_PENDING;
            decodeState = STATE_INIT;
            if (removePending) {
                handlerRemoved(ctx);
            }
        }
    }

    /**
     * Is called one last time when the {@link ChannelHandlerContext} goes in-active. Which means the
     * {@link #channelInactive(ChannelHandlerContext)} was triggered.
     *
     * By default this will just call {@link #decode(ChannelHandlerContext, ByteBuf, List)} but sub-classes may
     * override this for some special cleanup operation.
     */
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.isReadable()) {
            // Only call decode() if there is something left in the buffer to decode.
            // See https://github.com/netty/netty/issues/4386
            decodeRemovalReentryProtection(ctx, in, out);
        }
    }

    static ByteBuf expandCumulation(ByteBufAllocator alloc, ByteBuf cumulation, int readable) {
        ByteBuf oldCumulation = cumulation;
        cumulation = alloc.buffer(oldCumulation.readableBytes() + readable);
        cumulation.writeBytes(oldCumulation);
        oldCumulation.release();
        return cumulation;
    }

    /**
     * 累积器(合并器可能好理解一点)。用于累积{@link ByteBuf}中的数据
     * Cumulate {@link ByteBuf}s.
     */
    public interface Cumulator {
        /**
         * 将给定的两个{@link ByteBuf}合并到一起，返回的ByteBuf持有合并之后的字节。
         * 实现类需要负责正确的处理给定{@link ByteBuf}的生命周期，
         * 并且当{@link ByteBuf}完全消费之后调用{@link ByteBuf#release()}释放它。
         * <p>
         * Cumulate the given {@link ByteBuf}s and return the {@link ByteBuf} that holds the cumulated bytes.
         * The implementation is responsible to correctly handle the life-cycle of the given {@link ByteBuf}s and so
         * call {@link ByteBuf#release()} if a {@link ByteBuf} is fully consumed.
         *
         * @param alloc byte的分配器
         * @param cumulation 已合并的待处理的数据
         * @param in 待合并的数据
         */
        ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in);
    }
}
