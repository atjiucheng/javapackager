/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.openjfx.tools.packager.bundlers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.JLinkBundlerHelper;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.Platform;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.StandardBundlerParam;
import com.sun.openjfx.tools.packager.bundlers.Bundler.BundleType;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_RESOURCES;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_RESOURCES_LIST;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ARGUMENTS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.CATEGORY;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.COPYRIGHT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.DESCRIPTION;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.IDENTIFIER;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.JVM_OPTIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.JVM_PROPERTIES;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.LICENSE_FILE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.LICENSE_TYPE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MAIN_CLASS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MENU_HINT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.PREFERENCES_ID;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.PRELOADER_CLASS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SHORTCUT_HINT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SIGN_BUNDLE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SOURCE_DIR;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SYSTEM_WIDE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.TITLE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.USER_JVM_OPTIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VENDOR;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERBOSE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERSION;

public class BundleParams {

    protected final Map<String, ? super Object> params;

    public static final String PARAM_RUNTIME                = "runtime"; // RelativeFileSet
    public static final String PARAM_APP_RESOURCES          = "appResources"; // RelativeFileSet
    public static final String PARAM_TYPE                   = "type"; // BundlerType
    public static final String PARAM_BUNDLE_FORMAT          = "bundleFormat"; // String
    public static final String PARAM_ICON                   = "icon"; // String

    /* Name of bundle file and native launcher */
    public static final String PARAM_NAME                   = "name"; // String

    /* application vendor, used by most of the bundlers */
    public static final String PARAM_VENDOR                 = "vendor"; // String

    /* email name and email, only used for debian */
    public static final String PARAM_EMAIL                  = "email"; // String

    /* Copyright. Used on Mac */
    public static final String PARAM_COPYRIGHT              = "copyright"; // String

    /* GUID on windows for MSI, CFBundleIdentifier on Mac
       If not compatible with requirements then bundler either do not bundle
       or autogenerate */
    public static final String PARAM_IDENTIFIER             = "identifier"; // String

    /* shortcut preferences */
    public static final String PARAM_SHORTCUT               = "shortcutHint"; // boolean
    public static final String PARAM_MENU                   = "menuHint"; // boolean

    /* Application version. Format may differ for different bundlers */
    public static final String PARAM_VERSION                = "appVersion"; // String
    /* Application category. Used at least on Mac/Linux. Value is platform specific */
    public static final String PARAM_CATEGORY               = "applicationCategory"; // String

    /* Optional short application */
    public static final String PARAM_TITLE                  = "title"; // String

    /* Optional application description. Used by MSI and on Linux */
    public static final String PARAM_DESCRIPTION            = "description"; // String

    /* License type. Needed on Linux (rpm) */
    public static final String PARAM_LICENSE_TYPE           = "licenseType"; // String

    /* File(s) with license. Format is OS/bundler specific */
    public static final String PARAM_LICENSE_FILE          = "licenseFile"; // List<String>

    /* user or system level install.
       null means "default" */
    public static final String PARAM_SYSTEM_WIDE            = "systemWide"; // Boolean

    /* service/daemon install.
       null means "default" */
    public static final String PARAM_SERVICE_HINT           = "serviceHint"; // Boolean


    /* Main application class. Not used directly but used to derive default values */
    public static final String PARAM_APPLICATION_CLASS      = "applicationClass"; // String

    /* Adds a dialog to let the user choose a directory where the product will be installed. */
    public static final String PARAM_INSTALLDIR_CHOOSER     = "installdirChooser"; // Boolean

    /* Prevents from launching multiple instances of application.  */
    public static final String PARAM_SINGLETON              = "singleton"; // Boolean

    /**
     * create a new bundle with all default values
     */
    public BundleParams() {
        params = new HashMap<>();
    }

    /**
     * Create a bundle params with a copy of the params
     * @param params map of initial parameters to be copied in.
     */
    public BundleParams(Map<String, ?> params) {
        this.params = new HashMap<>(params);
    }

