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
package com.oracle.truffle.r.runtime;

import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.runtime.RPackages.RPackage;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.envframe.*;

/**
 * Denotes an R {@code environment}.
 *
 * Abstractly, environments consist of a frame (collection of named objects), and a pointer to an
 * enclosing environment.
 *
 * R environments can be named or unnamed. {@code base} is an example of a named environment.
 * Environments associated with function invocations are unnamed. The {@code environmentName}
 * builtin returns "" for an unnamed environment. However, unnamed environments print using a unique
 * numeric id in the place where the name would appear for a named environment. This is finessed
 * using the {@link #getPrintNameHelper} method. Further, environments on the {@code search} path
 * return a yet different name in the result of {@code search}, e.g. ".GlobalEnv", "package:base",
 * which is handled via {@link #getSearchName()}. Finally, environments can be given names using the
 * {@code attr} function, and (then) they print differently again. Of the default set of
 * environments, only "Autoloads" has a {@code name} attribute.
 * <p>
 * Environments can also be locked preventing any bindings from being added or removed. N.B. the
 * empty environment can't be assigned to but is not locked (see GnuR). Further, individual bindings
 * within an environment can be locked, although they can be removed unless the environment is also
 * locked.
 * <p>
 * Environments are used for many different things in R, including something close to a
 * {@link java.util.Map} created in R code using the {@code new.env} function. This is the only case
 * where the {@code size} parameter is specified. All the other instances of environments are
 * implicitly created by the virtual machine, for example, on function call.
 * <p>
 * The different kinds of environments are implemented as subclasses. The variation in behavior
 * regarding access to the "frame" is handled by delegation to an instance of
 * {@link REnvFrameAccess}. Conceptually, variables are searched for by starting in a given
 * environment and searching backwards through the "parent" chain. In practice, variables are
 * accessed in the Truffle environment using {@link Frame} instances which may, in some cases such
 * as compiled code, not even exist as actual objects. Therefore, we have to keep the name lookup in
 * the two worlds in sync. This is an issue during initialization, and when a new environment is
 * attached, cf. {@link #attach}.
 * <p>
 * Packages have three associated environments, "package:xxx", "imports:xxx" and "namespace:xxx",
 * for package "xxx". The {@code base} package is a special case in that it does not have an
 * "imports" environment. The parent of "package:base" is the empty environment, but the parent of
 * "namespace:base" is the global environment.
 *
 */
public abstract class REnvironment {
    public enum PackageKind {
        PACKAGE,
        IMPORTS,
        NAMESPACE
    }

    /**
     * Tagging interface that indicates this is a "package" environment.
     */
    private interface IsPackage {

    }

    public static class PutException extends Exception {
        private static final long serialVersionUID = 1L;

        public PutException(String message) {
            super(message);
        }
    }

    private static final REnvFrameAccess defaultFrameAccess = new REnvFrameAccessBindingsAdapter();
    private static final REnvFrameAccess noFrameAccess = new REnvFrameAccess();

    public static final String UNNAMED = "";
    private static final String NAME_ATTR_KEY = "name";
    private static final String PATH_ATTR_KEY = "path";

    private static final Empty emptyEnv = new Empty();
    private static Global globalEnv;
    private static REnvironment initialGlobalEnvParent;
    private static Base baseEnv;
    private static Autoload autoloadEnv;

    /**
     * The environments returned by the R {@code search} function.
     */
    private static ArrayList<REnvironment> searchPath;

    private REnvironment parent;
    private final String name;
    final REnvFrameAccess frameAccess;
    private Map<String, Object> attributes;
    private boolean locked;

    /**
     * Value returned by {@code emptyenv()}.
     */
    public static Empty emptyEnv() {
        return emptyEnv;
    }

    /**
     * Value returned by {@code globalenv()}.
     */
    public static Global globalEnv() {
        assert globalEnv != null;
        return globalEnv;
    }

    /**
     * Returns {@code true} iff {@code frame} is that associated with {@code env}.
     */
    public static boolean isFrameForEnv(MaterializedFrame frame, REnvironment env) {
        Object id = env.frameAccess.id();
        if (id == null) {
            return false;
        }
        FrameSlot idSlot = frame.getFrameDescriptor().findFrameSlot(id);
        if (idSlot == null) {
            return false;
        }
        try {
            return frame.getObject(idSlot) == id;
        } catch (FrameSlotTypeException fste) {
            return false;
        }
    }

    /**
     * Check whether the given frame is indeed the frame stored in the global environment.
     */
    public static boolean isGlobalEnvFrame(MaterializedFrame frame) {
        return isFrameForEnv(frame, globalEnv);
    }

