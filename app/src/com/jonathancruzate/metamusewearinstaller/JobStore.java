package com.jonathancruzate.metamusewearinstaller;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * JobStore — the watch-side record of the current install job (v1:
 * one job at a time; a new offer supersedes a finished one and is
 * refused while another job is mid-flight). Persisted in the app's
 * private preferences so the prompt, the status receiver, and the
 * idle UI all read the same truth, and a process death mid-install
 * still leaves an accurate last-stage record.
 */
public final class JobStore {
    private JobStore() {}

    private static final String PREFS = "wear_installer_jobs";
    private static final String K_ID = "job_id";
    private static final String K_LABEL = "label";
    private static final String K_SHA = "sha256";
    private static final String K_SIZE = "size";
    private static final String K_STAGE = "stage";
    private static final String K_DETAIL = "detail";
    private static final String K_AT = "updated_at";
    private static final String K_FILE_READY = "file_ready";

    public static final class Job {
        public final String id;
        public final String label;
        public final String sha256;
        public final long size;
        public final String stage;
        public final String detail;
        public final long updatedAt;
        public final boolean fileReady;

        Job(String id, String label, String sha256, long size, String stage,
            String detail, long updatedAt, boolean fileReady) {
            this.id = id;
            this.label = label;
            this.sha256 = sha256;
            this.size = size;
            this.stage = stage;
            this.detail = detail;
            this.updatedAt = updatedAt;
            this.fileReady = fileReady;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized Job current(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String id = p.getString(K_ID, "");
        if (id.isEmpty()) return null;
        return new Job(id, p.getString(K_LABEL, ""), p.getString(K_SHA, ""),
                p.getLong(K_SIZE, 0), p.getString(K_STAGE, ""),
                p.getString(K_DETAIL, ""), p.getLong(K_AT, 0),
                p.getBoolean(K_FILE_READY, false));
    }

    /** Record a fresh offer. Returns false when a different job is
     *  still mid-flight (not terminal) — the caller reports busy. */
    public static synchronized boolean begin(Context ctx, String id,
                                             String label, String sha256,
                                             long size) {
        Job cur = current(ctx);
        if (cur != null && !cur.id.equals(id)
                && !Protocol.isTerminal(cur.stage)) {
            return false;
        }
        prefs(ctx).edit()
                .putString(K_ID, id)
                .putString(K_LABEL, label)
                .putString(K_SHA, sha256)
                .putLong(K_SIZE, size)
                .putString(K_STAGE, "received")
                .putString(K_DETAIL, "")
                .putLong(K_AT, System.currentTimeMillis())
                .putBoolean(K_FILE_READY, false)
                .apply();
        return true;
    }

    public static synchronized void setStage(Context ctx, String id,
                                             String stage, String detail) {
        Job cur = current(ctx);
        if (cur == null || !cur.id.equals(id)) return;
        prefs(ctx).edit()
                .putString(K_STAGE, stage)
                .putString(K_DETAIL, detail == null ? "" : detail)
                .putLong(K_AT, System.currentTimeMillis())
                .apply();
    }

    public static synchronized void setFileReady(Context ctx, String id) {
        Job cur = current(ctx);
        if (cur == null || !cur.id.equals(id)) return;
        prefs(ctx).edit()
                .putBoolean(K_FILE_READY, true)
                .putLong(K_AT, System.currentTimeMillis())
                .apply();
    }

    /** Staging file for a job's payload (cache: the OS may reclaim it
     *  under pressure, which just fails the job closed). */
    public static File stagingFile(Context ctx, String jobId) {
        File dir = new File(ctx.getApplicationContext().getCacheDir(), "incoming");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, jobId + ".apk");
    }
}
