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
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import java.nio.ByteOrder;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.serialization.ObjectDecoder;

/**
 * <h3>处理TCP拆包、粘包的重要处理器</h3>
 * {@link LengthFieldBasedFrameDecoder}通过消息中的长度字段的值动态拆分接收到的{@link ByteBuf}。
 * 它在你解码一个消息体中有一个整数header域表示消息内容的长度或消息的总长度的消息 时特别的有用。
 * <strong>decoder总是假设长度字段表示的是消息中它后面的字节数</strong>
 * <p>
 *
 * A decoder that splits the received {@link ByteBuf}s dynamically by the
 * value of the length field in the message.  It is particularly useful when you
 * decode a binary message which has an integer header field that represents the
 * length of the message body or the whole message.
 *
 * <p>
 * {@link LengthFieldBasedFrameDecoder} 有许多可配置的参数，因此它能够解码有长度字段的任意消息，
 * 这种消息在专有的客户端-服务器协议中很常见。这里有一些示例可以为您提供每个选项是做什么的基本概念。
 *
 * <p>
 * {@link LengthFieldBasedFrameDecoder} has many configuration parameters so
 * that it can decode any message with a length field, which is often seen in
 * proprietary client-server protocols. Here are some example that will give
 * you the basic idea on which option does what.
 *
 * <h3>偏移量为0，2个字节的长度字段，不跳过消息头</h3>
 * {@code LengthFieldBasedFrameDecoder(8192,0,2)}
 * <p>
 * 该示例中长度字段的值为 <tt>12 (0x0C)</tt>，它表示"HELLO, WORLD"的长度。
 * 默认情况下，decoder假设长度字段的表示的是长度字段后面的字节数。
 * 因此它可以通过简单的参数组合进行解码。
 *
 * <h3>2 bytes length field at offset 0, do not strip header</h3>
 *
 * The value of the length field in this example is <tt>12 (0x0C)</tt> which
 * represents the length of "HELLO, WORLD".  By default, the decoder assumes
 * that the length field represents the number of the bytes that follows the
 * length field.  Therefore, it can be decoded with the simplistic parameter
 * combination.
 * <pre>
 * <b>lengthFieldOffset</b>   = <b>0</b>
 * <b>lengthFieldLength</b>   = <b>2</b>
 * lengthAdjustment    = 0
 * initialBytesToStrip = 0 (= do not strip header)
 *
 * BEFORE DECODE (14 bytes)         AFTER DECODE (14 bytes)
 * +--------+----------------+      +--------+----------------+
 * | Length | Actual Content |----->| Length | Actual Content |
 * | 0x000C | "HELLO, WORLD" |      | 0x000C | "HELLO, WORLD" |
 * +--------+----------------+      +--------+----------------+
 * </pre>
 *
 * <h3>偏移量为0，长度字段两个字节，跳过消息头(最常用的)</h3>
 * {@code LengthFieldBasedFrameDecoder(8192,0,2,0,2)}
 * <p>
 * 因为我们可以通过调用{@link ByteBuf#readableBytes()}获取内容的长度。
 * 你可能想指定<tt>initialBytesToStrip</tt>跳过长度字段。在这个例子中，
 * 我们指定为2，和长度字段的长度相同，以跳过前两个字节(只将内容部分传递给下一个handler)。
 *
 * <h3>2 bytes length field at offset 0, strip header</h3>
 *
 * Because we can get the length of the content by calling
 * {@link ByteBuf#readableBytes()}, you might want to strip the length
 * field by specifying <tt>initialBytesToStrip</tt>.  In this example, we
 * specified <tt>2</tt>, that is same with the length of the length field, to
 * strip the first two bytes.
 * <pre>
 * lengthFieldOffset   = 0
 * lengthFieldLength   = 2
 * lengthAdjustment    = 0
 * <b>initialBytesToStrip</b> = <b>2</b> (= the length of the Length field)
 *
 * BEFORE DECODE (14 bytes)         AFTER DECODE (12 bytes)
 * +--------+----------------+      +----------------+
 * | Length | Actual Content |----->| Actual Content |
 * | 0x000C | "HELLO, WORLD" |      | "HELLO, WORLD" |
 * +--------+----------------+      +----------------+
 * </pre>
 *
 *
 * <h3>两个字节的长度字段，偏移量为0，不跳过消息头，长度字段表示整个消息的长度</h3>
 * {@code LengthFieldBasedFrameDecoder(8192,0,2,2,0)}
 * <p>
 * 在多数情况下，长度字段仅仅表示消息内容的长度，如上面的例子所示。然而，在某些协议中，
 * 长度字段表示的整个消息的长度，包括了消息的头。在这种情况下，我们指定一个非0的<tt>lengthAdjustment</tt>。
 * 在这个示例中，因为长度的值总是比消息内容部分(body)的长度大2，我们指定<tt>lengthAdjustment</tt>为<tt>-2</tt>
 * 以进行修正(补偿)。
 *
 * <h3>2 bytes length field at offset 0, do not strip header, the length field
 *     represents the length of the whole message</h3>
 *
 * In most cases, the length field represents the length of the message body
 * only, as shown in the previous examples.  However, in some protocols, the
 * length field represents the length of the whole message, including the
 * message header.  In such a case, we specify a non-zero
 * <tt>lengthAdjustment</tt>.  Because the length value in this example message
 * is always greater than the body length by <tt>2</tt>, we specify <tt>-2</tt>
 * as <tt>lengthAdjustment</tt> for compensation.
 * <pre>
 * lengthFieldOffset   =  0
 * lengthFieldLength   =  2
 * <b>lengthAdjustment</b>    = <b>-2</b> (= the length of the Length field)
 * initialBytesToStrip =  0
 *
 * BEFORE DECODE (14 bytes)         AFTER DECODE (14 bytes)
 * +--------+----------------+      +--------+----------------+
 * | Length | Actual Content |----->| Length | Actual Content |
 * | 0x000E | "HELLO, WORLD" |      | 0x000E | "HELLO, WORLD" |
 * +--------+----------------+      +--------+----------------+
 * </pre>
 *
 * <h3>5个字节的消息头，消息头的后三个字节未长度字段，不跳过消息头</h3>
 * {@code LengthFieldBasedFrameDecoder(8192,2,3,0,0)}
 * <p>
 * 下面的消息是第一个实例的简单变种。一个额外的header值添加到了消息头。
 * <tt>lengthAdjustment</tt>仍然为0，因为被添加到(长度字段)前面的数据在计算帧的长度时总是被考虑到的。
 *
 * <h3>3 bytes length field at the end of 5 bytes header, do not strip header</h3>
 *
 * The following message is a simple variation of the first example.  An extra
 * header value is prepended to the message.  <tt>lengthAdjustment</tt> is zero
 * again because the decoder always takes the length of the prepended data into
 * account during frame length calculation.
 * <pre>
 * <b>lengthFieldOffset</b>   = <b>2</b> (= the length of Header 1)
 * <b>lengthFieldLength</b>   = <b>3</b>
 * lengthAdjustment    = 0
 * initialBytesToStrip = 0
 *
 * BEFORE DECODE (17 bytes)                      AFTER DECODE (17 bytes)
 * +----------+----------+----------------+      +----------+----------+----------------+
 * | Header 1 |  Length  | Actual Content |----->| Header 1 |  Length  | Actual Content |
 * |  0xCAFE  | 0x00000C | "HELLO, WORLD" |      |  0xCAFE  | 0x00000C | "HELLO, WORLD" |
 * +----------+----------+----------------+      +----------+----------+----------------+
 * </pre>
 *
 * <h3> 5个字节的消息头，长度字段在消息头的前3个字节，不跳过消息头</h3>
 * {@code LengthFieldBasedFrameDecoder(8192,0,3,2,0)}
 * <p>
 * 这是一个高级示例，显示了在长度字段和消息正文之间有一个额外header的情况(有两个冗余字节，长度字段表示的消息正文的长度)。
 * 你必须指定一个正数的<tt>lengthAdjustment</tt>，这样decoder在计算帧长度时计算额外的header长度。
 *
 * <h3>3 bytes length field at the beginning of 5 bytes header, do not strip header</h3>
 *
 * This is an advanced example that shows the case where there is an extra
 * header between the length field and the message body.  You have to specify a
 * positive <tt>lengthAdjustment</tt> so that the decoder counts the extra
 * header into the frame length calculation.
 * <pre>
 * lengthFieldOffset   = 0
 * lengthFieldLength   = 3
 * <b>lengthAdjustment</b>    = <b>2</b> (= the length of Header 1)
 * initialBytesToStrip = 0
 *
 * BEFORE DECODE (17 bytes)                      AFTER DECODE (17 bytes)
 * +----------+----------+----------------+      +----------+----------+----------------+
 * |  Length  | Header 1 | Actual Content |----->|  Length  | Header 1 | Actual Content |
 * | 0x00000C |  0xCAFE  | "HELLO, WORLD" |      | 0x00000C |  0xCAFE  | "HELLO, WORLD" |
 * +----------+----------+----------------+      +----------+----------+----------------+
 * </pre>
 *
 * <h3>4个字节的消息头，长度字段在中间的两个字节，跳过消息头的第一个header和长度字段</h3>
 * {@code LengthFieldBasedFrameDecoder(8192,1,2,1,3)}
 * <p>
 * 这是所有上面示例的一个结合体。在长度字段前后都有额外的header。前面的header影响了<tt>lengthFieldOffset</tt>，
 * 后面的header影响<tt>lengthAdjustment</tt>。我们也指定了一个非零的<tt>initialBytesToStrip</tt>以跳过
 * 长度字段和它前面的header。如果你不想跳过前面的header，你可以指定<tt>initialBytesToSkip</tt>为0。
 *
 * <h3>2 bytes length field at offset 1 in the middle of 4 bytes header,
 *     strip the first header field and the length field</h3>
 *
 * This is a combination of all the examples above.  There are the prepended
 * header before the length field and the extra header after the length field.
 * The prepended header affects the <tt>lengthFieldOffset</tt> and the extra
 * header affects the <tt>lengthAdjustment</tt>.  We also specified a non-zero
 * <tt>initialBytesToStrip</tt> to strip the length field and the prepended
 * header from the frame.  If you don't want to strip the prepended header, you
 * could specify <tt>0</tt> for <tt>initialBytesToSkip</tt>.
 * <pre>
 * lengthFieldOffset   = 1 (= the length of HDR1)
 * lengthFieldLength   = 2
 * <b>lengthAdjustment</b>    = <b>1</b> (= the length of HDR2)
 * <b>initialBytesToStrip</b> = <b>3</b> (= the length of HDR1 + LEN)
 *
 * BEFORE DECODE (16 bytes)                       AFTER DECODE (13 bytes)
 * +------+--------+------+----------------+      +------+----------------+
 * | HDR1 | Length | HDR2 | Actual Content |----->| HDR2 | Actual Content |
 * | 0xCA | 0x000C | 0xFE | "HELLO, WORLD" |      | 0xFE | "HELLO, WORLD" |
 * +------+--------+------+----------------+      +------+----------------+
 * </pre>
 *
 * <h3>4个字节的消息头，长度字段在中间的两个字节，跳过消息头的第一个header和长度字段，并且长度字段表示消息的总长度</h3>
 * {@code LengthFieldBasedFrameDecoder(8192,1,2,-3,3)}。
 * 有点过于复杂了，自己定义协议的时候最好不要这么做。
 * <p>
 *
 * 让我们对前一个示例进行一次变化。与前一个示例的唯一区别是，长度字段表示整个消息的长度，而不是消息体的长度，
 * 就像第三个示例一样。我们必须将HDR1 + Length的和赋值给<tt>lengthAdjustment</tt>.
 * 清注意：我们并不需要将HDR2计入，因为长度字段已经包括了HDR2的长度
 *
 * <h3>2 bytes length field at offset 1 in the middle of 4 bytes header,
 *     strip the first header field and the length field, the length field
 *     represents the length of the whole message</h3>
 *
 * Let's give another twist to the previous example.  The only difference from
 * the previous example is that the length field represents the length of the
 * whole message instead of the message body, just like the third example.
 * We have to count the length of HDR1 and Length into <tt>lengthAdjustment</tt>.
 * Please note that we don't need to take the length of HDR2 into account
 * because the length field already includes the whole header length.
 * <pre>
 * lengthFieldOffset   =  1
 * lengthFieldLength   =  2
 * <b>lengthAdjustment</b>    = <b>-3</b> (= the length of HDR1 + LEN, negative)
 * <b>initialBytesToStrip</b> = <b> 3</b>
 *
 * BEFORE DECODE (16 bytes)                       AFTER DECODE (13 bytes)
 * +------+--------+------+----------------+      +------+----------------+
 * | HDR1 | Length | HDR2 | Actual Content |----->| HDR2 | Actual Content |
 * | 0xCA | 0x0010 | 0xFE | "HELLO, WORLD" |      | 0xFE | "HELLO, WORLD" |
 * +------+--------+------+----------------+      +------+----------------+
 * </pre>
 *
 * 再次强调：<strong>decoder总是假设长度字段表示的是消息中它后面的字节数</strong>
 * @see LengthFieldPrepender
 */
