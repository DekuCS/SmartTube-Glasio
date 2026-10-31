package com.liskovsoft.smartyoutubetv2.common.misc;

import java.io.File;

/**
 * MATERIAL: adapted from NewTube (MIT), BackupExclusions. Files the local backup never copies to shared storage
 * (Android/media, Documents/SmartTubeBackup), so the YouTube and Google Drive sign-in tokens stay out of it.
 */
public final class BackupExclusions {
    /** {parent dir name, file name} */
    private static final String[][] SIGN_IN_FILES = {
            {"global_prefs", "media_service_account_data"}, // YouTube accounts with refresh tokens
            {"global_prefs", "oauth2_account_data"},        // Google Drive backup sign-in
            {"shared_prefs", "AuthTokenCache.xml"}          // cached access header + refresh token
    };

    private BackupExclusions() {
    }

    public static boolean isSignInFile(File file) {
        if (file == null) {
            return false;
        }

        File parent = file.getParentFile();
        String parentName = parent != null ? parent.getName() : null;

        for (String[] entry : SIGN_IN_FILES) {
            if (entry[0].equals(parentName) && entry[1].equals(file.getName())) {
                return true;
            }
        }

        return false;
    }

    /** MATERIAL: frequency in days; -1 (None) and 0 mean the periodic local backup must not run. */
    public static boolean isAutoBackupEnabled(int freqDays) {
        return freqDays > 0;
    }
}
