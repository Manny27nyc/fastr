/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.ffi.impl.upcalls;

import com.oracle.truffle.r.ffi.impl.nodes.AsCharNode;
import com.oracle.truffle.r.ffi.impl.nodes.AsIntegerNode;
import com.oracle.truffle.r.ffi.impl.nodes.AsLogicalNode;
import com.oracle.truffle.r.ffi.impl.nodes.AsRealNode;
import com.oracle.truffle.r.ffi.impl.nodes.AttributesAccessNodes.ATTRIB;
import com.oracle.truffle.r.ffi.impl.nodes.AttributesAccessNodes.CopyMostAttrib;
import com.oracle.truffle.r.ffi.impl.nodes.AttributesAccessNodes.TAG;
import com.oracle.truffle.r.ffi.impl.nodes.CoerceNodes.CoerceVectorNode;
import com.oracle.truffle.r.ffi.impl.nodes.CoerceNodes.VectorToPairListNode;
import com.oracle.truffle.r.ffi.impl.nodes.ListAccessNodes.CADDRNode;
import com.oracle.truffle.r.ffi.impl.nodes.ListAccessNodes.CADRNode;
import com.oracle.truffle.r.ffi.impl.nodes.ListAccessNodes.CARNode;
import com.oracle.truffle.r.ffi.impl.nodes.ListAccessNodes.CDDRNode;
import com.oracle.truffle.r.ffi.impl.nodes.ListAccessNodes.CDRNode;
import com.oracle.truffle.r.ffi.impl.nodes.MiscNodes.LENGTHNode;
import com.oracle.truffle.r.ffi.processor.RFFICstring;
import com.oracle.truffle.r.ffi.processor.RFFINoGC;
import com.oracle.truffle.r.ffi.processor.RFFIUpCallNode;

/**
 * This class defines methods that match the functionality of the macro/function definitions in the
 * R header files, e.g. {@code Rinternals.h} that are used by C/C++ code to call into the R
 * implementation. For ease of identification, we use method names that match the names in the R
 * header files. These methods should never be called from normal FastR code.
 *
 * The set is incomplete; these are the functions that have been found to be used to at this time of
 * writing. From the GNU R perspective all {@code Object} parameters are {@code SEXP} instances.
 * Some of the functions are typed with a specific return type but, again, this is a {@code SEXP} in
 * GNU R terms. The native side does not require a specific Java type.
 *
 * N.B. It is important not to be too specific about types owing the support for Truffle interop
 * implementations. For example, many arguments are "strings" but we do not specify them as
 * {@code String} here. In particular do not use array types as these are passed as custom Truffle
 * objects in some implementations.
 *
 * N.B. Although some functions do not return results, the Truffle interop EXECUTE message machinery
 * does not like {@code void} functions, so we just use {@code int}. Evidently the actual value does
 * not matter.
 */
public interface StdUpCallsRFFI {
    // Checkstyle: stop method name check

    Object Rf_ScalarInteger(int value);

    Object Rf_ScalarLogical(int value);

    Object Rf_ScalarDouble(double value);

    Object Rf_ScalarString(Object value);

    @RFFIUpCallNode(AsIntegerNode.class)
    int Rf_asInteger(Object x);

    @RFFIUpCallNode(AsRealNode.class)
    double Rf_asReal(Object x);

    @RFFIUpCallNode(AsLogicalNode.class)
    int Rf_asLogical(Object x);

    @RFFIUpCallNode(AsCharNode.class)
    Object Rf_asChar(Object x);

    @RFFIUpCallNode(CoerceVectorNode.class)
    Object Rf_coerceVector(Object x, int mode);

    Object Rf_mkCharLenCE(@RFFICstring(convert = false) Object bytes, int len, int encoding);

    Object Rf_cons(Object car, Object cdr);

    int /* void */ Rf_defineVar(Object symbolArg, Object value, Object envArg);

    Object R_do_MAKE_CLASS(@RFFICstring String clazz);

    Object R_do_new_object(Object classDef);

    /**
     * WARNING: argument order reversed from Rf_findVarInFrame!
     */
    Object Rf_findVar(Object symbolArg, Object envArg);

    Object Rf_findVarInFrame(Object envArg, Object symbolArg);

    Object Rf_findVarInFrame3(Object envArg, Object symbolArg, int doGet);

    @RFFIUpCallNode(ATTRIB.class)
    Object ATTRIB(Object obj);

    Object Rf_getAttrib(Object obj, Object name);

    int /* void */ Rf_setAttrib(Object obj, Object name, Object val);

    int Rf_inherits(Object x, @RFFICstring String clazz);

    Object Rf_install(@RFFICstring String name);

    Object Rf_installChar(Object name);

    Object Rf_lengthgets(Object x, int newSize);

    int Rf_isString(Object x);

    int Rf_isNull(Object x);

    Object Rf_PairToVectorList(Object x);

    int /* void */ Rf_error(@RFFICstring String msg);

    int /* void */ Rf_warning(@RFFICstring String msg);

    int /* void */ Rf_warningcall(Object call, @RFFICstring String msg);

    int /* void */ Rf_errorcall(Object call, @RFFICstring String msg);

    Object Rf_allocVector(int mode, long n);

    Object Rf_allocArray(int mode, Object dimsObj);

    Object Rf_allocMatrix(int mode, int nrow, int ncol);

    int Rf_nrows(Object x);

    int Rf_ncols(Object x);

    @RFFIUpCallNode(LENGTHNode.class)
    int LENGTH(Object x);

