package com.example.ptvisdatteknikutama;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class HistoryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout listContainer = view.findViewById(R.id.listContainer);
        FloatingActionButton fabRefresh = view.findViewById(R.id.fab_refresh);

        fabRefresh.setOnClickListener(v -> {
            listContainer.removeAllViews();
            fetchAttendanceHistory(listContainer);
            CustomToast.showToast(requireContext(), "Memperbarui riwayat...", Toast.LENGTH_SHORT);
        });

        // Fetch history from API
        fetchAttendanceHistory(listContainer);
    }

    private View createItemView(HistoryStore.HistoryItem item) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundColor(0xFFFFFFFF);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));

        View accent = new View(requireContext());
        LinearLayout.LayoutParams accentLp = new LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT);
        accent.setLayoutParams(accentLp);
        accent.setBackgroundColor(getStatusColor(item.statusText));


        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        contentLp.setMargins(dp(12), 0, 0, 0);
        content.setLayoutParams(contentLp);

        SimpleDateFormat dayFmt = new SimpleDateFormat("dd MMMM yyyy", new Locale("id"));
        TextView tanggal = new TextView(requireContext());
        tanggal.setText(dayFmt.format(new java.util.Date(item.timestamp)));
        tanggal.setTextColor(0xFF999999);
        tanggal.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView status = new TextView(requireContext());
        status.setText(item.statusText);
        status.setTextColor(0xFF1EAD4E); // Warna hijau untuk status berhasil
        status.setTextSize(16);
        status.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout rows = new LinearLayout(requireContext());
        rows.setOrientation(LinearLayout.HORIZONTAL);
        rows.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        rows.setPadding(0, dp(8), 0, 0);

        LinearLayout left = new LinearLayout(requireContext());
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView lblHadir = new TextView(requireContext()); lblHadir.setText("Waktu Hadir :"); lblHadir.setTextColor(0xFF666666);
        TextView jamHadir = new TextView(requireContext()); jamHadir.setText(item.jamHadir); jamHadir.setTextColor(0xFF333333); jamHadir.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView lblTelat = new TextView(requireContext()); lblTelat.setText("Terlambat :"); lblTelat.setTextColor(0xFF666666);
        TextView telat = new TextView(requireContext()); telat.setText(item.terlambat); telat.setTextColor(0xFFD32F2F); telat.setTypeface(null, android.graphics.Typeface.BOLD);

        left.addView(lblHadir); left.addView(jamHadir); left.addView(lblTelat); left.addView(telat);

        LinearLayout right = new LinearLayout(requireContext());
        right.setOrientation(LinearLayout.VERTICAL);
        right.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView lblPulang = new TextView(requireContext()); lblPulang.setText("Waktu Pulang :"); lblPulang.setTextColor(0xFF666666);
        TextView jamPulang = new TextView(requireContext()); jamPulang.setText(item.jamPulang); jamPulang.setTextColor(0xFF333333); jamPulang.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView lblCepat = new TextView(requireContext()); lblCepat.setText("Cepat Pulang :"); lblCepat.setTextColor(0xFF666666);
        TextView cepat = new TextView(requireContext()); cepat.setText(item.cepatPulang); cepat.setTextColor(0xFFD32F2F); cepat.setTypeface(null, android.graphics.Typeface.BOLD);

        right.addView(lblPulang); right.addView(jamPulang); right.addView(lblCepat); right.addView(cepat);

        content.addView(tanggal);
        content.addView(status);
        content.addView(rows);
        rows.addView(left);
        rows.addView(right);

        card.addView(accent);
        card.addView(content);

        root.addView(card);

        // Add resend button if failed
        if (item.isFailed) {
            Button resendButton = new Button(requireContext());
            resendButton.setText("Kirim Ulang");
            resendButton.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark));
            resendButton.setTextColor(0xFFFFFFFF);
            LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            buttonLp.setMargins(dp(12), dp(4), dp(12), dp(4));
            resendButton.setLayoutParams(buttonLp);
            resendButton.setOnClickListener(v -> {
                // Handle resend
                CustomToast.showToast(requireContext(), "Mengirim ulang...", Toast.LENGTH_SHORT);
                // TODO: Implement resend logic
            });
            root.addView(resendButton);
        }

        return root;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void fetchAttendanceHistory(LinearLayout listContainer) {
        Request request = new Request.Builder()
                .url(ApiClient.API_BASE_URL + "/v1/attendance/history")
                .get()
                .build();

        ApiClient.getClient(requireContext()).newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                requireActivity().runOnUiThread(() -> {
                    CustomToast.showToast(requireContext(), "Gagal mengambil riwayat: " + e.getMessage(), Toast.LENGTH_SHORT);
                    // Fallback to local history if API fails
                    List<HistoryStore.HistoryItem> items = HistoryStore.getEntries(requireContext());
                    for (HistoryStore.HistoryItem item : items) {
                        listContainer.addView(createItemView(item));
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        if (jsonResponse.getBoolean("success")) {
                            JSONObject data = jsonResponse.getJSONObject("data");
                            JSONArray attendances = data.getJSONArray("attendances");

                            requireActivity().runOnUiThread(() -> {
                                listContainer.removeAllViews(); // Clear any existing views
                                for (int i = 0; i < attendances.length(); i++) {
                                    try {
                                        JSONObject attendance = attendances.getJSONObject(i);
                                        String date = attendance.getString("date");
                                        String checkInTime = attendance.optString("check_in_time", "-");
                                        String checkOutTime = attendance.optString("check_out_time", "-");
                                        boolean isLembur = attendance.optBoolean("is_lembur", false);
                                        String status = attendance.optString("status", "");

                                        // Convert date to timestamp
                                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                        long timestamp = sdf.parse(date).getTime();

                                        // Determine status text based on API response
                                        String statusText = getStatusDisplayText(status, isLembur, checkInTime, checkOutTime, date);

                                        // Get terlambat and cepat pulang from API response
                                        String terlambat = attendance.optString("terlambat", "00:00:00");
                                        String cepatPulang = attendance.optString("cepat_pulang", "00:00:00");

                                        HistoryStore.HistoryItem item = new HistoryStore.HistoryItem(
                                                statusText,
                                                timestamp,
                                                checkInTime,
                                                terlambat,
                                                checkOutTime,
                                                cepatPulang
                                        );
                                        listContainer.addView(createItemView(item));
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } else {
                            requireActivity().runOnUiThread(() -> CustomToast.showToast(requireContext(), "Gagal mengambil riwayat", Toast.LENGTH_SHORT));
                        }
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> CustomToast.showToast(requireContext(), "Error parsing response", Toast.LENGTH_SHORT));
                        e.printStackTrace();
                    }
                } else {
                    requireActivity().runOnUiThread(() -> CustomToast.showToast(requireContext(), "Response gagal", Toast.LENGTH_SHORT));
                }
            }
        });
    }

    private String getStatusDisplayText(String status, boolean isLembur, String checkInTime, String checkOutTime, String date) {
        if (isLembur) {
            return "Lembur";
        }
        if (!status.isEmpty()) {
            switch (status.toLowerCase()) {
                case "hadir":
                    return "Hadir";
                case "terlambat":
                    return "Terlambat";
                case "pulang cepat":
                    return "Pulang Cepat";
                case "tidak konsisten":
                    return "Tidak Konsisten";
                case "lembur":
                    return "Lembur";
                case "tidak hadir":
                    return "Tidak Hadir";
                case "izin":
                    return "Izin";
                case "sakit":
                    return "Sakit";
                case "cuti":
                    return "Cuti";
                case "dinas luar":
                    return "Dinas Luar";
                default:
                    return status.substring(0, 1).toUpperCase() + status.substring(1);
            }
        }
        // Fallback
        if (checkInTime != null && !checkInTime.equals("-")) {
            return "Cek In Berhasil";
        } else if (checkOutTime != null && !checkOutTime.equals("-")) {
            return "Cek Out Berhasil";
        } else {
            return "Absensi " + date;
        }
    }

    private int getStatusColor(String statusText) {
        switch (statusText) {
            case "Hadir":
                return ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark);
            case "Terlambat":
            case "Tidak Konsisten":
                return ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark);
            case "Pulang Cepat":
                return ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark);
            case "Lembur":
                return ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark);
            case "Tidak Hadir":
                return ContextCompat.getColor(requireContext(), android.R.color.darker_gray);
            case "Izin":
            case "Sakit":
            case "Cuti":
            case "Dinas Luar":
                return ContextCompat.getColor(requireContext(), android.R.color.holo_purple);
            case "Cek In Berhasil":
                return ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark);
            case "Cek Out Berhasil":
                return ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark);
            default:
                return ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark);
        }
    }
}
