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

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * {@link DefaultAttributeMap}是{@link AttributeMap}的默认实现，它使用简单的锁分段技术
 * 尽可能的保持内存负载尽可能的小。
 *
 * Default {@link AttributeMap} implementation which use simple synchronization per bucket to keep the memory overhead
 * as low as possible.
 */
public class DefaultAttributeMap implements AttributeMap {

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultAttributeMap, AtomicReferenceArray> updater =
            AtomicReferenceFieldUpdater.newUpdater(DefaultAttributeMap.class, AtomicReferenceArray.class, "attributes");

    private static final int BUCKET_SIZE = 4;
    private static final int MASK = BUCKET_SIZE  - 1;

    // Initialize lazily to reduce memory consumption; updated by AtomicReferenceFieldUpdater above.
    @SuppressWarnings("UnusedDeclaration")
    private volatile AtomicReferenceArray<DefaultAttribute<?>> attributes;

    @SuppressWarnings("unchecked")
    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        if (attributes == null) {
            // 不使用ConcurrentHashMap，是因为内存消耗太大
            // Not using ConcurrentHashMap due to high memory consumption.
            attributes = new AtomicReferenceArray<DefaultAttribute<?>>(BUCKET_SIZE);

            // 尝试CAS赋值为指定对象，如果成功，那么什么都不需要做，如果失败，那么证明已有值，需要取当前值
            if (!updater.compareAndSet(this, null, attributes)) {
                attributes = this.attributes;
            }
        }
        // 计算key所在的桶索引
        int i = index(key);
        // 获取对应桶的链表头
        DefaultAttribute<?> head = attributes.get(i);
        if (head == null) {
            // 如果链表头不存在，那么我们尝试为key创建一个attribute，设置为首元素(非链表头)，然后尝试CAS更新链表头。
            // 如果CAS设置成功，那么证明我们的执行过程未出现冲突，整个过程结束。
            // 如果CAS设置失败，那么链表头已存在，那么必须加锁，互斥访问最新的链表。

            // No head exists yet which means we may be able to add the attribute without synchronization and just
            // use compare and set. At worst we need to fallback to synchronization and waste two allocations.
            head = new DefaultAttribute();
            DefaultAttribute<T> attr = new DefaultAttribute<T>(head, key);
            head.next = attr;
            attr.prev = head;

            if (attributes.compareAndSet(i, null, head)) {
                // 如果CAS成功，那么结束
                // we were able to add it so return the attr right away
                return attr;
            } else {
                // 如果CAS失败，那么证明链表头已结束，需要使用真正的链表头进行尝试。
                head = attributes.get(i);
            }
        }
        // CAS失败，证明链表头已经存在，必须遍历链表 -- 为什么可以对head加锁？因为head是不会删除的，至始至终不会改变。
        synchronized (head) {
            DefaultAttribute<?> curr = head;
            for (;;) {
                DefaultAttribute<?> next = curr.next;
                if (next == null) {
                    // 如果发现了一个key相同的，但是removed为true的话，会导致这里又为该key创建一个attribute属性！！！
                    // 导致了同一个key,不同的地方取出来的attribute的可能不是一个对象。。。。
                    // 所以netty禁用了remove系列方法。
                    DefaultAttribute<T> attr = new DefaultAttribute<T>(head, key);
                    curr.next = attr;
                    attr.prev = curr;
                    return attr;
                }
                // 为何会出现removed为true的情况？
                // 因为remove操作会先赋值remove，然后获取锁才能删除，可能无法立即获得锁，从而导致这里能看见removed属性为true的时候
                if (next.key == key && !next.removed) {
                    // 找到一个合法属性
                    return (Attribute<T>) next;
                }
                curr = next;
            }
        }
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        if (attributes == null) {
            // 数组还未创建，一定不存在
            // no attribute exists
            return false;
        }

        int i = index(key);
        DefaultAttribute<?> head = attributes.get(i);
        if (head == null) {
            // key所在的桶不存在，那么一定不存在对应的属性
            // No attribute exists which point to the bucket in which the head should be located
            return false;
        }

        // 此时需要进行互斥访问，因为其它线程可能对该链表进行增加或删除操作。
        // We need to synchronize on the head.
        synchronized (head) {
            // Start with head.next as the head itself does not store an attribute.
            DefaultAttribute<?> curr = head.next;
            while (curr != null) {
                if (curr.key == key && !curr.removed) {
                    return true;
                }
                curr = curr.next;
            }
            return false;
        }
    }

    /**
     * 计算属性键对应的数组索引(桶索引) --- 这个索引必须是固定的，否则将出现异常
     * @param key 属性键 -- 常量
     * @return 数组索引 index
     */
    private static int index(AttributeKey<?> key) {
        // 获取id的后x位得到所有 （现在版本是桶是4，后两位有效）
        return key.id() & MASK;
    }

    /**
     * 在{@link AtomicReference}的基础上，配合DefaultAttributeMap构成了节点链表。
     * @param <T>
     */
    @SuppressWarnings("serial")
    private static final class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {

        private static final long serialVersionUID = -2661411462200283011L;

        // The head of the linked-list this attribute belongs to
        private final DefaultAttribute<?> head;
        private final AttributeKey<T> key;

        // Double-linked list to prev and next node to allow fast removal
        private DefaultAttribute<?> prev;
        private DefaultAttribute<?> next;

        // Will be set to true one the attribute is removed via getAndRemove() or remove()
        private volatile boolean removed;

        DefaultAttribute(DefaultAttribute<?> head, AttributeKey<T> key) {
            this.head = head;
            this.key = key;
        }

        // 这是作为链表头使用的特殊的节点，链表头不会删除。
        // Special constructor for the head of the linked-list.
        DefaultAttribute() {
            head = this;
            key = null;
        }

        @Override
        public AttributeKey<T> key() {
            return key;
        }

        @Override
        public T setIfAbsent(T value) {
            while (!compareAndSet(null, value)) {
                // 即使设置失败，取出来的值也不一定非null，因为其它线程可以再次置为null，
                // 取出第一个看见的非null值
                T old = get();
                if (old != null) {
                    return old;
                }
            }
            return null;
        }

        @Override
        public T getAndRemove() {
            removed = true;
            T oldValue = getAndSet(null);
            remove0();
            return oldValue;
        }

        @Override
        public void remove() {
            removed = true;
            set(null);
            remove0();
        }

        /**
         * 从对应的节点列表中删除。（对应的桶）
         */
        private void remove0() {
            synchronized (head) {
                if (prev == null) {
                    // Q: 什么时候回出现？
                    // A: 多个线程调用remove或同一个线程重复调用，后面的操作必须要能感知到前面的操作已执行
                    // Removed before.
                    return;
                }

                prev.next = next;

                if (next != null) {
                    next.prev = prev;
                }

                // 显式的将前后索引置为null，如果错误的使用它们可能破坏链表结构。
                // Null out prev and next - this will guard against multiple remove0() calls which may corrupt
                // the linked list for the bucket.
                prev = null;
                next = null;
            }
        }
    }
}
