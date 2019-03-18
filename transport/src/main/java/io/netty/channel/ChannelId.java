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

import java.io.Serializable;

/**
 * 表示 {@link Channel}的一个全球唯一的识别码
 *
 * Represents the globally unique identifier of a {@link Channel}.
 *
 * 识别码的通过以下几种来源生成：
 * <li>Mac地址(48位或64位) 或者 网络适配器，优选的全球唯一的源</li>
 * <li>当前进程ID</li>
 * <li>当前系统毫秒时间</li>
 * <li>当前系统纳秒时间</li>
 * <li>一个随机的32位整数</li>
 * <li>和一个顺序递增的32位整数</li>
 *
 * <p>
 * The identifier is generated from various sources listed in the following:
 * <ul>
 * <li>MAC address (EUI-48 or EUI-64) or the network adapter, preferably a globally unique one,</li>
 * <li>the current process ID,</li>
 * <li>{@link System#currentTimeMillis()},</li>
 * <li>{@link System#nanoTime()},</li>
 * <li>a random 32-bit integer, and</li>
 * <li>a sequentially incremented 32-bit integer.</li>
 * </ul>
 * </p>
 * <p>
 *
 * 生成全局唯一的识别码主要依赖于 MAC地址 和 当前进程IP。 它们会尽可能的在类加载的时候主动监测。
 * 如果所有请求它们(MAC地址和进程ID)的尝试都失败了，那么将会打一个警告日志，然后会用随机数替代。
 * 另外，你可以通过系统属性手动的指定它们。{@code io.netty.machineId}  + {@code io.netty.processId}
 *
 * The global uniqueness of the generated identifier mostly depends on the MAC address and the current process ID,
 * which are auto-detected at the class-loading time in best-effort manner.  If all attempts to acquire them fail,
 * a warning message is logged, and random values will be used instead.  Alternatively, you can specify them manually
 * via system properties:
 * <ul>
 * <li>{@code io.netty.machineId} - hexadecimal representation of 48 (or 64) bit integer,
 *     optionally separated by colon or hyphen.</li>
 * <li>{@code io.netty.processId} - an integer between 0 and 65535</li>
 * </ul>
 * </p>
 */
public interface ChannelId extends Serializable, Comparable<ChannelId> {
    /**
     * 返回一个短的但是不一定全局唯一的字符串表示{@link ChannelId}
     * Returns the short but globally non-unique string representation of the {@link ChannelId}.
     */
    String asShortText();

    /**
     * 返回一个足够长的全区唯一的字符串表示 {@link ChannelId}
     *
     * Returns the long yet globally unique string representation of the {@link ChannelId}.
     */
    String asLongText();
}
