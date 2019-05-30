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
package io.netty.channel;

import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;

/**
 * {@link SimpleChannelInboundHandler} 允许你明确的只处理特定类型的消息。
 * {@link TypeParameterMatcher} 才是真正的核心。可以扩展到各个地方。在我的项目中，对它进行了扩展。
 *
 * {@link ChannelInboundHandlerAdapter} which allows to explicit only handle a specific type of messages.
 *
 * For example here is an implementation which only handle {@link String} messages.
 *
 * <pre>
 *     public class StringHandler extends
 *             {@link SimpleChannelInboundHandler}&lt;{@link String}&gt; {
 *
 *         {@code @Override}
 *         protected void channelRead0({@link ChannelHandlerContext} ctx, {@link String} message)
 *                 throws {@link Exception} {
 *             System.out.println(message);
 *         }
 *     }
 * </pre>
 *
 * 注意，将调用{@link ReferenceCountUtil#release(Object)}释放所有处理完的消息依赖于构造方法的参数。
 * 在这种情况下，如果你希望传递消息到{@link ChannelPipeline}中的下一个handler，你可以使用
 * {@link ReferenceCountUtil#retain(Object)}。
 * <p>
 *
 * Be aware that depending of the constructor parameters it will release all handled messages by passing them to
 * {@link ReferenceCountUtil#release(Object)}. In this case you may need to use
 * {@link ReferenceCountUtil#retain(Object)} if you pass the object to the next handler in the {@link ChannelPipeline}.
 *
 * <h3>未来的兼容性通知</h3>
 * <p>
 * 请注意，{@link #channelRead0(ChannelHandlerContext, I)}将会在下一个版本 5.0中重命名为
 * {@code messageReceived(ChannelHandlerContext, I)}
 * <p>
 *
 * <h3>Forward compatibility notice</h3>
 * <p>
 * Please keep in mind that {@link #channelRead0(ChannelHandlerContext, I)} will be renamed to
 * {@code messageReceived(ChannelHandlerContext, I)} in 5.0.
 * </p>
 */
public abstract class SimpleChannelInboundHandler<I> extends ChannelInboundHandlerAdapter {
    /**
     * 类型参数匹配器
     */
    private final TypeParameterMatcher matcher;
    /**
     * 是否字段是否资源
     */
    private final boolean autoRelease;

    /**
     * 默认自动释放资源
     * see {@link #SimpleChannelInboundHandler(boolean)} with {@code true} as boolean parameter.
     */
    protected SimpleChannelInboundHandler() {
        this(true);
    }

    /**
     * Create a new instance which will try to detect the types to match out of the type parameter of the class.
     *
     * @param autoRelease   {@code true} if handled messages should be released automatically by passing them to
     *                      {@link ReferenceCountUtil#release(Object)}.
     */
    protected SimpleChannelInboundHandler(boolean autoRelease) {
        matcher = TypeParameterMatcher.find(this, SimpleChannelInboundHandler.class, "I");
        this.autoRelease = autoRelease;
    }

    /**
     * see {@link #SimpleChannelInboundHandler(Class, boolean)} with {@code true} as boolean value.
     */
    protected SimpleChannelInboundHandler(Class<? extends I> inboundMessageType) {
        this(inboundMessageType, true);
    }

    /**
     * Create a new instance
     *
     * @param inboundMessageType    The type of messages to match
     * @param autoRelease           {@code true} if handled messages should be released automatically by passing them to
     *                              {@link ReferenceCountUtil#release(Object)}.
     */
    protected SimpleChannelInboundHandler(Class<? extends I> inboundMessageType, boolean autoRelease) {
        matcher = TypeParameterMatcher.get(inboundMessageType);
        this.autoRelease = autoRelease;
    }

    /**
     * 如果给定的消息应该被处理，则返回true。如果消息需要被传递到{@link ChannelPipeline}中的下一个
     * {@link ChannelInboundHandler}则返回false。
     */
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return matcher.match(msg);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean release = true;
        try {
            if (acceptInboundMessage(msg)) {
                // 如果消息可以被接收
                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                channelRead0(ctx, imsg);
            } else {
                // 数据不能被处理，传递给下一个handler(context)
                release = false;
                ctx.fireChannelRead(msg);
            }
        } finally {
            // 如果设置了自动释放，并且消息已被处理，则尝试释放资源
            if (autoRelease && release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    /**
     * 真正执行读操作的地方。
     * （当上一个handler发布的数据是我期待的数据类型的时候）
     *
     * <strong>Please keep in mind that this method will be renamed to
     * {@code messageReceived(ChannelHandlerContext, I)} in 5.0.</strong>
     *
     * Is called for each message of type {@link I}.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this {@link SimpleChannelInboundHandler}
     *                      belongs to
     * @param msg           the message to handle
     * @throws Exception    is thrown if an error occurred
     */
    protected abstract void channelRead0(ChannelHandlerContext ctx, I msg) throws Exception;
}
