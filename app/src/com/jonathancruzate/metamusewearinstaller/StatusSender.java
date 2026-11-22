package com.jonathancruzate.metamusewearinstaller;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * StatusSender — reports one job stage to the phone over the Data
 * Layer (MessageClient, PATH_STATUS) and mirrors it into JobStore
 * first, so the on-watch record is right even when no phone node is
 * currently connected (the phone picks the state up from the next
 * successful send; v1 keeps no outbox beyond the JobStore record).
 */
public final class StatusSender {
    private StatusSender() {}

    private static final String TAG = "MuseWearInstaller";

    public static void send(Context ctx, String jobId, String stage,
                            String detail) {
        final Context app = ctx.getApplicationContext();
        JobStore.setStage(app, jobId, stage, detail);
        JobStore.Job job = JobStore.current(app);
        final String label = job != null ? job.label : "";
        final byte[] payload = Protocol.statusJson(jobId, label, stage,
                detail, System.currentTimeMillis())
                .getBytes(StandardCharsets.UTF_8);
        new Thread(() -> {
            try {
                List<Node> nodes = Tasks.await(
                        Wearable.getNodeClient(app).getConnectedNodes());
                for (Node n : nodes) {
                    try {
                        Tasks.await(Wearable.getMessageClient(app)
                                .sendMessage(n.getId(), Protocol.PATH_STATUS,
                                        payload));
                    } catch (Exception e) {
                        Log.w(TAG, "status send to " + n.getId()
                                + " failed: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "status send failed: " + e.getMessage());
            }
        }, "wear-status-send").start();
    }
}
