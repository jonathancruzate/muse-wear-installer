package com.jonathancruzate.metamusewearinstaller;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

/**
 * MainActivity — the idle status screen: the app is alive, and the
 * last job's label / stage / time if there has been one. No settings
 * surface: the only on-device choice this app offers is the install
 * prompt itself.
 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Muse Wear Installer");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView body = new TextView(this);
        body.setTextColor(Color.LTGRAY);
        body.setTextSize(12);
        body.setGravity(Gravity.CENTER);
        JobStore.Job job = JobStore.current(this);
        if (job == null) {
            body.setText("\nReady.\nWatch faces Muse sends will ask here.");
        } else {
            String when = job.updatedAt > 0
                    ? DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(new Date(job.updatedAt))
                    : "";
            body.setText("\nLast job:\n" + job.label + "\n" + job.stage
                    + (when.isEmpty() ? "" : "\n" + when));
        }
        root.addView(body);
        setContentView(root);
    }
}
