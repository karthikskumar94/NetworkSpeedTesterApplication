package com.example.speedtest.NetworkSpeedTesterApplication.controller;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Random;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/speedtest")
public class SpeedTestController {
	
	@GetMapping("/ping")
    public ResponseEntity<String> pingServer() {
        try {
            long start = System.currentTimeMillis();
            InetAddress address = InetAddress.getByName("8.8.8.8");
            boolean reachable = address.isReachable(5000);
            long end = System.currentTimeMillis();

            if (reachable) {
                return ResponseEntity.ok("Ping time: " + (end - start) + " ms");
            } else {
                return ResponseEntity.status(504).body("Ping failed");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
	
	@GetMapping("/download")
    public ResponseEntity<String> testDownloadSpeed() {
        try {
            long start = System.currentTimeMillis();
            URL url = URI.create("https://speed.hetzner.de/1MB.bin").toURL();
            InputStream in = url.openStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                totalBytes += bytesRead;
            }
            long end = System.currentTimeMillis();
            in.close();

            double seconds = (end - start) / 1000.0;
            double mbps = (totalBytes * 8.0) / (seconds * 1000000.0);

            return ResponseEntity.ok("Download Speed: " + String.format("%.2f", mbps) + " Mbps");
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
            long end = System.currentTimeMillis();

            double seconds = (end - start) / 1000.0;
            double mbps = (uploadData.length * 8.0) / (seconds * 1000000.0);

            return ResponseEntity.ok("Upload Speed (simulated): " + String.format("%.2f", mbps) + " Mbps");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Upload Error: " + e.getMessage());
        }
    }

}
