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

package com.sun.openjfx.tools.packager.linux;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import com.sun.openjfx.tools.packager.AbstractBundler;
import com.sun.openjfx.tools.packager.BundlerParamInfo;
import com.sun.openjfx.tools.packager.ConfigException;
import com.sun.openjfx.tools.packager.IOUtils;
import com.sun.openjfx.tools.packager.Log;
import com.sun.openjfx.tools.packager.RelativeFileSet;
import com.sun.openjfx.tools.packager.StandardBundlerParam;
import com.sun.openjfx.tools.packager.UnsupportedPlatformException;

import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_CDS_CACHE_MODE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_FS_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_NAME;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.APP_RESOURCES_LIST;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.BUILD_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.CATEGORY;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.DESCRIPTION;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.DROP_IN_RESOURCES_ROOT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.ENABLE_APP_CDS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_CONTENT_TYPE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_DESCRIPTION;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_EXTENSIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FA_ICON;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.FILE_ASSOCIATIONS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.LICENSE_FILE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.LICENSE_TYPE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MENU_HINT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.MODULE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.RUN_AT_STARTUP;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SECONDARY_LAUNCHERS;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SERVICE_HINT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SHORTCUT_HINT;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.START_ON_INSTALL;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.STOP_ON_UNINSTALL;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.SYSTEM_WIDE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.TITLE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VENDOR;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERBOSE;
import static com.sun.openjfx.tools.packager.StandardBundlerParam.VERSION;
import static com.sun.openjfx.tools.packager.linux.LinuxAppBundler.ICON_PNG;

public class LinuxRpmBundler extends AbstractBundler {

    public static final BundlerParamInfo<LinuxAppBundler> APP_BUNDLER = new StandardBundlerParam<>(
            "",
            "",
            "linux.app.bundler",
            LinuxAppBundler.class,
        params -> new LinuxAppBundler(), null);

    public static final BundlerParamInfo<File> RPM_IMAGE_DIR = new StandardBundlerParam<>(
            "",
            "",
            "linux.rpm.imageDir",
            File.class,
        params -> {
            File imagesRoot = IMAGES_ROOT.fetchFrom(params);
            if (!imagesRoot.exists()) {
                imagesRoot.mkdirs();
            }
            return new File(imagesRoot, "linux-rpm.image");
        },
        (s, p) -> new File(s));

    public static final BundlerParamInfo<File> CONFIG_ROOT = new StandardBundlerParam<>(
            "",
            "",
            "configRoot",
            File.class,
        params ->  new File(BUILD_ROOT.fetchFrom(params), "linux"),
        (s, p) -> new File(s));

    // Fedora rules for package naming are used here
    // https://fedoraproject.org/wiki/Packaging:NamingGuidelines?rd=Packaging/NamingGuidelines
    //
    // all Fedora packages must be named using only the following ASCII characters.
    // These characters are displayed here:
    //
    // abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._+
    //
    private static final Pattern RPM_BUNDLE_NAME_PATTERN =
            Pattern.compile("[a-z\\d\\+\\-\\.\\_]+", Pattern.CASE_INSENSITIVE);

