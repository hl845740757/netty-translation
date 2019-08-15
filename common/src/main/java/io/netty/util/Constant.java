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
 * {@link Constant} 表示的是一个单例，它允许安全的使用 == 进行比较。Constant 由 {@link ConstantPool}创建管理的。
 * 使用 == 操作可以极大的提高查找速度，比较速度。
 * (要使用==比较，那么一定不能重写hashcode和equals方法)
 *
 * 常量总是和常量池关联。
 *
 * A singleton which is safe to compare via the {@code ==} operator. Created and managed by {@link ConstantPool}.
 */
public interface Constant<T extends Constant<T>> extends Comparable<T> {

    /**
     * 返回赋值给该 {@link Constant}的唯一数字。
     *
     * Returns the unique number assigned to this {@link Constant}.
     */
    int id();

    /**
     * 返回该{@link Constant}的名字(未要求唯一)，但是实际上常量的名字应该是唯一的。否则会导致误解，而导致错误。
     *
     * Returns the name of this {@link Constant}.
     */
    String name();
}
