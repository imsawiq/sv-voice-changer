package org.sawiq.svvoicechanger.client.ai.install;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Which of the engine's three package sets fits this computer's graphics
 * card, chosen the way the engine's own start.bat chooses.
 *
 * <p>PyTorch for CUDA 13 needs an NVIDIA card of compute capability 7.5 (RTX
 * 20xx) or newer; older NVIDIA cards get the CUDA 12.6 build. Without
 * nvidia-smi there is no NVIDIA card, and the small DirectML build runs on
 * AMD, Intel or the processor.</p>
 */
public enum PackageSet {
    CUDA_13("requirements.txt", 2100),
    CUDA_12("requirements-cu126.txt", 2700),
    DIRECTML("requirements-dml.txt", 250);

    private static final long NVIDIA_SMI_TIMEOUT_SECONDS = 10L;

    private final String requirementsFile;
    private final int downloadMegabytes;

    PackageSet(String requirementsFile, int downloadMegabytes) {
        this.requirementsFile = requirementsFile;
        this.downloadMegabytes = downloadMegabytes;
    }

    public String requirementsFile() {
        return this.requirementsFile;
    }

    /** Roughly how much the packages download, for telling the player before they agree. */
    public int downloadMegabytes() {
        return this.downloadMegabytes;
    }

    /** Asks nvidia-smi for the first card's compute capability; DirectML when it cannot be asked. */
    public static PackageSet detect() {
        try {
            Process process = new ProcessBuilder("nvidia-smi", "--query-gpu=compute_cap", "--format=csv,noheader")
                    .redirectErrorStream(true)
                    .start();
            String firstLine;
            try (BufferedReader output = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                firstLine = output.readLine();
            }
            if (!process.waitFor(NVIDIA_SMI_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return DIRECTML;
            }
            return process.exitValue() == 0 ? forComputeCapability(firstLine) : DIRECTML;
        } catch (IOException exception) {
            return DIRECTML;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return DIRECTML;
        }
    }

    /** @param capability nvidia-smi's answer, such as "8.9"; anything unreadable means no usable card */
    static PackageSet forComputeCapability(String capability) {
        if (capability == null) {
            return DIRECTML;
        }
        String[] parts = capability.strip().split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 7 || major == 7 && minor >= 5 ? CUDA_13 : CUDA_12;
        } catch (NumberFormatException exception) {
            return DIRECTML;
        }
    }
}