    public static final BundlerParamInfo<String> BUNDLE_NAME = new StandardBundlerParam<>(
            "",
            "",
            "linux.bundleName",
            String.class,
        params -> {
            String nm = APP_NAME.fetchFrom(params);
            if (nm == null) {
                return null;
            }

            // make sure to lower case and spaces become dashes
            nm = nm.toLowerCase().replaceAll("[ ]", "-");

            return nm;
        },
        (s, p) -> {
            if (!RPM_BUNDLE_NAME_PATTERN.matcher(s).matches()) {
                throw new IllegalArgumentException(
                        new ConfigException(
                            MessageFormat.format("Invalid value \"{0}\" for the package name.", s),
                                "Set the \"linux.bundleName\" parameter to a valid RPM package name. Note that " +
                                        "the packages must be named using only the following ASCII characters:  " +
                                        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._+"));
            }

            return s;
        }
    );

    public static final BundlerParamInfo<String> XDG_FILE_PREFIX = new StandardBundlerParam<>(
            "Prefix for XDG files (mime, desktop)",
            "Prefix for XDG MimeInfo and Desktop Files.  Defaults to <vendor>-<appName>, with spaces dropped.",
            "linux.xdg-prefix",
            String.class,
        params -> {
            try {
                String vendor;
                if (params.containsKey(VENDOR.getID())) {
                    vendor = VENDOR.fetchFrom(params);
                } else {
                    vendor = "javapackager";
                }
                String appName = APP_FS_NAME.fetchFrom(params);

                return (vendor + "-" + appName).replaceAll("\\s", "");
            } catch (Exception e) {
                if (Log.isDebug()) {
                    e.printStackTrace();
                }
            }
            return "unknown-MimeInfo.xml";
        },
        (s, p) -> s);

    private static final String DEFAULT_ICON = "/packager/linux/javalogo_white_32.png";
    private static final String DEFAULT_SPEC_TEMPLATE = "/packager/linux/template.spec";
    private static final String DEFAULT_DESKTOP_FILE_TEMPLATE = "/packager/linux/template.desktop";
    private static final String DEFAULT_INIT_SCRIPT_TEMPLATE = "/packager/linux/template.rpm.init.script";
    public static final String TOOL_RPMBUILD = "rpmbuild";
    public static final double TOOL_RPMBUILD_MIN_VERSION = 4.0d;

    public LinuxRpmBundler() {
        super();
    }

    public static boolean testTool(String toolName, double minVersion) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(baos)) {
            ProcessBuilder pb = new ProcessBuilder(toolName, "--version");
            IOUtils.exec(pb, Log.isDebug(), false, ps); // not interested in the output
            String content = new String(baos.toByteArray());
            Pattern pattern = Pattern.compile(" (\\d+\\.\\d+)");
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                String v = matcher.group(1);
                double version = Double.valueOf(v);
                return minVersion <= version;
            } else {
                return false;
            }
        } catch (Exception e) {
            Log.verbose(MessageFormat.format("Test for [{0}]. Result: {1}", toolName, e.getMessage()));
            return false;
        }
    }

    @Override
    public boolean validate(Map<String, ? super Object> p) throws UnsupportedPlatformException, ConfigException {
        try {
            if (p == null) {
                throw new ConfigException("Parameters map is null.", "Pass in a non-null parameters map.");
            }

            // run basic validation to ensure requirements are met
            // we are not interested in return code, only possible exception
            APP_BUNDLER.fetchFrom(p).doValidate(p);

            // validate license file, if used, exists in the proper place
            if (p.containsKey(LICENSE_FILE.getID())) {
                List<RelativeFileSet> appResourcesList = APP_RESOURCES_LIST.fetchFrom(p);
                for (String license : LICENSE_FILE.fetchFrom(p)) {
                    boolean found = false;
                    for (RelativeFileSet appResources : appResourcesList) {
                        found = found || appResources.contains(license);
                    }
                    if (!found) {
                        throw new ConfigException("Specified license file is missing.",
                                MessageFormat.format("Make sure that \"{0}\" references a file in the app resources, " +
                                        "and that it is relative file reference.", license));
                    }
                }
            }

            // validate presence of required tools
            if (!testTool(TOOL_RPMBUILD, TOOL_RPMBUILD_MIN_VERSION)) {
                throw new ConfigException(MessageFormat.format(
                        "Can not find rpmbuild {0} or newer.", TOOL_RPMBUILD_MIN_VERSION), MessageFormat.format(
                                "Install packages needed to build RPM, version {0} or newer.",
                                TOOL_RPMBUILD_MIN_VERSION));
            }

            // treat default null as "system wide install"
            boolean systemWide = SYSTEM_WIDE.fetchFrom(p) == null || SYSTEM_WIDE.fetchFrom(p);
            boolean serviceHint = p.containsKey(SERVICE_HINT.getID()) && SERVICE_HINT.fetchFrom(p);

            if (serviceHint && !systemWide) {
                throw new ConfigException("Bundler doesn't support per-user daemons.",
                        "Make sure that the system wide hint is set to true.");
            }

            // only one mime type per association, at least one file extension
            List<Map<String, ? super Object>> associations = FILE_ASSOCIATIONS.fetchFrom(p);
            if (associations != null) {
                for (int i = 0; i < associations.size(); i++) {
                    Map<String, ? super Object> assoc = associations.get(i);
                    List<String> mimes = FA_CONTENT_TYPE.fetchFrom(assoc);
                    if (mimes == null || mimes.isEmpty()) {
                        throw new ConfigException(MessageFormat.format(
                                "No MIME types were specified for File Association number {0}.", i),
                                "For Linux Bundling specify one and only one MIME type for each file association.");
                    } else if (mimes.size() > 1) {
                        throw new ConfigException(MessageFormat.format(
                                "More than one MIME types was specified for File Association number {0}.", i),
                                "For Linux Bundling specify one and only one MIME type for each file association.");
                    }
                }
            }

            // bundle name has some restrictions
            // the string converter will throw an exception if invalid
            BUNDLE_NAME.getStringConverter().apply(BUNDLE_NAME.fetchFrom(p), p);

            return true;
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    private static boolean prepareProto(Map<String, ? super Object> params) {
        File imageDir = RPM_IMAGE_DIR.fetchFrom(params);
        File appDir = APP_BUNDLER.fetchFrom(params).doBundle(params, imageDir, true);
        return appDir != null;
    }

    public File bundle(Map<String, ? super Object> p, File outdir) {
        if (!outdir.isDirectory() && !outdir.mkdirs()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} cannot be created.",
                    outdir.getAbsolutePath()));
        }
        if (!outdir.canWrite()) {
            throw new RuntimeException(MessageFormat.format("Output directory {0} is not writable.",
                    outdir.getAbsolutePath()));
        }

        File imageDir = RPM_IMAGE_DIR.fetchFrom(p);
        try {
            imageDir.mkdirs();

            boolean menuShortcut = MENU_HINT.fetchFrom(p);
            boolean desktopShortcut = SHORTCUT_HINT.fetchFrom(p);
            if (!menuShortcut && !desktopShortcut) {
                //both can not be false - user will not find the app
                Log.verbose("At least one type of shortcut is required. Enabling menu shortcut.");
                p.put(MENU_HINT.getID(), true);
            }

            if (prepareProto(p) && prepareProjectConfig(p)) {
                return buildRPM(p, outdir);
            }
            return null;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        } finally {
            try {
                if (VERBOSE.fetchFrom(p)) {
                    saveConfigFiles(p);
                }
                if (imageDir != null && !Log.isDebug()) {
                    IOUtils.deleteRecursive(imageDir);
                } else if (imageDir != null) {
                    Log.info(MessageFormat.format("Kept working directory for debug: {0}",
                            imageDir.getAbsolutePath()));
                }
            } catch (IOException ex) {
                Log.debug(ex.getMessage());
                // noinspection ReturnInsideFinallyBlock
                return null;
            }
        }
    }

    /*
     * set permissions with a string like "rwxr-xr-x"
     */
    private static void setPermissions(File file, String permissions) {
        Set<PosixFilePermission> filePermissions = PosixFilePermissions.fromString(permissions);
        try {
            if (file.exists()) {
                Files.setPosixFilePermissions(file.toPath(), filePermissions);
            }
        } catch (IOException ex) {
            Logger.getLogger(LinuxDebBundler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void saveConfigFiles(Map<String, ? super Object> params) {
        try {
            File configRoot = CONFIG_ROOT.fetchFrom(params);
            File rootDir = LinuxAppBundler.getRootDir(RPM_IMAGE_DIR.fetchFrom(params), params);

            if (getConfig_SpecFile(params).exists()) {
                IOUtils.copyFile(getConfig_SpecFile(params),
                        new File(configRoot, getConfig_SpecFile(params).getName()));
            }
            if (getConfig_DesktopShortcutFile(rootDir, params).exists()) {
                IOUtils.copyFile(getConfig_DesktopShortcutFile(rootDir, params),
                        new File(configRoot, getConfig_DesktopShortcutFile(rootDir, params).getName()));
            }
            if (getConfig_IconFile(rootDir, params).exists()) {
                IOUtils.copyFile(getConfig_IconFile(rootDir, params),
                        new File(configRoot, getConfig_IconFile(rootDir, params).getName()));
            }
            if (SERVICE_HINT.fetchFrom(params)) {
                if (getConfig_InitScriptFile(params).exists()) {
                    IOUtils.copyFile(getConfig_InitScriptFile(params),
                            new File(configRoot, getConfig_InitScriptFile(params).getName()));
                }
            }
            Log.info(MessageFormat.format("Config files are saved to {0}. Use them to customize package.",
                    configRoot.getAbsolutePath()));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static String getLicenseFileString(Map<String, ? super Object> params) {
        StringBuilder sb = new StringBuilder();
        for (String f : LICENSE_FILE.fetchFrom(params)) {
            if (sb.length() != 0) {
                sb.append("\n");
            }
            sb.append("%doc /opt/");
            sb.append(APP_FS_NAME.fetchFrom(params));
            sb.append("/app/");
            sb.append(f);
        }
        return sb.toString();
    }

    private boolean prepareProjectConfig(Map<String, ? super Object> params) throws IOException {
        Map<String, String> data = createReplacementData(params);
        File rootDir = LinuxAppBundler.getRootDir(RPM_IMAGE_DIR.fetchFrom(params), params);

        // prepare installer icon
        File iconTarget = getConfig_IconFile(rootDir, params);
        File icon = ICON_PNG.fetchFrom(params);
        if (icon == null || !icon.exists()) {
            fetchResource(LinuxAppBundler.LINUX_BUNDLER_PREFIX + iconTarget.getName(),
                    "menu icon",
                    DEFAULT_ICON,
                    iconTarget,
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        } else {
            fetchResource(LinuxAppBundler.LINUX_BUNDLER_PREFIX + iconTarget.getName(),
                    "menu icon",
                    icon,
                    iconTarget,
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        }

        StringBuilder installScripts = new StringBuilder();
        StringBuilder removeScripts = new StringBuilder();
        for (Map<String, ? super Object> secondaryLauncher : SECONDARY_LAUNCHERS.fetchFrom(params)) {
            Map<String, String> secondaryLauncherData = createReplacementData(secondaryLauncher);
            secondaryLauncherData.put("APPLICATION_FS_NAME", data.get("APPLICATION_FS_NAME"));
            secondaryLauncherData.put("DESKTOP_MIMES", "");

            // prepare desktop shortcut
            Writer w = new BufferedWriter(new FileWriter(getConfig_DesktopShortcutFile(rootDir, secondaryLauncher)));
            String content = preprocessTextResource(LinuxAppBundler.LINUX_BUNDLER_PREFIX +
                            getConfig_DesktopShortcutFile(rootDir, secondaryLauncher).getName(),
                    "Menu shortcut descriptor", DEFAULT_DESKTOP_FILE_TEMPLATE, secondaryLauncherData,
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
            w.write(content);
            w.close();

            // prepare installer icon
            iconTarget = getConfig_IconFile(rootDir, secondaryLauncher);
            icon = ICON_PNG.fetchFrom(secondaryLauncher);
            if (icon == null || !icon.exists()) {
                fetchResource(LinuxAppBundler.LINUX_BUNDLER_PREFIX + iconTarget.getName(),
                        "menu icon",
                        DEFAULT_ICON,
                        iconTarget,
                        VERBOSE.fetchFrom(params),
                        DROP_IN_RESOURCES_ROOT.fetchFrom(params));
            } else {
                fetchResource(LinuxAppBundler.LINUX_BUNDLER_PREFIX + iconTarget.getName(),
                        "menu icon",
                        icon,
                        iconTarget,
                        VERBOSE.fetchFrom(params),
                        DROP_IN_RESOURCES_ROOT.fetchFrom(params));
            }

            //post copying of desktop icon
            installScripts.append("xdg-desktop-menu install --novendor /opt/");
            installScripts.append(data.get("APPLICATION_FS_NAME"));
            installScripts.append("/");
            installScripts.append(secondaryLauncherData.get("APPLICATION_LAUNCHER_FILENAME"));
            installScripts.append(".desktop\n");

            //preun cleanup of desktop icon
            removeScripts.append("xdg-desktop-menu uninstall --novendor /opt/");
            removeScripts.append(data.get("APPLICATION_FS_NAME"));
            removeScripts.append("/");
            removeScripts.append(secondaryLauncherData.get("APPLICATION_LAUNCHER_FILENAME"));
            removeScripts.append(".desktop\n");

        }
        data.put("SECONDARY_LAUNCHERS_INSTALL", installScripts.toString());
        data.put("SECONDARY_LAUNCHERS_REMOVE", removeScripts.toString());

        StringBuilder cdsScript = new StringBuilder();
        if (ENABLE_APP_CDS.fetchFrom(params) &&
                ("install".equals(APP_CDS_CACHE_MODE.fetchFrom(params)) ||
                        "auto+install".equals(APP_CDS_CACHE_MODE.fetchFrom(params)))) {
            cdsScript.append("/opt/");
            cdsScript.append(data.get("APPLICATION_FS_NAME"));
            cdsScript.append("/");
            cdsScript.append(data.get("APPLICATION_LAUNCHER_FILENAME"));
            cdsScript.append(" -Xappcds:generatecache\n");
        }

        data.put("APP_CDS_CACHE", cdsScript.toString());

        data.put("FILE_ASSOCIATION_INSTALL", "");
        data.put("FILE_ASSOCIATION_REMOVE", "");
        data.put("DESKTOP_MIMES", "");
        List<Map<String, ? super Object>> associations = FILE_ASSOCIATIONS.fetchFrom(params);
        if (associations != null) {
            String mimeInfoFile = XDG_FILE_PREFIX.fetchFrom(params) + "-MimeInfo.xml";
            StringBuilder mimeInfo = new StringBuilder("<?xml version=\"1.0\"?>\n" +
                    "<mime-info xmlns='http://www.freedesktop.org/standards/shared-mime-info'>\n");
            StringBuilder registrations = new StringBuilder();
            StringBuilder deregistrations = new StringBuilder();
            StringBuilder desktopMimes = new StringBuilder("MimeType=");
            boolean addedEntry = false;

            for (Map<String, ? super Object> assoc : associations) {
                //  <mime-type type="application/x-vnd.awesome">
                //    <comment>Awesome document</comment>
                //    <glob pattern="*.awesome"/>
                //    <glob pattern="*.awe"/>
                //  </mime-type>

                if (assoc == null) {
                    continue;
                }

                List<String> extensions = FA_EXTENSIONS.fetchFrom(assoc);
                if (extensions == null) {
                    Log.info("Creating association with null extension.");
                }

                List<String> mimes = FA_CONTENT_TYPE.fetchFrom(assoc);
                if (mimes == null || mimes.isEmpty()) {
                    continue;
                }
                String thisMime = mimes.get(0);
                String dashMime = thisMime.replace('/', '-');

                mimeInfo.append("  <mime-type type='")
                        .append(thisMime)
                        .append("'>\n");
                String description = FA_DESCRIPTION.fetchFrom(assoc);
                if (description != null && !description.isEmpty()) {
                    mimeInfo.append("    <comment>")
                            .append(description)
                            .append("</comment>\n");
                }

                if (extensions != null) {
                    for (String ext : extensions) {
                        mimeInfo.append("    <glob pattern='*.")
                                .append(ext)
                                .append("'/>\n");
                    }
                }

                mimeInfo.append("  </mime-type>\n");
                if (!addedEntry) {
                    registrations.append("xdg-mime install /opt/")
                            .append(data.get("APPLICATION_FS_NAME"))
                            .append("/")
                            .append(mimeInfoFile)
                            .append("\n");

                    deregistrations.append("xdg-mime uninstall /opt/")
                            .append(data.get("APPLICATION_FS_NAME"))
                            .append("/")
                            .append(mimeInfoFile)
                            .append("\n");
                    addedEntry = true;
                } else {
                    desktopMimes.append(";");
                }
                desktopMimes.append(thisMime);

                File faIcon = FA_ICON.fetchFrom(assoc); //TODO FA_ICON_PNG
                if (faIcon != null && faIcon.exists()) {
                    int size = getSquareSizeOfImage(faIcon);

                    if (size > 0) {
                        File target = new File(rootDir, APP_FS_NAME.fetchFrom(params) + "_fa_" + faIcon.getName());
                        IOUtils.copyFile(faIcon, target);

                        // xdg-icon-resource install --context mimetypes --size 64
                        // awesomeapp_fa_1.png application-x.vnd-awesome
                        registrations.append("xdg-icon-resource install --context mimetypes --size ")
                                .append(size)
                                .append(" /opt/")
                                .append(data.get("APPLICATION_FS_NAME"))
                                .append("/")
                                .append(target.getName())
                                .append(" ")
                                .append(dashMime)
                                .append("\n");

                        // xdg-icon-resource uninstall --context mimetypes --size 64
                        // awesomeapp_fa_1.png application-x.vnd-awesome
                        deregistrations.append("xdg-icon-resource uninstall --context mimetypes --size ")
                                .append(size)
                                .append(" /opt/")
                                .append(data.get("APPLICATION_FS_NAME"))
                                .append("/")
                                .append(target.getName())
                                .append(" ")
                                .append(dashMime)
                                .append("\n");
                    }
                }
            }
            mimeInfo.append("</mime-info>");

            if (addedEntry) {
                Writer w = new BufferedWriter(new FileWriter(new File(rootDir, mimeInfoFile)));
                w.write(mimeInfo.toString());
                w.close();
                data.put("FILE_ASSOCIATION_INSTALL", registrations.toString());
                data.put("FILE_ASSOCIATION_REMOVE", deregistrations.toString());
                data.put("DESKTOP_MIMES", desktopMimes.toString());
            }
        }
        //prepare desktop shortcut
        Writer w = new BufferedWriter(new FileWriter(getConfig_DesktopShortcutFile(rootDir, params)));
        String content = preprocessTextResource(
                LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_DesktopShortcutFile(rootDir, params).getName(),
                "Menu shortcut descriptor", DEFAULT_DESKTOP_FILE_TEMPLATE, data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        w.write(content);
        w.close();

        //prepare spec file
        w = new BufferedWriter(new FileWriter(getConfig_SpecFile(params)));
        content = preprocessTextResource(
                LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_SpecFile(params).getName(),
                "RPM spec file", DEFAULT_SPEC_TEMPLATE, data,
                VERBOSE.fetchFrom(params),
                DROP_IN_RESOURCES_ROOT.fetchFrom(params));
        w.write(content);
        w.close();

        if (SERVICE_HINT.fetchFrom(params)) {
            //prepare init script
            w = new BufferedWriter(new FileWriter(getConfig_InitScriptFile(params)));
            content = preprocessTextResource(
                    LinuxAppBundler.LINUX_BUNDLER_PREFIX + getConfig_InitScriptFile(params).getName(),
                    "RPM init script",
                    DEFAULT_INIT_SCRIPT_TEMPLATE,
                    data,
                    VERBOSE.fetchFrom(params),
                    DROP_IN_RESOURCES_ROOT.fetchFrom(params));
            w.write(content);
            w.close();
            setPermissions(getConfig_InitScriptFile(params), "rwxr-xr-x");
        }

        return true;
    }

    private Map<String, String> createReplacementData(Map<String, ? super Object> params) {
        Map<String, String> data = new HashMap<>();
        data.put("APPLICATION_NAME", APP_NAME.fetchFrom(params));
        data.put("APPLICATION_FS_NAME", APP_FS_NAME.fetchFrom(params));
        data.put("APPLICATION_PACKAGE", BUNDLE_NAME.fetchFrom(params));
        data.put("APPLICATION_VENDOR", VENDOR.fetchFrom(params));
        data.put("APPLICATION_VERSION", VERSION.fetchFrom(params));
        data.put("APPLICATION_LAUNCHER_FILENAME", APP_FS_NAME.fetchFrom(params));
        data.put("XDG_PREFIX", XDG_FILE_PREFIX.fetchFrom(params));
        data.put("DEPLOY_BUNDLE_CATEGORY", CATEGORY.fetchFrom(params)); //TODO rpm categories
        data.put("APPLICATION_DESCRIPTION", DESCRIPTION.fetchFrom(params));
        data.put("APPLICATION_SUMMARY", TITLE.fetchFrom(params));
        data.put("APPLICATION_LICENSE_TYPE", LICENSE_TYPE.fetchFrom(params));
        data.put("APPLICATION_LICENSE_FILE", getLicenseFileString(params));
        data.put("SERVICE_HINT", String.valueOf(SERVICE_HINT.fetchFrom(params)));
        data.put("START_ON_INSTALL", String.valueOf(START_ON_INSTALL.fetchFrom(params)));
        data.put("STOP_ON_UNINSTALL", String.valueOf(STOP_ON_UNINSTALL.fetchFrom(params)));
        data.put("RUN_AT_STARTUP", String.valueOf(RUN_AT_STARTUP.fetchFrom(params)));
        return data;
    }

    private File getConfig_DesktopShortcutFile(File rootDir, Map<String, ? super Object> params) {
        return new File(rootDir, APP_FS_NAME.fetchFrom(params) + ".desktop");
    }

    private File getConfig_IconFile(File rootDir, Map<String, ? super Object> params) {
        return new File(rootDir, APP_FS_NAME.fetchFrom(params) + ".png");
    }

    private File getConfig_InitScriptFile(Map<String, ? super Object> params) {
        return new File(LinuxAppBundler.getRootDir(RPM_IMAGE_DIR.fetchFrom(params), params),
                BUNDLE_NAME.fetchFrom(params) + ".init");
    }

    private File getConfig_SpecFile(Map<String, ? super Object> params) {
        return new File(RPM_IMAGE_DIR.fetchFrom(params), APP_FS_NAME.fetchFrom(params) + ".spec");
    }

    private File buildRPM(Map<String, ? super Object> params, File outdir) throws IOException {
        Log.verbose(MessageFormat.format("Generating RPM for installer to: {0}", outdir.getAbsolutePath()));

        File broot = new File(BUILD_ROOT.fetchFrom(params), "rmpbuildroot");

        outdir.mkdirs();

        // run rpmbuild
        ProcessBuilder pb = new ProcessBuilder(
                TOOL_RPMBUILD,
                "-bb", getConfig_SpecFile(params).getAbsolutePath(),
                // debug: improves build time (but will require unpack to install?)
                // "--define", "%__jar_repack %{nil}",
                "--define", "%_sourcedir " + RPM_IMAGE_DIR.fetchFrom(params).getAbsolutePath(),
                "--define", "%_rpmdir " + outdir.getAbsolutePath(), // save result to output dir
                // do not use other system directories to build as current user
                "--define", "%_topdir " + broot.getAbsolutePath());
        pb.directory(RPM_IMAGE_DIR.fetchFrom(params));
        IOUtils.exec(pb, VERBOSE.fetchFrom(params));

        if (!Log.isDebug()) {
            IOUtils.deleteRecursive(broot);
        }

        Log.info(MessageFormat.format("Package (.rpm) saved to: {0}", outdir.getAbsolutePath()));

        // presume the result is the ".rpm" file with the newest modified time
        // not the best solution, but it is the most reliable
        File result = null;
        long lastModified = 0;
        File[] list = outdir.listFiles();
        if (list != null) {
            for (File f : list) {
                if (f.getName().endsWith(".rpm") && f.lastModified() > lastModified) {
                    result = f;
                    lastModified = f.lastModified();
                }
            }
        }

        return result;
    }

    @Override
    public String getName() {
        return "Linux RPM Bundler";
    }

    @Override
    public String getDescription() {
        return "Redhat Package Manager (RPM) bundler.";
    }

    @Override
    public String getID() {
        return "rpm";
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();
        results.addAll(LinuxAppBundler.getAppBundleParameters());
        results.addAll(getRpmBundleParameters());
        return results;
    }

    public static Collection<BundlerParamInfo<?>> getRpmBundleParameters() {
        return Arrays.asList(BUNDLE_NAME,
                CATEGORY,
                DESCRIPTION,
                ICON_PNG,
                LICENSE_FILE,
                LICENSE_TYPE,
                MODULE,
                TITLE,
                VENDOR);
    }

    @Override
    public File execute(Map<String, ? super Object> params, File outputParentDir) {
        return bundle(params, outputParentDir);
    }

    private static int getSquareSizeOfImage(File f) {
        try {
            BufferedImage bi = ImageIO.read(f);
            if (bi.getWidth() == bi.getHeight()) {
                return bi.getWidth();
            } else {
                return 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}