    /**
     * Value returned by {@code baseenv()}. This is the "package:base" environment.
     */
    public static Package baseEnv() {
        assert baseEnv != null;
        return baseEnv;
    }

    /**
     * Value set in {@code .baseNameSpaceEnv} variable. This is the "namespace:base" environment.
     */
    public static Namespace baseNamespaceEnv() {
        assert baseEnv != null;
        return baseEnv.getNamespace();
    }

    /**
     * Value set in the {@code .AutoloadEnv} variable.
     */
    public static Autoload autoloadEnv() {
        assert autoloadEnv != null;
        return autoloadEnv;
    }

    /**
     * Invoked on startup to setup the global values and package search path. Owing to the
     * restrictions on storing {@link VirtualFrame} instances, this method creates the
     * {@link VirtualFrame} instance(s) for the packages and evaluates any associated R code using
     * that frame and then installs it in the search path correctly so that Truffle code can locate
     * objects defined by the R code.
     *
     * @param globalFrame this is the anchor frame to which the package search path is attached
     * @param baseFrame this is for the base frame (we can't create it because our caller also needs
     *            to eval in it)
     * @param rPackages the list of packages to add to the search path on startup, including
     *            {@code base}.
     */
    public static void initialize(VirtualFrame globalFrame, VirtualFrame baseFrame, ArrayList<RPackage> rPackages) {
        // The base "package" is special, it has no "imports" and
        // its "namespace" parent is globalenv

        // Populate the base package.
        // There is a circularity here in that we can't materialize the frame
        // until we have evaluated the R code, so if that happened to call baseenv() we have
        // a problem.
        baseEnv = new Base(baseFrame);
        // autoload always next, has no R state
        autoloadEnv = new Autoload();
        globalEnv = new Global(autoloadEnv, globalFrame);
        initSearchList();

        // load base package first
        RPackages.loadBuiltin("base", baseFrame);
        // now load rPackages, we need a new VirtualFrame for each
        REnvironment pkgParent = autoloadEnv;
        for (RPackage rPackage : rPackages) {
            VirtualFrame pkgFrame = RRuntime.createVirtualFrame();
            RPackages.loadBuiltin(rPackage.name, pkgFrame);
            Package pkgEnv = new Package(pkgParent, rPackage.name, pkgFrame, rPackage.path);
            attach(2, pkgEnv);
            pkgParent = pkgEnv;
        }

        initialGlobalEnvParent = pkgParent;
        baseEnv.getNamespace().setParent(globalEnv);
        // set up the initial search path
    }

    private static void initSearchList() {
        searchPath = new ArrayList<>();
        REnvironment env = globalEnv;
        do {
            searchPath.add(env);
            env = env.parent;
        } while (env != emptyEnv);
    }

    /**
     * Intended for use by unit test environment to reset the environment to a clean state. We want
     * to reset the {@link #globalEnv}, and by extension {@link #searchPath} but not everything
     * else. This evidently depends on there not being destructive tests, and in particular any that
     * mess with the set of default packages.
     *
     */
    public static void resetForTest(VirtualFrame globalFrame) {
        globalEnv = new Global(initialGlobalEnvParent, globalFrame);
        // update .GlobalEnv
        try {
            baseEnv.put(".GlobalEnv", globalEnv);
        } catch (PutException ex) {
            Utils.fail("could not update .GlobalEnv");
        }
        initSearchList();
        // one more thing, namespace:base always has globalEnv as it's parent, so update that
        baseEnv.getNamespace().setParent(globalEnv);
    }

    /**
     * Data for the {@code search} function.
     */
    public static String[] searchPath() {
        String[] result = new String[searchPath.size()];
        for (int i = 0; i < searchPath.size(); i++) {
            REnvironment env = searchPath.get(i);
            result[i] = env.getSearchName();
        }
        return result;
    }

    /**
     * Lookup an environment by name on the search path.
     *
     * @param name the name as it would appear in R the {@code search} function.
     * @return the environment or {@code null} if not found.
     */
    public static REnvironment lookupOnSearchPath(String name) {
        int i = lookupIndexOnSearchPath(name);
        return i <= 0 ? null : searchPath.get(i - 1);
    }

