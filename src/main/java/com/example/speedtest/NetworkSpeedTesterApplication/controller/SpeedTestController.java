package com.example.speedtest.NetworkSpeedTesterApplication.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/speedtest")
public class SpeedTestController {
	
	private final ConcurrentHashMap<String, AtomicBoolean> uploadCancels = new ConcurrentHashMap<>();
	
	@Value("${app.upload.url:}")
    private String uploadUrl; // optional remote sink; if empty, simulate locally

    // ---------- Single-shot endpoints ----------
    @GetMapping("/ping")
    public ResponseEntity<String> pingServer() {
        try {
            long start = System.currentTimeMillis();
            boolean reachable = InetAddress.getByName("8.8.8.8").isReachable(5000);
            long end = System.currentTimeMillis();
            if (reachable) return ResponseEntity.ok("Ping time: " + (end - start) + " ms");
            return ResponseEntity.status(504).body("Ping failed");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/download")
    public ResponseEntity<String> testDownloadSpeed() {
        try {
            long start = System.currentTimeMillis();
            URL url = URI.create("http://speedtest.tele2.net/100MB.zip").toURL();
            try (InputStream in = url.openStream()) {
                byte[] buf = new byte[64 * 1024];
                long total = 0; int n;
                while ((n = in.read(buf)) != -1) total += n;
                double seconds = (System.currentTimeMillis() - start) / 1000.0;
                double mbps = (total * 8.0) / (seconds * 1_000_000.0);
                return ResponseEntity.ok("Download Speed: " + String.format("%.2f", mbps) + " Mbps");
            }
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Download Error: " + e.getMessage());
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<String> testUploadSpeed() {
        try {
            long start = System.currentTimeMillis();
            byte[] uploadData = new byte[10 * 1024 * 1024]; // 10 MB
            new Random().nextBytes(uploadData);
            double seconds = (System.currentTimeMillis() - start) / 1000.0;
            double mbps = (uploadData.length * 8.0) / (seconds * 1_000_000.0);
            return ResponseEntity.ok("Upload Speed (simulated): " + String.format("%.2f", mbps) + " Mbps");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Upload Error: " + e.getMessage());
        }
    }

    // ---------- Streaming endpoints (live variation) ----------
    @GetMapping(value = "/ping/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pingStream(@RequestParam(defaultValue = "5") int count,
                                 @RequestParam(defaultValue = "500") long intervalMs) {
        SseEmitter emitter = new SseEmitter(0L);
        new Thread(() -> {
            try {
                for (int i = 1; i <= count; i++) {
                    long start = System.currentTimeMillis();
                    boolean ok = InetAddress.getByName("8.8.8.8").isReachable(3000);
                    long latency = System.currentTimeMillis() - start;
                    String json = "{\"seq\":" + i + ",\"ok\":" + ok + ",\"latencyMs\":" + latency + "}";
                    emitter.send(SseEmitter.event().name("ping").data(json, MediaType.APPLICATION_JSON));
                    Thread.sleep(intervalMs);
                }
                emitter.send(SseEmitter.event().name("done").data("{\"done\":true}"));
                emitter.complete();
            } catch (Exception e) {
                try { emitter.send(SseEmitter.event().name("error").data(e.getMessage())); } catch (IOException ignore) {}
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }

    @GetMapping(value = "/download/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter downloadStream() {
        SseEmitter emitter = new SseEmitter(0L);
        new Thread(() -> {
            InputStream in = null;
            try {
                URL url = URI.create("http://speedtest.tele2.net/100MB.zip").toURL();
                URLConnection conn = url.openConnection();
                long contentLength = conn.getContentLengthLong();
                in = conn.getInputStream();

                byte[] buffer = new byte[256 * 1024]; // 256 KB
                long total = 0; long startedAt = System.currentTimeMillis(); long lastEmit = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    long now = System.currentTimeMillis();
                    double seconds = (now - startedAt) / 1000.0;
                    double avgMbps = seconds > 0 ? (total * 8.0) / (seconds * 1_000_000.0) : 0.0;
                    double percent = contentLength > 0 ? (total * 100.0 / contentLength) : -1;

                    if (now - lastEmit >= 100) {
                        String json = String.format(java.util.Locale.US,
                                "{\"bytes\":%d,\"avgMbps\":%.2f,\"percent\":%.2f}",
                                total, avgMbps, percent);
                        emitter.send(SseEmitter.event().name("progress").data(json));
                        lastEmit = now;
                    }
                }

                double totalSecs = (System.currentTimeMillis() - startedAt) / 1000.0;
                double finalMbps = (total * 8.0) / (Math.max(totalSecs, 0.001) * 1_000_000.0);
                String done = String.format(java.util.Locale.US,
                        "{\"done\":true,\"totalBytes\":%d,\"seconds\":%.2f,\"avgMbps\":%.2f}",
                        total, totalSecs, finalMbps);
                emitter.send(SseEmitter.event().name("done").data(done));
                emitter.complete();
            } catch (Exception e) {
                try { emitter.send(SseEmitter.event().name("error").data(e.getMessage())); } catch (IOException ignore) {}
                emitter.completeWithError(e);
            } finally {
                if (in != null) try { in.close(); } catch (IOException ignore) {}
            }
        }).start();
        return emitter;
    }

    @GetMapping(value = "/upload/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter uploadStream(@RequestParam(defaultValue = "10") int sizeMb,
                                   @RequestParam(defaultValue = "128") int chunkKb,
                                   @RequestParam String id) {  // <-- REQUIRED
        final long totalBytesPlanned = (long) sizeMb * 1024 * 1024;
        final int chunkBytes = Math.max(1, chunkKb) * 1024;

        final SseEmitter emitter = new SseEmitter(0L);

        // create/get cancel flag for this id
        final AtomicBoolean canceled = uploadCancels.computeIfAbsent(id, k -> new AtomicBoolean(false));

        // if connection closes, mark canceled
        emitter.onCompletion(() -> canceled.set(true));
        emitter.onTimeout(() -> canceled.set(true));
        emitter.onError(t -> canceled.set(true));

        new Thread(() -> {
            OutputStream out = null;
            HttpURLConnection http = null;
            try {
                long started = System.currentTimeMillis();
                long sent = 0L;
                byte[] chunk = new byte[chunkBytes];
                new Random().nextBytes(chunk);

                // Try real upload if app.upload.url is set, else simulate
                boolean simulate = (uploadUrl == null || uploadUrl.isBlank());
                try {
                    if (!simulate) {
                        URL target = URI.create(uploadUrl).toURL();
                        http = (HttpURLConnection) target.openConnection();
                        http.setDoOutput(true);
                        http.setRequestMethod("POST");
                        http.setConnectTimeout(5000);
                        http.setReadTimeout(5000);
                        http.setChunkedStreamingMode(chunkBytes);
                        http.connect();
                        out = http.getOutputStream();
                    }
                } catch (Exception connectEx) {
                    simulate = true;
                    try { emitter.send(SseEmitter.event().name("info").data("{\"fallback\":\"simulate\"}")); } catch (IOException ignore) {}
                }

                while (!canceled.get() && sent < totalBytesPlanned) {
                    int toWrite = (int) Math.min(chunkBytes, totalBytesPlanned - sent);

                    if (!simulate) {
                        out.write(chunk, 0, toWrite);
                        out.flush();
                    } else {
                        // Simulate a bit of work
                        for (int i = 0; i < toWrite; i += 4096) { /* no-op */ }
                    }

                    sent += toWrite;

                    if (canceled.get()) break;

                    double seconds = Math.max(0.001, (System.currentTimeMillis() - started) / 1000.0);
                    double avgMbps = (sent * 8.0) / (seconds * 1_000_000.0);
                    double percent = (sent * 100.0) / totalBytesPlanned;

                    String json = String.format(java.util.Locale.US,
                            "{\"bytes\":%d,\"avgMbps\":%.2f,\"percent\":%.2f}",
                            sent, avgMbps, percent);
                    emitter.send(SseEmitter.event().name("progress").data(json));
                }

                // Cleanup
                if (out != null) try { out.close(); } catch (Exception ignore) {}
                if (http != null) {
                    try { http.getResponseCode(); } catch (Exception ignore) {}
                    http.disconnect();
                }

                if (canceled.get()) {
                    // client stopped or cancel endpoint called
                    emitter.complete(); // no 'done'
                    return;
                }

                double totalSecs = Math.max(0.001, (System.currentTimeMillis() - started) / 1000.0);
                double finalMbps = (sent * 8.0) / (totalSecs * 1_000_000.0);
                String done = String.format(java.util.Locale.US,
                        "{\"done\":true,\"totalBytes\":%d,\"seconds\":%.2f,\"avgMbps\":%.2f}",
                        sent, totalSecs, finalMbps);
                emitter.send(SseEmitter.event().name("done").data(done));
                emitter.complete();
            } catch (Exception e) {
                try { emitter.send(SseEmitter.event().name("error").data(e.getMessage())); } catch (IOException ignore) {}
                emitter.completeWithError(e);
            } finally {
                // remove flag so map doesn't grow forever
                uploadCancels.remove(id);
            }
        }).start();

        return emitter;
    }
    
    @PostMapping("/sink")
    public ResponseEntity<String> sink(jakarta.servlet.http.HttpServletRequest request) {
        long total = 0;
        byte[] buf = new byte[256 * 1024];
        try (InputStream in = request.getInputStream()) {
            int n; while ((n = in.read(buf)) != -1) total += n;
            return ResponseEntity.ok("received=" + total);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("sink error: " + e.getMessage());
        }
    }
    
    @PostMapping("/upload/cancel")
    public ResponseEntity<String> cancelUpload(@RequestParam String id) {
        AtomicBoolean flag = uploadCancels.get(id);
        if (flag != null) {
            flag.set(true);
            return ResponseEntity.ok("canceled");
        }
        return ResponseEntity.ok("no-op"); // unknown/finished id
    }

}
