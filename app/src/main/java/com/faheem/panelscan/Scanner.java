package com.faheem.panelscan;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Read-only scan of the device. Never changes anything on the panel. */
final class Scanner {

    private static final Pattern SECRET_KEY = Pattern.compile(
            "(serial|macaddr|mac_address|imei|android_id|ipaddr|bt_address|bluetooth_address|dhcp|wifi\\.interface)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IP = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");
    private static final Pattern MAC = Pattern.compile("\\b[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}\\b");

    private static final int MAX_VENDOR_PACKAGES = 160;
    private static final int MAX_PER_COMPONENT_TYPE = 40;

    private Scanner() {}

    static String run(Context ctx) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        PackageManager pm = ctx.getPackageManager();

        sb.append("PANEL SCAN (app, read-only)\n");
        sb.append("time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');

        try { device(sb, ctx); } catch (Throwable t) { err(sb, "device", t); }
        try { screen(sb, ctx); } catch (Throwable t) { err(sb, "screen", t); }
        try { memory(sb, ctx); } catch (Throwable t) { err(sb, "memory", t); }
        try { storage(sb); } catch (Throwable t) { err(sb, "storage", t); }
        try { cpu(sb); } catch (Throwable t) { err(sb, "cpu", t); }
        try { settings(sb, ctx); } catch (Throwable t) { err(sb, "settings", t); }
        try { home(sb, pm); } catch (Throwable t) { err(sb, "home", t); }
        try { launchable(sb, pm); } catch (Throwable t) { err(sb, "launchable", t); }
        try { handlers(sb, pm); } catch (Throwable t) { err(sb, "handlers", t); }
        try { packages(sb, pm); } catch (Throwable t) { err(sb, "packages", t); }
        try { vendorDetails(sb, pm); } catch (Throwable t) { err(sb, "vendor details", t); }
        try { props(sb); } catch (Throwable t) { err(sb, "getprop", t); }

        sb.append("\nNOTE: running processes and live RAM per app are not visible to a normal app on Android 8+.\n");
        sb.append("END OF REPORT\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------- sections

    private static void device(StringBuilder sb, Context ctx) {
        section(sb, "Device");
        kv(sb, "manufacturer", Build.MANUFACTURER);
        kv(sb, "brand", Build.BRAND);
        kv(sb, "model", Build.MODEL);
        kv(sb, "device", Build.DEVICE);
        kv(sb, "product", Build.PRODUCT);
        kv(sb, "board", Build.BOARD);
        kv(sb, "hardware", Build.HARDWARE);
        kv(sb, "android release", Build.VERSION.RELEASE);
        kv(sb, "sdk int", String.valueOf(Build.VERSION.SDK_INT));
        kv(sb, "security patch", Build.VERSION.SECURITY_PATCH);
        kv(sb, "build id", Build.ID);
        kv(sb, "build display", Build.DISPLAY);
        kv(sb, "build type/tags", Build.TYPE + " / " + Build.TAGS);
        kv(sb, "fingerprint", Build.FINGERPRINT);
        kv(sb, "supported abis", join(Build.SUPPORTED_ABIS));
        kv(sb, "kernel", System.getProperty("os.version"));
        kv(sb, "this app package", ctx.getPackageName());
    }

    private static void screen(StringBuilder sb, Context ctx) {
        section(sb, "Screen");
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        kv(sb, "real size px", m.widthPixels + " x " + m.heightPixels);
        kv(sb, "density dpi", String.valueOf(m.densityDpi));
        kv(sb, "density", String.valueOf(m.density));
        kv(sb, "refresh rate", String.valueOf(wm.getDefaultDisplay().getRefreshRate()));
        kv(sb, "touchscreen feature", String.valueOf(ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)));
    }

    private static void memory(StringBuilder sb, Context ctx) {
        section(sb, "Memory");
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        kv(sb, "total RAM MB", String.valueOf(mi.totalMem / (1024 * 1024)));
        kv(sb, "available RAM MB", String.valueOf(mi.availMem / (1024 * 1024)));
        kv(sb, "low memory threshold MB", String.valueOf(mi.threshold / (1024 * 1024)));
        kv(sb, "low memory now", String.valueOf(mi.lowMemory));
        kv(sb, "app heap class MB", String.valueOf(am.getMemoryClass()));
        kv(sb, "large heap class MB", String.valueOf(am.getLargeMemoryClass()));
        kv(sb, "low-ram device", String.valueOf(am.isLowRamDevice()));
        sb.append("-- /proc/meminfo (top)\n");
        sb.append(readFile("/proc/meminfo", 10));
    }

    private static void storage(StringBuilder sb) {
        section(sb, "Storage");
        StatFs data = new StatFs(Environment.getDataDirectory().getPath());
        long total = data.getBlockCountLong() * data.getBlockSizeLong();
        long free = data.getAvailableBlocksLong() * data.getBlockSizeLong();
        kv(sb, "/data total GB", String.format(Locale.US, "%.2f", total / 1073741824.0));
        kv(sb, "/data free GB", String.format(Locale.US, "%.2f", free / 1073741824.0));
    }

    private static void cpu(StringBuilder sb) {
        section(sb, "CPU");
        sb.append(readFile("/proc/cpuinfo", 40));
        String[] files = {
                "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
                "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
                "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq",
                "/sys/devices/system/cpu/online"};
        for (String f : files) {
            String v = readFile(f, 1).trim();
            if (!v.isEmpty()) kv(sb, f, v);
        }
    }

    private static void settings(StringBuilder sb, Context ctx) {
        section(sb, "Settings (read only)");
        android.content.ContentResolver cr = ctx.getContentResolver();
        kv(sb, "notification listeners enabled", String.valueOf(Settings.Secure.getString(cr, "enabled_notification_listeners")));
        kv(sb, "accessibility services enabled", String.valueOf(Settings.Secure.getString(cr, "enabled_accessibility_services")));
        kv(sb, "default input method", String.valueOf(Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD)));
        kv(sb, "window animation scale", String.valueOf(Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, -1f)));
        kv(sb, "transition animation scale", String.valueOf(Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, -1f)));
        kv(sb, "animator duration scale", String.valueOf(Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, -1f)));
        kv(sb, "developer options on", String.valueOf(Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)));
        kv(sb, "adb enabled", String.valueOf(Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0)));
    }

    private static void home(StringBuilder sb, PackageManager pm) {
        section(sb, "HOME / launcher");
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo def = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        kv(sb, "current default home", def == null ? "none" : ri(pm, def));
        sb.append("-- all HOME candidates\n");
        for (ResolveInfo r : pm.queryIntentActivities(home, 0)) sb.append("  ").append(ri(pm, r)).append('\n');
    }

    private static void launchable(StringBuilder sb, PackageManager pm) {
        section(sb, "Launchable apps (MAIN + LAUNCHER)");
        List<ResolveInfo> list = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo r : sortByLabel(pm, list)) {
            sb.append("  ").append(ri(pm, r)).append('\n');
            seen.add(r.activityInfo.packageName + "/" + r.activityInfo.name);
        }

        section(sb, "Other MAIN activities (no launcher icon: stock radio/BT/camera/factory screens often live here)");
        List<ResolveInfo> all = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN), 0);
        for (ResolveInfo r : sortByLabel(pm, all)) {
            String key = r.activityInfo.packageName + "/" + r.activityInfo.name;
            if (!seen.contains(key)) sb.append("  ").append(ri(pm, r)).append('\n');
        }
    }

    private static void handlers(StringBuilder sb, PackageManager pm) {
        section(sb, "Handlers (what a launcher can call)");

        listActivities(sb, pm, "navigation (geo:)", new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=test")));
        listActivities(sb, pm, "phone dialer (tel:)", new Intent(Intent.ACTION_DIAL, Uri.parse("tel:123")));
        listActivities(sb, pm, "music player (APP_MUSIC)", Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC));
        listActivities(sb, pm, "browser (APP_BROWSER)", Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER));
        listActivities(sb, pm, "maps (APP_MAPS)", Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS));
        listActivities(sb, pm, "settings (ACTION_SETTINGS)", new Intent(Settings.ACTION_SETTINGS));
        listActivities(sb, pm, "wifi settings", new Intent(Settings.ACTION_WIFI_SETTINGS));
        listActivities(sb, pm, "bluetooth settings", new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));

        listReceivers(sb, pm, "MEDIA_BUTTON receivers", new Intent(Intent.ACTION_MEDIA_BUTTON));
        listReceivers(sb, pm, "BOOT_COMPLETED receivers (apps that auto-start)", new Intent(Intent.ACTION_BOOT_COMPLETED));
        listReceivers(sb, pm, "QUICKBOOT_POWERON receivers", new Intent("android.intent.action.QUICKBOOT_POWERON"));
        listReceivers(sb, pm, "USB_DEVICE_ATTACHED receivers", new Intent("android.hardware.usb.action.USB_DEVICE_ATTACHED"));

        listServices(sb, pm, "MediaBrowserService (music apps)", new Intent("android.media.browse.MediaBrowserService"));
        listServices(sb, pm, "NotificationListenerService", new Intent("android.service.notification.NotificationListenerService"));
        listServices(sb, pm, "AccessibilityService", new Intent("android.accessibilityservice.AccessibilityService"));
    }

    private static void packages(StringBuilder sb, PackageManager pm) {
        List<PackageInfo> pkgs = pm.getInstalledPackages(0);
        Collections.sort(pkgs, new Comparator<PackageInfo>() {
            @Override public int compare(PackageInfo a, PackageInfo b) {
                return a.packageName.compareTo(b.packageName);
            }
        });

        int sys = 0, user = 0, disabled = 0;
        for (PackageInfo p : pkgs) {
            ApplicationInfo ai = p.applicationInfo;
            if (ai == null) continue;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) sys++; else user++;
            if (!ai.enabled) disabled++;
        }
        section(sb, "ALL PACKAGES  (total " + pkgs.size() + ", system " + sys + ", user " + user + ", disabled " + disabled + ")");
        sb.append("legend: S=system U=user  u=updated-system-app  D=DISABLED  P=persistent\n");
        for (PackageInfo p : pkgs) {
            ApplicationInfo ai = p.applicationInfo;
            if (ai == null) continue;
            boolean isSys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean upd = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            boolean persistent = (ai.flags & 0x8) != 0; // FLAG_PERSISTENT (hidden constant)
            sb.append(isSys ? 'S' : 'U')
              .append(upd ? 'u' : '-')
              .append(ai.enabled ? '-' : 'D')
              .append(persistent ? 'P' : '-')
              .append(' ').append(p.packageName)
              .append(" | ").append(safeLabel(pm, ai))
              .append(" | v").append(p.versionName == null ? "?" : p.versionName)
              .append(" | uid ").append(ai.uid)
              .append(" | ").append(ai.sourceDir)
              .append('\n');
        }
    }

    private static void vendorDetails(StringBuilder sb, PackageManager pm) {
        List<PackageInfo> pkgs = pm.getInstalledPackages(0);
        Collections.sort(pkgs, new Comparator<PackageInfo>() {
            @Override public int compare(PackageInfo a, PackageInfo b) {
                return a.packageName.compareTo(b.packageName);
            }
        });

        List<String> vendor = new ArrayList<>();
        for (PackageInfo p : pkgs) {
            String n = p.packageName;
            if (n.equals("android") || n.startsWith("com.android.") || n.startsWith("com.google.")
                    || n.startsWith("com.mediatek.") || n.equals("com.faheem.panelscan")) continue;
            vendor.add(n);
        }
        section(sb, "VENDOR / NON-STANDARD PACKAGES: components  (" + Math.min(vendor.size(), MAX_VENDOR_PACKAGES)
                + " of " + vendor.size() + ")");
        sb.append("legend: [x]=exported  A=activity  S=service  R=receiver  perm=requested permissions\n");

        int count = 0;
        for (String name : vendor) {
            if (count++ >= MAX_VENDOR_PACKAGES) break;
            sb.append("\n--- ").append(name).append('\n');
            try {
                PackageInfo full = pm.getPackageInfo(name,
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
                                | PackageManager.GET_RECEIVERS | PackageManager.GET_PERMISSIONS);
                int n = 0;
                if (full.activities != null) {
                    for (ActivityInfo a : full.activities) {
                        if (n++ >= MAX_PER_COMPONENT_TYPE) { sb.append("  A ...\n"); break; }
                        sb.append("  A").append(a.exported ? "[x] " : "    ").append(shorten(a.name, name)).append('\n');
                    }
                }
                n = 0;
                if (full.services != null) {
                    for (ServiceInfo s : full.services) {
                        if (n++ >= MAX_PER_COMPONENT_TYPE) { sb.append("  S ...\n"); break; }
                        sb.append("  S").append(s.exported ? "[x] " : "    ").append(shorten(s.name, name)).append('\n');
                    }
                }
                n = 0;
                if (full.receivers != null) {
                    for (ActivityInfo r : full.receivers) {
                        if (n++ >= MAX_PER_COMPONENT_TYPE) { sb.append("  R ...\n"); break; }
                        sb.append("  R").append(r.exported ? "[x] " : "    ").append(shorten(r.name, name)).append('\n');
                    }
                }
                if (full.requestedPermissions != null && full.requestedPermissions.length > 0) {
                    sb.append("  perm: ");
                    int k = 0;
                    for (String p : full.requestedPermissions) {
                        if (k++ >= MAX_PER_COMPONENT_TYPE) { sb.append(" ..."); break; }
                        sb.append(p.replace("android.permission.", "")).append(' ');
                    }
                    sb.append('\n');
                }
            } catch (Throwable t) {
                sb.append("  (could not read components: ").append(t.getClass().getSimpleName()).append(")\n");
            }
        }
    }

    private static void props(StringBuilder sb) {
        section(sb, "getprop (serials, MACs and IPs masked)");
        String out = exec("getprop");
        for (String line : out.split("\n")) {
            if (line.trim().isEmpty()) continue;
            if (SECRET_KEY.matcher(line).find()) { sb.append("[masked]\n"); continue; }
            line = IP.matcher(line).replaceAll("x.x.x.x");
            line = MAC.matcher(line).replaceAll("xx:xx:xx:xx:xx:xx");
            sb.append(line).append('\n');
        }
    }

    // ----------------------------------------------------------------- helpers

    private static void listActivities(StringBuilder sb, PackageManager pm, String title, Intent i) {
        sb.append("\n-- ").append(title).append('\n');
        List<ResolveInfo> l = pm.queryIntentActivities(i, 0);
        if (l.isEmpty()) sb.append("  (none)\n");
        for (ResolveInfo r : l) sb.append("  ").append(ri(pm, r)).append('\n');
    }

    private static void listReceivers(StringBuilder sb, PackageManager pm, String title, Intent i) {
        sb.append("\n-- ").append(title).append('\n');
        List<ResolveInfo> l = pm.queryBroadcastReceivers(i, 0);
        if (l.isEmpty()) sb.append("  (none)\n");
        for (ResolveInfo r : l) {
            if (r.activityInfo != null) sb.append("  ").append(r.activityInfo.packageName).append(" / ").append(shorten(r.activityInfo.name, r.activityInfo.packageName)).append('\n');
        }
    }

    private static void listServices(StringBuilder sb, PackageManager pm, String title, Intent i) {
        sb.append("\n-- ").append(title).append('\n');
        List<ResolveInfo> l = pm.queryIntentServices(i, 0);
        if (l.isEmpty()) sb.append("  (none)\n");
        for (ResolveInfo r : l) {
            if (r.serviceInfo != null) sb.append("  ").append(r.serviceInfo.packageName).append(" / ").append(shorten(r.serviceInfo.name, r.serviceInfo.packageName)).append('\n');
        }
    }

    private static List<ResolveInfo> sortByLabel(final PackageManager pm, List<ResolveInfo> in) {
        List<ResolveInfo> l = new ArrayList<>(in);
        Collections.sort(l, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });
        return l;
    }

    private static String ri(PackageManager pm, ResolveInfo r) {
        if (r.activityInfo == null) return String.valueOf(r);
        return String.valueOf(r.loadLabel(pm)) + "  ->  " + r.activityInfo.packageName + "/" + r.activityInfo.name;
    }

    private static String safeLabel(PackageManager pm, ApplicationInfo ai) {
        try { return String.valueOf(pm.getApplicationLabel(ai)); } catch (Throwable t) { return "?"; }
    }

    private static String shorten(String cls, String pkg) {
        return cls != null && cls.startsWith(pkg) ? cls.substring(pkg.length()) : cls;
    }

    private static void section(StringBuilder sb, String title) {
        sb.append("\n").append("======================================================================\n")
          .append("## ").append(title).append('\n')
          .append("======================================================================\n");
    }

    private static void kv(StringBuilder sb, String k, String v) {
        sb.append(k).append(": ").append(v == null ? "null" : v).append('\n');
    }

    private static void err(StringBuilder sb, String where, Throwable t) {
        sb.append("\n[").append(where).append(" failed: ").append(t).append("]\n");
    }

    private static String join(String[] a) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) s.append(", "); s.append(a[i]); }
        return s.toString();
    }

    private static String readFile(String path, int maxLines) {
        StringBuilder s = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(new File(path)))) {
            String line;
            int n = 0;
            while ((line = br.readLine()) != null && n++ < maxLines) s.append(line).append('\n');
        } catch (Throwable ignored) { }
        return s.toString();
    }

    private static String exec(String cmd) {
        StringBuilder s = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{cmd});
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) s.append(line).append('\n');
            }
            p.waitFor();
        } catch (Throwable t) {
            s.append("(").append(cmd).append(" failed: ").append(t).append(")\n");
        }
        return s.toString();
    }
}
