/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.unary;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.utilities.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.access.ConstantNode.ConstantIntegerScalarNode;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

@NodeFields({@NodeField(name = "preserveNames", type = boolean.class), @NodeField(name = "dimensionsPreservation", type = boolean.class), @NodeField(name = "attrPreservation", type = boolean.class)})
public abstract class CastNode extends UnaryNode {

    private final BranchProfile listCoercionErrorBranch = BranchProfile.create();
    private final ConditionProfile hasDimNamesProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile hasDimensionsProfile = ConditionProfile.createBinaryProfile();
    private final ConditionProfile hasNamesProfile = ConditionProfile.createBinaryProfile();
    private final RAttributeProfiles attrProfiles = RAttributeProfiles.create();

    public abstract Object executeCast(Object value);

    protected abstract boolean isPreserveNames();

    protected abstract boolean isDimensionsPreservation();

    protected abstract boolean isAttrPreservation();

    protected RError throwCannotCoerceListError(String type) {
        listCoercionErrorBranch.enter();
        throw RError.error(getSourceSection(), RError.Message.LIST_COERCION, type);
    }

    protected int[] getPreservedDimensions(RAbstractContainer operand) {
        int[] dimensions = isDimensionsPreservation() ? operand.getDimensions() : null;
        int[] preservedDimensions = hasDimensionsProfile.profile(dimensions != null) ? dimensions : null;
        return preservedDimensions;
    }

    protected RStringVector getPreservedNames(RAbstractContainer operand) {
        RStringVector names = isPreserveNames() ? operand.getNames(attrProfiles) : null;
        RStringVector preservedNames = hasNamesProfile.profile(names != null) ? names : null;
        return preservedNames;
    }

    protected void preserveDimensionNames(RAbstractContainer operand, RVector ret) {
        RList dimNames = isDimensionsPreservation() ? operand.getDimNames(attrProfiles) : null;
        if (hasDimNamesProfile.profile(dimNames != null)) {
            ret.setDimNames((RList) dimNames.copy());
        }
    }

    public static RNode toInteger(RNode value, boolean preserveNames, boolean dimensionsPreservation, boolean attrPreservation) {
        if (value instanceof ConstantIntegerScalarNode) {
            return value;
        }
        return CastIntegerNodeGen.create(value, preserveNames, dimensionsPreservation, attrPreservation);
    }
}
