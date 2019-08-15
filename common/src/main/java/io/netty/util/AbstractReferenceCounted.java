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

import static io.netty.util.internal.ObjectUtil.checkPositive;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.netty.util.internal.PlatformDependent;

/**
 * 引用计数的抽象实现。
 *
 * Abstract base class for classes wants to implement {@link ReferenceCounted}.
 */
public abstract class AbstractReferenceCounted implements ReferenceCounted {

    /**
     * 引用计数属性的偏移量(为了使用unsafe绕过volatile读)
     */
    private static final long REFCNT_FIELD_OFFSET;
    /**
     * 引用计数属性字段updater
     */
    private static final AtomicIntegerFieldUpdater<AbstractReferenceCounted> refCntUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCounted.class, "refCnt");

    /**
     * 当前引用计数，之前的版本好像就是真实计数，1就是1，初始就是1。
     * 好像是为了解决溢出问题变成现在的位移版本了。不知道什么情况下会导致引用计数溢出。。。
     * 搞得复杂度高了不少。
     */
    // even => "real" refcount is (refCnt >>> 1); odd => "real" refcount is 0
    @SuppressWarnings("unused")
    private volatile int refCnt = 2;

    static {
        // 通过unsafe获取字段偏移量
        long refCntFieldOffset = -1;
        try {
            if (PlatformDependent.hasUnsafe()) {
                refCntFieldOffset = PlatformDependent.objectFieldOffset(
                        AbstractReferenceCounted.class.getDeclaredField("refCnt"));
            }
        } catch (Throwable ignore) {
            refCntFieldOffset = -1;
        }

        REFCNT_FIELD_OFFSET = refCntFieldOffset;
    }

    /**
     * 计算真实的引用计数
     * @param rawCnt 原始计数
     * @return 真实计数
     */
    private static int realRefCnt(int rawCnt) {
        // 如果最后一位是1，表示引用计数为0，否则右移一位得到真实计数。
        return (rawCnt & 1) != 0 ? 0 : rawCnt >>> 1;
    }

    // 这方法不是netty的方法，它这个判断引用计数是否为0的逻辑应该提供独立方法，不然每次看见都得思考一下。每次看见 rawCnt & 1 都有点蛋疼。