public class LengthFieldBasedFrameDecoder extends ByteToMessageDecoder {
    /**
     * 数据包的顺序(一般用大端序更直观-先看见的先存储，后看见的后存储，都当做字符串处理)
     */
    private final ByteOrder byteOrder;
    /**
     * 最大帧长度。超过该长度表示数据过大，需要被处理。会抛出异常通知下一个handler
     */
    private final int maxFrameLength;
    /**
     * 表示帧长度的字段在帧中起始偏移量(include)
     */
    private final int lengthFieldOffset;
    /**
     * 表示帧长度的字段的长度(int 4 ,long 8,short 2)
     */
    private final int lengthFieldLength;
    /**
     * 表示帧长度的字段在帧中结束偏移量(exclude)。
     * 它同时也表示了一个消息的最少字节数。
     * {@code lengthFieldEndOffset=lengthFieldOffset + lengthFieldLength}
     */
    private final int lengthFieldEndOffset;
    /**
     * 默认情况下，我们假设长度字段的值表示的是长度字段后面的字节数量。
     * 当长度字段表示的不是这部分的长度的时候，则需要对读取到的长度进行调整
     * 即:{@code realLength = bodyLength + lengthAdjustment}
     */
    private final int lengthAdjustment;
    /**
     * 跳过帧的前多少个字节
     */
    private final int initialBytesToStrip;
    /**
     * 是否快速失败，即解析消息出现过长帧时是否抛错异常，使解码失败。
     * <li>true表示检测到过长帧时立即通知一下一个handler出现过长帧异常；</li>
     * <li>false表示丢弃完过长帧以后才通知下一个handler出现过长帧异常</li>
     * 默认是true。
     */
    private final boolean failFast;

