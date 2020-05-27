/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.ffi.impl.nodes;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.data.RIntVector;
import com.oracle.truffle.r.runtime.data.VectorDataLibrary;
import com.oracle.truffle.r.runtime.ffi.util.NativeArrayWrapper;

@ImportStatic(DSLConfig.class)
@GenerateUncached
public abstract class IntegerGetRegionNode extends FFIUpCallNode.Arg4 {

    public static IntegerGetRegionNode create() {
        return IntegerGetRegionNodeGen.create();
    }

    @Specialization(guards = "bufferInterop.hasArrayElements(buffer)", limit = "getGenericDataLibraryCacheSize()")
    public long doBufferArray(RIntVector vec, long fromIdx, long size, Object buffer,
                              @CachedLibrary("vec.getData()") VectorDataLibrary dataLibrary,
                              @CachedLibrary("buffer") InteropLibrary bufferInterop) {
        validateArguments(fromIdx, size);

        return dataLibrary.getIntRegion(vec.getData(), (int) fromIdx, (int) size, buffer, bufferInterop);
    }

    @Specialization(guards = "!bufferInterop.hasArrayElements(buffer)", limit = "getGenericDataLibraryCacheSize()")
    public long doGenericBuffer(RIntVector vec, long fromIdx, long size, Object buffer,
                                @CachedLibrary("vec.getData()") VectorDataLibrary dataLibrary,
                                @CachedLibrary("buffer") InteropLibrary bufferInterop,
                                @CachedLibrary(limit = "1") InteropLibrary bufferWrapperInterop) {

        validateArguments(fromIdx, size);
        bufferInterop.toNative(buffer);
        long bufferAddr = 0;
        try {
            bufferAddr = bufferInterop.asPointer(buffer);
        } catch (UnsupportedMessageException e) {
            throw RInternalError.shouldNotReachHere(e);
        }
        int sizeInt = (int) size;
        Object bufferWrapper = NativeArrayWrapper.createIntWrapper(bufferAddr, sizeInt);
        return dataLibrary.getIntRegion(vec.getData(), (int) fromIdx, sizeInt, bufferWrapper, bufferWrapperInterop);
    }

    private static void validateArguments(long fromIdx, long size) {
        if (fromIdx > Integer.MAX_VALUE || size > Integer.MAX_VALUE) {
            throw RError.error(RError.NO_CALLER, Message.LONG_VECTORS_NOT_SUPPORTED);
        }
    }


    @Fallback
    public long getRegionFallback(Object x,
                                  @SuppressWarnings("unused") Object fromIdx,
                                  @SuppressWarnings("unused") Object size,
                                  @SuppressWarnings("unused") Object buffer) {
        throw RInternalError.shouldNotReachHere("Type error: Unexpected type:" + x.getClass());
    }
}
