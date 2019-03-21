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

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConstantPool是一个或多个常量的一个池。
 *
 * A pool of {@link Constant}s.
 *
 * @param <T> 常量的类型。
 *           the type of the constant。
 */
public abstract class ConstantPool<T extends Constant<T>> {
    /**
     * 常量集合，由名字作为key。 名字就可能出现重名的情况
     */
    private final ConcurrentMap<String, T> constants = PlatformDependent.newConcurrentHashMap();

    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * 一个快捷调用方式。
     * 将{@code firstNameComponent.getName() + "#" + secondNameComponent} 构成新的key;
     * 再调用 {@link #valueOf(String)}方法
     *
     * Shortcut of {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     */
    public T valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        if (firstNameComponent == null) {
            throw new NullPointerException("firstNameComponent");
        }
        if (secondNameComponent == null) {
            throw new NullPointerException("secondNameComponent");
        }

        return valueOf(firstNameComponent.getName() + '#' + secondNameComponent);
    }

    /**
     * 返回赋值为指定名字的{@link Constant}。如果不存在对应的{@link Constant}，那么将会创建一个新的{@link Constant}并返回。
     * 一旦创建之后，接下来的相同名字的调用将会返回之前创建的对象。(即：单例)
     *
     * Returns the {@link Constant} which is assigned to the specified {@code name}.
     * If there's no such {@link Constant}, a new one will be created and returned.
     * Once created, the subsequent calls with the same {@code name} will always return the previously created one
     * (i.e. singleton.)
     *
     * @param name the name of the {@link Constant}
     */
    public T valueOf(String name) {
        checkNotNullAndNotEmpty(name);
        return getOrCreate(name);
    }

    /**
     * 通过名字获取constant，或者当它不存在的时候创建一个。它是线程安全的。
     *
     * Get existing constant by name or creates new one if not exists. Threadsafe
     *
     * @param name the name of the {@link Constant}
     */
    private T getOrCreate(String name) {
        // 这里使用双重null校验是必须的，整个过程并不具备原子性，需要检测是否存在冲突。
        // get 和 putIfAbsent 返回的都是已存在的value。

        T constant = constants.get(name);
        if (constant == null) {
            // 尝试创建一个常量，并放入map
            final T tempConstant = newConstant(nextId(), name);
            // concurrentMap支持原子的putIfAbsent操作，返回的是旧值
            constant = constants.putIfAbsent(name, tempConstant);
            if (constant == null) {
                // 旧值不存在，新值就是目标对象
                return tempConstant;
            }
        }
        return constant;
    }

    /**
     * 如果给定{@code name}的{@link Constant}存在，则返回true.
     *
     * Returns {@code true} if a {@link Constant} exists for the given {@code name}.
     */
    public boolean exists(String name) {
        checkNotNullAndNotEmpty(name);
        return constants.containsKey(name);
    }

    /**
     * 使用给定的{@code name}创建一个新的{@link Constant}
     * Creates a new {@link Constant} for the given {@code name} or fail with an
     * {@link IllegalArgumentException} if a {@link Constant} for the given {@code name} exists.
     */
    public T newInstance(String name) {
        checkNotNullAndNotEmpty(name);
        return createOrThrow(name);
    }

    /**
     * 通过名字创建一个常量，如果常量已经存在，则抛出异常。
     *
     * Creates constant by name or throws exception. Threadsafe
     *
     * @param name the name of the {@link Constant}
     */
    private T createOrThrow(String name) {
        T constant = constants.get(name);
        if (constant == null) {
            // 常量不存在时，尝试创建一个常量，并尝试放入map
            final T tempConstant = newConstant(nextId(), name);
            // 返回的是旧值
            constant = constants.putIfAbsent(name, tempConstant);
            if (constant == null) {
                // 旧值不存在，则创建成功
                return tempConstant;
            }
        }

        throw new IllegalArgumentException(String.format("'%s' is already in use", name));
    }

    /**
     * 检查名字非空且非null
     * @param name
     * @return
     */
    private static String checkNotNullAndNotEmpty(String name) {
        ObjectUtil.checkNotNull(name, "name");

        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }

        return name;
    }

    /**
     * 一个工厂方法，由子类创建具体的类型。
     * 本类中使用调用该方法的都是模板方法。
     * @param id
     * @param name
     * @return
     */
    protected abstract T newConstant(int id, String name);

    @Deprecated
    public final int nextId() {
        return nextId.getAndIncrement();
    }
}
