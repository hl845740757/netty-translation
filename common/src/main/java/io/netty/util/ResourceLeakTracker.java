/*
 * Copyright 2016 The Netty Project
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
 * 资源泄漏追踪器
 * @param <T> 资源类型
 */
public interface ResourceLeakTracker<T>  {

    /**
     * 记录调用者的当且堆栈信息，以便{@link ResourceLeakDetector} 可以确定最后访问泄漏资源的位置。
     * 该方法是{@link #record(Object) record(null)}的一个快捷方式。
     *
     * Records the caller's current stack trace so that the {@link ResourceLeakDetector} can tell where the leaked
     * resource was accessed lastly. This method is a shortcut to {@link #record(Object) record(null)}.
     */
    void record();

    /**
     * 记录调用者的当且堆栈信息和指定的其它任意信息，以便{@link ResourceLeakDetector} 可以确定最后访问泄漏资源的位置。
     *
     * Records the caller's current stack trace and the specified additional arbitrary information
     * so that the {@link ResourceLeakDetector} can tell where the leaked resource was accessed lastly.
     */
    void record(Object hint);

    /**
     * 关闭该资源相关的泄漏追踪，使得{@link ResourceLeakTracker}不会再警告该泄漏的资源。
     *
     * Close the leak so that {@link ResourceLeakTracker} does not warn about leaked resources.
     * After this method is called a leak associated with this ResourceLeakTracker should not be reported.
     *
     * @return {@code true} if called first time, {@code false} if called already
     */
    boolean close(T trackedObject);
}
