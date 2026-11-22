package com.jonathancruzate.metamusewearinstaller;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installer — hands a verified APK file to Android's PackageInstaller
 * through a session (the stream-in form of the PackageInstaller API:
 * no content URI or FileProvider is needed, because the session takes
 * the bytes directly). The app declares REQUEST_INSTALL_PACKAGES;
 * whether installs are actually permitted is the per-app "install
 * unknown apps" switch, checked here via canRequestPackageInstalls()
 * and granted once at bootstrap (see bootstrap/BOOTSTRAP.md).
 *
 * The terminal outcome arrives asynchronously in
 * InstallStatusReceiver under PackageInstaller.EXTRA_STATUS.
 */
public final class Installer {
    private Installer() {}

    public static final String ACTION_INSTALL_STATUS =
            "com.jonathancruzate.metamusewearinstaller.action.INSTALL_STATUS";

    /** Start the install session for a verified job file. */
    public static void install(Context ctx, JobStore.Job job) throws Exception {
        Context app = ctx.getApplicationContext();
        if (!app.getPackageManager().canRequestPackageInstalls()) {
            throw new IllegalStateException(
                    "install_permission_missing: allow installs from "
                            + "Muse Wear Installer (bootstrap step)");
        }
        File file = JobStore.stagingFile(app, job.id);
        if (!file.isFile() || file.length() == 0) {
            throw new IllegalStateException("payload_missing");
        }
        PackageInstaller pi = app.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setSize(file.length());
        int sessionId = pi.createSession(params);
        PackageInstaller.Session session = pi.openSession(sessionId);
        try {
            try (InputStream in = new FileInputStream(file);
                 OutputStream out = session.openWrite("payload.apk", 0,
                         file.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                session.fsync(out);
            }
            Intent intent = new Intent(app, InstallStatusReceiver.class);
            intent.setAction(ACTION_INSTALL_STATUS);
            intent.putExtra("job_id", job.id);
            PendingIntent pi2 = PendingIntent.getBroadcast(app, sessionId,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT
                            | PendingIntent.FLAG_MUTABLE);
            session.commit(pi2.getIntentSender());
        } finally {
            session.close();
        }
    }
}
