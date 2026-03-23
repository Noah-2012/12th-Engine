package net.twelfthengine.core.logger;

public enum LogLevel {
    INFO("\u001B[32m"),      // Green
    WARN("\u001B[33m"),      // Yellow
    ERROR("\u001B[31m"),     // Red
    DEBUG("\u001B[36m");     // Cyan

    private final String colorCode;

    LogLevel(String colorCode) {
        this.colorCode = colorCode;
    }

    public String getColorCode() {
        return colorCode;
    }
}
