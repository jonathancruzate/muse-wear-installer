package com.jonathancruzate.metamusewearinstaller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Prompt — how a verified job asks Jonathan for its one tap.
 *
 * A high-importance notification is the reliable surface on Wear OS
 * (background activity launches are restricted): it carries a
 * full-screen intent to InstallPromptActivity plus direct Install /
 * Dismiss actions. A direct activity start is ALSO attempted — when
 * the watch is awake and interactive it lands immediately; when the
 * platform blocks it, the notification remains and nothing is lost.
 */
public final class Prompt {
    private Prompt() {}

    private static final String CHANNEL_ID = "install_jobs";
    private static final int NOTIF_BASE = 4711;

    private static void ensureChannel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "Install jobs", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Watch-face install prompts from Muse");
            nm.createNotificationChannel(ch);
        }
    }

    private static int notifId(String jobId) {
        return NOTIF_BASE + Math.abs(jobId.hashCode() % 1000);
    }

    public static void raise(Context ctx, JobStore.Job job) {
        Context app = ctx.getApplicationContext();
        ensureChannel(app);
        StatusSender.send(app, job.id, "prompted", null);

        Intent promptIntent = new Intent(app, InstallPromptActivity.class);
        promptIntent.putExtra("job_id", job.id);
        promptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(app,
                job.id.hashCode(), promptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent installIntent = new Intent(app, PromptActionReceiver.class);
        installIntent.setAction(PromptActionReceiver.ACTION_INSTALL);
        installIntent.putExtra("job_id", job.id);
        PendingIntent installPi = PendingIntent.getBroadcast(app,
                job.id.hashCode() + 1, installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(app, PromptActionReceiver.class);
        dismissIntent.setAction(PromptActionReceiver.ACTION_DISMISS);
        dismissIntent.putExtra("job_id", job.id);
        PendingIntent dismissPi = PendingIntent.getBroadcast(app,
                job.id.hashCode() + 2, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Install " + job.label + "?")
                .setContentText("Muse sent a watch face. Tap Install to apply.")
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(
                                app, R.mipmap.ic_launcher),
                        "Install", installPi).build())
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(
                                app, R.mipmap.ic_launcher),
                        "Dismiss", dismissPi).build())
                .setAutoCancel(false)
                .setOngoing(true)
                .build();
        app.getSystemService(NotificationManager.class)
                .notify(notifId(job.id), n);

        try {
            Vibrator v = app.getSystemService(Vibrator.class);
            if (v != null) {
                v.vibrate(VibrationEffect.createOneShot(200,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {}

        // Best-effort immediate surface; BAL may refuse when the
        // watch is asleep — the notification above is the guarantee.
        try {
            app.startActivity(promptIntent);
        } catch (Exception ignored) {}
    }

    public static void cancel(Context ctx, String jobId) {
        ctx.getApplicationContext()
                .getSystemService(NotificationManager.class)
                .cancel(notifId(jobId));
    }

    public static void raiseResultNotification(Context ctx, String jobId,
                                               boolean ok, String text) {
        Context app = ctx.getApplicationContext();
        ensureChannel(app);
        cancel(app, jobId);
        JobStore.Job job = JobStore.current(app);
        String label = job != null && job.id.equals(jobId) ? job.label : "";
        Notification n = new Notification.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(text)
                .setContentText(label)
                .setAutoCancel(true)
                .build();
        app.getSystemService(NotificationManager.class)
                .notify(notifId(jobId) + 5000, n);
    }

}
