/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.model.*;

public abstract class RSequence extends RBounded implements RAbstractVector {

    private final int length;

    protected RSequence(int length) {
        this.length = length;
    }

    @Override
    protected final int internalGetLength() {
        return length;
    }

    public boolean isComplete() {
        return true;
    }

    public boolean hasDimensions() {
        return false;
    }

    public int[] getDimensions() {
        return null;
    }

    public final RVector createVector() {
        return internalCreateVector();
    }

    protected abstract RVector internalCreateVector();

    @Override
    public RAbstractVector copy() {
        return createVector();
    }

    @Override
    public RAbstractVector copyDropAttributes() {
        return createVector();
    }

    @Override
    public RAbstractVector copyWithNewDimensions(int[] newDimensions) {
        return createVector().copyWithNewDimensions(newDimensions);
    }

    @Override
    public Object getNames() {
        return RNull.instance;
    }

    @Override
    public RList getDimNames() {
        return null;
    }

    @Override
    public Object getRowNames() {
        return RNull.instance;
    }

    @Override
    public RAttributes initAttributes() {
        // TODO implement
        assert false;
        return null;
    }

    @Override
    public RAttributes getAttributes() {
        return null;
    }

    public boolean isMatrix() {
        return false;
    }

    public boolean isArray() {
        return false;
    }

    public boolean isObject() {
        return false;
    }

    @Override
    public RVector materializeNonSharedVector() {
        RVector resultVector = this.materialize();
        assert !resultVector.isShared();
        return resultVector;
    }

    @Override
    public RShareable materializeToShareable() {
        return this.materialize();
    }

    @Override
    public void transferElementSameType(int toIndex, RAbstractVector fromVector, int fromIndex) {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    public RVector copyResizedWithDimensions(int[] newDimensions) {
        // TODO support for higher dimensions
        assert newDimensions.length == 2;
        RVector result = copyResized(newDimensions[0] * newDimensions[1], false);
        result.setDimensions(newDimensions);
        return result;
    }

}
