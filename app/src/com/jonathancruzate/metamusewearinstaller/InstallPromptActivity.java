package com.jonathancruzate.metamusewearinstaller;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * InstallPromptActivity — the one-tap consent screen: what is being
 * installed (Muse's label + the payload's short hash), Install, or
 * Dismiss. Both buttons route through PromptActionReceiver so the
 * notification actions and this screen share one code path.
 */
public final class InstallPromptActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        JobStore.Job job = JobStore.current(this);
        String jobId = getIntent() != null
                ? getIntent().getStringExtra("job_id") : null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(13);
        TextView detail = new TextView(this);
        detail.setTextColor(Color.LTGRAY);
        detail.setGravity(Gravity.CENTER);
        detail.setTextSize(11);

        if (job == null || jobId == null || !job.id.equals(jobId)
                || Protocol.isTerminal(job.stage)) {
            title.setText("No pending install");
            detail.setText(job != null
                    ? job.label + " — " + job.stage : "");
            root.addView(title);
            root.addView(detail);
            setContentView(root);
            return;
        }

        title.setText("Install " + job.label + "?");
        detail.setText("Watch face from Muse\nsha256 "
                + Protocol.shortSha(job.sha256) + "…  •  "
                + Math.max(1, job.size / 1024) + " KB");
        root.addView(title);
        root.addView(detail);

        Button install = new Button(this);
        install.setText("Install");
        install.setOnClickListener(v -> {
            dispatch(PromptActionReceiver.ACTION_INSTALL, job.id);
            finish();
        });
        Button dismiss = new Button(this);
        dismiss.setText("Dismiss");
        dismiss.setOnClickListener(v -> {
            dispatch(PromptActionReceiver.ACTION_DISMISS, job.id);
            finish();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        root.addView(install, lp);
        root.addView(dismiss, lp);
        setContentView(root);
    }

    private void dispatch(String action, String jobId) {
        Intent i = new Intent(this, PromptActionReceiver.class);
        i.setAction(action);
        i.putExtra("job_id", jobId);
        sendBroadcast(i);
    }
}