    int /* void */ SET_STRING_ELT(Object x, long i, Object v);

    int /* void */ SET_VECTOR_ELT(Object x, long i, Object v);

    @RFFINoGC
    Object RAW(Object x);

    @RFFINoGC
    Object LOGICAL(Object x);

    @RFFINoGC
    Object INTEGER(Object x);

    @RFFINoGC
    Object REAL(Object x);

    @RFFINoGC
    Object COMPLEX(Object x);

    Object STRING_ELT(Object x, long i);

    Object VECTOR_ELT(Object x, long i);

    int NAMED(Object x);

    Object SET_NAMED_FASTR(Object x, int v);

    Object SET_TYPEOF_FASTR(Object x, int v);

    int TYPEOF(Object x);

    int OBJECT(Object x);

    Object Rf_duplicate(Object x, int deep);

    long Rf_any_duplicated(Object x, int fromLast);

    Object PRINTNAME(Object x);

    @RFFIUpCallNode(TAG.class)
    Object TAG(Object e);

    @RFFIUpCallNode(CARNode.class)
    Object CAR(Object e);

    @RFFIUpCallNode(CDRNode.class)
    Object CDR(Object e);

    @RFFIUpCallNode(CADRNode.class)
    Object CADR(Object e);

    @RFFIUpCallNode(CADDRNode.class)
    Object CADDR(Object e);

    @RFFIUpCallNode(CDDRNode.class)
    Object CDDR(Object e);

    Object SET_TAG(Object x, Object y);

    Object SETCAR(Object x, Object y);

    Object SETCDR(Object x, Object y);

    Object SETCADR(Object x, Object y);

    Object SYMVALUE(Object x);

    int /* void */ SET_SYMVALUE(Object x, Object v);

    int R_BindingIsLocked(Object sym, Object env);

    Object R_FindNamespace(Object name);

    Object Rf_eval(Object expr, Object env);

    Object Rf_findFun(Object symbolObj, Object envObj);

    Object Rf_GetOption1(Object tag);

    int /* void */ Rf_gsetVar(Object symbol, Object value, Object rho);

    int /* void */ DUPLICATE_ATTRIB(Object to, Object from);

    int R_compute_identical(Object x, Object y, int flags);

    int /* void */ Rf_copyListMatrix(Object s, Object t, int byrow);

    int /* void */ Rf_copyMatrix(Object s, Object t, int byrow);

    Object R_tryEval(Object expr, Object env, int silent);

    Object R_ToplevelExec();

    int RDEBUG(Object x);

    int /* void */ SET_RDEBUG(Object x, int v);

    int RSTEP(Object x);

    int /* void */ SET_RSTEP(Object x, int v);

    Object ENCLOS(Object x);

    Object PRVALUE(Object x);

    Object R_ParseVector(Object text, int n, Object srcFile);

    Object R_lsInternal3(Object envArg, int allArg, int sortedArg);

    String R_HomeDir();

    int IS_S4_OBJECT(Object x);

    int /* void */ SET_S4_OBJECT(Object x);

    int /* void */ UNSET_S4_OBJECT(Object x);

    int /* void */ Rprintf(@RFFICstring String message);

    int /* void */ GetRNGstate();

    int /* void */ PutRNGstate();

    double unif_rand();

    Object Rf_classgets(Object x, Object y);

    Object R_MakeExternalPtr(long addr, Object tag, Object prot);

    long R_ExternalPtrAddr(Object x);

    Object R_ExternalPtrTag(Object x);

    Object R_ExternalPtrProtected(Object x);

    int /* void */ R_SetExternalPtrAddr(Object x, long addr);

    int /* void */ R_SetExternalPtrTag(Object x, Object tag);

    int /* void */ R_SetExternalPtrProtected(Object x, Object prot);

    int /* void */ R_CleanUp(int sa, int status, int runlast);

    Object R_NewHashedEnv(Object parent, Object initialSize);

    int PRSEEN(Object x);

    Object PRENV(Object x);

    Object R_PromiseExpr(Object x);

    Object PRCODE(Object x);

    Object R_CHAR(Object x);

    Object R_new_custom_connection(@RFFICstring String description, @RFFICstring String mode, @RFFICstring String className, Object readAddr);

    int R_ReadConnection(int fd, long bufAddress, int size);

    int R_WriteConnection(int fd, long bufAddress, int size);

    Object R_GetConnection(int fd);

    String getSummaryDescription(Object x);

    String getConnectionClassString(Object x);

    String getOpenModeString(Object x);

    boolean isSeekable(Object x);

    Object R_do_slot(Object o, Object name);

    Object R_do_slot_assign(Object o, Object name, Object value);

    Object R_MethodsNamespace();

    int Rf_str2type(@RFFICstring String name);

    int FASTR_getConnectionChar(Object obj);

    double Rf_dunif(double a, double b, double c, int d);

    double Rf_qunif(double a, double b, double c, int d, int e);

    double Rf_punif(double a, double b, double c, int d, int e);

    double Rf_runif(double a, double b);

    Object Rf_namesgets(Object vec, Object val);

    @RFFIUpCallNode(CopyMostAttrib.class)
    int Rf_copyMostAttrib(Object x, Object y);

    @RFFIUpCallNode(VectorToPairListNode.class)
    Object Rf_VectorToPairList(Object x);

    @RFFIUpCallNode(CADDRNode.class)
    Object Rf_asCharacterFactor(Object x);
}
