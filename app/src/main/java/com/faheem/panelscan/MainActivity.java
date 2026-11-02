package com.faheem.panelscan;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity {

    private TextView status;
    private TextView urlView;
    private TextView body;
    private Button rescan;
    private ReportServer server;
    private volatile String report = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(12));

        TextView title = new TextView(this);
        title.setText("Panel Scan");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0, dp(6), 0, dp(6));
        root.addView(status);

        urlView = new TextView(this);
        urlView.setTextSize(22);
        urlView.setTypeface(Typeface.DEFAULT_BOLD);
        urlView.setPadding(0, 0, 0, dp(6));
        root.addView(urlView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        rescan = new Button(this);
        rescan.setText("Dobara scan");
        rescan.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runScan(); }
        });
        Button copy = new Button(this);
        copy.setText("Copy (chhota)");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copyReport(); }
        });
        row.addView(rescan);
        row.addView(copy);
        root.addView(row);

        ScrollView scroll = new ScrollView(this);
        body = new TextView(this);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(13);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        server = new ReportServer();
        server.start();
        runScan();
    }

    private void runScan() {
        rescan.setEnabled(false);
        status.setText("Scan chal raha hai... (30 se 90 second lag sakte hain)");
        urlView.setText("");
        body.setText("");
        new Thread(new Runnable() {
            @Override public void run() {
                String r;
                try {
                    r = Scanner.run(MainActivity.this);
                } catch (Throwable t) {
                    r = "SCAN ERROR: " + t;
                }
                final String path = save(r);
                report = r;
                server.setReport(r);
                final String fr = r;
                runOnUiThread(new Runnable() {
                    @Override public void run() { show(fr, path); }
                });
            }
        }, "scan").start();
    }

    private void show(String r, String path) {
        rescan.setEnabled(true);
        status.setText("Scan mukammal (" + (r.length() / 1024) + " KB). Yeh app khuli rakhein, aur\n"
                + "phone ya laptop ke browser mein (ek hi WiFi par) yeh link kholen:");
        StringBuilder u = new StringBuilder();
        if (server.port < 0) {
            u.append("Server start nahi hua. File: ").append(path);
        } else {
            for (String ip : localIps()) u.append("http://").append(ip).append(':').append(server.port).append("/\n");
            if (u.length() == 0) u.append("WiFi connected nahi lagta. File: ").append(path);
        }
        urlView.setText(u.toString().trim());
        String preview = r.length() > 3500 ? r.substring(0, 3500) + "\n\n... (poori report browser link se download karein)" : r;
        if (path != null) preview = "File: " + path + "\n\n" + preview;
        body.setText(preview);
    }

    private void copyReport() {
        String r = report;
        if (r == null || r.isEmpty()) return;
        String part = r.length() > 90000 ? r.substring(0, 90000) : r;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("panel_scan", part));
        Toast.makeText(this, "Copy ho gaya (pehle " + (part.length() / 1024) + " KB)", Toast.LENGTH_SHORT).show();
    }

    private String save(String r) {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            File f = new File(dir, "panel_scan.txt");
            try (FileOutputStream o = new FileOutputStream(f)) {
                o.write(r.getBytes("UTF-8"));
            }
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    private java.util.List<String> localIps() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) out.add(a.getHostAddress());
                }
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.shutdown();
        super.onDestroy();
    }
}
