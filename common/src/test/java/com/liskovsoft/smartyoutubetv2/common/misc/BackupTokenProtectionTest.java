package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;

import androidx.work.Configuration;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkInfo.State;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// MATERIAL: covers the token filters in backupToDir and the "None" frequency handling of the local backup job
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BackupTokenProtectionTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext, new Configuration.Builder().build());
    }

    private static void write(File file) throws Exception {
        file.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(file)) {
            w.write("data");
        }
    }

    @Test
    public void backupToDirLeavesSignInFilesOut() throws Exception {
        File filesDir = mContext.getFilesDir();
        write(new File(filesDir, "global_prefs/media_service_account_data"));
        write(new File(filesDir, "global_prefs/oauth2_account_data"));
        write(new File(filesDir, "global_prefs/media_service_data"));

        File sharedPrefs = new File(mContext.getApplicationInfo().dataDir, "shared_prefs");
        write(new File(sharedPrefs, "AuthTokenCache.xml"));
        // Same suffix the backup accepts, to prove the exclusion (not the name pattern) drops the token cache
        write(new File(sharedPrefs, "com.liskovsoft.sharedutils.prefs.GlobalPreferences.xml"));

        File target = Files.createTempDirectory("backup").toFile();
        new BackupAndRestoreManager(mContext).backupToDir(target);

        assertTrue(new File(target, "files/global_prefs/media_service_data").exists());
        assertFalse(new File(target, "files/global_prefs/media_service_account_data").exists());
        assertFalse(new File(target, "files/global_prefs/oauth2_account_data").exists());
        assertFalse(new File(target, "shared_prefs/AuthTokenCache.xml").exists());
    }

    @Test
    public void cancelRemovesJobWhenFrequencyIsNone() throws Exception {
        GeneralData.instance(mContext).setLocalDriveBackupFreqDays(1);
        LocalDriveBackupWorker.schedule(mContext);
        assertEquals(State.ENQUEUED, uniqueWorkState());

        GeneralData.instance(mContext).setLocalDriveBackupFreqDays(-1);
        LocalDriveBackupWorker.cancel(mContext);

        assertEquals(State.CANCELLED, uniqueWorkState());
    }

    @Test
    public void doWorkSkipsBackupWhenFrequencyIsNone() throws Exception {
        GeneralData.instance(mContext).setLocalDriveBackupFreqDays(-1);
        File marker = new File(mContext.getFilesDir(), "global_prefs/media_service_data");
        write(marker);

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(LocalDriveBackupWorker.class).build();
        WorkManager.getInstance(mContext).enqueue(request).getResult().get();

        WorkInfo info = WorkManager.getInstance(mContext).getWorkInfoById(request.getId()).get();
        assertEquals(State.SUCCEEDED, info.getState());
        assertFalse(new File(new BackupAndRestoreManager(mContext).getRestoreRootPath(),
                mContext.getPackageName() + "/Backup").exists());
    }

    private State uniqueWorkState() throws Exception {
        List<WorkInfo> infos = WorkManager.getInstance(mContext).getWorkInfosForUniqueWork("LocalDriveBackupWorker").get();
        return infos.get(0).getState();
    }
}
