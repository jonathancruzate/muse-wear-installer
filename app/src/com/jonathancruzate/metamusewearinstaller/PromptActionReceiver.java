package com.jonathancruzate.metamusewearinstaller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;

/**
 * PromptActionReceiver — the Install / Dismiss actions on the job
 * prompt notification (and the prompt activity's fallback path).
 * Install re-checks the job is still staged and verified before
 * touching PackageInstaller; Dismiss is a terminal user verdict.
 */
public final class PromptActionReceiver extends BroadcastReceiver {
    private static final String TAG = "MuseWearInstaller";

    public static final String ACTION_INSTALL =
            "com.jonathancruzate.metamusewearinstaller.action.PROMPT_INSTALL";
    public static final String ACTION_DISMISS =
            "com.jonathancruzate.metamusewearinstaller.action.PROMPT_DISMISS";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String jobId = intent.getStringExtra("job_id");
        JobStore.Job job = JobStore.current(ctx);
        if (job == null || jobId == null || !job.id.equals(jobId)) return;
        Prompt.cancel(ctx, jobId);
        if (ACTION_DISMISS.equals(intent.getAction())) {
            StatusSender.send(ctx, jobId, "dismissed", null);
            File f = JobStore.stagingFile(ctx, jobId);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return;
        }
        if (ACTION_INSTALL.equals(intent.getAction())) {
            if (!job.fileReady || !"verified".equals(job.stage)
                    && !"prompted".equals(job.stage)) {
                StatusSender.send(ctx, jobId, "failed",
                        "install_requested_before_verified");
                return;
            }
            StatusSender.send(ctx, jobId, "installing", null);
            new Thread(() -> {
                try {
                    Installer.install(ctx, job);
                } catch (Exception e) {
                    Log.w(TAG, "install start failed: " + e.getMessage());
                    StatusSender.send(ctx, jobId, "failed", "install_start: "
                            + e.getClass().getSimpleName() + " "
                            + e.getMessage());
                }
            }, "wear-install-start").start();
        }
    }
}
