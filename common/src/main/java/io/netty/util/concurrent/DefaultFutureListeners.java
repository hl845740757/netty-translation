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
package io.netty.util.concurrent;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * FutureListener的容器。
 * Q: 主要解决什么问题？
 * A: 主要解决扩容问题，不使用{@link ArrayList}，是因为@link ArrayList}默认扩容50%，初始容量较小的话，扩容过于频繁 - 2，3，4，6，9。
 * 而这里是扩一倍，对于小容量容器来说，更为友好。
 */
final class DefaultFutureListeners {
    /**
     * listener数组，会自动扩容，容量大于等于实际listener数量。
     */
    private GenericFutureListener<? extends Future<?>>[] listeners;
    /**
     * 所有的监听器数量
     */
    private int size;
    /**
     * GenericProgressiveFutureListener类型的监听器数量。
     */
    private int progressiveSize; // the number of progressive listeners

    @SuppressWarnings("unchecked")
    DefaultFutureListeners(
            GenericFutureListener<? extends Future<?>> first, GenericFutureListener<? extends Future<?>> second) {
        listeners = new GenericFutureListener[2];
        listeners[0] = first;
        listeners[1] = second;
        size = 2;
        // 该类型需要进行额外的处理
        if (first instanceof GenericProgressiveFutureListener) {
            progressiveSize++;
        }
        if (second instanceof GenericProgressiveFutureListener) {
            progressiveSize++;
        }
    }

    public void add(GenericFutureListener<? extends Future<?>> l) {
        GenericFutureListener<? extends Future<?>>[] listeners = this.listeners;
        final int size = this.size;
        if (size == listeners.length) {
            this.listeners = listeners = Arrays.copyOf(listeners, size << 1);
        }
        listeners[size] = l;
        this.size = size + 1;

        // 如果是GenericProgressiveFutureListener类型的监听器，则对应计数加1
        if (l instanceof GenericProgressiveFutureListener) {
            progressiveSize++;
        }
    }

    /**
     * 移除一个监听器
     *
     * @param l 要移除的监听器
     */
    public void remove(GenericFutureListener<? extends Future<?>> l) {
        final GenericFutureListener<? extends Future<?>>[] listeners = this.listeners;
        int size = this.size;
        for (int i = 0; i < size; i++) {
            if (listeners[i] == l) {
                int listenersToMove = size - i - 1;
                if (listenersToMove > 0) {
                    System.arraycopy(listeners, i + 1, listeners, i, listenersToMove);
                }
                listeners[--size] = null;
                this.size = size;

                // 如果是GenericProgressiveFutureListener类型的监听器，则对应计数减1
                if (l instanceof GenericProgressiveFutureListener) {
                    progressiveSize--;
                }
                return;
            }
        }
    }

    /**
     * @return 所有的监听器
     */
    public GenericFutureListener<? extends Future<?>>[] listeners() {
        return listeners;
    }

    /**
     * @return 所有的监听器数量
     */
    public int size() {
        return size;
    }

    /**
     * @return GenericProgressiveFutureListener类型的监听器数量
     */
    public int progressiveSize() {
        return progressiveSize;
    }
}
