/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

/**
 * 异步映射函数。
 * @param <IN> 输入类型
 * @param <OUT> 输出类型
 */
public interface AsyncMapping<IN, OUT> {

    /**
     * 返回的future将会提供映射的结果，其实就是参数的promise。
     * 给定的promise在结果可以的时候回被填充结果。
     *
     * Returns the {@link Future} that will provide the result of the mapping. The given {@link Promise} will
     * be fulfilled when the result is available.
     */
    Future<OUT> map(IN input, Promise<OUT> promise);
}
