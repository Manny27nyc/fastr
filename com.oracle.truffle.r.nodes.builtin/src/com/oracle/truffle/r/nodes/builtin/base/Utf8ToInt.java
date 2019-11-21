/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.notEmpty;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.size;
import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.stringValue;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.PURE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RIntVector;

@RBuiltin(name = "utf8ToInt", kind = INTERNAL, parameterNames = {"x"}, behavior = PURE)
public abstract class Utf8ToInt extends RBuiltinNode.Arg1 {

    static {
        Casts casts = new Casts(Utf8ToInt.class);
        casts.arg(0, "x").defaultError(RError.Message.ARG_MUST_BE_CHARACTER_VECTOR_LENGTH_ONE, "x").mustBe(stringValue()).asStringVector().mustBe(notEmpty()).shouldBe(size(1),
                        RError.Message.ARG_SHOULD_BE_CHARACTER_VECTOR_LENGTH_ONE).findFirst();
    }

    @Specialization
    protected RIntVector utf8ToInt(String value) {
        RIntVector ret;
        if (!RRuntime.isNA(value)) {
            int valueLen = value.length();
            int[] result = new int[valueLen];
            for (int i = 0; i < valueLen; i++) {
                char c = value.charAt(i);
                result[i] = c;
            }
            ret = RDataFactory.createIntVector(result, true);
        } else { // NA_character_
            ret = RDataFactory.createIntVector(new int[]{RRuntime.INT_NA}, false);
        }
        return ret;
    }
}
