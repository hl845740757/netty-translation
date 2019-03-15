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

import io.netty.channel.socket.ServerSocketChannel;

/**
 * {@link ServerChannel}是一个{@link Channel}，
 * 它会尝试去接收到来的链接，并通过接收它们创建它的子连接(与客户端真正连接的channel)。
 * {@link ServerSocketChannel}是一个很好的例子。
 *
 * A {@link Channel} that accepts an incoming connection attempt and creates
 * its child {@link Channel}s by accepting them.  {@link ServerSocketChannel} is
 * a good example.
 */
public interface ServerChannel extends Channel {
    // 该接口没有方法，因此它是一个标记接口。表示它的实现应该遵循约定。
    // This is a tag interface.
}
