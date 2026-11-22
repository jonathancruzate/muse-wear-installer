package com.jonathancruzate.metamusewearinstaller;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.ChannelClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WearInstallListenerService — the Data Layer doorbell.
 *
 *   onMessageReceived (PATH_OFFER): a new job's metadata. Accepted
 *   into JobStore (one job at a time); stage "received" goes back to
 *   the phone at once.
 *   onChannelOpened (CHANNEL_PREFIX + job_id): the APK bytes. The
 *   channel is drained to the job's staging file on a worker thread,
 *   then the payload is hash-verified against the offer. Only a
 *   verified payload raises the install prompt — an unverified or
 *   partial transfer fails closed (verify_failed / failed) and is
 *   deleted.
 *
 * Offer and channel may arrive in either order; both halves land in
 * JobStore/the staging file and tryProceed() joins them.
 */
public final class WearInstallListenerService extends WearableListenerService {
    private static final String TAG = "MuseWearInstaller";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();

    @Override
    public void onMessageReceived(MessageEvent event) {
        if (!Protocol.PATH_OFFER.equals(event.getPath())) return;
        String json = new String(event.getData(), StandardCharsets.UTF_8);
        Map<String, String> offer = Protocol.parseFlat(json);
        String jobId = offer.get("job_id");
        String label = offer.get("label");
        String sha = offer.get("sha256");
        long size = 0;
        try { size = Long.parseLong(offer.getOrDefault("size", "0")); }
        catch (NumberFormatException ignored) {}
        if (jobId == null || jobId.isEmpty() || sha == null
                || !sha.matches("[0-9a-f]{64}")) {
            Log.w(TAG, "malformed offer refused");
            return;
        }
        if (size <= 0 || size > Protocol.MAX_APK_BYTES) {
            StatusSender.send(this,
                    jobId, "failed", "size_out_of_bounds: " + size);
            return;
        }
        if (!JobStore.begin(this, jobId, label == null ? "" : label, sha, size)) {
            StatusSender.send(this, jobId, "failed",
                    "busy: another job is mid-flight");
            return;
        }
        StatusSender.send(this, jobId, "received", null);
        WORK.execute(() -> tryProceed(jobId));
    }

    @Override
    public void onChannelOpened(ChannelClient.Channel channel) {
        String path = channel.getPath();
        if (path == null || !path.startsWith(Protocol.CHANNEL_PREFIX)) return;
        final String jobId = path.substring(Protocol.CHANNEL_PREFIX.length());
        WORK.execute(() -> {
            File staging = JobStore.stagingFile(this, jobId);
            try {
                Tasks.await(Wearable.getChannelClient(this)
                        .receiveFile(channel, Uri.fromFile(staging), false));
                JobStore.setFileReady(this, jobId);
                StatusSender.send(this, jobId, "transferred",
                        "bytes=" + staging.length());
            } catch (Exception e) {
                Log.w(TAG, "channel receive failed: " + e.getMessage());
                StatusSender.send(this, jobId, "failed",
                        "transfer: " + e.getClass().getSimpleName() + " "
                                + e.getMessage());
                //noinspection ResultOfMethodCallIgnored
                staging.delete();
            } finally {
                try { Tasks.await(Wearable.getChannelClient(this)
                        .close(channel)); } catch (Exception ignored) {}
            }
            tryProceed(jobId);
        });
    }

    /** Join offer + file: verify the hash, then prompt. */
    private void tryProceed(String jobId) {
        JobStore.Job job = JobStore.current(this);
        if (job == null || !job.id.equals(jobId) || !job.fileReady) return;
        if (!"received".equals(job.stage) && !"transferred".equals(job.stage)) {
            return; // already verified/prompted, or terminal
        }
        File staging = JobStore.stagingFile(this, jobId);
        try {
            if (!staging.isFile() || staging.length() != job.size) {
                StatusSender.send(this, jobId, "verify_failed",
                        "size_mismatch: expected " + job.size + " got "
                                + (staging.isFile() ? staging.length() : -1));
                //noinspection ResultOfMethodCallIgnored
                staging.delete();
                return;
            }
            String actual = Protocol.sha256Hex(staging);
            if (!actual.equals(job.sha256)) {
                StatusSender.send(this, jobId, "verify_failed",
                        "sha256_mismatch: got " + Protocol.shortSha(actual));
                //noinspection ResultOfMethodCallIgnored
                staging.delete();
                return;
            }
        } catch (Exception e) {
            StatusSender.send(this, jobId, "failed",
                    "verify: " + e.getClass().getSimpleName() + " "
                            + e.getMessage());
            return;
        }
        JobStore.Job fresh = JobStore.current(this);
        StatusSender.send(this, jobId, "verified", null);
        Prompt.raise(this, fresh != null ? fresh : job);
    }
}
