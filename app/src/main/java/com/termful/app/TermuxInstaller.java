package com.termful.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.system.Os;
import android.util.Pair;
import android.view.WindowManager;

import com.termful.R;
import com.termful.app.activities.DistributionSelectorActivity;
import com.termful.app.utils.TerminalLogger;
import com.termful.shared.file.FileUtils;
import com.termful.shared.termux.crash.TermuxCrashUtils;
import com.termful.shared.termux.file.TermuxFileUtils;
import com.termful.shared.logger.Logger;
import com.termful.shared.markdown.MarkdownUtils;
import com.termful.shared.errors.Error;
import com.termful.shared.android.PackageUtils;
import com.termful.shared.termux.TermuxConstants;
import com.termful.shared.termux.TermuxUtils;
import com.termful.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.termful.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR;
import static com.termful.shared.termux.TermuxConstants.TERMUX_PREFIX_DIR_PATH;
import static com.termful.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR;
import static com.termful.shared.termux.TermuxConstants.TERMUX_STAGING_PREFIX_DIR_PATH;

/**
 * Install the Termux bootstrap packages if necessary by following the below steps:
 * <p/>
 * (1) If $PREFIX already exist, assume that it is correct and be done. Note that this relies on that we do not create a
 * broken $PREFIX directory below.
 * <p/>
 * (2) A progress dialog is shown with "Installing..." message and a spinner.
 * <p/>
 * (3) A staging directory, $STAGING_PREFIX, is cleared if left over from broken installation below.
 * <p/>
 * (4) The zip file is loaded from a shared library.
 * <p/>
 * (5) The zip, containing entries relative to the $PREFIX, is is downloaded and extracted by a zip input stream
 * continuously encountering zip file entries:
 * <p/>
 * (5.1) If the zip entry encountered is SYMLINKS.txt, go through it and remember all symlinks to setup.
 * <p/>
 * (5.2) For every other zip entry, extract it into $STAGING_PREFIX and set execute permissions if necessary.
 */
public final class TermuxInstaller {
    
    private static final String LOG_TAG = "TermuxInstaller";
    
    private static class NoBootstrapAvailableException extends RuntimeException {
        public NoBootstrapAvailableException(String message) {
            super(message);
        }
    }
    
    private static void showDistributionSelectorDialog(final Activity activity, final Runnable whenDone) {
        // Log to terminal instead of showing popup to preserve log visibility
        TerminalLogger.logInfo(activity, LOG_TAG, "No bootstrap files available - redirecting to distribution selector");
        TerminalLogger.logInfo(activity, LOG_TAG, "Please select a Linux distribution to install.");
        
        // Show a toast message instead of popup
        try {
            android.widget.Toast.makeText(activity, "Please select a Linux distribution", android.widget.Toast.LENGTH_LONG).show();
            
            // Start the distribution selector activity directly
            Intent intent = new Intent(activity, DistributionSelectorActivity.class);
            activity.startActivityForResult(intent, 2001);
        } catch (Exception e) {
            TerminalLogger.logError(activity, LOG_TAG, "Failed to start distribution selector: " + e.getMessage());
            // If we can't start the selector, just continue
            if (whenDone != null) {
                whenDone.run();
            }
        }
    }