    public void addAllBundleParams(Map<String, ? super Object> p) {
        params.putAll(p);
    }

    private <C> C fetchParam(BundlerParamInfo<C> paramInfo) {
        return paramInfo.fetchFrom(params);
    }

    @SuppressWarnings("unchecked")
    private <C> C fetchParamWithDefault(Class<C> klass, C defaultValue, String... keys) {
        for (String key : keys) {
            Object o = params.get(key);
            if (klass.isInstance(o)) {
                return (C) o;
            } else if (params.containsKey(keys) && o == null) {
                return null;
            }
            //else if (o != null) {
            //TODO log an error.
            //}
        }
        return defaultValue;
    }

    private <C> C fetchParam(Class<C> klass, String... keys) {
        return fetchParamWithDefault(klass, null, keys);
    }

    // NOTE: we do not care about application parameters here
    // as they will be embedded into jar file manifest and
    // java launcher will take care of them!

    public Map<String, ? super Object> getBundleParamsAsMap() {
        return new HashMap<>(params);
    }

    public void setJvmargs(List<String> jvmargs) {
        putUnlessNullOrEmpty(JVM_OPTIONS.getID(), jvmargs);
    }

    public void setJvmUserArgs(Map<String, String> userArgs) {

        putUnlessNullOrEmpty(USER_JVM_OPTIONS.getID(), userArgs);
    }

    public void setJvmProperties(Map<String, String> jvmProperties) {
        putUnlessNullOrEmpty(JVM_PROPERTIES.getID(), jvmProperties);
    }

    public void setArguments(List<String> arguments) {
        putUnlessNullOrEmpty(ARGUMENTS.getID(), arguments);
    }

    public void setAddModules(String value) {
        putUnlessNull(StandardBundlerParam.ADD_MODULES.getID(), value);
    }

    public void setLimitModules(String value)  {
        putUnlessNull(StandardBundlerParam.LIMIT_MODULES.getID(), value);
    }

    public void setStripNativeCommands(boolean value) {
        putUnlessNull(StandardBundlerParam.STRIP_NATIVE_COMMANDS.getID(), value);
    }

    public void setDetectMods(boolean value) {
        putUnlessNull(JLinkBundlerHelper.DETECT_MODULES.getID(), value);
    }

    public void setSrcDir(String value) {
        putUnlessNull(SOURCE_DIR.getID(), value);
    }

    public void setModulePath(String value) {
        putUnlessNull(StandardBundlerParam.MODULE_PATH.getID(), value);
    }

    public void setMainModule(String value) {
        putUnlessNull(StandardBundlerParam.MODULE.getID(), value);
    }

    public void setDebug(String value) {
        putUnlessNull(JLinkBundlerHelper.DEBUG.getID(), value);
    }

    public String getApplicationID() {
        return fetchParam(IDENTIFIER);
    }

    public String getPreferencesID() {
        return fetchParam(PREFERENCES_ID);
    }

    public String getTitle() {
        return fetchParam(TITLE);
    }

    public void setTitle(String title) {
        putUnlessNull(PARAM_TITLE, title);
    }

    public String getApplicationClass() {
        return fetchParam(MAIN_CLASS);
    }

    public void setApplicationClass(String applicationClass) {
        putUnlessNull(PARAM_APPLICATION_CLASS, applicationClass);
    }

    public void setPrelaoderClass(String preloaderClass) {
        putUnlessNull(PRELOADER_CLASS.getID(), preloaderClass);
    }

    public String getAppVersion() {
        return fetchParam(VERSION);
    }

    public void setAppVersion(String version) {
        putUnlessNull(PARAM_VERSION, version);
    }

    public String getDescription() {
        return fetchParam(DESCRIPTION);
    }

    public void setDescription(String s) {
        putUnlessNull(PARAM_DESCRIPTION, s);
    }

    public String getLicenseType() {
        return fetchParam(LICENSE_TYPE);
    }

    public void setLicenseType(String version) {
        putUnlessNull(PARAM_LICENSE_TYPE, version);
    }

