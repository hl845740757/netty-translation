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
package io.netty.channel.socket;

import io.netty.channel.ServerChannel;

import java.net.InetSocketAddress;

/**
 * 一个TCP/IP的{@link ServerChannel}，它会接收到来的TCP/IP连接。
 * A TCP/IP {@link ServerChannel} which accepts incoming TCP/IP connections.
 */
public interface ServerSocketChannel extends ServerChannel {
    @Override
    ServerSocketChannelConfig config();
    @Override
    InetSocketAddress localAddress();

    /**
     * 获取远程地址，因为它仅仅是监听的channel，因此没有远程地址才对
     * @return
     */
    @Override
    InetSocketAddress remoteAddress();
}
