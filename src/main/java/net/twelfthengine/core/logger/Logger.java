// net/twelfthengine/core/logger/Logger.java
package net.twelfthengine.core.logger;

import net.twelfthengine.core.console.Console;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final boolean ENABLE_FILE_LOGGING = true;
    private static final String LOG_FILE_PATH = "logs/engine.log";
    private static PrintWriter writer;
    private static final boolean ENABLE_GUI_CONSOLE = true;

    static {
        if (ENABLE_FILE_LOGGING) {
            try {
                java.io.File logDir = new java.io.File("logs");
                if (!logDir.exists()) logDir.mkdirs();

                writer = new PrintWriter(new FileWriter(LOG_FILE_PATH, true));
            } catch (IOException e) {
                System.err.println("[Logger] Failed to initialize log file.");
                e.printStackTrace();
            }
        }
    }

    public static void log(LogLevel level, String tag, String message) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = "[" + now.format(formatter) + "]";

        String prefix = "[12th Engine][" + tag + "]";
        String fullMessage = timestamp + " [" + level.name() + "] " + prefix + ": " + message;

        // Print to system console with colors
        System.out.println(level.getColorCode() + fullMessage + "\u001B[0m");

        // Write to file if enabled
        if (writer != null) {
            writer.println(fullMessage);
            writer.flush();
        }

        // Send to GUI console if enabled
        if (ENABLE_GUI_CONSOLE) {
            try {
                switch (level) {
                    case INFO:
                        Console.getInstance().printInfo("[" + tag + "] " + message);
                        break;
                    case WARN:
                        Console.getInstance().printWarning("[" + tag + "] " + message);
                        break;
                    case ERROR:
                        Console.getInstance().printError("[" + tag + "] " + message);
                        break;
                    case DEBUG:
                        Console.getInstance().printDebug("[" + tag + "] " + message);
                        break;
                }
            } catch (Exception e) {
                // Ignore GUI errors
            }
        }
    }

    public static void info(String tag, String message) {
        log(LogLevel.INFO, tag, message);
    }

    public static void warn(String tag, String message) {
        log(LogLevel.WARN, tag, message);
    }

    public static void error(String tag, String message) {
        log(LogLevel.ERROR, tag, message);
    }

    public static void debug(String tag, String message) {
        log(LogLevel.DEBUG, tag, message);
    }

    public static void showConsole() {
        Console.getInstance().showConsole();
    }

    public static void hideConsole() {
        Console.getInstance().hideConsole();
    }
}
