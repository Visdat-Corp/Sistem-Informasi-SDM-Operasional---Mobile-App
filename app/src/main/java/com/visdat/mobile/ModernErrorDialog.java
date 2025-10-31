package com.visdat.mobile;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Dialog custom modern untuk menampilkan pesan error absensi
 * dengan tampilan yang lebih user-friendly
 */
public class ModernErrorDialog {

    /**
     * Tampilkan dialog error modern untuk absensi gagal
     */
    public static void showAttendanceError(Context context, String jenisAbsensi, String errorMessage, Runnable onRetry) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        View view = LayoutInflater.from(context).inflate(R.layout.notification_attendance_failure, null);
        dialog.setContentView(view);

        // Make dialog background transparent untuk rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        // Setup views
        ImageView iconView = view.findViewById(R.id.notification_icon);
        TextView titleView = view.findViewById(R.id.notification_title);
        TextView jenisView = view.findViewById(R.id.notification_jenis);
        TextView errorView = view.findViewById(R.id.notification_error);
        Button retryButton = view.findViewById(R.id.btn_retry);
        Button closeButton = view.findViewById(R.id.btn_close);

        // Set data
        if (jenisView != null) {
            jenisView.setText(jenisAbsensi);
        }
        if (errorView != null) {
            errorView.setText(errorMessage);
        }

        // Setup button listeners
        if (retryButton != null) {
            retryButton.setOnClickListener(v -> {
                dialog.dismiss();
                if (onRetry != null) {
                    onRetry.run();
                }
            });
        }

        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dialog.dismiss());
        }

        // Animate icon
        if (iconView != null) {
            iconView.setScaleX(0f);
            iconView.setScaleY(0f);
            iconView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start();
        }

        dialog.show();
    }

    /**
     * Tampilkan dialog sukses modern
     */
    public static void showSuccess(Context context, String title, String message) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        View view = LayoutInflater.from(context).inflate(R.layout.notification_attendance_failure, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        // Setup views untuk sukses
        ImageView iconView = view.findViewById(R.id.notification_icon);
        TextView titleView = view.findViewById(R.id.notification_title);
        TextView jenisView = view.findViewById(R.id.notification_jenis);
        TextView errorView = view.findViewById(R.id.notification_error);
        Button retryButton = view.findViewById(R.id.btn_retry);
        Button closeButton = view.findViewById(R.id.btn_close);

        if (iconView != null) {
            iconView.setImageResource(android.R.drawable.checkbox_on_background);
            iconView.setColorFilter(Color.GREEN);
        }

        if (titleView != null) {
            titleView.setText("✅ " + title);
            titleView.setTextColor(Color.GREEN);
        }

        if (jenisView != null) {
            jenisView.setVisibility(View.GONE);
        }

        if (errorView != null) {
            errorView.setText(message);
            errorView.setTextColor(Color.BLACK);
        }

        if (retryButton != null) {
            retryButton.setVisibility(View.GONE);
        }

        if (closeButton != null) {
            closeButton.setText("OK");
            closeButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    /**
     * Tampilkan dialog warning
     */
    public static void showWarning(Context context, String title, String message, Runnable onAction) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        View view = LayoutInflater.from(context).inflate(R.layout.notification_attendance_failure, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        // Setup views untuk warning
        ImageView iconView = view.findViewById(R.id.notification_icon);
        TextView titleView = view.findViewById(R.id.notification_title);
        TextView jenisView = view.findViewById(R.id.notification_jenis);
        TextView errorView = view.findViewById(R.id.notification_error);
        Button retryButton = view.findViewById(R.id.btn_retry);
        Button closeButton = view.findViewById(R.id.btn_close);

        if (iconView != null) {
            iconView.setImageResource(android.R.drawable.ic_dialog_info);
            iconView.setColorFilter(Color.rgb(255, 165, 0)); // Orange
        }

        if (titleView != null) {
            titleView.setText("⚠️ " + title);
            titleView.setTextColor(Color.rgb(255, 165, 0));
        }

        if (jenisView != null) {
            jenisView.setVisibility(View.GONE);
        }

        if (errorView != null) {
            errorView.setText(message);
            errorView.setTextColor(Color.BLACK);
        }

        if (retryButton != null && onAction != null) {
            retryButton.setText("OK");
            retryButton.setOnClickListener(v -> {
                dialog.dismiss();
                onAction.run();
            });
        } else if (retryButton != null) {
            retryButton.setVisibility(View.GONE);
        }

        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
}
