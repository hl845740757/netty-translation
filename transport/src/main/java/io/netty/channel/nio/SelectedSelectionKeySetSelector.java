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
package io.netty.channel.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;

final class SelectedSelectionKeySetSelector extends Selector {
    private final SelectedSelectionKeySet selectionKeys;
    private final Selector delegate;

    SelectedSelectionKeySetSelector(Selector delegate, SelectedSelectionKeySet selectionKeys) {
        this.delegate = delegate;
        this.selectionKeys = selectionKeys;
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public SelectorProvider provider() {
        return delegate.provider();
    }

    /**
     * channel们注册的所有的key
     * @return
     */
    @Override
    public Set<SelectionKey> keys() {
        return delegate.keys();
    }

    /**
     * 上一次select操作选择的Key
     * (某些channel准备好了某些IO操作)
     * @return
     */
    @Override
    public Set<SelectionKey> selectedKeys() {
        return delegate.selectedKeys();
    }

    /**
     * 立即执行一次选择操作，选择一组准备就绪的键(对应的channel在对应的IO操作上已做好准备)。
     *
     * {@link Selector#selectNow()}是非阻塞操作。
     * 如果自上次选择操作后没有可选择的通道，则此方法立即返回零。
     * 调用此方法可清除之前调用{@link #wakeup wakeup}方法的效果。
     * @return 本次select操作选中的准备就绪的操作数。
     * @throws IOException
     */
    @Override
    public int selectNow() throws IOException {
        selectionKeys.reset();
        return delegate.selectNow();
    }

    /**
     *
     * @param timeout
     * @return
     * @throws IOException
     */
    @Override
    public int select(long timeout) throws IOException {
        selectionKeys.reset();
        return delegate.select(timeout);
    }

    /**
     * 阻塞的方式选择一组键，表示它们关联的channel已经准备好了对应的IO操作(write、read、Accept,connect)。
     * 它仅在
     * 1.选择了至少一个通道后；
     * 2.或调用此选择器的{@link #wakeup wakeup}方法；
     * 3.或者当前线程被中断。
     * 三种情况下会返回，以先到者为准。
     * @return
     * @throws IOException
     */
    @Override
    public int select() throws IOException {
        selectionKeys.reset();
        return delegate.select();
    }

    @Override
    public Selector wakeup() {
        return delegate.wakeup();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
