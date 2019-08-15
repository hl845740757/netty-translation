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
package io.netty.util;

/**
 * 一个引用计数的对象需要显示的释放/回收。
 *
 * A reference-counted object that requires explicit deallocation.
 *
 * <p>
 * 当一个新的{@link ReferenceCounted}对象被实例化时。它的引用计数起始值为1。
 * {@link #retain()}方法用于增加引用计数，{@link #release()}用于减少引用计数。
 * 如果引用计数减少到0，对象将会被显式的回收。并且访问被回收的对象通常会导致访问冲突。
 * </p>
 * 如果一个实现了{@link ReferenceCounted}的对象是其它实现了{@link ReferenceCounted}对象的容器。
 * 当容器的引用计数变为0时容器内的对象也会通过{@link #release()}被释放。
 *
 * Q: 它的主要目的是什么？
 * A: Netty使用了大量的堆外内存以提高IO速度(消除冗余拷贝)，但是堆外内存的分配与回收代价是较高的 (堆内内存JVM有绝对的控制权，而堆外内存属于操作系统管理)。
 * 引用计数便是为了重用分配的的堆外内存，减少堆外内存的分配与回收操作。
 *
 * Q: 冗余拷贝是什么？
 * Q: 对于JVM堆内内存，操作系统是无法安全的进行访问的，因为JVM的垃圾回收机制会导致对象地址变动，但是操作系统并不了解JVM的机制，因此操作系统并不能安全的访问JVM堆内内存，
 * 如果操作系统要访问JVM内的某块内存，JVM必须先将这部分内存拷贝到堆外内存，然后操作系统可以访问这块内存。 --- 从JVM堆内拷贝到堆外，这是一个冗余拷贝。
 * 而对于IO操作，必须由操作系统执行，如果每次都先从堆内拷贝到堆外，性能将是极差的。
 *
 * <h3>release与retain的时序问题（潜规则规范）</h3>
 * 底层是基于CAS更新引用计数的，那么存在这种情况：
 * 如果对象引用计数为1，此时两个线程一个调用retain，一个调用release，另一边retain还没成功时，release先成功了，那么这种时序下将出现问题。
 * 调用retain的一方虽然也是正常的使用，但是却存在风险 --- 要解决这个问题，必须在将引用计数对象传递给另一个线程之前先retain，算是一种规范吧。
 *
 * 该接口支持流式语法，所有返回{@link ReferenceCounted}的其实返回的都是自己。
 *
 * <p>
 * When a new {@link ReferenceCounted} is instantiated, it starts with the reference count of {@code 1}.
 * {@link #retain()} increases the reference count, and {@link #release()} decreases the reference count.
 * If the reference count is decreased to {@code 0}, the object will be deallocated explicitly, and accessing
 * the deallocated object will usually result in an access violation.
 * </p>
 * <p>
 * If an object that implements {@link ReferenceCounted} is a container of other objects that implement
 * {@link ReferenceCounted}, the contained objects will also be released via {@link #release()} when the container's
 * reference count becomes 0.
 * </p>
 */
public interface ReferenceCounted {
    /**
     * 返回当前对象的引用数。
     * 如果返回0，它意味着该对象已经被释放(回收)了。
     *
     * Returns the reference count of this object.  If {@code 0}, it means this object has been deallocated.
     */
    int refCnt();

    /**
     * 增加对象的引用计数,引用计数+1.
     * 通常表示希望保存该对象一段时间。
     * Increases the reference count by {@code 1}.
     */
    ReferenceCounted retain();

    /**
     * 引用计数增加指定数
     * Increases the reference count by the specified {@code increment}.
     */
    ReferenceCounted retain(int increment);

    /**
     * 记录此对象的当前访问位置以进行调试。
     * 如果该对象被确定为泄漏，该操作记录的信息将会通过{@link ResourceLeakDetector}提供。
     * 该方法是{@link #touch(Object) touch(null)}的快捷调用。
     *
     * Records the current access location of this object for debugging purposes.
     * If this object is determined to be leaked, the information recorded by this operation will be provided to you
     * via {@link ResourceLeakDetector}.  This method is a shortcut to {@link #touch(Object) touch(null)}.
     */
    ReferenceCounted touch();

    /**
     * 记录此对象的当前访问位置以进行调试。
     * 如果该对象被确定为泄漏，该操作记录的信息将会通过{@link ResourceLeakDetector}提供。
     *
     * Records the current access location of this object with an additional arbitrary information for debugging
     * purposes.  If this object is determined to be leaked, the information recorded by this operation will be
     * provided to you via {@link ResourceLeakDetector}.
     */
    ReferenceCounted touch(Object hint);

    /**
     * 引用计数减1,并且如果引用数到达0时释放对象。
     * 当且仅当引用数变为0并且该对象被释放时返回True。
     *
     * Decreases the reference count by {@code 1} and deallocates this object if the reference count reaches at
     * {@code 0}.
     *
     * @return {@code true} if and only if the reference count became {@code 0} and this object has been deallocated
     */
    boolean release();

    /**
     * 减少指定引用数,并且如果引用数到达0时释放对象。
     * 当且仅当引用数变为0并且该对象被释放时返回True。
     *
     * Decreases the reference count by the specified {@code decrement} and deallocates this object if the reference
     * count reaches at {@code 0}.
     *
     * @return {@code true} if and only if the reference count became {@code 0} and this object has been deallocated
     */
    boolean release(int decrement);
}
