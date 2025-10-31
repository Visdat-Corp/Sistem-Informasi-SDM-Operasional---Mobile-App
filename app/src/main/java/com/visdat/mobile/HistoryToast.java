package com.visdat.mobile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class HistoryToast {
    public static void show(Context context, String message) {
        View layout = LayoutInflater.from(context).inflate(R.layout.toast_custom, null);
        TextView text = layout.findViewById(R.id.toastMessage);
        text.setText(message);

        Toast toast = new Toast(context.getApplicationContext());
        toast.setView(layout);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.show();


    }
}