    // path is relative to the application root
    public void addLicenseFile(String path) {
        List<String> licenseFiles = fetchParam(LICENSE_FILE);
        if (licenseFiles == null || licenseFiles.isEmpty()) {
            licenseFiles = new ArrayList<>();
            params.put(PARAM_LICENSE_FILE, licenseFiles);
        }
        licenseFiles.add(path);
    }

    public Boolean getSystemWide() {
        return fetchParam(SYSTEM_WIDE);
    }

    public void setSystemWide(Boolean b) {
        putUnlessNull(PARAM_SYSTEM_WIDE, b);
    }

    public void setServiceHint(Boolean b) {
        putUnlessNull(PARAM_SERVICE_HINT, b);
    }

    public void setInstalldirChooser(Boolean b) {
        putUnlessNull(PARAM_INSTALLDIR_CHOOSER, b);
    }

    public void setSingleton(Boolean b) {
        putUnlessNull(PARAM_SINGLETON, b);
    }

    public void setSignBundle(Boolean b) {
        putUnlessNull(SIGN_BUNDLE.getID(), b); }

    public boolean isShortcutHint() {
        return fetchParam(SHORTCUT_HINT);
    }

    public void setShortcutHint(Boolean v) {
        putUnlessNull(PARAM_SHORTCUT, v);
    }

    public boolean isMenuHint() {
        return fetchParam(MENU_HINT);
    }

    public void setMenuHint(Boolean v) {
        putUnlessNull(PARAM_MENU, v);
    }

    public String getName() {
        return fetchParam(APP_NAME);
    }

    public void setName(String name) {
        putUnlessNull(PARAM_NAME, name);
    }

    public BundleType getType() {
        return fetchParam(BundleType.class, PARAM_TYPE);
    }

    public void setType(BundleType type) {
        putUnlessNull(PARAM_TYPE, type);
    }

    public String getBundleFormat() {
        return fetchParam(String.class, PARAM_BUNDLE_FORMAT);
    }

    public void setBundleFormat(String t) {
        putUnlessNull(PARAM_BUNDLE_FORMAT, t);
    }

    public boolean getVerbose() {
        return fetchParam(VERBOSE);
    }

    public void setVerbose(Boolean verbose) {
        putUnlessNull(VERBOSE.getID(), verbose);
    }

    public List<String> getLicenseFile() {
        return fetchParam(LICENSE_FILE);
    }

    public List<String> getJvmargs() {
        return JVM_OPTIONS.fetchFrom(params);
    }

    public List<String> getArguments() {
        return ARGUMENTS.fetchFrom(params);
    }

    // Validation approach:
    //  - javac and
    //
    //  - /jmods dir
    // or
    //  - JRE marker (rt.jar)
    //  - FX marker (jfxrt.jar)
    //  - JDK marker (tools.jar)
    private static boolean checkJDKRoot(File jdkRoot) {
        String exe = Platform.getPlatform() == Platform.WINDOWS ? ".exe" : "";
        File javac = new File(jdkRoot, "bin/javac" + exe);
        if (!javac.exists()) {
            Log.verbose("javac is not found at " + javac.getAbsolutePath());
            return false;
        }

        File jmods = new File(jdkRoot, "jmods");
        if (!jmods.exists()) {
            // old non-modular JDKs
            File rtJar = new File(jdkRoot, "jre/lib/rt.jar");
            if (!rtJar.exists()) {
                Log.verbose("rt.jar is not found at " + rtJar.getAbsolutePath());
                return false;
            }

            File jfxJar = new File(jdkRoot, "jre/lib/ext/jfxrt.jar");
            if (!jfxJar.exists()) {
                //Try again with new location
                jfxJar = new File(jdkRoot, "jre/lib/jfxrt.jar");
                if (!jfxJar.exists()) {
                    Log.verbose("jfxrt.jar is not found at " + jfxJar.getAbsolutePath());
                    return false;
                }
            }

            File toolsJar = new File(jdkRoot, "lib/tools.jar");
            if (!toolsJar.exists()) {
                Log.verbose("tools.jar is not found at " + toolsJar.getAbsolutePath());
                return false;
            }
        }
        return true;
    }

