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
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import io.netty.util.concurrent.EventExecutor;

import java.nio.channels.Channels;

// Channel ChannelPipeline ChannelHandler channelHandlerContext

/**
 * <p>
 * {@link ChannelHandlerContext}使得{@link ChannelHandler}能够给和{@link ChannelPipeline}中的其它 handler交互。
 * 除此之外，{@link ChannelHandler}可以通知{@link ChannelPipeline}中的下一个handler，以及动态的修改它所属的{@link ChannelPipeline}。
 * (另一个大作用就是存储状态信息)
 * </p>
 *
 * Enables a {@link ChannelHandler} to interact with its {@link ChannelPipeline}
 * and other handlers. Among other things a handler can notify the next {@link ChannelHandler} in the
 * {@link ChannelPipeline} as well as modify the {@link ChannelPipeline} it belongs to dynamically.
 *
 * <h3>通知</h3>
 * 你可以通过调用提供的多个方法中的一个通知相同{@link ChannelPipeline}中最近的handler。
 * 请查阅{@link ChannelPipeline}以理解事件是如何流动的。
 *
 * <h3>Notify</h3>
 *
 * You can notify the closest handler in the same {@link ChannelPipeline} by calling one of the various methods
 * provided here.
 *
 * Please refer to {@link ChannelPipeline} to understand how an event flows.
 *
 * <h3>修改Pipeline</h3>
 * 你可以调用{@link #pipeline()}方法获取你的handler所属的{@link ChannelPipeline}。
 * 一个不平凡(特殊)的应用可以在运行时动态地insert, remove, replace pipeline中的handler.
 *
 *  <h3>Modifying a pipeline</h3>
 *
 * You can get the {@link ChannelPipeline} your handler belongs to by calling
 * {@link #pipeline()}.  A non-trivial application could insert, remove, or
 * replace handlers in the pipeline dynamically at runtime.
 *
 * <h3>取回以供稍后使用</h3>
 * 你可以持有{@link ChannelHandlerContext}供稍后使用，如：触发一个handler提供的方法之外的事件，
 * 甚至可以在不同的线程中。
 *
 * <h3>Retrieving for later use</h3>
 *
 * You can keep the {@link ChannelHandlerContext} for later use, such as
 * triggering an event outside the handler methods, even from a different thread.
 * <pre>
 * public class MyHandler extends {@link ChannelDuplexHandler} {
 *
 *     <b>private {@link ChannelHandlerContext} ctx;</b>
 *
 *     public void beforeAdd({@link ChannelHandlerContext} ctx) {
 *         <b>this.ctx = ctx;</b>
 *     }
 *
 *     public void login(String username, password) {
 *         ctx.write(new LoginMessage(username, password));
 *     }
 *     ...
 * }
 * </pre>
 *
 * <h3>存储状态信息</h3>
 * {@link #attr(AttributeKey)}允许你存储和访问和handler和它的context有关系的状态信息。
 * 请查阅{@link ChannelHandler}了解多种管理状态信息的推荐方法。
 *
 * <h3>Storing stateful information</h3>
 *
 * {@link #attr(AttributeKey)} allow you to
 * store and access stateful information that is related with a handler and its
 * context.  Please refer to {@link ChannelHandler} to learn various recommended
 * ways to manage stateful information.
 *
 * <h3>一个Handler可以有多个Context</h3>
 * <li><b>其本质是：handler可以共享，而context不会被共享</b></li>
 * <li><b>每添加一次就会有一个Context，不论添加到相同的pipeline还是不同的pipeline.</b></li>
 *
 * 请注意，一个{@link ChannelHandler}实例可以添加到多个{@link ChannelPipeline}(初始化的时候用的同一个实例，而不是new的)。
 * 它意味着一个{@link ChannelHandler}实例可以有多个{@link ChannelHandlerContext}，
 * 因此如果它多次添加到{@link ChannelPipeline}中，单个handler实例可能会被多个{@link ChannelHandlerContext}调用。
 * <p>
 *
 * <h3>A handler can have more than one context</h3>
 *
 * Please note that a {@link ChannelHandler} instance can be added to more than
 * one {@link ChannelPipeline}.  It means a single {@link ChannelHandler}
 * instance can have more than one {@link ChannelHandlerContext} and therefore
 * the single instance can be invoked with different
 * {@link ChannelHandlerContext}s if it is added to one or more
 * {@link ChannelPipeline}s more than once.
 *
 * <p>
 * 例如下面的handler将会有和它添加到pipeline相同次数的独立的{@link AttributeKey}。无论它是添加到
 * 相同的pipeline多次还是添加到不同的pipeline中多次。
 *
 * <p>
 * For example, the following handler will have as many independent {@link AttributeKey}s
 * as how many times it is added to pipelines, regardless if it is added to the
 * same pipeline multiple times or added to different pipelines multiple times:
 * <pre>
 * public class FactorialHandler extends {@link ChannelInboundHandlerAdapter} {
 *
 *   private final {@link AttributeKey}&lt;{@link Integer}&gt; counter = {@link AttributeKey}.valueOf("counter");
 *
 *   // This handler will receive a sequence of increasing integers starting
 *   // from 1.
 *   {@code @Override}
 *   public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     Integer a = ctx.attr(counter).get();
 *
 *     if (a == null) {
 *       a = 1;
 *     }
 *
 *     attr.set(a * (Integer) msg);
 *   }
 * }
 *
 * <p>
 *
 * // 即使他们引用相同的handler实例，仍然为"f1", "f2", "f3", 和 "f4"分配了不同的context对象。
 * // (下面的对例子的解释看反而会看迷糊，可以跳过)
 * // 因为FactorialHandler在它的context对象中存储了它的状态。一旦两个pipeline(p1和p2)被激活的时候，
 * // 可以正确的计算4次阶乘(它们是独立的)。
 * </p>
 *
 * // Different context objects are given to "f1", "f2", "f3", and "f4" even if
 * // they refer to the same handler instance.  Because the FactorialHandler
 * // stores its state in a context object (using an {@link AttributeKey}), the factorial is
 * // calculated correctly 4 times once the two pipelines (p1 and p2) are active.
 * FactorialHandler fh = new FactorialHandler();
 *
 * {@link ChannelPipeline} p1 = {@link Channels}.pipeline();
 * p1.addLast("f1", fh);
 * p1.addLast("f2", fh);
 *
 * {@link ChannelPipeline} p2 = {@link Channels}.pipeline();
 * p2.addLast("f3", fh);
 * p2.addLast("f4", fh);
 * </pre>
 *
 * <h3>值得一读的其它资源</h3>
 * <p>
 * 请参阅@link channelhandler和@link channelpipeline，以了解有关入站和出站操作的更多信息，
 * 它们有哪些重要的区别，它们在管道中的流动方式，以及如何在你的应用程序中处理操作。
 * </p>
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 */
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {

    /**
     * 返回{@link ChannelHandlerContext}绑定到的{@link Channel}对象。
     *
     * Return the {@link Channel} which is bound to the {@link ChannelHandlerContext}.
     */
    Channel channel();

    /**
     * 返回channel绑定到的{@link EventExecutor}，用于执行一个任意的任务。
     *
     * Returns the {@link EventExecutor} which is used to execute an arbitrary task.
     */
    EventExecutor executor();

    /**
     * 返回{@link ChannelHandlerContext}的唯一名字。
     * 该名字用于{@link ChannelHandler}添加到{@link ChannelPipeline}。
     * 该名字也可以用于从{@link ChannelPipeline}中访问注册的{@link ChannelHandler}。
     * (添加handler的时候的名字，可以看做handler的名字，也可以看做handlerContext的名字)。
     *
     * The unique name of the {@link ChannelHandlerContext}.The name was used when then {@link ChannelHandler}
     * was added to the {@link ChannelPipeline}. This name can also be used to access the registered
     * {@link ChannelHandler} from the {@link ChannelPipeline}.
     */
    String name();

    /**
     * 返回{@link ChannelHandlerContext}持有的{@link ChannelHandler}对象。
     * (一个context有且仅有一个handler，一个handler可以有多个context，handler可以共享，而context不会被共享)
     *
     * The {@link ChannelHandler} that is bound this {@link ChannelHandlerContext}.
     */
    ChannelHandler handler();

    /**
     * 如果ChannelContext持有的{@link ChannelHandler}已经从它的{@link ChannelPipeline}中移除，则返回true。
     * 注意：该方法仅允许在它关联的{@link EventLoop}中调用
     *
     * Return {@code true} if the {@link ChannelHandler} which belongs to this context was removed
     * from the {@link ChannelPipeline}. Note that this method is only meant to be called from with in the
     * {@link EventLoop}.
     */
    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    /**
     * 返回该{@link ChannelHandlerContext} 所属的{@link ChannelPipeline}
     *
     * Return the assigned {@link ChannelPipeline}
     */
    ChannelPipeline pipeline();

    /**
     * 返回该{@link ChannelHandlerContext} 所属的{@link Channel}的{@link ByteBufAllocator}。
     * 用于分配{@link ByteBuf}。
     *
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     */
    ByteBufAllocator alloc();

    /**
     * 不再被使用，请使用{@link Channel#attr(AttributeKey)}代替。
     *
     * 禁用理由：
     * Channel和ChannelHandlerContext都实现了接口AttributeMap，
     * 使用户能够将一个或多个用户定义的属性附加到它们。
     * 有时让用户感到困惑的是Channel和ChannelHandlerContext都有自己的用户定义属性存储。
     * 例如，即使您通过Channel.attr(KEY_X).set(valueX)放置属性“KEY_X”，
     * 但你也永远不能通过ChannelHandlerContext.attr(KEY_X).get( )得到它，反之亦然。
     * 这种行为不仅令人困惑，而且浪费内存。
     *
     * 为了解决这个问题，我们决定在内部每个Channel只保留一个Map。
     * AttributeMap始终使用AttributeKey作为其键。
     * AttributeKey确保每个键之间的唯一性，因此每个通道不能有多个属性映射。
     * 只要用户将自己的AttributeKey定义为其ChannelHandler的私有静态最终字段，就不会有重复键的风险。
     *
     * - https://netty.io/wiki/new-and-noteworthy-in-4.1.html
     *
     * @deprecated Use {@link Channel#attr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * 同样的禁用理由：
     * - https://netty.io/wiki/new-and-noteworthy-in-4.1.html
     *
     * @deprecated Use {@link Channel#hasAttr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}
