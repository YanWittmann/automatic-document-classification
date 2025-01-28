package de.yanwittmann.document.model;

public class TimeStats {
    private long startTime;
    private long duration = -1;
    private boolean stopped = false;

    public TimeStats() {
        start();
    }

    public TimeStats(boolean autoStart) {
        if (autoStart) start();
    }

    public TimeStats start() {
        startTime = System.nanoTime();
        stopped = false;
        return this;
    }

    public long stop() {
        if (!stopped) {
            duration = System.nanoTime() - startTime;
            stopped = true;
        }
        return duration;
    }

    public String stopFormatted() {
        return formatDuration(stop());
    }

    private String formatDuration(long nanos) {
        if (nanos < 1_000_000) {
            return String.format("%dns", nanos);
        } else if (nanos < 1_000_000_000) {
            return String.format("%.1fms", nanos / 1_000_000.0);
        }
        return String.format("%.2fs", nanos / 1_000_000_000.0);
    }
}
