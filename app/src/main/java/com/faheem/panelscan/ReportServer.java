package com.faheem.panelscan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Minimal HTTP server so the report can be opened from a phone/laptop browser on the same WiFi.
 * Serves only the scan report. Listens while the app is open.
 */
final class ReportServer extends Thread {

    private volatile String report = "Scan chal raha hai... thori der baad dobara kholen.";
    private volatile boolean running = true;
    private volatile ServerSocket server;
    volatile int port = -1;

    ReportServer() {
        setDaemon(true);
        setName("report-server");
    }

    void setReport(String r) {
        report = r;
    }

    void shutdown() {
        running = false;
        try {
            ServerSocket s = server;
            if (s != null) s.close();
        } catch (IOException ignored) { }
    }

    @Override
    public void run() {
        for (int p = 8080; p <= 8090 && server == null; p++) {
            try {
                ServerSocket s = new ServerSocket();
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress(p));
                server = s;
                port = p;
            } catch (IOException ignored) { }
        }
        if (server == null) return;

        while (running) {
            try (Socket c = server.accept()) {
                c.setSoTimeout(4000);
                handle(c);
            } catch (IOException e) {
                if (!running) break;
            }
        }
    }

    private void handle(Socket c) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        String req = in.readLine();
        String path = "/";
        if (req != null) {
            String[] parts = req.split(" ");
            if (parts.length > 1) path = parts[1];
        }
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) { /* skip headers */ }

        byte[] body;
        String type;
        String extra = "";
        if (path.startsWith("/download")) {
            body = report.getBytes("UTF-8");
            type = "text/plain; charset=utf-8";
            extra = "Content-Disposition: attachment; filename=\"panel_scan.txt\"\r\n";
        } else if (path.startsWith("/panel_scan.txt")) {
            body = report.getBytes("UTF-8");
            type = "text/plain; charset=utf-8";
        } else {
            String html = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                    + "<title>Panel Scan</title>"
                    + "<body style='font-family:sans-serif;padding:24px;font-size:20px'>"
                    + "<h2>Panel Scan report tayyar hai</h2>"
                    + "<p><a href='/download'>Download panel_scan.txt</a></p>"
                    + "<p><a href='/panel_scan.txt'>Browser mein kholen</a></p>"
                    + "<p>Size: " + (report.length() / 1024) + " KB</p></body>";
            body = html.getBytes("UTF-8");
            type = "text/html; charset=utf-8";
        }

        String head = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + extra
                + "Connection: close\r\n\r\n";
        OutputStream out = c.getOutputStream();
        out.write(head.getBytes("UTF-8"));
        out.write(body);
        out.flush();
    }
}
