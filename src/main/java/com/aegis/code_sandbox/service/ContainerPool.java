package com.aegis.code_sandbox.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class ContainerPool {

    private static final int POOL_SIZE = 3;
    private static final String IMAGE = "eclipse-temurin:17-alpine";

    private final BlockingQueue<String> availableContainers = new LinkedBlockingQueue<>();
    private final ExecutorService poolRefiller = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() throws Exception {
        System.out.println("Pulling Docker image...");
        new ProcessBuilder("docker", "pull", IMAGE)
                .inheritIO()
                .start()
                .waitFor();
        System.out.println("Image ready. Warming up container pool...");

        for (int i = 0; i < POOL_SIZE; i++) {
            availableContainers.add(createWarmContainer());
        }
        System.out.println("Container pool ready with " + POOL_SIZE + " containers.");
    }

    // Borrow a container — blocks up to 10s if none available
    public String borrow() throws InterruptedException {
        return availableContainers.poll(10, TimeUnit.SECONDS);
    }

    // After every execution, spin up a fresh replacement in the background
    public void returnFresh() {
        poolRefiller.submit(() -> {
            int attempts = 3;
            while (attempts-- > 0) {
                try {
                    availableContainers.add(createWarmContainer());
                    System.out.println("Replacement container added. Pool size: "
                            + availableContainers.size());
                    return; // success
                } catch (Exception e) {
                    System.err.println("Failed to create replacement (attempt "
                            + (3 - attempts) + "): " + e.getMessage());
                }
            }
            System.err.println("CRITICAL: Could not refill container pool!");
        });
    }

    private String createWarmContainer() throws Exception {
        String name = "warm_container_"
                + System.currentTimeMillis()
                + "_"
                + ThreadLocalRandom.current().nextInt(9999);

        String[] cmd = {
                "docker", "run",
                "--name", name,
                "-d",
                "--network", "none",
                "--memory=128m",
                "--memory-swap=128m",
                "--cpus=0.5",
                "--read-only",
                "--tmpfs", "/app:rw,noexec,size=32m",
                "--tmpfs", "/tmp:rw,noexec,size=16m",
                IMAGE,
                "tail", "-f", "/dev/null"
        };

        Process p = new ProcessBuilder(cmd).start();
        boolean started = p.waitFor(10, TimeUnit.SECONDS);

        if (!started || p.exitValue() != 0) {
            throw new RuntimeException("Failed to start warm container: " + name);
        }

        System.out.println("Warm container created: " + name);
        return name;
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("Shutting down container pool...");
        poolRefiller.shutdownNow();
        availableContainers.forEach(name -> {
            try {
                new ProcessBuilder("docker", "rm", "-f", name)
                        .start()
                        .waitFor(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        });
        System.out.println("Container pool shut down.");
    }
}