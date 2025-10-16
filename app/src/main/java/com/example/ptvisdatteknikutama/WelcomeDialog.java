package com.example.ptvisdatteknikutama;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

public class WelcomeDialog {
    private static final String PREFS_NAME = "welcome_prefs";
    private static final String KEY_DONT_SHOW_AGAIN = "dont_show_again";

    // Updated show method to accept idKaryawan and namaKaryawan
    public static void show(Context context, String idKaryawan, String namaKaryawan) {
        // Check if user chose not to show again
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean dontShowAgain = prefs.getBoolean(KEY_DONT_SHOW_AGAIN, false);

        if (dontShowAgain) {
            return; // Don't show the dialog
        }

        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.activity_welcome_dialog);
        dialog.setCancelable(true);

        // Find TextViews in the dialog layout
        TextView tvNip = dialog.findViewById(R.id.tv_nip); // tv_nip untuk id_karyawan
        TextView tvName = dialog.findViewById(R.id.tv_name); // tv_name untuk nama_karyawan

        // Set the text for idKaryawan and namaKaryawan
        if (tvNip != null && idKaryawan != null) {
            tvNip.setText(idKaryawan);
        }
        if (tvName != null && namaKaryawan != null) {
            tvName.setText(namaKaryawan);
        }

        CheckBox dontShowCheckbox = dialog.findViewById(R.id.checkbox_dont_show_again);
        Button dismissBtn = dialog.findViewById(R.id.btn_dismiss);

        dismissBtn.setOnClickListener(x -> {
            // Save preference if checkbox is checked
            if (dontShowCheckbox.isChecked()) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(KEY_DONT_SHOW_AGAIN, true);
                editor.apply();
            }
            dialog.dismiss();
        });

        dialog.show();
    }
}
