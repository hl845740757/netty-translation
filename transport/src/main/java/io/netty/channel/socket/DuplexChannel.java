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
package io.netty.channel.socket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;

import java.net.Socket;

/**
 * 双工{@link Channel}，有两个可以独立关闭的通道。
 *
 * A duplex {@link Channel} that has two sides that can be shutdown independently.
 */
public interface DuplexChannel extends Channel {

    /**
     * 当前仅当远端关闭了它的输出流之后，再也不能从该channel接收到数据时返回true。
     * 注意：这里的语义不同于{@link Socket#shutdownInput()} 和 {@link Socket#isInputShutdown()}
     *
     *
     * Returns {@code true} if and only if the remote peer shut down its output so that no more
     * data is received from this channel.  Note that the semantic of this method is different from
     * that of {@link Socket#shutdownInput()} and {@link Socket#isInputShutdown()}.
     */
    boolean isInputShutdown();

    /**
     * 关闭输入通道。
     *
     * @see Socket#shutdownInput()
     */
    ChannelFuture shutdownInput();

    /**
     * 关闭输入通道，并在完成时通知promise。
     *
     * Will shutdown the input and notify {@link ChannelPromise}.
     *
     * @see Socket#shutdownInput()
     */
    ChannelFuture shutdownInput(ChannelPromise promise);

    /**
     * 输出通道是否已经关闭
     *
     * @see Socket#isOutputShutdown()
     */
    boolean isOutputShutdown();

    /**
     * 关闭输出通道。
     *
     * @see Socket#shutdownOutput()
     */
    ChannelFuture shutdownOutput();

    /**
     * 关闭输出通道，并在关闭完成之后通知promise。
     *
     * Will shutdown the output and notify {@link ChannelPromise}.
     *
     * @see Socket#shutdownOutput()
     */
    ChannelFuture shutdownOutput(ChannelPromise promise);

    /**
     * 输入和输出通道是否都已经关闭。
     *
     * Determine if both the input and output of this channel have been shutdown.
     */
    boolean isShutdown();

    /**
     * 关闭输入和输出通道。
     * Will shutdown the input and output sides of this channel.
     * @return will be completed when both shutdown operations complete.
     */
    ChannelFuture shutdown();

    /**
     * 关闭输入和输出通道，并在关闭之后通知promise
     *
     * Will shutdown the input and output sides of this channel.
     * @param promise will be completed when both shutdown operations complete.
     * @return will be completed when both shutdown operations complete.
     */
    ChannelFuture shutdown(ChannelPromise promise);
}