    // ----过大帧的状态信息
    /**
     * 当前是否过长的帧还未完全丢弃
     */
    private boolean discardingTooLongFrame;
    /**
     * 正在被丢弃的帧的长度
     */
    private long tooLongFrameLength;
    /**
     * 剩下的需要被丢弃的字节数
     */
    private long bytesToDiscard;

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength
     *        the maximum length of the frame.  If the length of the frame is
     *        greater than this value, {@link TooLongFrameException} will be
     *        thrown.
     * @param lengthFieldOffset
     *        the offset of the length field
     * @param lengthFieldLength
     *        the length of the length field
     */
    public LengthFieldBasedFrameDecoder(
            int maxFrameLength,
            int lengthFieldOffset, int lengthFieldLength) {
        this(maxFrameLength, lengthFieldOffset, lengthFieldLength, 0, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength
     *        the maximum length of the frame.  If the length of the frame is
     *        greater than this value, {@link TooLongFrameException} will be
     *        thrown.
     * @param lengthFieldOffset
     *        the offset of the length field
     * @param lengthFieldLength
     *        the length of the length field
     * @param lengthAdjustment
     *        the compensation value to add to the value of the length field
     * @param initialBytesToStrip
     *        the number of first bytes to strip out from the decoded frame
     */
    public LengthFieldBasedFrameDecoder(
            int maxFrameLength,
            int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip) {
        this(
                maxFrameLength,
                lengthFieldOffset, lengthFieldLength, lengthAdjustment,
                initialBytesToStrip, true);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength
     *        the maximum length of the frame.  If the length of the frame is
     *        greater than this value, {@link TooLongFrameException} will be
     *        thrown.
     * @param lengthFieldOffset
     *        the offset of the length field
     * @param lengthFieldLength
     *        the length of the length field
     * @param lengthAdjustment
     *        the compensation value to add to the value of the length field
     * @param initialBytesToStrip
     *        the number of first bytes to strip out from the decoded frame
     * @param failFast
     *        If <tt>true</tt>, a {@link TooLongFrameException} is thrown as
     *        soon as the decoder notices the length of the frame will exceed
     *        <tt>maxFrameLength</tt> regardless of whether the entire frame
     *        has been read.  If <tt>false</tt>, a {@link TooLongFrameException}
     *        is thrown after the entire frame that exceeds <tt>maxFrameLength</tt>
     *        has been read.
     */
    public LengthFieldBasedFrameDecoder(
            int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        this(
                ByteOrder.BIG_ENDIAN, maxFrameLength, lengthFieldOffset, lengthFieldLength,
                lengthAdjustment, initialBytesToStrip, failFast);
    }

    /**
     * Creates a new instance.
     *
     * @param byteOrder
     *        the {@link ByteOrder} of the length field
     * @param maxFrameLength
     *        the maximum length of the frame.  If the length of the frame is
     *        greater than this value, {@link TooLongFrameException} will be
     *        thrown.
     * @param lengthFieldOffset
     *        the offset of the length field
     * @param lengthFieldLength
     *        the length of the length field
     * @param lengthAdjustment
     *        the compensation value to add to the value of the length field
     * @param initialBytesToStrip
     *        the number of first bytes to strip out from the decoded frame
     * @param failFast
     *        If <tt>true</tt>, a {@link TooLongFrameException} is thrown as
     *        soon as the decoder notices the length of the frame will exceed
     *        <tt>maxFrameLength</tt> regardless of whether the entire frame
     *        has been read.  If <tt>false</tt>, a {@link TooLongFrameException}
     *        is thrown after the entire frame that exceeds <tt>maxFrameLength</tt>
     *        has been read.
     */
    public LengthFieldBasedFrameDecoder(
            ByteOrder byteOrder, int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        if (byteOrder == null) {
            throw new NullPointerException("byteOrder");
        }

        checkPositive(maxFrameLength, "maxFrameLength");

        checkPositiveOrZero(lengthFieldOffset, "lengthFieldOffset");

        checkPositiveOrZero(initialBytesToStrip, "initialBytesToStrip");

        if (lengthFieldOffset > maxFrameLength - lengthFieldLength) {
            throw new IllegalArgumentException(
                    "maxFrameLength (" + maxFrameLength + ") " +
                    "must be equal to or greater than " +
                    "lengthFieldOffset (" + lengthFieldOffset + ") + " +
                    "lengthFieldLength (" + lengthFieldLength + ").");
        }

        this.byteOrder = byteOrder;
        this.maxFrameLength = maxFrameLength;
        this.lengthFieldOffset = lengthFieldOffset;
        this.lengthFieldLength = lengthFieldLength;
        this.lengthAdjustment = lengthAdjustment;
        lengthFieldEndOffset = lengthFieldOffset + lengthFieldLength;
        this.initialBytesToStrip = initialBytesToStrip;
        this.failFast = failFast;
    }

    /**
     * 覆盖超类的decode方法，并实现为final。
     * 定义为模板方法，但是觉得这个模板方法定义的有点不好
     * @param ctx           the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in            the {@link ByteBuf} from which to read data
     * @param out           the {@link List} to which decoded messages should be added
     * @throws Exception
     */
    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    /**
     * 继续丢弃未完全丢弃的消息(过长的帧可能无法一次性完成丢弃)
     * @param in
     */
    private void discardingTooLongFrame(ByteBuf in) {
        // 剩余需要被丢弃的字节
        long bytesToDiscard = this.bytesToDiscard;
        // 取两者小很重要(当可读字节<=还需要被丢弃的字节时，可以完全丢弃，当可读字节大于还需要被丢弃的字节时，则可以完成丢弃)
        int localBytesToDiscard = (int) Math.min(bytesToDiscard, in.readableBytes());
        in.skipBytes(localBytesToDiscard);
        // 更新剩余要被丢弃的字节
        bytesToDiscard -= localBytesToDiscard;
        this.bytesToDiscard = bytesToDiscard;

        // 这里检查是否需要失败，检查是否已经丢弃完了在这里
        failIfNecessary(false);
    }

    private static void failOnNegativeLengthField(ByteBuf in, long frameLength, int lengthFieldEndOffset) {
        in.skipBytes(lengthFieldEndOffset);
        throw new CorruptedFrameException(
           "negative pre-adjustment length field: " + frameLength);
    }

    private static void failOnFrameLengthLessThanLengthFieldEndOffset(ByteBuf in,
                                                                      long frameLength,
                                                                      int lengthFieldEndOffset) {
        in.skipBytes(lengthFieldEndOffset);
        throw new CorruptedFrameException(
           "Adjusted frame length (" + frameLength + ") is less " +
              "than lengthFieldEndOffset: " + lengthFieldEndOffset);
    }

    /**
     * 处理超过最大帧长度的帧
     * @param in
     * @param frameLength
     */
    private void exceededFrameLength(ByteBuf in, long frameLength) {
        // 这时只读取了长度字段，帧内容不一定完全到达，本次可能无法丢弃
        long discard = frameLength - in.readableBytes();
        // 被丢弃的帧的长度
        tooLongFrameLength = frameLength;
        // 讲道理 = 0不应该可以丢弃吗？
        if (discard < 0) {
            // 这里buffer有比被丢弃的帧更多的字节，那么可以一次性的丢弃掉该帧
            // buffer contains more bytes then the frameLength so we can discard all now
            in.skipBytes((int) frameLength);
        } else {
            // 无法一次性丢弃，丢弃一部分，
            // Enter the discard mode and discard everything received so far.
            discardingTooLongFrame = true;
            bytesToDiscard = discard;
            in.skipBytes(in.readableBytes());
        }
        // 检查是否需要立即失败
        failIfNecessary(true);
    }

    private static void failOnFrameLengthLessThanInitialBytesToStrip(ByteBuf in,
                                                                     long frameLength,
                                                                     int initialBytesToStrip) {
        in.skipBytes((int) frameLength);
        throw new CorruptedFrameException(
           "Adjusted frame length (" + frameLength + ") is less " +
              "than initialBytesToStrip: " + initialBytesToStrip);
    }

    /**
     * 从输入的{@link ByteBuf}中创建一帧的数据，并且返回它。
     * (其实这里应该定义为模板方法)
     *
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param   ctx             the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param   in              the {@link ByteBuf} from which to read data
     * @return  frame           the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     *                          be created.
     */
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // 还有未完全丢弃的帧，则继续丢弃
        if (discardingTooLongFrame) {
            discardingTooLongFrame(in);
        }
        // 可读字节数不足，无法读取长度字段
        if (in.readableBytes() < lengthFieldEndOffset) {
            return null;
        }
        // ByteBuf读索引可能不能0，因此必须使用readerIndex。
        int actualLengthFieldOffset = in.readerIndex() + lengthFieldOffset;
        // 获取还未纠正的帧长度，也就是长度字段后面的字节数(content的长度)
        long frameLength = getUnadjustedFrameLength(in, actualLengthFieldOffset, lengthFieldLength, byteOrder);

        // 如果长度为负，失败
        if (frameLength < 0) {
            failOnNegativeLengthField(in, frameLength, lengthFieldEndOffset);
        }

        // frameLength + lengthFieldEndOffset
        // 等于我们预估的消息总长度 (前面的字节数(包含长度字段本身)+后面的字节(content的长度))
        // + lengthAdjustment
        // 等于真正的消息总长度。因为用户对长度字段的含义可能和我们假设的不一样，因此需要对估算消息的长度进行调整

        frameLength += lengthAdjustment + lengthFieldEndOffset;

        // 如果消息的长度小于最少消息字节数(长度字段之后没有内容时)
        if (frameLength < lengthFieldEndOffset) {
            failOnFrameLengthLessThanLengthFieldEndOffset(in, frameLength, lengthFieldEndOffset);
        }

        // 如果帧过长，则对过大帧进行处理，并且不解析
        if (frameLength > maxFrameLength) {
            exceededFrameLength(in, frameLength);
            return null;
        }

        // 到这里时，永远不会溢出，因为它小于maxFrameLength。
        // never overflows because it's less than maxFrameLength
        int frameLengthInt = (int) frameLength;
        // 可读字节数还不是完整的一帧，则进行等待
        if (in.readableBytes() < frameLengthInt) {
            return null;
        }
        // 如果跳过的字节数大于帧的真正长度，则抛出异常
        if (initialBytesToStrip > frameLengthInt) {
            failOnFrameLengthLessThanInitialBytesToStrip(in, frameLength, initialBytesToStrip);
        }
        // 跳过指定的字节数
        in.skipBytes(initialBytesToStrip);

        // 这里跳过了消息头后剩下的字节
        // extract frame
        int readerIndex = in.readerIndex();
        int actualFrameLength = frameLengthInt - initialBytesToStrip;
        // 给子类留下接口可自定义如果拆分帧内容
        ByteBuf frame = extractFrame(ctx, in, readerIndex, actualFrameLength);
        // 强制更新了buffer索引，表示该帧内容已解码
        in.readerIndex(readerIndex + actualFrameLength);
        return frame;
    }

    /**
     * 获取未被修正的消息长度，也就是直接从消息中读取到的值。默认的实现能够解码指定字段为一个无符号的整数。
     * 可以覆盖该方法以不同的方式解码被编码的长度字段。
     * 注意：该方法必须不能修改buffer的状态(如：{@code readerIndex}, {@code writerIndex}，和它里的内容)
     * <p>
     *
     * Decodes the specified region of the buffer into an unadjusted frame length.  The default implementation is
     * capable of decoding the specified region into an unsigned 8/16/24/32/64 bit integer.  Override this method to
     * decode the length field encoded differently.  Note that this method must not modify the state of the specified
     * buffer (e.g. {@code readerIndex}, {@code writerIndex}, and the content of the buffer.)
     *
     * @throws DecoderException if failed to decode the specified region
     */
    protected long getUnadjustedFrameLength(ByteBuf buf, int offset, int length, ByteOrder order) {
        buf = buf.order(order);
        long frameLength;
        switch (length) {
        case 1:
            frameLength = buf.getUnsignedByte(offset);
            break;
        case 2:
            frameLength = buf.getUnsignedShort(offset);
            break;
        case 3:
            frameLength = buf.getUnsignedMedium(offset);
            break;
        case 4:
            frameLength = buf.getUnsignedInt(offset);
            break;
        case 8:
            frameLength = buf.getLong(offset);
            break;
        default:
            throw new DecoderException(
                    "unsupported lengthFieldLength: " + lengthFieldLength + " (expected: 1, 2, 3, 4, or 8)");
        }
        return frameLength;
    }

    /**
     * 检查是否需要立即失败
     * @param firstDetectionOfTooLongFrame 是否是被丢弃的帧第一次调用
     */
    private void failIfNecessary(boolean firstDetectionOfTooLongFrame) {
        if (bytesToDiscard == 0) {
            // 已经丢弃完过大帧，丢弃完之后decoder恢复到初始状态，并通知下一个handler有一个过大帧
            // Reset to the initial state and tell the handlers that
            // the frame was too large.
            long tooLongFrameLength = this.tooLongFrameLength;
            this.tooLongFrameLength = 0;
            discardingTooLongFrame = false;
            // 如果不是快速失败(丢弃完才通知) 或者 第一次就丢弃完，则通知下一个handler出现了过大帧异常
            if (!failFast || firstDetectionOfTooLongFrame) {
                fail(tooLongFrameLength);
            }
        } else {
            // 还未完全丢弃该过长帧，则保持继续丢弃状态。
            // 如果快速失败(检测到过长帧就通知)并且是该过长帧(当前被丢弃的帧)第一次被检测，则通知下一个handler出现了过大帧异常
            // Keep discarding and notify handlers if necessary.
            if (failFast && firstDetectionOfTooLongFrame) {
                fail(tooLongFrameLength);
            }
        }
    }

    /**
     * Extract the sub-region of the specified buffer.
     * <p>
     * If you are sure that the frame and its content are not accessed after
     * the current {@link #decode(ChannelHandlerContext, ByteBuf)}
     * call returns, you can even avoid memory copy by returning the sliced
     * sub-region (i.e. <tt>return buffer.slice(index, length)</tt>).
     * It's often useful when you convert the extracted frame into an object.
     * Refer to the source code of {@link ObjectDecoder} to see how this method
     * is overridden to avoid memory copy.
     */
    protected ByteBuf extractFrame(ChannelHandlerContext ctx, ByteBuf buffer, int index, int length) {
        return buffer.retainedSlice(index, length);
    }

    private void fail(long frameLength) {
        if (frameLength > 0) {
            throw new TooLongFrameException(
                            "Adjusted frame length exceeds " + maxFrameLength +
                            ": " + frameLength + " - discarded");
        } else {
            throw new TooLongFrameException(
                            "Adjusted frame length exceeds " + maxFrameLength +
                            " - discarding");
        }
    }
}