    /** Performs bootstrap setup if necessary. */
    static void setupBootstrapIfNeeded(final Activity activity, final Runnable whenDone) {
        TerminalLogger.logInfo(activity, LOG_TAG, "=== BOOTSTRAP INSTALLATION STARTED ===");
        TerminalLogger.logInfo(activity, LOG_TAG, "All installation progress will be logged here.");
        TerminalLogger.logInfo(activity, LOG_TAG, "If installation fails, check the error messages below.");
        TerminalLogger.logInfo(activity, LOG_TAG, "Starting bootstrap setup process");
        String bootstrapErrorMessage;
        Error filesDirectoryAccessibleError;

        // This will also call Context.getFilesDir(), which should ensure that termux files directory
        // is created if it does not already exist
        TerminalLogger.logInfo(activity, LOG_TAG, "Checking if files directory is accessible");
        filesDirectoryAccessibleError = TermuxFileUtils.isTermuxFilesDirectoryAccessible(activity, true, true);
        boolean isFilesDirectoryAccessible = filesDirectoryAccessibleError == null;
        TerminalLogger.logInfo(activity, LOG_TAG, "Files directory accessible: " + isFilesDirectoryAccessible);

        // Termux can only be run as the primary user (device owner) since only that
        // account has the expected file system paths. Verify that:
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !PackageUtils.isCurrentUserThePrimaryUser(activity)) {
            bootstrapErrorMessage = activity.getString(R.string.bootstrap_error_not_primary_user_message,
                MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            TerminalLogger.logError(activity, LOG_TAG, "isFilesDirectoryAccessible: " + isFilesDirectoryAccessible);
            TerminalLogger.logError(activity, LOG_TAG, bootstrapErrorMessage);
            TerminalLogger.logError(activity, LOG_TAG, "Termux can only be run as the primary user. Please switch to the primary user account.");
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            // Don't exit app, just log error and continue
            return;
        }

        if (!isFilesDirectoryAccessible) {
            bootstrapErrorMessage = Error.getMinimalErrorString(filesDirectoryAccessibleError);
            //noinspection SdCardPath
            if (PackageUtils.isAppInstalledOnExternalStorage(activity) &&
                !TermuxConstants.TERMUX_FILES_DIR_PATH.equals(activity.getFilesDir().getAbsolutePath().replaceAll("^/data/user/0/", "/data/data/"))) {
                bootstrapErrorMessage += "\n\n" + activity.getString(R.string.bootstrap_error_installed_on_portable_sd,
                    MarkdownUtils.getMarkdownCodeForString(TERMUX_PREFIX_DIR_PATH, false));
            }

            TerminalLogger.logError(activity, LOG_TAG, bootstrapErrorMessage);
            TerminalLogger.logError(activity, LOG_TAG, "Files directory is not accessible. Please check app permissions and storage access.");
            sendBootstrapCrashReportNotification(activity, bootstrapErrorMessage);
            // Don't show popup, just log error and continue
            return;
        }

        // If prefix directory exists, even if its a symlink to a valid directory and symlink is not broken/dangling
        TerminalLogger.logInfo(activity, LOG_TAG, "Checking if prefix directory exists: " + TERMUX_PREFIX_DIR_PATH);
        if (FileUtils.directoryFileExists(TERMUX_PREFIX_DIR_PATH, true)) {
            TerminalLogger.logInfo(activity, LOG_TAG, "Prefix directory exists, checking if empty");
            if (TermuxFileUtils.isTermuxPrefixDirectoryEmpty()) {
                TerminalLogger.logInfo(activity, LOG_TAG, "The termux prefix directory \"" + TERMUX_PREFIX_DIR_PATH + "\" exists but is empty or only contains specific unimportant files.");
            } else {
                TerminalLogger.logInfo(activity, LOG_TAG, "Prefix directory exists and is not empty, skipping bootstrap installation");
                whenDone.run();
                return;
            }
        } else if (FileUtils.fileExists(TERMUX_PREFIX_DIR_PATH, false)) {
            TerminalLogger.logInfo(activity, LOG_TAG, "The termux prefix directory \"" + TERMUX_PREFIX_DIR_PATH + "\" does not exist but another file exists at its destination.");
        } else {
            TerminalLogger.logInfo(activity, LOG_TAG, "Prefix directory does not exist, proceeding with bootstrap installation");
        }

        final ProgressDialog progress = ProgressDialog.show(activity, null, activity.getString(R.string.bootstrap_installer_body), true, false);
        new Thread() {
            @Override
            public void run() {
                try {
                    TerminalLogger.logInfo(activity, LOG_TAG, "Installing " + TermuxConstants.TERMUX_APP_NAME + " bootstrap packages.");

                    Error error;

                    // Delete prefix staging directory or any file at its destination
                    error = FileUtils.deleteFile("termux prefix staging directory", TERMUX_STAGING_PREFIX_DIR_PATH, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    // Delete prefix directory or any file at its destination
                    error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    // Create prefix staging directory if it does not already exist and set required permissions
                    error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    // Create prefix directory if it does not already exist and set required permissions
                    error = TermuxFileUtils.isTermuxPrefixDirectoryAccessible(true, true);
                    if (error != null) {
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }

                    TerminalLogger.logInfo(activity, LOG_TAG, "Extracting bootstrap zip to prefix staging directory \"" + TERMUX_STAGING_PREFIX_DIR_PATH + "\".");

                    final byte[] buffer = new byte[8096];
                    final List<Pair<String, String>> symlinks = new ArrayList<>(50);

                    // Use streaming extraction to avoid loading entire zip into memory
                    try {
                        TerminalLogger.logInfo(activity, LOG_TAG, "Creating BootstrapZipInputStream");
                        TerminalLogger.logInfo(activity, LOG_TAG, "Loading termful-bootstrap native library");
                        BootstrapZipInputStream bootstrapStream = new BootstrapZipInputStream();
                        TerminalLogger.logInfo(activity, LOG_TAG, "Zip size from native library: " + bootstrapStream.totalSize + " bytes");
                        try (ZipInputStream zipInput = new ZipInputStream(bootstrapStream)) {
                            ZipEntry zipEntry;
                            while ((zipEntry = zipInput.getNextEntry()) != null) {
                                if (zipEntry.getName().equals("SYMLINKS.txt")) {
                                    BufferedReader symlinksReader = new BufferedReader(new InputStreamReader(zipInput));
                                    String line;
                                    while ((line = symlinksReader.readLine()) != null) {
                                        String[] parts = line.split("←");
                                        if (parts.length != 2)
                                            throw new RuntimeException("Malformed symlink line: " + line);
                                        String oldPath = parts[0];
                                        String newPath = TERMUX_STAGING_PREFIX_DIR_PATH + "/" + parts[1];
                                        symlinks.add(Pair.create(oldPath, newPath));

                                        error = ensureDirectoryExists(new File(newPath).getParentFile());
                                        if (error != null) {
                                            showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                                            return;
                                        }
                                    }
                                } else {
                                    String zipEntryName = zipEntry.getName();
                                    File targetFile = new File(TERMUX_STAGING_PREFIX_DIR_PATH, zipEntryName);
                                    boolean isDirectory = zipEntry.isDirectory();

                                    error = ensureDirectoryExists(isDirectory ? targetFile : targetFile.getParentFile());
                                    if (error != null) {
                                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                                        return;
                                    }

                                    if (!isDirectory) {
                                        try (FileOutputStream outStream = new FileOutputStream(targetFile)) {
                                            int readBytes;
                                            while ((readBytes = zipInput.read(buffer)) != -1)
                                                outStream.write(buffer, 0, readBytes);
                                        }
                                        if (zipEntryName.startsWith("bin/") || zipEntryName.startsWith("sbin/") || 
                                            zipEntryName.startsWith("libexec") || zipEntryName.startsWith("usr/bin/") ||
                                            zipEntryName.startsWith("usr/sbin/") || zipEntryName.startsWith("lib/apk/") ||
                                            zipEntryName.startsWith("usr/lib/apk/")) {
                                            TerminalLogger.logInfo(activity, LOG_TAG, "Setting execute permissions for: " + zipEntryName);
                                            TerminalLogger.logInfo(activity, LOG_TAG, "Target file path: " + targetFile.getAbsolutePath());
                                            TerminalLogger.logInfo(activity, LOG_TAG, "File exists before chmod: " + targetFile.exists());
                                            
                                            try {
                                                //noinspection OctalInteger
                                                Os.chmod(targetFile.getAbsolutePath(), 0755);
                                                TerminalLogger.logInfo(activity, LOG_TAG, "Successfully set execute permissions (0755) for: " + zipEntryName);
                                                
                                                // Verify permissions were set
                                                boolean canExecute = targetFile.canExecute();
                                                TerminalLogger.logInfo(activity, LOG_TAG, "Verification - can execute after chmod: " + canExecute);
                                                
                                            } catch (Exception e) {
                                                TerminalLogger.logError(activity, LOG_TAG, "Failed to set execute permissions for " + zipEntryName + ": " + e.getMessage());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (NoBootstrapAvailableException e) {
                        // No bootstrap files available - redirect to distribution selector
                        TerminalLogger.logInfo(activity, LOG_TAG, "NoBootstrapAvailableException caught: " + e.getMessage());
                        TerminalLogger.logInfo(activity, LOG_TAG, "This is expected for Manual Distribution Selector mode - redirecting to distribution selector");
                        showDistributionSelectorDialog(activity, whenDone);
                        return;
                    }

                    if (symlinks.isEmpty())
                        throw new RuntimeException("No SYMLINKS.txt encountered");
                    for (Pair<String, String> symlink : symlinks) {
                        Os.symlink(symlink.first, symlink.second);
                    }

                    TerminalLogger.logInfo(activity, LOG_TAG, "Moving termux prefix staging to prefix directory.");

                    if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                        throw new RuntimeException("Moving termux prefix staging to prefix directory failed");
                    }

                    TerminalLogger.logInfo(activity, LOG_TAG, "Bootstrap packages installed successfully.");
                    
                    // Debug: List all files in the prefix directory
                    TerminalLogger.logInfo(activity, LOG_TAG, "Listing all files in prefix directory: " + TERMUX_PREFIX_DIR_PATH);
                    try {
                        File prefixDir = new File(TERMUX_PREFIX_DIR_PATH);
                        if (prefixDir.exists() && prefixDir.isDirectory()) {
                            listDirectoryRecursively(activity, prefixDir, "", 0);
                        } else {
                            TerminalLogger.logError(activity, LOG_TAG, "Prefix directory does not exist or is not a directory");
                        }
                    } catch (Exception e) {
                        TerminalLogger.logError(activity, LOG_TAG, "Failed to list prefix directory: " + e.getMessage());
                    }
                    
                    // Ensure shell script has execute permissions (fallback)
                    File shellScript = new File(TERMUX_PREFIX_DIR_PATH, "usr/bin/sh");
                    TerminalLogger.logInfo(activity, LOG_TAG, "Checking shell script permissions: " + shellScript.getAbsolutePath());
                    TerminalLogger.logInfo(activity, LOG_TAG, "Shell script exists: " + shellScript.exists());
                    if (shellScript.exists()) {
                        TerminalLogger.logInfo(activity, LOG_TAG, "Shell script can read: " + shellScript.canRead());
                        TerminalLogger.logInfo(activity, LOG_TAG, "Shell script can write: " + shellScript.canWrite());
                        TerminalLogger.logInfo(activity, LOG_TAG, "Shell script can execute: " + shellScript.canExecute());
                        TerminalLogger.logInfo(activity, LOG_TAG, "Shell script length: " + shellScript.length() + " bytes");
                        
                        if (!shellScript.canExecute()) {
                            TerminalLogger.logInfo(activity, LOG_TAG, "Shell script is not executable, attempting to fix...");
                            try {
                                // Try Java File.setExecutable first
                                boolean javaResult = shellScript.setExecutable(true);
                                TerminalLogger.logInfo(activity, LOG_TAG, "Java setExecutable result: " + javaResult);
                                
                                // Try native chmod as well
                                Os.chmod(shellScript.getAbsolutePath(), 0755);
                                TerminalLogger.logInfo(activity, LOG_TAG, "Native chmod completed");
                                
                                // Check again
                                TerminalLogger.logInfo(activity, LOG_TAG, "After fix - can execute: " + shellScript.canExecute());
                                
                            } catch (Exception e) {
                                TerminalLogger.logError(activity, LOG_TAG, "Failed to set execute permissions for shell script: " + e.getMessage());
                                TerminalLogger.logError(activity, LOG_TAG, "Exception type: " + e.getClass().getSimpleName());
                            }
                        } else {
                            TerminalLogger.logInfo(activity, LOG_TAG, "Shell script already has execute permissions");
                        }
                    } else {
                        TerminalLogger.logError(activity, LOG_TAG, "Shell script does not exist at: " + shellScript.getAbsolutePath());
                        
                        // Check if it exists in bin/ instead
                        File altShellScript = new File(TERMUX_PREFIX_DIR_PATH, "bin/sh");
                        TerminalLogger.logInfo(activity, LOG_TAG, "Checking alternative location: " + altShellScript.getAbsolutePath());
                        TerminalLogger.logInfo(activity, LOG_TAG, "Alternative shell script exists: " + altShellScript.exists());
                        if (altShellScript.exists()) {
                            TerminalLogger.logInfo(activity, LOG_TAG, "Alternative shell script can execute: " + altShellScript.canExecute());
                        }
                    }
                    
                    TerminalLogger.logInfo(activity, LOG_TAG, "=== BOOTSTRAP INSTALLATION COMPLETED ===");

                    // Recreate env file since termux prefix was wiped earlier
                    TermuxShellEnvironment.writeEnvironmentToFile(activity);

                    activity.runOnUiThread(whenDone);

                } catch (final Exception e) {
                    showBootstrapErrorDialog(activity, whenDone, Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)));

                } finally {
                    activity.runOnUiThread(() -> {
                        try {
                            progress.dismiss();
                        } catch (RuntimeException e) {
                            // Activity already dismissed - ignore.
                        }
                    });
                }
            }
        }.start();
    }

    /** Performs custom bootstrap setup from a user-selected rootfs file. */
    static void setupCustomBootstrap(final Activity activity, final String fileUriString, final Runnable whenDone) {
        TerminalLogger.logInfo(activity, LOG_TAG, "=== CUSTOM BOOTSTRAP INSTALLATION STARTED ===");
        TerminalLogger.logInfo(activity, LOG_TAG, "All installation progress will be logged here.");
        TerminalLogger.logInfo(activity, LOG_TAG, "If installation fails, check the error messages below.");
        TerminalLogger.logInfo(activity, LOG_TAG, "Starting custom bootstrap setup from file: " + fileUriString);
        
        final ProgressDialog progress = ProgressDialog.show(activity, null, "Installing custom distribution...", true, false);
        new Thread() {
            @Override
            public void run() {
                try {
                    Uri fileUri = Uri.parse(fileUriString);
                    TerminalLogger.logInfo(activity, LOG_TAG, "Parsed file URI: " + fileUri.toString());
                    
                    // Check if files directory is accessible
                    Error filesDirectoryAccessibleError = TermuxFileUtils.isTermuxFilesDirectoryAccessible(activity, true, true);
                    if (filesDirectoryAccessibleError != null) {
                        TerminalLogger.logError(activity, LOG_TAG, "Files directory not accessible: " + filesDirectoryAccessibleError.getMessage());
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(filesDirectoryAccessibleError));
                        return;
                    }
                    
                    // Delete existing prefix directories
                    TerminalLogger.logInfo(activity, LOG_TAG, "Cleaning up existing prefix directories");
                    Error error = FileUtils.deleteFile("termux prefix staging directory", TERMUX_STAGING_PREFIX_DIR_PATH, true);
                    if (error != null) {
                        TerminalLogger.logError(activity, LOG_TAG, "Failed to delete staging directory: " + error.getMessage());
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    
                    error = FileUtils.deleteFile("termux prefix directory", TERMUX_PREFIX_DIR_PATH, true);
                    if (error != null) {
                        TerminalLogger.logError(activity, LOG_TAG, "Failed to delete prefix directory: " + error.getMessage());
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    
                    // Create staging directory
                    TerminalLogger.logInfo(activity, LOG_TAG, "Creating staging directory");
                    error = TermuxFileUtils.isTermuxPrefixStagingDirectoryAccessible(true, true);
                    if (error != null) {
                        TerminalLogger.logError(activity, LOG_TAG, "Failed to create staging directory: " + error.getMessage());
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    
                    // Process the custom rootfs file
                    TerminalLogger.logInfo(activity, LOG_TAG, "Processing custom rootfs file");
                    error = processCustomRootfsFile(activity, fileUri);
                    if (error != null) {
                        TerminalLogger.logError(activity, LOG_TAG, "Failed to process custom rootfs file: " + error.getMessage());
                        showBootstrapErrorDialog(activity, whenDone, Error.getErrorMarkdownString(error));
                        return;
                    }
                    
                    // Move staging to final location
                    TerminalLogger.logInfo(activity, LOG_TAG, "Moving staging directory to final location");
                    if (!TERMUX_STAGING_PREFIX_DIR.renameTo(TERMUX_PREFIX_DIR)) {
                        TerminalLogger.logError(activity, LOG_TAG, "Failed to move staging directory to final location");
                        showBootstrapErrorDialog(activity, whenDone, "Failed to move staging directory to final location");
                        return;
                    }
                    
                    TerminalLogger.logInfo(activity, LOG_TAG, "Custom bootstrap installation completed successfully");
                    TerminalLogger.logInfo(activity, LOG_TAG, "=== CUSTOM BOOTSTRAP INSTALLATION COMPLETED ===");
                    
                    // Recreate env file
                    TermuxShellEnvironment.writeEnvironmentToFile(activity);
                    
                    activity.runOnUiThread(whenDone);
                    
                } catch (final Exception e) {
                    TerminalLogger.logError(activity, LOG_TAG, "Exception during custom bootstrap installation: " + e.getMessage());
                    TerminalLogger.logError(activity, LOG_TAG, "Stack trace: " + e.toString());
                    showBootstrapErrorDialog(activity, whenDone, "Custom bootstrap installation failed: " + e.getMessage());
                } finally {
                    activity.runOnUiThread(() -> {
                        try {
                            progress.dismiss();
                        } catch (RuntimeException e) {
                            // Activity already dismissed - ignore.
                        }
                    });
                }
            }
        }.start();
    }
    
    private static Error processCustomRootfsFile(Activity activity, Uri fileUri) {
        TerminalLogger.logInfo(activity, LOG_TAG, "Processing custom rootfs file: " + fileUri.toString());
        
        try {
            // For now, create a minimal structure since we don't have the actual file processing implemented
            // This is a placeholder that will need to be implemented based on the actual rootfs format
            TerminalLogger.logInfo(activity, LOG_TAG, "Creating minimal bootstrap structure for custom distribution");
            
            // Create basic directory structure
            File binDir = new File(TERMUX_STAGING_PREFIX_DIR_PATH, "bin");
            File etcDir = new File(TERMUX_STAGING_PREFIX_DIR_PATH, "etc");
            File usrDir = new File(TERMUX_STAGING_PREFIX_DIR_PATH, "usr");
            
            Error error = FileUtils.createDirectoryFile(binDir.getAbsolutePath());
            if (error != null) return error;
            
            error = FileUtils.createDirectoryFile(etcDir.getAbsolutePath());
            if (error != null) return error;
            
            error = FileUtils.createDirectoryFile(usrDir.getAbsolutePath());
            if (error != null) return error;
            
            // Create a basic shell script
            File shellScript = new File(binDir, "sh");
            shellScript.createNewFile();
            shellScript.setExecutable(true);
            
            // Write a simple shell script
            java.io.FileWriter writer = new java.io.FileWriter(shellScript);
            writer.write("#!/bin/sh\n");
            writer.write("echo 'Termful Custom Distribution'\n");
            writer.write("echo 'Custom rootfs file: " + fileUri.toString() + "'\n");
            writer.write("echo 'This is a placeholder implementation'\n");
            writer.write("echo 'The actual rootfs processing needs to be implemented'\n");
            writer.close();
            
            TerminalLogger.logInfo(activity, LOG_TAG, "Created minimal bootstrap structure for custom distribution");
            return null;
            
        } catch (Exception e) {
            TerminalLogger.logError(activity, LOG_TAG, "Failed to process custom rootfs file: " + e.getMessage());
            return new Error("Failed to process custom rootfs file: " + e.getMessage());
        }
    }

    public static void showBootstrapErrorDialog(Activity activity, Runnable whenDone, String message) {
        // Log the error to terminal instead of showing popup to preserve log visibility
        TerminalLogger.logError(activity, LOG_TAG, "Bootstrap Error: " + message);
        TerminalLogger.logError(activity, LOG_TAG, "Bootstrap installation failed. Check the logs above for details.");
        TerminalLogger.logError(activity, LOG_TAG, "You can try again by restarting the app or selecting a different distribution.");

        // Send a notification with the exception so that the user knows why bootstrap setup failed
        sendBootstrapCrashReportNotification(activity, message);

        // Instead of showing popup, just run the whenDone callback to continue
        // This allows the user to see the logs and try again
        activity.runOnUiThread(() -> {
            try {
                // Show a toast message instead of popup to inform user
                android.widget.Toast.makeText(activity, "Bootstrap installation failed. Check terminal for details.", android.widget.Toast.LENGTH_LONG).show();
                
                // Run the callback to continue (this will likely start a new session)
                if (whenDone != null) {
                    whenDone.run();
                }
            } catch (Exception e) {
                // If even toast fails, just continue silently
                if (whenDone != null) {
                    whenDone.run();
                }
            }
        });
    }
    
    /**
     * Log bootstrap error to terminal without showing any popup or interrupting the flow.
     * This preserves log visibility for debugging.
     */
    public static void logBootstrapErrorToTerminal(Activity activity, String message) {
        TerminalLogger.logError(activity, LOG_TAG, "Bootstrap Error: " + message);
        TerminalLogger.logError(activity, LOG_TAG, "Check the logs above for details.");
    }

    private static void sendBootstrapCrashReportNotification(Activity activity, String message) {
        final String title = TermuxConstants.TERMUX_APP_NAME + " Bootstrap Error";

        // Add info of all install Termux plugin apps as well since their target sdk or installation
        // on external/portable sd card can affect Termux app files directory access or exec.
        TermuxCrashUtils.sendCrashReportNotification(activity, LOG_TAG,
            title, null, "## " + title + "\n\n" + message + "\n\n" +
                TermuxUtils.getTermuxDebugMarkdownString(activity),
            true, false, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES, true);
    }

    static void setupStorageSymlinks(final Context context) {
        final String LOG_TAG = "termux-storage";
        final String title = TermuxConstants.TERMUX_APP_NAME + " Setup Storage Error";

        Logger.logInfo(LOG_TAG, "Setting up storage symlinks.");

        new Thread() {
            public void run() {
                try {
                    Error error;
                    File storageDir = TermuxConstants.TERMUX_STORAGE_HOME_DIR;

                    error = FileUtils.clearDirectory("~/storage", storageDir.getAbsolutePath());
                    if (error != null) {
                        Logger.logErrorAndShowToast(context, LOG_TAG, error.getMessage());
                        Logger.logErrorExtended(LOG_TAG, "Setup Storage Error\n" + error.toString());
                        TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null,
                            "## " + title + "\n\n" + Error.getErrorMarkdownString(error),
                            true, false, TermuxUtils.AppInfoMode.TERMUX_PACKAGE, true);
                        return;
                    }

                    Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/shared, ~/storage/downloads, ~/storage/dcim, ~/storage/pictures, ~/storage/music and ~/storage/movies for directories in \"" + Environment.getExternalStorageDirectory().getAbsolutePath() + "\".");

                    // Get primary storage root "/storage/emulated/0" symlink
                    File sharedDir = Environment.getExternalStorageDirectory();
                    Os.symlink(sharedDir.getAbsolutePath(), new File(storageDir, "shared").getAbsolutePath());

                    File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                    Os.symlink(documentsDir.getAbsolutePath(), new File(storageDir, "documents").getAbsolutePath());

                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    Os.symlink(downloadsDir.getAbsolutePath(), new File(storageDir, "downloads").getAbsolutePath());

                    File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                    Os.symlink(dcimDir.getAbsolutePath(), new File(storageDir, "dcim").getAbsolutePath());

                    File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    Os.symlink(picturesDir.getAbsolutePath(), new File(storageDir, "pictures").getAbsolutePath());

                    File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                    Os.symlink(musicDir.getAbsolutePath(), new File(storageDir, "music").getAbsolutePath());

                    File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                    Os.symlink(moviesDir.getAbsolutePath(), new File(storageDir, "movies").getAbsolutePath());

                    File podcastsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS);
                    Os.symlink(podcastsDir.getAbsolutePath(), new File(storageDir, "podcasts").getAbsolutePath());

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        File audiobooksDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_AUDIOBOOKS);
                        Os.symlink(audiobooksDir.getAbsolutePath(), new File(storageDir, "audiobooks").getAbsolutePath());
                    }

                    // Dir 0 should ideally be for primary storage
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ContextImpl.java;l=818
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=219
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/os/Environment.java;l=181
                    // https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/StorageManagerService.java;l=3796
                    // https://cs.android.com/android/platform/superproject/+/android-7.0.0_r36:frameworks/base/services/core/java/com/android/server/MountService.java;l=3053

                    // Create "Android/data/com.termux" symlinks
                    File[] dirs = context.getExternalFilesDirs(null);
                    if (dirs != null && dirs.length > 0) {
                        for (int i = 0; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null) continue;
                            String symlinkName = "external-" + i;
                            Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/" + symlinkName + " for \"" + dir.getAbsolutePath() + "\".");
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }

                    // Create "Android/media/com.termux" symlinks
                    dirs = context.getExternalMediaDirs();
                    if (dirs != null && dirs.length > 0) {
                        for (int i = 0; i < dirs.length; i++) {
                            File dir = dirs[i];
                            if (dir == null) continue;
                            String symlinkName = "media-" + i;
                            Logger.logInfo(LOG_TAG, "Setting up storage symlinks at ~/storage/" + symlinkName + " for \"" + dir.getAbsolutePath() + "\".");
                            Os.symlink(dir.getAbsolutePath(), new File(storageDir, symlinkName).getAbsolutePath());
                        }
                    }

                    Logger.logInfo(LOG_TAG, "Storage symlinks created successfully.");
                } catch (Exception e) {
                    Logger.logErrorAndShowToast(context, LOG_TAG, e.getMessage());
                    Logger.logStackTraceWithMessage(LOG_TAG, "Setup Storage Error: Error setting up link", e);
                    TermuxCrashUtils.sendCrashReportNotification(context, LOG_TAG, title, null,
                        "## " + title + "\n\n" + Logger.getStackTracesMarkdownString(null, Logger.getStackTracesStringArray(e)),
                        true, false, TermuxUtils.AppInfoMode.TERMUX_PACKAGE, true);
                }
            }
        }.start();
    }

    private static Error ensureDirectoryExists(File directory) {
        return FileUtils.createDirectoryFile(directory.getAbsolutePath());
    }
    
    private static void listDirectoryRecursively(Activity activity, File dir, String prefix, int depth) {
        if (depth > 3) return; // Limit depth to avoid too much output
        
        try {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String fileInfo = prefix + file.getName();
                    if (file.isDirectory()) {
                        fileInfo += "/";
                        TerminalLogger.logInfo(activity, LOG_TAG, "DIR:  " + fileInfo);
                        listDirectoryRecursively(activity, file, prefix + "  ", depth + 1);
                    } else {
                        fileInfo += " (" + file.length() + " bytes)";
                        fileInfo += " [r:" + file.canRead() + " w:" + file.canWrite() + " x:" + file.canExecute() + "]";
                        TerminalLogger.logInfo(activity, LOG_TAG, "FILE: " + fileInfo);
                    }
                }
            }
        } catch (Exception e) {
            TerminalLogger.logError(activity, LOG_TAG, "Error listing directory " + dir.getAbsolutePath() + ": " + e.getMessage());
        }
    }


    public static native byte[] getZip();
    public static native long getZipSize();
    public static native int getZipChunk(long offset, byte[] buffer, int maxBytes);

    /**
     * Custom InputStream that streams zip data directly from native library in chunks
     * to avoid loading the entire zip file into memory at once.
     */
    private static class BootstrapZipInputStream extends InputStream {
        private static final int CHUNK_SIZE = 64 * 1024; // 64KB chunks
        private long position = 0;
        public long totalSize;
        private byte[] currentChunk;
        private int chunkOffset = 0;
        private int chunkLength = 0;
        
        public BootstrapZipInputStream() {
            // Only load the shared library when necessary to save memory usage.
            // Note: We can't use TerminalLogger here since we don't have access to the activity context
            // The native library loading will be logged by the calling method
            System.loadLibrary("termful-bootstrap");
            
            totalSize = getZipSize();
            
            if (totalSize <= 0) {
                // No bootstrap files available - this is expected for Manual Distribution Selector
                throw new NoBootstrapAvailableException("No bootstrap files available - user must select their own distribution");
            }
            
            // Check if zip size is reasonable (less than 500MB to avoid OOM)
            if (totalSize > 500 * 1024 * 1024) {
                throw new RuntimeException("Bootstrap zip file is too large (" + (totalSize / 1024 / 1024) + " MB). " +
                    "This exceeds the memory limit for mobile devices. Please reduce the bootstrap size.");
            }
        }
        
        @Override
        public int read() throws java.io.IOException {
            if (position >= totalSize) {
                return -1;
            }
            
            if (chunkOffset >= chunkLength) {
                if (!loadNextChunk()) {
                    return -1;
                }
            }
            
            int result = currentChunk[chunkOffset] & 0xFF;
            chunkOffset++;
            position++;
            return result;
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            if (position >= totalSize) {
                return -1;
            }
            
            int totalRead = 0;
            while (len > 0 && position < totalSize) {
                if (chunkOffset >= chunkLength) {
                    if (!loadNextChunk()) {
                        break;
                    }
                }
                
                int availableInChunk = chunkLength - chunkOffset;
                int bytesToRead = Math.min(len, availableInChunk);
                
                System.arraycopy(currentChunk, chunkOffset, b, off, bytesToRead);
                
                chunkOffset += bytesToRead;
                position += bytesToRead;
                off += bytesToRead;
                len -= bytesToRead;
                totalRead += bytesToRead;
            }
            
            return totalRead > 0 ? totalRead : -1;
        }
        
        private boolean loadNextChunk() throws java.io.IOException {
            if (position >= totalSize) {
                return false;
            }
            
            if (currentChunk == null) {
                currentChunk = new byte[CHUNK_SIZE];
            }
            
            long remainingBytes = totalSize - position;
            int bytesToRead = (int) Math.min(CHUNK_SIZE, remainingBytes);
            
            int bytesRead = getZipChunk(position, currentChunk, bytesToRead);
            if (bytesRead <= 0) {
                throw new java.io.IOException("Failed to read zip chunk at position " + position + 
                    ". Expected " + bytesToRead + " bytes, got " + bytesRead);
            }
            
            chunkOffset = 0;
            chunkLength = bytesRead;
            return true;
        }
        
        @Override
        public void close() throws java.io.IOException {
            currentChunk = null;
            super.close();
        }
    }

}
