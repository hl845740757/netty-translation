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
package io.netty.buffer;

/**
 * 实现类应该能响应分配Buffer需求。
 * 实现类应该为线程安全的。
 *
 * Implementations are responsible to allocate buffers. Implementations of this interface are expected to be
 * thread-safe.
 */
public interface ByteBufAllocator {

    ByteBufAllocator DEFAULT = ByteBufUtil.DEFAULT_ALLOCATOR;

    /**
     * 分配一个{@link ByteBuf}，可能是直接内存buffer 或 堆内存buffer，取决于具体的实现。
     * @see #buffer(int, int) 使用默认的初始容量和最大容量
     *
     * Allocate a {@link ByteBuf}. If it is a direct or heap buffer
     * depends on the actual implementation.
     */
    ByteBuf buffer();

    /**
     * 分配一个{@link ByteBuf}，可能是直接内存buffer 或 堆内存buffer，取决于具体的实现。
     * @param initialCapacity 指定初始容量
     * @see #buffer(int, int) 使用默认的最大容量
     *
     * Allocate a {@link ByteBuf} with the given initial capacity.
     * If it is a direct or heap buffer depends on the actual implementation.
     */
    ByteBuf buffer(int initialCapacity);

    /**
     * 分配一个给定初始容量和最大容量的{@link ByteBuf}，可能是直接内存buffer 或 堆内存buffer，取决于具体的实现。
     * @param initialCapacity 初始容量
     * @param maxCapacity 最大容量
     *
     * Allocate a {@link ByteBuf} with the given initial capacity and the given
     * maximal capacity. If it is a direct or heap buffer depends on the actual
     * implementation.
     */
    ByteBuf buffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个{@link ByteBuf}，优先尝试分配适合IO的direct buffer.
     * @see #ioBuffer(int, int) 使用默认的初始容量和最大容量
     *
     * Allocate a {@link ByteBuf}, preferably a direct buffer which is suitable for I/O.
     */
    ByteBuf ioBuffer();

    /**
     * 分配一个给定的初始容量和最大容量的{@link ByteBuf}，优先尝试分配适合IO的direct buffer.
     * @param initialCapacity 初始容量
     * @see #ioBuffer(int, int) 使用默认的最大容量
     *
     * Allocate a {@link ByteBuf}, preferably a direct buffer which is suitable for I/O.
     */
    ByteBuf ioBuffer(int initialCapacity);

    /**
     * 分配一个{@link ByteBuf}，优先尝试分配适合IO的direct buffer.
     * @param initialCapacity 初始容量
     * @param maxCapacity 最大容量
     *
     * Allocate a {@link ByteBuf}, preferably a direct buffer which is suitable for I/O.
     */
    ByteBuf ioBuffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个堆内存上的Buffer
     * @see #heapBuffer(int, int) 使用默认的初始容量和最大容量
     * 
     * Allocate a heap {@link ByteBuf}.
     */
    ByteBuf heapBuffer();

    /**
     * 分配一个给定初始容量的堆内存上的Buffer
     * @param initialCapacity 初始容量
     * @see #heapBuffer(int, int) 使用默认的最大容量
     * 
     * Allocate a heap {@link ByteBuf} with the given initial capacity.
     */
    ByteBuf heapBuffer(int initialCapacity);

    /**
     * 分配一个给定初始容量和最大容量的堆内存上的Buffer。------ 这么多重载方法不累吗...
     * @param initialCapacity 初始容量
     * Allocate a heap {@link ByteBuf} with the given initial capacity and the given
     * maximal capacity.
     */
    ByteBuf heapBuffer(int initialCapacity, int maxCapacity);

    /**
     * 分配一个直接内存上的{@link ByteBuf}.
     * @see #directBuffer(int, int) 使用默认的初始容量和最大容量
     * Allocate a direct {@link ByteBuf}.
     */
    ByteBuf directBuffer();

    /**
     * 分配一个给定初始容量的直接内存上的{@link ByteBuf}.
     * @param initialCapacity 初始容量
     * @see #directBuffer(int, int) 使用默认的最大容量
     * 
     * Allocate a direct {@link ByteBuf} with the given initial capacity.
     */
    ByteBuf directBuffer(int initialCapacity);

    /**
     * 分配一个给定初始容量和最大容量的直接内存上的{@link ByteBuf}
     * Allocate a direct {@link ByteBuf} with the given initial capacity and the given
     * maximal capacity.
     */
    ByteBuf directBuffer(int initialCapacity, int maxCapacity);

    /**
     * Allocate a {@link CompositeByteBuf}.
     * If it is a direct or heap buffer depends on the actual implementation.
     */
    CompositeByteBuf compositeBuffer();

    /**
     * Allocate a {@link CompositeByteBuf} with the given maximum number of components that can be stored in it.
     * If it is a direct or heap buffer depends on the actual implementation.
     */
    CompositeByteBuf compositeBuffer(int maxNumComponents);

    /**
     * Allocate a heap {@link CompositeByteBuf}.
     */
    CompositeByteBuf compositeHeapBuffer();

    /**
     * Allocate a heap {@link CompositeByteBuf} with the given maximum number of components that can be stored in it.
     */
    CompositeByteBuf compositeHeapBuffer(int maxNumComponents);

    /**
     * Allocate a direct {@link CompositeByteBuf}.
     */
    CompositeByteBuf compositeDirectBuffer();

    /**
     * Allocate a direct {@link CompositeByteBuf} with the given maximum number of components that can be stored in it.
     */
    CompositeByteBuf compositeDirectBuffer(int maxNumComponents);

    /**
     * Returns {@code true} if direct {@link ByteBuf}'s are pooled
     */
    boolean isDirectBufferPooled();

    /**
     * Calculate the new capacity of a {@link ByteBuf} that is used when a {@link ByteBuf} needs to expand by the
     * {@code minNewCapacity} with {@code maxCapacity} as upper-bound.
     */
    int calculateNewCapacity(int minNewCapacity, int maxCapacity);
 }
