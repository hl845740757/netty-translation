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
package io.netty.util;

/**
 * 一个{@link Attribute} 允许存储一个对象引用。它支持原子方式更新，因此它是线程安全的。
 * (其实就是一个 {@link java.util.concurrent.atomic.AtomicReference})
 *
 * An attribute which allows to store a value reference. It may be updated atomically and so is thread-safe.
 *
 * @param <T>   the type of the value it holds.
 */
public interface Attribute<T> {

    /**
     * Returns the key of this attribute.
     */
    AttributeKey<T> key();

    /**
     * Returns the current value, which may be {@code null}
     */
    T get();

    /**
     * Sets the value
     */
    void set(T value);

    /**
     *  Atomically sets to the given value and returns the old value which may be {@code null} if non was set before.
     */
    T getAndSet(T value);

    /**
     *  Atomically sets to the given value if this {@link Attribute}'s value is {@code null}.
     *  If it was not possible to set the value as it contains a value it will just return the current value.
     */
    T setIfAbsent(T value);

    /**
     * 从该attribute所属的attributeMap中删除，并返回旧的值，接下来的{@link #get()}调用将放回null.
     * 如果你仅仅是想返回并清空当前值，但是并不从AttributeMap中删除，那么请使用{@link #getAndSet(Object)}，将值置为null。
     *
     * 请注意，即使调用此方法，也可能有另一个线程通过{@link AttributeMap#attr(AttributeKey)} 获得了对该{@link Attribute}的引用。
     * 这样两个线程仍然可能操作的是同一个实例。
     * 如果另一个线程或者当前线程稍后调用{@link AttributeMap#attr(AttributeKey)}，将会创建一个和之前的attribute不同的新的Attribute实例。
     * 因此调用@link #remove()} 或 {@link #getAndRemove()}应特别小心。
     *
     * 底层实现：
     * 在调用{@link AttributeMap#attr(AttributeKey)}时，如果没有符合要求的Attribute，那么将会新建一个符合要求的Attribute，
     * 某个瞬间可能存在一个AttributeKey的多个Attribute，但是只有一个是有效的，其它的都是等待删除的，
     * 你拿着一个AttributeKey，根本不知道取出来的是哪个，可能是对的，也可能的错的，但是一定是最新的。
     *
     * Removes this attribute from the {@link AttributeMap} and returns the old value. Subsequent {@link #get()}
     * calls will return {@code null}.
     *
     * If you only want to return the old value and clear the {@link Attribute} while still keep it in the
     * {@link AttributeMap} use {@link #getAndSet(Object)} with a value of {@code null}.
     *
     * <p>
     * Be aware that even if you call this method another thread that has obtained a reference to this {@link Attribute}
     * via {@link AttributeMap#attr(AttributeKey)} will still operate on the same instance. That said if now another
     * thread or even the same thread later will call {@link AttributeMap#attr(AttributeKey)} again, a new
     * {@link Attribute} instance is created and so is not the same as the previous one that was removed. Because of
     * this special caution should be taken when you call {@link #remove()} or {@link #getAndRemove()}.
     *
     * @deprecated please consider using {@link #getAndSet(Object)} (with value of {@code null}).
     */
    @Deprecated
    T getAndRemove();

    /**
     * Atomically sets the value to the given updated value if the current value == the expected value.
     * If it the set was successful it returns {@code true} otherwise {@code false}.
     */
    boolean compareAndSet(T oldValue, T newValue);

    /**
     * 从该attribute所属的attributeMap中删除，接下来的{@link #get()}调用将放回null.
     * 如果你仅仅是想返回并清空当前值，但是并不从AttributeMap中删除，那么请使用{@link #set(Object)}，将值置为null。
     *
     * 请注意，即使调用此方法，也可能有另一个线程通过{@link AttributeMap#attr(AttributeKey)} 获得了对该{@link Attribute}的引用。
     * 这样两个线程仍然可能操作的是同一个实例。
     * 如果另一个线程或者当前线程稍后调用{@link AttributeMap#attr(AttributeKey)}，将会创建一个和之前的attribute不同的新的Attribute实例。
     * 因此调用@link #remove()} 或 {@link #getAndRemove()}应特别小心。
     *
     * 底层实现：
     * 在调用{@link AttributeMap#attr(AttributeKey)}时，如果没有符合要求的Attribute，那么将会新建一个符合要求的Attribute，
     * 某个瞬间可能存在一个AttributeKey的多个Attribute，但是只有一个是有效的，其它的都是等待删除的，
     * 你拿着一个AttributeKey，根本不知道取出来的是哪个，可能是对的，也可能的错的，但是一定是最新的。
     *
     * Removes this attribute from the {@link AttributeMap}. Subsequent {@link #get()} calls will return @{code null}.
     *
     * If you only want to remove the value and clear the {@link Attribute} while still keep it in
     * {@link AttributeMap} use {@link #set(Object)} with a value of {@code null}.
     *
     * <p>
     * Be aware that even if you call this method another thread that has obtained a reference to this {@link Attribute}
     * via {@link AttributeMap#attr(AttributeKey)} will still operate on the same instance. That said if now another
     * thread or even the same thread later will call {@link AttributeMap#attr(AttributeKey)} again, a new
     * {@link Attribute} instance is created and so is not the same as the previous one that was removed. Because of
     * this special caution should be taken when you call {@link #remove()} or {@link #getAndRemove()}.
     *
     * @deprecated please consider using {@link #set(Object)} (with value of {@code null}).
     */
    @Deprecated
    void remove();
}
