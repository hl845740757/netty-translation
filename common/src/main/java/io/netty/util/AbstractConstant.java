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

import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link Constant}的基本实现，制定了基本骨架(属性)，和比较实现。
 *
 * Base implementation of {@link Constant}.
 */
public abstract class AbstractConstant<T extends AbstractConstant<T>> implements Constant<T> {
    /**
     * 唯一id生成器，本进程唯一
     */
    private static final AtomicLong uniqueIdGenerator = new AtomicLong();

    private final int id;
    private final String name;
    /**
     * 唯一id
     */
    private final long uniquifier;

    /**
     * Creates a new instance.
     */
    protected AbstractConstant(int id, String name) {
        this.id = id;
        this.name = name;
        this.uniquifier = uniqueIdGenerator.getAndIncrement();
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final int id() {
        return id;
    }

    @Override
    public final String toString() {
        return name();
    }

    // ------------------ 禁止子类重写hashcode和equals 和 compareTo ------------------------
    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public final int compareTo(T o) {
        if (this == o) {
            return 0;
        }

        @SuppressWarnings("UnnecessaryLocalVariable")
        AbstractConstant<T> other = o;
        int returnCode;

        // 先比较的hashcode (似乎也没有什么特别大的意义？默认的hashcode也并不具备区分度)
        returnCode = hashCode() - other.hashCode();
        if (returnCode != 0) {
            return returnCode;
        }
        // 再比较uniquifier (倒是uniquifier一定是先分配的更小)
        if (uniquifier < other.uniquifier) {
            return -1;
        }
        if (uniquifier > other.uniquifier) {
            return 1;
        }
        // 每一个常量都有一个唯一的id
        throw new Error("failed to compare two different constants");
    }

}