    // Depending on platform and user input we may get different "references"
    // Should support
    //   - java.home
    //   - reference to JDK install folder
    //   - should NOT support JRE dir
    // Note: input could be null (then we asked to use system JRE)
    //       or it must be valid directory
    // Returns null on validation failure. Returns jre root if ok.
    public static File validateRuntimeLocation(File javaHome) {
        if (javaHome == null) {
            return null;
        }

        File jdkRoot;
        File rtJar = new File(javaHome, "lib/rt.jar");

        if (rtJar.exists()) { //must be "java.home" case i.e. we are in JRE folder
            jdkRoot = javaHome.getParentFile();
        } else { //expect it to be root of JDK installation folder
            //On Mac it could be jdk/ or jdk/Contents/Home
            //Norm to jdk/Contents/Home for validation
            if (Platform.getPlatform() == Platform.MAC) {
                File file = new File(javaHome, "Contents/Home");
                if (file.exists() && file.isDirectory()) {
                    javaHome = file;
                }
            }
            jdkRoot = javaHome;
        }

        if (!checkJDKRoot(jdkRoot)) {
            throw new RuntimeException("Can not find JDK artifacts in specified location: " +
                    javaHome.getAbsolutePath());
        }

        return new File(jdkRoot, "jre");
    }

    //select subset of given runtime using predefined rules
    public void setRuntime(File baseDir) {
        baseDir = validateRuntimeLocation(baseDir);

        //mistake or explicit intent to use system runtime
        if (baseDir == null) {
            Log.verbose("No Java runtime to embed. Package will need system Java.");
            params.put(PARAM_RUNTIME, null);
            return;
        }
        doSetRuntime(baseDir);
    }

    //input dir "jdk/jre" (i.e. jre folder in the jdk)
    private void doSetRuntime(File baseDir) {
        params.put(PARAM_RUNTIME, baseDir.toString());
    }

    public RelativeFileSet getAppResource() {
        return fetchParam(APP_RESOURCES);
    }

    public void setAppResource(com.sun.openjfx.tools.packager.RelativeFileSet fs) {
        putUnlessNull(PARAM_APP_RESOURCES, fs);
    }

    public void setAppResourcesList(List<com.sun.openjfx.tools.packager.RelativeFileSet> rfs) {
        putUnlessNull(APP_RESOURCES_LIST.getID(), rfs);
    }

    public String getApplicationCategory() {
        return fetchParam(CATEGORY);
    }

    public void setApplicationCategory(String category) {
        putUnlessNull(PARAM_CATEGORY, category);
    }

    public String getCopyright() {
        return fetchParam(COPYRIGHT);
    }

    public void setCopyright(String c) {
        putUnlessNull(PARAM_COPYRIGHT, c);
    }

    public String getIdentifier() {
        return fetchParam(IDENTIFIER);
    }

    public void setIdentifier(String s) {
        putUnlessNull(PARAM_IDENTIFIER, s);
    }

    public String getVendor() {
        return fetchParam(VENDOR);
    }

    public void setVendor(String vendor) {
        putUnlessNull(PARAM_VENDOR, vendor);
    }

    public String getEmail() {
        return fetchParam(String.class, PARAM_EMAIL);
    }

    public void setEmail(String email) {
        putUnlessNull(PARAM_EMAIL, email);
    }

    public void putUnlessNull(String param, Object value) {
        if (value != null) {
            params.put(param, value);
        }
    }

    public void putUnlessNullOrEmpty(String param, Collection value) {
        if (value != null && !value.isEmpty()) {
            params.put(param, value);
        }
    }

    public void putUnlessNullOrEmpty(String param, Map value) {
        if (value != null && !value.isEmpty()) {
            params.put(param, value);
        }
    }

}
