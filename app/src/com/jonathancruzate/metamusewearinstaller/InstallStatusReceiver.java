package com.jonathancruzate.metamusewearinstaller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

import java.io.File;

/**
 * InstallStatusReceiver — the PackageInstaller session's verdict.
 * Maps the platform status onto the job's stage vocabulary and
 * reports it to the phone:
 *   STATUS_SUCCESS              -> installed (terminal)
 *   STATUS_PENDING_USER_ACTION  -> launch the system confirmation
 *                                  intent; the session reports again
 *                                  with the final verdict afterwards
 *   STATUS_FAILURE_*            -> failed (terminal), with the
 *                                  platform's status message
 */
public final class InstallStatusReceiver extends BroadcastReceiver {
    private static final String TAG = "MuseWearInstaller";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String jobId = intent.getStringExtra("job_id");
        if (jobId == null) jobId = "";
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(
                PackageInstaller.EXTRA_STATUS_MESSAGE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        ctx.startActivity(confirm);
                    } catch (Exception e) {
                        StatusSender.send(ctx, jobId, "failed",
                                "user_action_launch_failed: " + e.getMessage());
                    }
                } else {
                    StatusSender.send(ctx, jobId, "failed",
                            "user_action_intent_missing");
                }
                break;
            }
            case PackageInstaller.STATUS_SUCCESS: {
                StatusSender.send(ctx, jobId, "installed", null);
                File f = JobStore.stagingFile(ctx, jobId);
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                Prompt.raiseResultNotification(ctx, jobId, true,
                        "Installed");
                break;
            }
            default: {
                String detail = "status=" + status
                        + (message != null && !message.isEmpty()
                                ? " " + message : "");
                Log.w(TAG, "install failed: " + detail);
                StatusSender.send(ctx, jobId, "failed", detail);
                Prompt.raiseResultNotification(ctx, jobId, false,
                        "Install failed");
                break;
            }
        }
    }
}
