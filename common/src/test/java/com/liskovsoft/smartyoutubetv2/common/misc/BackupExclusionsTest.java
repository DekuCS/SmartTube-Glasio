package com.liskovsoft.smartyoutubetv2.common.misc;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackupExclusionsTest {
    @Test
    public void signInFilesAreExcluded() {
        assertTrue(BackupExclusions.isSignInFile(new File("/x/files/global_prefs/media_service_account_data")));
        assertTrue(BackupExclusions.isSignInFile(new File("/x/files/global_prefs/oauth2_account_data")));
        assertTrue(BackupExclusions.isSignInFile(new File("/x/shared_prefs/AuthTokenCache.xml")));
    }

    @Test
    public void otherFilesAreKept() {
        assertFalse(BackupExclusions.isSignInFile(new File("/x/files/global_prefs/media_service_data")));
        assertFalse(BackupExclusions.isSignInFile(new File("/x/files/other/media_service_account_data")));
        assertFalse(BackupExclusions.isSignInFile(new File("media_service_account_data")));
        assertFalse(BackupExclusions.isSignInFile(null));
    }

    // Regression: cancel() used to act only when frequency > 0, so choosing "None" (-1) left the job running.
    @Test
    public void autoBackupDisabledForNoneFrequency() {
        assertFalse(BackupExclusions.isAutoBackupEnabled(-1));
        assertFalse(BackupExclusions.isAutoBackupEnabled(0));
        assertTrue(BackupExclusions.isAutoBackupEnabled(1));
        assertTrue(BackupExclusions.isAutoBackupEnabled(30));
    }
}
