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
 * {@link AttributeMap}是{@link Attribute}的持有者，{@link Attribute}可以通过{@link AttributeKey}访问。
 * 实现必须是线程安全的。
 *
 * Holds {@link Attribute}s which can be accessed via {@link AttributeKey}.
 * Implementations must be Thread-safe.
 */
public interface AttributeMap {
    /**
     * {@link java.util.Map#get(Object)}
     *
     * 返回给定的{@link AttributeKey}对应的{@link Attribute}。该方法返回值永远不为null。
     * 但是返回的{@link Attribute}中的值可能未被赋值。也就是说{@link Attribute#get()}可能返回Null。
     *
     * Get the {@link Attribute} for the given {@link AttributeKey}. This method will never return null, but may return
     * an {@link Attribute} which does not have a value set yet.
     */
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * {@link java.util.Map#containsKey(Object)}
     *
     * 如果 {@link AttributeMap}中存在给定{@link AttributeKey}的{@link Attribute}时返回true。
     *
     * Returns {@code} true if and only if the given {@link Attribute} exists in this {@link AttributeMap}.
     */
    <T> boolean hasAttr(AttributeKey<T> key);
}
