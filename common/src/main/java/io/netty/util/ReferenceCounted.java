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
 * 如果引用计数减少到0，对象将会被显式的回收。并且访问被回收的对象通常会导致访问冲突(违反)。
 * </p>
 * 如果一个实现了{@link ReferenceCounted}的对象是其它实现了{@link ReferenceCounted}对象的容器。
 * 当容器的引用计数变为0时容器内的对象也会通过{@link #release()}被释放。
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
