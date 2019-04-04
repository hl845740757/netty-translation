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
package io.netty.buffer;

/**
 * (内存)块的度量。
 * Metrics for a chunk.
 */
public interface PoolChunkMetric {

    /**
     * 返回当前块的使用百分比。
     * Return the percentage of the current usage of the chunk.
     */
    int usage();

    /**
     * 返回当前块的大小(以字节为单位)，这是该块能提供的最大字节数。
     * Return the size of the chunk in bytes, this is the maximum of bytes
     * that can be served out of the chunk.
     */
    int chunkSize();

    /**
     * 返回该块中释放的字节数。
     * Return the number of free bytes in the chunk.
     */
    int freeBytes();
}
