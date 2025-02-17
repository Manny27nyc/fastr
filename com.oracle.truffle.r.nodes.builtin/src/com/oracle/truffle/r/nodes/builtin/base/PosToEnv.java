/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.runtime.builtins.RBehavior.READS_STATE;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.function.GetCallerFrameNode;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.env.REnvironment;

@RBuiltin(name = "pos.to.env", kind = PRIMITIVE, parameterNames = {"pos"}, behavior = READS_STATE)
public abstract class PosToEnv extends RBuiltinNode.Arg1 {

    static {
        Casts casts = new Casts(PosToEnv.class);
        casts.arg(0).defaultWarningContext(RError.NO_CALLER).asIntegerVector().findFirst().mustNotBeNA();
    }

    public static PosToEnv create() {
        return PosToEnvNodeGen.create();
    }

    @Specialization(guards = "isMinusOne(x)")
    protected Object doPosToEnvMinusOne(VirtualFrame frame, @SuppressWarnings("unused") int x,
                    @Cached("create()") GetCallerFrameNode callerFrameNode) {
        if (REnvironment.isGlobalEnvFrame(frame)) {
            throw error(Message.NO_ENCLOSING_ENVIRONMENT);
        }
        return REnvironment.frameToEnvironment(callerFrameNode.execute(frame));
    }

    @Specialization(guards = "!isMinusOne(x)")
    protected REnvironment doPosToEnv(int x) {
        try {
            return REnvironment.getFromSearchPath(getRContext(), x - 1);
        } catch (IndexOutOfBoundsException ex) {
            throw error(Message.INVALID_ARG, "'pos'");
        }
    }

    protected static boolean isMinusOne(int x) {
        return x == -1;
    }
}
