/*
 * Copyright 2017 The Netty Project
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
package io.netty.util.internal;

import java.util.Queue;

public interface PriorityQueue<T> extends Queue<T> {

    /**
     * 和{@link #remove(Object)}相同，但是使用泛型类型。
     * Same as {@link #remove(Object)} but typed using generics.
     */
    boolean removeTyped(T node);

    /**
     * 和{@link #contains(Object)}相同，但是使用泛型类型。
     * Same as {@link #contains(Object)} but typed using generics.
     */
    boolean containsTyped(T node);

    /**
     * 通知队列该节点的优先级发生改变。
     * 如果优先级和可变属性有关，这是很有必要的，否则会导致优先级队列状态错误(需要调整)。
     * 在我游戏项目中的timer其实就存在该情况。
     *
     * Notify the queue that the priority for {@code node} has changed. The queue will adjust to ensure the priority
     * queue properties are maintained.
     * @param node An object which is in this queue and the priority may have changed.
     */
    void priorityChanged(T node);

    /**
     * 删除{@link PriorityQueue}中的所有元素而不调用{@link PriorityQueueNode#priorityQueueIndex(DefaultPriorityQueue)}
     * 或明确地删除到它们的引用以允许它们被垃圾回收。
     * 该方法只应该在确定所有的节点不会重新插入到该队列或任何其它队列并且队列自身知道自己在调用该方法后将要被回收是。
     *
     * Removes all of the elements from this {@link PriorityQueue} without calling
     * {@link PriorityQueueNode#priorityQueueIndex(DefaultPriorityQueue)} or explicitly removing references to them to
     * allow them to be garbage collected. This should only be used when it is certain that the nodes will not be
     * re-inserted into this or any other {@link PriorityQueue} and it is known that the {@link PriorityQueue} itself
     * will be garbage collected after this call.
     */
    void clearIgnoringIndexes();
}
