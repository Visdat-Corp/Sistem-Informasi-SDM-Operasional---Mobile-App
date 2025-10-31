package com.visdat.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Helper class untuk menampilkan notifikasi modern di aplikasi mobile
 * ketika absensi gagal dikirim ke server
 */
public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "attendance_channel";
    private static final String CHANNEL_NAME = "Absensi";
    private static final String CHANNEL_DESCRIPTION = "Notifikasi status absensi";
    private static final int NOTIFICATION_ID_FAILURE = 1001;
    private static final int NOTIFICATION_ID_SUCCESS = 1002;
    private static final int NOTIFICATION_ID_WARNING = 1003;

    /**
     * Inisialisasi notification channel (diperlukan untuk Android O ke atas)
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(CHANNEL_DESCRIPTION);
                channel.enableLights(true);
                channel.setLightColor(Color.RED);
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 500, 200, 500});
                channel.setShowBadge(true);

                NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                    Log.d(TAG, "Notification channel created successfully");
                } else {
                    Log.e(TAG, "NotificationManager is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating notification channel: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Cek apakah aplikasi memiliki permission untuk menampilkan notifikasi
     */
    private static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hasPermission = ActivityCompat.checkSelfPermission(
                    context, 
                    android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
            
            Log.d(TAG, "Notification permission check (Android 13+): " + hasPermission);
            return hasPermission;
        }
        // Untuk Android < 13, notifikasi selalu diizinkan
        return true;
    }

    /**
     * Tampilkan notifikasi ketika absensi GAGAL dikirim ke server
     */
    public static void showAttendanceFailureNotification(Context context, String errorMessage, String jenisAbsensi) {
        Log.d(TAG, "showAttendanceFailureNotification called - Jenis: " + jenisAbsensi + ", Error: " + errorMessage);
        
        createNotificationChannel(context);

        // Cek permission terlebih dahulu
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "No notification permission, using Toast fallback");
            CustomToast.showToast(context, "❌ Absensi gagal: " + errorMessage, android.widget.Toast.LENGTH_LONG);
            return;
        }

        try {
            // Intent untuk membuka aplikasi saat notifikasi diklik
            Intent intent = new Intent(context, NavigationActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("open_history", true);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Intent untuk retry
            Intent retryIntent = new Intent(context, SummaryPage.class);
            retryIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent retryPendingIntent = PendingIntent.getActivity(
                    context,
                    1,
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Suara notifikasi
            Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            // Build notifikasi
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("❌ Absensi Gagal Dikirim")
                    .setContentText(jenisAbsensi + " gagal terkirim ke server")
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("Jenis: " + jenisAbsensi + "\n\n"
                                    + "Error: " + errorMessage + "\n\n"
                                    + "Silakan cek koneksi internet Anda dan coba lagi, atau hubungi Manajer SDM untuk request override."))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ERROR)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setSound(defaultSoundUri)
                    .setVibrate(new long[]{0, 500, 200, 500})
                    .setLights(Color.RED, 3000, 3000)
                    .setColor(Color.RED)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .addAction(
                            android.R.drawable.ic_menu_revert,
                            "Coba Lagi",
                            retryPendingIntent
                    );

            // Tampilkan notifikasi
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(NOTIFICATION_ID_FAILURE, builder.build());
            
            Log.d(TAG, "Notification displayed successfully");
            
            // Tampilkan juga Toast sebagai feedback tambahan
            CustomToast.showToast(context, "❌ Absensi gagal: " + errorMessage, android.widget.Toast.LENGTH_LONG);
            
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when showing notification: " + e.getMessage(), e);
            // Jika permission ditolak, gunakan toast sebagai fallback
            CustomToast.showToast(context, "❌ Absensi gagal: " + errorMessage, android.widget.Toast.LENGTH_LONG);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error showing notification: " + e.getMessage(), e);
            CustomToast.showToast(context, "❌ Absensi gagal: " + errorMessage, android.widget.Toast.LENGTH_LONG);
        }
    }

    /**
     * Tampilkan notifikasi ketika absensi BERHASIL dikirim
     */
    public static void showAttendanceSuccessNotification(Context context, String jenisAbsensi) {
        Log.d(TAG, "showAttendanceSuccessNotification called - Jenis: " + jenisAbsensi);
        
        createNotificationChannel(context);

        // Cek permission terlebih dahulu
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "No notification permission, using Toast fallback");
            CustomToast.showToast(context, "✅ " + jenisAbsensi + " berhasil!", android.widget.Toast.LENGTH_SHORT);
            return;
        }

        try {
            Intent intent = new Intent(context, NavigationActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("open_history", true);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.checkbox_on_background)
                    .setContentTitle("✅ Absensi Berhasil")
                    .setContentText(jenisAbsensi + " berhasil terkirim ke server")
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(jenisAbsensi + " Anda telah berhasil tercatat di sistem.\n\n"
                                    + "Klik untuk melihat riwayat absensi."))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setSound(defaultSoundUri)
                    .setColor(Color.GREEN)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(NOTIFICATION_ID_SUCCESS, builder.build());
            
            Log.d(TAG, "Success notification displayed");
            
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when showing success notification: " + e.getMessage(), e);
            CustomToast.showToast(context, "✅ " + jenisAbsensi + " berhasil!", android.widget.Toast.LENGTH_SHORT);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error showing success notification: " + e.getMessage(), e);
            CustomToast.showToast(context, "✅ " + jenisAbsensi + " berhasil!", android.widget.Toast.LENGTH_SHORT);
        }
    }

    /**
     * Tampilkan notifikasi WARNING untuk kondisi tertentu
     */
    public static void showAttendanceWarningNotification(Context context, String title, String message) {
        Log.d(TAG, "showAttendanceWarningNotification called - Title: " + title);
        
        createNotificationChannel(context);

        // Cek permission terlebih dahulu
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "No notification permission, using Toast fallback");
            CustomToast.showToast(context, "⚠️ " + title + ": " + message, android.widget.Toast.LENGTH_LONG);
            return;
        }

        try {
            Intent intent = new Intent(context, NavigationActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Intent untuk tombol "Coba Lagi" - kembali ke NavigationActivity untuk foto ulang
            Intent retryIntent = new Intent(context, NavigationActivity.class);
            retryIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            retryIntent.putExtra("relaunch_camera", true);
            retryIntent.putExtra("from_retry_notification", true);
            PendingIntent retryPendingIntent = PendingIntent.getActivity(
                    context,
                    2,
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("⚠️ " + title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setSound(defaultSoundUri)
                    .setColor(Color.YELLOW)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .addAction(
                            android.R.drawable.ic_menu_revert,
                            "Coba Lagi",
                            retryPendingIntent
                    );

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(NOTIFICATION_ID_WARNING, builder.build());
            
            Log.d(TAG, "Warning notification with retry button displayed");
            
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when showing warning notification: " + e.getMessage(), e);
            CustomToast.showToast(context, "⚠️ " + title + ": " + message, android.widget.Toast.LENGTH_LONG);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error showing warning notification: " + e.getMessage(), e);
            CustomToast.showToast(context, "⚠️ " + title + ": " + message, android.widget.Toast.LENGTH_LONG);
        }
    }

    /**
     * Batalkan semua notifikasi yang sedang ditampilkan
     */
    public static void cancelAllNotifications(Context context) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancelAll();
    }

    /**
     * Batalkan notifikasi spesifik berdasarkan ID
     */
    public static void cancelNotification(Context context, int notificationId) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(notificationId);
    }
}
