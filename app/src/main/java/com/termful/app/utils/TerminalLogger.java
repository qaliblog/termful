package com.termful.app.utils;

import android.app.Activity;
import com.termful.terminal.TerminalSession;
import com.termful.app.TermuxActivity;

/**
 * Utility class to write log messages directly to the terminal instead of using Android's logcat.
 * This makes debugging easier when logcat access is not available.
 */
public class TerminalLogger {
    
    private static final String LOG_TAG = "TerminalLogger";
    
    /**
     * Write a log message to the terminal if available.
     * @param activity The activity context
     * @param tag The log tag
     * @param message The message to log
     */
    public static void logToTerminal(Activity activity, String tag, String message) {
        try {
            if (activity instanceof TermuxActivity) {
                TermuxActivity termuxActivity = (TermuxActivity) activity;
                TerminalSession currentSession = termuxActivity.getCurrentSession();
                if (currentSession != null) {
                    String logMessage = "[" + tag + "] " + message + "\r\n";
                    currentSession.write(logMessage);
                }
            }
        } catch (Exception e) {
            // If terminal logging fails, silently continue
            // This prevents bootstrap installation from failing due to logging issues
        }
    }
    
    /**
     * Write an info message to the terminal.
     */
    public static void logInfo(Activity activity, String tag, String message) {
        logToTerminal(activity, tag, "INFO: " + message);
    }
    
    /**
     * Write an error message to the terminal.
     */
    public static void logError(Activity activity, String tag, String message) {
        logToTerminal(activity, tag, "ERROR: " + message);
    }
    
    /**
     * Write a warning message to the terminal.
     */
    public static void logWarning(Activity activity, String tag, String message) {
        logToTerminal(activity, tag, "WARNING: " + message);
    }
    
    /**
     * Write a debug message to the terminal.
     */
    public static void logDebug(Activity activity, String tag, String message) {
        logToTerminal(activity, tag, "DEBUG: " + message);
    }
}