package com.invapp.shared.termux.shell.command.environment;

import android.content.Context;

import androidx.annotation.NonNull;

import com.invapp.shared.errors.Error;
import com.invapp.shared.file.FileUtils;
import com.invapp.shared.logger.Logger;
import com.invapp.shared.shell.command.ExecutionCommand;
import com.invapp.shared.shell.command.environment.AndroidShellEnvironment;
import com.invapp.shared.shell.command.environment.ShellEnvironmentUtils;
import com.invapp.shared.shell.command.environment.ShellCommandShellEnvironment;
import com.invapp.shared.termux.TermuxBootstrap;
import com.invapp.shared.termux.TermuxConstants;
import com.invapp.shared.termux.shell.TermuxShellUtils;

import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * Environment for Termux.
 */
public class TermuxShellEnvironment extends AndroidShellEnvironment {

    private static final String LOG_TAG = "TermuxShellEnvironment";

    /** Environment variable for the termux {@link TermuxConstants#TERMUX_PREFIX_DIR_PATH}. */
    public static final String ENV_PREFIX = "PREFIX";

    public TermuxShellEnvironment() {
        super();
        shellCommandShellEnvironment = new TermuxShellCommandShellEnvironment();
    }


    /** Init {@link TermuxShellEnvironment} constants and caches. */
    public synchronized static void init(@NonNull Context currentPackageContext) {
        TermuxAppShellEnvironment.setTermuxAppEnvironment(currentPackageContext);
    }

    /** Init {@link TermuxShellEnvironment} constants and caches. */
    public synchronized static void writeEnvironmentToFile(@NonNull Context currentPackageContext) {
        HashMap<String, String> environmentMap = new TermuxShellEnvironment().getEnvironment(currentPackageContext, false);
        String environmentString = ShellEnvironmentUtils.convertEnvironmentToDotEnvFile(environmentMap);

        // Write environment string to temp file and then move to final location since otherwise
        // writing may happen while file is being sourced/read
        Error error = FileUtils.writeTextToFile("termux.env.tmp", TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH,
            Charset.defaultCharset(), environmentString, false);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
            return;
        }

        error = FileUtils.moveRegularFile("termux.env.tmp", TermuxConstants.TERMUX_ENV_TEMP_FILE_PATH, TermuxConstants.TERMUX_ENV_FILE_PATH, true);
        if (error != null) {
            Logger.logErrorExtended(LOG_TAG, error.toString());
        }
    }

    /** Get shell environment for Termux. */
    @NonNull
    @Override
    public HashMap<String, String> getEnvironment(@NonNull Context currentPackageContext, boolean isFailSafe) {

        // Termux environment builds upon the Android environment
        HashMap<String, String> environment = super.getEnvironment(currentPackageContext, isFailSafe);

        HashMap<String, String> termuxAppEnvironment = TermuxAppShellEnvironment.getEnvironment(currentPackageContext);
        if (termuxAppEnvironment != null)
            environment.putAll(termuxAppEnvironment);

        HashMap<String, String> termuxApiAppEnvironment = TermuxAPIShellEnvironment.getEnvironment(currentPackageContext);
        if (termuxApiAppEnvironment != null)
            environment.putAll(termuxApiAppEnvironment);

        environment.put(ENV_HOME, TermuxConstants.TERMUX_HOME_DIR_PATH);
        environment.put(ENV_PREFIX, TermuxConstants.TERMUX_PREFIX_DIR_PATH);

        // If failsafe is not enabled, then we keep default PATH and TMPDIR so that system binaries can be used
        if (!isFailSafe) {
            environment.put(ENV_TMPDIR, TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH);

            // Enable our custom path redirector to fix "Permission denied" during pkg update/install.
            // This library hooks stat/open/execve and redirects /data/data/com.termux to /data/data/com.invapp.
            String nativeLibDir = currentPackageContext.getApplicationInfo().nativeLibraryDir;
            String redirectorLib = nativeLibDir + "/libinvapp-redirector.so";
            String existingPreload = environment.get("LD_PRELOAD");
            if (existingPreload != null && !existingPreload.isEmpty()) {
                environment.put("LD_PRELOAD", redirectorLib + ":" + existingPreload);
            } else {
                environment.put("LD_PRELOAD", redirectorLib);
            }

            if (TermuxBootstrap.isAppPackageVariantAPTAndroid5()) {
                // Termux in android 5/6 era shipped busybox binaries in applets directory
                environment.put(ENV_PATH, TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + ":" + TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/applets");
                environment.put(ENV_LD_LIBRARY_PATH, TermuxConstants.TERMUX_LIB_PREFIX_DIR_PATH);
            } else {
                // Termux binaries on Android 7+ rely on DT_RUNPATH, so LD_LIBRARY_PATH should be unset by default
                environment.put(ENV_PATH, TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH);
                environment.remove(ENV_LD_LIBRARY_PATH);
            }
        }

        return environment;
    }


    @NonNull
    @Override
    public String getDefaultWorkingDirectoryPath() {
        return TermuxConstants.TERMUX_HOME_DIR_PATH;
    }

    @NonNull
    @Override
    public String getDefaultBinPath() {
        return TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
    }

    @NonNull
    @Override
    public String[] setupShellCommandArguments(@NonNull String executable, String[] arguments) {
        return TermuxShellUtils.setupShellCommandArguments(executable, arguments);
    }

}