//    private static boolean isReleased(int rawCnt) {
//        return (rawCnt & 1) != 0;
//    }

    /**
     * 尝试以非volatile方式读取原始计数。
     * 为了减少volatile读，这样操作是否有风险(内存顺序)，我个人不是很清楚，但是清楚的是，底层不是特别的熟的人，千万不要这样使用。
     */
    private int nonVolatileRawCnt() {
        // TODO: Once we compile against later versions of Java we can replace the Unsafe usage here by varhandles.
        return REFCNT_FIELD_OFFSET != -1 ? PlatformDependent.getInt(this, REFCNT_FIELD_OFFSET)
                : refCntUpdater.get(this);
    }

    /**
     * 获取真实引用计数，对外接口
     */
    @Override
    public int refCnt() {
        return realRefCnt(refCntUpdater.get(this));
    }

    /**
     * 更新引用计数。
     * 声明为final是为了避免重写该方法，该方法实现比较特殊，不允许子类重写。
     * An unsafe operation intended for use by a subclass that sets the reference count of the buffer directly
     */
    protected final void setRefCnt(int newRefCnt) {
        refCntUpdater.set(this, newRefCnt << 1); // overflow OK here
    }

    @Override
    public ReferenceCounted retain() {
        return retain0(1);
    }

    @Override
    public ReferenceCounted retain(int increment) {
        return retain0(checkPositive(increment, "increment"));
    }

    /**
     * 增加引用计数的真正实现。
     * @param increment 要增加的引用计数 大于0
     * @return this
     */
    private ReferenceCounted retain0(final int increment) {
        // 所有的改变都会左移一位(乘以2)
        // all changes to the raw count are 2x the "real" change
        int adjustedIncrement = increment << 1; // overflow OK here
        int oldRef = refCntUpdater.getAndAdd(this, adjustedIncrement);
        // oldRef & 1 != 0 (最后一位为1)表示引用计数0，引用计数为0的对象已被回收，不可以继续使用。
        if ((oldRef & 1) != 0) {
            throw new IllegalReferenceCountException(0, increment);
        }
        // 溢出处理(符号二次溢出)
        // don't pass 0!
        if ((oldRef <= 0 && oldRef + adjustedIncrement >= 0)
                || (oldRef >= 0 && oldRef + adjustedIncrement < oldRef)) {
            // overflow case
            refCntUpdater.getAndAdd(this, -adjustedIncrement);
            throw new IllegalReferenceCountException(realRefCnt(oldRef), increment);
        }
        return this;
    }

    @Override
    public ReferenceCounted touch() {
        return touch(null);
    }

    @Override
    public boolean release() {
        return release0(1);
    }

    @Override
    public boolean release(int decrement) {
        return release0(checkPositive(decrement, "decrement"));
    }

    /**
     * 减少引用计数
     * @param decrement 要减去的引用计数
     * @return 是否执行了真正的释放操作
     */
    private boolean release0(int decrement) {
        // 原始计数，真实计数
        int rawCnt = nonVolatileRawCnt(), realCnt = toLiveRealCnt(rawCnt, decrement);
        // 如果要减去的计数等于真实计数，表示可能是要进行释放操作
        if (decrement == realCnt) {
            if (refCntUpdater.compareAndSet(this, rawCnt, 1)) {
                // 成功将原始计数变为1(真实计数0)，表明可以进行最终释放。
                // CAS这样的机制好像也有点问题，那么存在这种情况:
                // 如果对象引用计数为1，此时两个线程一个调用retain，一个调用release，另一边还没retain成功时，release先成功了，那么这种时序下将出现问题。
                // 调用retain的一方虽然也是正常的使用，但是却存在风险 --- 要解决这个问题，必须在将引用计数对象传递给另一个线程之前先retain，算是一种规范吧。
                deallocate();
                return true;
            }
            // 未能成功将计数更新为0，那么表示有线程进行了release或retain操作，重试release操作
            return retryRelease0(decrement);
        }
        // 当前看见的不等于真实计数，那么很可能不是最终的release操作
        return releaseNonFinal0(decrement, rawCnt, realCnt);
    }

    private boolean releaseNonFinal0(int decrement, int rawCnt, int realCnt) {
        // 如果要减去的计数小于真实计数，并且成功更新计数，那么执行结束
        if (decrement < realCnt
                // 外部的所有计数在内部都会被乘以2进行调整
                // all changes to the raw count are 2x the "real" change
                && refCntUpdater.compareAndSet(this, rawCnt, rawCnt - (decrement << 1))) {
            return false;
        }
        return retryRelease0(decrement);
    }

    /**
     * 重试，进行释放
     * @param decrement 要减去的引用计数
     * @return 是否执行了真正的释放操作。
     */
    private boolean retryRelease0(int decrement) {
        for (;;) {
            int rawCnt = refCntUpdater.get(this), realCnt = toLiveRealCnt(rawCnt, decrement);
            if (decrement == realCnt) {
                // 如果要减去的计数等于真实计数，表示可能是要进行释放操作
                if (refCntUpdater.compareAndSet(this, rawCnt, 1)) {
                    // 成功将原始计数变为1(真实计数0)，表明可以进行最终释放。
                    deallocate();
                    return true;
                }
            } else if (decrement < realCnt) {
                // 如果要减去的计数小于真实计数，并且成功更新计数，那么执行结束
                // all changes to the raw count are 2x the "real" change
                if (refCntUpdater.compareAndSet(this, rawCnt, rawCnt - (decrement << 1))) {
                    return false;
                }
            } else {
                // 真实计数小于要减去的计数，异常情况。
                throw new IllegalReferenceCountException(realCnt, -decrement);
            }
            Thread.yield(); // this benefits throughput under high contention
        }
    }

    /**
     * 计算真正的引用计数
     * Like {@link #realRefCnt(int)} but throws if refCnt == 0
     */
    private static int toLiveRealCnt(int rawCnt, int decrement) {
        // 如果最后一位是0，那么可以安全的右移，表示真实计数大于0
        if ((rawCnt & 1) == 0) {
            return rawCnt >>> 1;
        }
        // 如果最后一位是1，那么表示真实计数是0，已被回收
        // odd rawCnt => already deallocated
        throw new IllegalReferenceCountException(0, -decrement);
    }

    /**
     * Called once {@link #refCnt()} is equals 0.
     */
    protected abstract void deallocate();
}