    /**
     * Lookup the index of an environment by name on the search path.
     *
     * @param name the name as it would appear in R the {@code search} function.
     * @return the index (1-based) or {@code 0} if not found.
     */
    public static int lookupIndexOnSearchPath(String name) {
        for (int i = 0; i < searchPath.size(); i++) {
            REnvironment env = searchPath.get(i);
            String searchName = env.getSearchName();
            if (searchName.equals(name)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Get the registered {@link Namespace} environment {@code name}, or {@code null} if not found.
     * TODO this only searches the search path; it seems namespaces can also be registered without
     * attaching first.
     */
    public static Namespace getRegisteredNamespace(String name) {
        Package pkgEnv = (Package) lookupOnSearchPath("package:" + name);
        if (pkgEnv == null) {
            return null;
        } else {
            return pkgEnv.getNamespace();
        }
    }

    /**
     * Attach (insert) an environment as position {@code pos} in the search path. TODO handle
     * packages
     *
     * @param pos position for insert, {@code pos >= 2}. As per GnuR, values beyond the index of
     *            "base" are truncated to the index before "base".
     */
    public static void attach(int pos, REnvironment env) {
        assert pos >= 2;
        // N.B. pos is 1-based
        int bpos = pos - 1;
        if (bpos > searchPath.size() - 1) {
            bpos = searchPath.size() - 1;
        }
        // Insert in the REnvironment search path, adjusting the parent fields appropriately
        // In the default case (pos == 2), envAbove is the Global env
        REnvironment envAbove = searchPath.get(bpos - 1);
        REnvironment envBelow = searchPath.get(bpos);
        env.parent = envBelow;
        envAbove.parent = env;
        searchPath.add(bpos, env);
        // Now must adjust the Frame world so that unquoted variable lookup works
        MaterializedFrame aboveFrame = envAbove.frameAccess.getFrame();
        MaterializedFrame envFrame = env.frameAccess.getFrame();
        if (envFrame == null) {
            envFrame = new REnvMaterializedFrame((REnvMapFrameAccess) env.frameAccess);
        }
        RArguments.attachFrame(aboveFrame, envFrame);
    }

    public static class DetachException extends Exception {
        private static final long serialVersionUID = 1L;

        DetachException(String msg) {
            super(msg);
        }
    }

    /**
     * Detach the environment at search position {@code pos}. TODO handle packages
     *
     * @param unload if {@code true} and env is a package, unload associated namespace
     * @param force the detach even if there are dependent packages
     * @return the {@link REnvironment} that was detached.
     */
    public static REnvironment detach(int pos, boolean unload, boolean force) throws DetachException {
        if (pos == searchPath.size()) {
            throw new DetachException("detaching \"package:base\" is not allowed");
        }
        if (pos <= 0 || pos >= searchPath.size()) {
            throw new DetachException("subscript out of range");
        }
        assert pos != 1; // explicitly checked in caller
        // N.B. pos is 1-based
        int bpos = pos - 1;
        REnvironment envAbove = searchPath.get(bpos - 1);
        REnvironment envToRemove = searchPath.get(bpos);
        envAbove.parent = envToRemove.parent;
        searchPath.remove(bpos);
        MaterializedFrame aboveFrame = envAbove.frameAccess.getFrame();
        RArguments.detachFrame(aboveFrame);
        if (envToRemove.frameAccess instanceof REnvMapFrameAccess) {
            ((REnvMapFrameAccess) envToRemove.frameAccess).detach();
        }
        return envToRemove;
    }

    @SlowPath
    public static String packageQualName(PackageKind packageKind, String packageName) {
        StringBuffer sb = new StringBuffer();
        sb.append(packageKind.name().toLowerCase());
        sb.append(':');
        sb.append(packageName);
        return sb.toString();
    }

    // end of static members

    /**
     * The basic constructor; just assigns the essential fields.
     */
    protected REnvironment(REnvironment parent, String name, REnvFrameAccess frameAccess) {
        this.parent = parent;
        this.name = name;
        this.frameAccess = frameAccess;
    }

    /**
     * An environment associated with a {@link VirtualFrame} where is it is important to establish
     * the parent environment's frame as the enclosing frame in {@code frame} <i>before</i> it is
     * materialised.
     */
    protected REnvironment(REnvironment parent, String name, VirtualFrame frame) {
        this(parent, name, setEnclosingHelper(parent, frame));
        RArguments.setEnvironment(frame, this);
    }

    /**
     * Helper method to comply with constructor ordering rules.
     */
    private static REnvFrameAccess setEnclosingHelper(REnvironment parent, VirtualFrame frame) {
        RArguments.setEnclosingFrame(frame, parent.getFrame());
        // This call invokes frame.materialize();
        return new REnvTruffleFrameAccess(frame);
    }

    /**
     * An environment associated with an already materialized frame.
     */
    protected REnvironment(REnvironment parent, String name, MaterializedFrame frame) {
        this(parent, name, new REnvTruffleFrameAccess(frame));
    }

    public REnvironment getParent() {
        return parent;
    }

    /**
     * Explicity set the parent of an environment. TODO Change the enclosingFrame of (any)
     * associated Truffle frame
     */
    public void setParent(REnvironment env) {
        parent = env;
    }

    /**
     * The "simple" name of the environment. For "package:xxx", "namespace:xxx", "imports:xxx", this
     * is "xxx". If the environment has been given a "name" attribute, then it is that value. This
     * is the value returned by the R {@code environmentName} function.
     */
    public String getName() {
        String attrName = attributes == null ? null : (String) attributes.get(NAME_ATTR_KEY);
        return attrName != null ? attrName : name;
    }

    /**
     * The "print" name of an environment, i.e. what is output for {@code print(env)}.
     */
    @SlowPath
    public String getPrintName() {
        return new StringBuilder("<environment: ").append(getPrintNameHelper()).append('>').toString();
    }

    protected String getPrintNameHelper() {
        if (name.equals(UNNAMED)) {
            return String.format("%#x", hashCode());
        } else {
            return getName();
        }
    }

    /**
     * Name returned by the {@code search()} function. The default is just the simple name, but
     * globalenv() is different.
     */
    protected String getSearchName() {
        String result = getName();
        return result;
    }

    /**
     * Return the {@link MaterializedFrame} associated with this environment, or {@code null} if
     * there is none.
     */
    public MaterializedFrame getFrame() {
        return frameAccess.getFrame();
    }

    public void lock(boolean bindings) {
        locked = true;
        if (bindings) {
            frameAccess.lockBindings();
        }
    }

    public boolean isLocked() {
        return locked;
    }

    public Object get(String key) {
        return frameAccess.get(key);
    }

    public void put(String key, Object value) throws PutException {
        if (locked) {
            // if the binding exists already, can try to update it
            if (frameAccess.get(key) == null) {
                throw new PutException("cannot add bindings to a locked environment");
            }
        }
        frameAccess.put(key, value);
    }

    public void safePut(String key, Object value) {
        try {
            put(key, value);
        } catch (PutException ex) {
            Utils.fail("exception in safePut");
        }
    }

    public void rm(String key) throws PutException {
        if (locked) {
            throw new PutException("cannot remove bindings from a locked environment");
        }
        frameAccess.rm(key);
    }

    public RStringVector ls(boolean allNames, String pattern) {
        return frameAccess.ls(allNames, pattern);
    }

    public void lockBinding(String key) {
        frameAccess.lockBinding(key);
    }

    public void unlockBinding(String key) {
        frameAccess.unlockBinding(key);

    }

    public boolean bindingIsLocked(String key) {
        return frameAccess.bindingIsLocked(key);
    }

    @SlowPath
    public void setAttr(String attrName, Object value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(attrName, value);
    }

    @SlowPath
    public void removeAttr(String attrName) {
        if (attributes != null) {
            attributes.remove(attrName);
        }
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    @SlowPath
    public String toString() {
        return getPrintName();
    }

    /**
     * Denotes the "namespace:xxx" environment of an R package. The "parent" is the associated
     * "imports" environment, except for "base" where it is globalEnv
     */
    private static class Namespace extends REnvironment {
        Namespace(REnvironment parent, String name, REnvFrameAccess frameAccess) {
            super(parent, name, frameAccess);
        }

        @Override
        protected String getPrintNameHelper() {
            return "namespace:" + getName();
        }
    }

    /**
     * Denotes the "imports:xxx" environment of an R package.
     */
    private static class Imports extends REnvironment {
        Imports(String name, REnvFrameAccess frameAccess) {
            super(baseEnv.getNamespace(), UNNAMED, frameAccess);
            setAttr(NAME_ATTR_KEY, "imports:" + name);
        }

    }

    /**
     * Denotes an environment associated with an R package. This represents the "package:xxx"; the
     * "namespace:xxx" and "imports:xxx" environments are stored as fields of this instance.
     */
    public static class Package extends REnvironment implements IsPackage {
        private final Imports importsEnv;
        private final Namespace namespaceEnv;

        Package(REnvironment parent, String name, VirtualFrame frame, String path) {
            // This sets up the EnvFrameAccess instance, which is shared by the
            // Namespace (and Imports?) environments.
            super(parent, name, frame);
            this.importsEnv = new Imports(name, this.frameAccess);
            this.namespaceEnv = new Namespace(this.importsEnv, name, this.frameAccess);
            setName(name);
            setPath(path);
        }

        protected void setName(String name) {
            setAttr(NAME_ATTR_KEY, "package:" + name);
        }

        protected void setPath(String path) {
            setAttr(PATH_ATTR_KEY, path);
        }

        /**
         * Constructor for {@link Base}. During initialization the parent is emptyEnv. Ultimately it
         * will be set to globalEnv..
         */
        protected Package(VirtualFrame frame) {
            super(emptyEnv, "base", frame);
            this.importsEnv = null;
            this.namespaceEnv = new Namespace(emptyEnv, "base", this.frameAccess);
            RArguments.setEnvironment(frame, this.namespaceEnv);
        }

        public Namespace getNamespace() {
            return namespaceEnv;
        }
    }

    private static class Base extends Package {
        Base(VirtualFrame frame) {
            super(frame);
        }

        @Override
        public void rm(String key) throws PutException {
            throw new PutException("cannot remove variables from the " + getPrintNameHelper() + " environment");
        }

        @Override
        protected void setName(String name) {
            // base not attributed
        }

        @Override
        protected void setPath(String path) {
            // base not attributed
        }

        @Override
        protected String getSearchName() {
            return "package:base";
        }
    }

    /**
     * The users workspace environment (so called global). The parent depends on the set of default
     * packages loaded.
     */
    public static final class Global extends REnvironment {

        private Global(REnvironment parent, VirtualFrame frame) {
            super(parent, "R_GlobalEnv", frame);
        }

        @Override
        protected String getSearchName() {
            return ".GlobalEnv";
        }

    }

    /**
     * When a function is invoked a {@link Function} environment may be created in response to the R
     * {@code environment()} base package function, and it will have an associated frame.
     */
    public static final class Function extends REnvironment {

        public Function(REnvironment parent, MaterializedFrame frame) {
            // function environments are not named
            super(parent, UNNAMED, frame);
        }

        /**
         * Specifically for {@code ls()}, we don't care about the parent, as the use is transient.
         */
        public static Function createLsCurrent(MaterializedFrame frame) {
            Function result = new Function(null, frame);
            return result;
        }

        public static Function create(REnvironment parent, MaterializedFrame frame) {
            Function result = new Function(parent, frame);
            return result;
        }

    }

    /**
     * Denotes an environment associated with a function definition during AST building.
     *
     * {@link FunctionDefinition} environments are created when a function is defined see
     * {@code RFunctionDefinitonNode} and {@code RTruffleVisitor}. In that situation the
     * {@code parent} is the lexically enclosing environment and there is no associated frame.
     */

    public static final class FunctionDefinition extends REnvironment {
        private FrameDescriptor descriptor;

        public FunctionDefinition(REnvironment parent) {
            // function environments are not named
            super(parent, UNNAMED, noFrameAccess);
            this.descriptor = new FrameDescriptor();
        }

        public FrameDescriptor getDescriptor() {
            return descriptor;
        }

    }

    /**
     * An environment explicitly created with, typically, {@code new.env}. Such environments are
     * always {@link #UNNAMED} but can be given a {@value #NAME_ATTR_KEY}.
     */
    public static final class NewEnv extends REnvironment {

        /**
         * Constructor for the {@code new.env} function.
         */
        public NewEnv(REnvironment parent, int size) {
            super(parent, UNNAMED, new REnvMapFrameAccess(size));
        }

        /**
         * Constructor for environment without a parent, e.g., for use by {@link #attach}.
         */
        public NewEnv(String name) {
            this(null, 0);
            setAttr(NAME_ATTR_KEY, name);
        }

    }

    public static String[] removeHiddenNames(String[] names) {
        int hiddenCount = 0;
        for (String name : names) {
            if (name.charAt(0) == '.') {
                hiddenCount++;
            }
        }
        if (hiddenCount > 0) {
            String[] newNames = new String[names.length - hiddenCount];
            int i = 0;
            for (String name : names) {
                if (name.charAt(0) == '.') {
                    continue;
                } else {
                    newNames[i++] = name;
                }
            }
            return newNames;
        }
        return names;
    }

    /**
     * A placeholder for the package autoload mechanism. N.B. Although "unnamed", it is given a name
     * with {@code attr} in GnuR.
     */
    private static final class Autoload extends REnvironment {
        Autoload() {
            super(baseEnv(), UNNAMED, baseEnv().getFrame());
            setAttr(NAME_ATTR_KEY, "Autoloads");
        }

    }

    /**
     * The empty environment has no runtime state and so can be allocated statically. TODO Attempts
     * to assign should cause an R error, if not prevented in caller. TODO check.
     */
    private static final class Empty extends REnvironment {

        private Empty() {
            super(null, "R_EmptyEnv", defaultFrameAccess);
        }

        @Override
        public void put(String key, Object value) throws PutException {
            throw new PutException("cannot assign values in the empty environment");
        }

    }

}
