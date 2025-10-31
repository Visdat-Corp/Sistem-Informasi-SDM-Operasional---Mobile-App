package com.visdat.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
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

        ImageView backButton = view.findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish(); // Finish NavigationActivity to go back to Dashboard
            }
        });

        LinearLayout listContainer = view.findViewById(R.id.listContainer);
        FloatingActionButton fabRefresh = view.findViewById(R.id.fab_refresh);

        fabRefresh.setOnClickListener(v -> {
            listContainer.removeAllViews();
            // Bersihkan cache lokal setiap kali refresh untuk memastikan data sinkron dengan server
            HistoryStore.clearAllEntries(requireContext());
            fetchAttendanceHistory(listContainer);
            CustomToast.showToast(requireContext(), "Memperbarui riwayat...", Toast.LENGTH_SHORT);
        });

        // Fetch history from API
        fetchAttendanceHistory(listContainer);
    }

    private View createItemView(HistoryStore.HistoryItem item) {
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.item_history, null, false);

        View viewAccent = root.findViewById(R.id.viewAccent);
        TextView tvTanggal = root.findViewById(R.id.tvTanggal);
        TextView tvStatus = root.findViewById(R.id.tvStatus);
        TextView tvJamHadir = root.findViewById(R.id.tvJamHadir);
        TextView tvTelat = root.findViewById(R.id.tvTelat);
        TextView tvJamPulang = root.findViewById(R.id.tvJamPulang);
        TextView tvCepat = root.findViewById(R.id.tvCepat);
        Button btnResend = root.findViewById(R.id.btnResend);
        Button btnRequestOverride = root.findViewById(R.id.btnRequestOverride);
        TextView tvOverrideStatus = root.findViewById(R.id.tvOverrideStatus);

        // Set tanggal
        SimpleDateFormat dayFmt = new SimpleDateFormat("dd MMMM yyyy", new Locale("id"));
        tvTanggal.setText(dayFmt.format(new java.util.Date(item.timestamp)));

        // Set status & warna
        tvStatus.setText(item.statusText);
        tvStatus.setTextColor(getStatusColor(item.statusText));
        if (viewAccent != null) {
            viewAccent.setBackgroundColor(getStatusColor(item.statusText));
        }

        // Set detail kiri-kanan
        tvJamHadir.setText(item.jamHadir);
        tvTelat.setText(item.terlambat);
        tvJamPulang.setText(item.jamPulang);
        tvCepat.setText(item.cepatPulang);

        // Resend button jika gagal
        if (item.isFailed) {
            btnResend.setVisibility(View.VISIBLE);
            btnResend.setOnClickListener(v -> {
                CustomToast.showToast(requireContext(), "Mengirim ulang...", Toast.LENGTH_SHORT);
                // TODO: Implement resend logic
            });
        } else {
            btnResend.setVisibility(View.GONE);
        }

        // Override status display and button
        if (item.overrideStatus != null) {
            tvOverrideStatus.setVisibility(View.VISIBLE);
            if ("pending".equals(item.overrideStatus)) {
                tvOverrideStatus.setText("⏳ Menunggu Approval Manager");
                tvOverrideStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light));
                btnRequestOverride.setVisibility(View.GONE);
            } else if ("approved".equals(item.overrideStatus)) {
                tvOverrideStatus.setText("✅ Override Disetujui");
                tvOverrideStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light));
                btnRequestOverride.setVisibility(View.GONE);
            } else if ("rejected".equals(item.overrideStatus)) {
                tvOverrideStatus.setText("❌ Override Ditolak");
                tvOverrideStatus.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_light));
                btnRequestOverride.setVisibility(View.VISIBLE);
                btnRequestOverride.setText("Request Ulang Override");
            }
        } else {
            tvOverrideStatus.setVisibility(View.GONE);
            // Show button only if item has id (from server) and check in/out exists
            if (item.id > 0 && !"-".equals(item.jamHadir)) {
                btnRequestOverride.setVisibility(View.VISIBLE);
            } else {
                btnRequestOverride.setVisibility(View.GONE);
            }
        }

        // Override request button handler
        btnRequestOverride.setOnClickListener(v -> {
            showOverrideRequestDialog(item.id);
        });

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
                Log.e("HistoryFragment", "Failed to fetch attendance history: " + e.getMessage(), e);
                requireActivity().runOnUiThread(() -> {
                    try {
                        String errorMessage;
                        if (e.getMessage() != null && e.getMessage().contains("Failed to connect")) {
                            errorMessage = "Gagal terhubung ke server. Periksa koneksi internet Anda.";
                        } else if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                            errorMessage = "Koneksi timeout. Coba lagi nanti.";
                        } else {
                            errorMessage = "Gagal mengambil riwayat: " + e.getMessage();
                        }
                        CustomToast.showToast(requireContext(), errorMessage, Toast.LENGTH_SHORT);
                    } catch (Exception ex) {
                        Log.e("HistoryFragment", "Error in failure handler: " + ex.getMessage(), ex);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            if (jsonResponse.getBoolean("success")) {
                                JSONObject data = jsonResponse.getJSONObject("data");
                                JSONArray attendances = data.getJSONArray("attendances");

                                requireActivity().runOnUiThread(() -> {
                                    listContainer.removeAllViews(); // Clear any existing views

                                    // Kumpulkan tanggal (yyyy-MM-dd) dari API untuk cegah duplikasi saat menambah data lokal
                                    java.util.HashSet<String> apiDates = new java.util.HashSet<>();
                                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                                    for (int i = 0; i < attendances.length(); i++) {
                                        try {
                                            JSONObject attendance = attendances.getJSONObject(i);
                                            int id = attendance.optInt("id", 0);
                                            String date = attendance.getString("date");
                                            apiDates.add(date);

                                            String checkInTime = attendance.optString("check_in_time", "-");
                                            String checkOutTime = attendance.optString("check_out_time", "-");
                                            boolean isLembur = attendance.optBoolean("is_lembur", false);
                                            String status = attendance.optString("status", "");
                                            
                                            // Get override status
                                            boolean overrideRequest = attendance.optBoolean("override_request", false);
                                            String overrideStatus = attendance.optString("override_status", null);

                                            long timestamp = sdf.parse(date).getTime();

                                            String statusText = getStatusDisplayText(status, isLembur, checkInTime, checkOutTime, date);
                                            String terlambat = attendance.optString("terlambat", "00:00:00");
                                            String cepatPulang = attendance.optString("cepat_pulang", "00:00:00");

                                            HistoryStore.HistoryItem item = new HistoryStore.HistoryItem(
                                                    id,
                                                    statusText,
                                                    timestamp,
                                                    checkInTime,
                                                    terlambat,
                                                    checkOutTime,
                                                    cepatPulang,
                                                    false,
                                                    overrideRequest ? overrideStatus : null
                                            );
                                            listContainer.addView(createItemView(item));
                                        } catch (Exception e) {
                                            Log.e("HistoryFragment", "Error processing attendance item: " + e.getMessage(), e);
                                        }
                                    }

                                    // Data history sekarang 100% dari API server, tidak perlu menampilkan data lokal
                                    // untuk menghindari tampilnya data yang sudah dihapus dari database
                                });
                            } else {
                                String errorMsg = jsonResponse.optString("message", "Gagal mengambil riwayat");
                                requireActivity().runOnUiThread(() -> 
                                    CustomToast.showToast(requireContext(), errorMsg, Toast.LENGTH_SHORT)
                                );
                            }
                        } catch (JSONException e) {
                            Log.e("HistoryFragment", "Error parsing response: " + e.getMessage(), e);
                            requireActivity().runOnUiThread(() -> 
                                CustomToast.showToast(requireContext(), "Error parsing response", Toast.LENGTH_SHORT)
                            );
                        }
                    } else {
                        int code = response.code();
                        String errorMsg = "Response gagal (Kode: " + code + ")";
                        
                        // Try to get error message from response body
                        try {
                            if (response.body() != null) {
                                String body = response.body().string();
                                JSONObject jsonError = new JSONObject(body);
                                if (jsonError.has("message")) {
                                    errorMsg = jsonError.getString("message");
                                }
                            }
                        } catch (Exception ex) {
                            Log.e("HistoryFragment", "Error reading error response: " + ex.getMessage(), ex);
                        }
                        
                        final String finalErrorMsg = errorMsg;
                        requireActivity().runOnUiThread(() -> 
                            CustomToast.showToast(requireContext(), finalErrorMsg, Toast.LENGTH_SHORT)
                        );
                    }
                } catch (Exception e) {
                    Log.e("HistoryFragment", "Critical error in response handler: " + e.getMessage(), e);
                    requireActivity().runOnUiThread(() -> 
                        CustomToast.showToast(requireContext(), "Terjadi kesalahan saat memproses data", Toast.LENGTH_SHORT)
                    );
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

    /**
     * Show dialog to request override with type selection and reason input
     */
    private void showOverrideRequestDialog(int idAbsensi) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(android.R.layout.select_dialog_item, null);
        
        // Create custom dialog layout
        LinearLayout dialogLayout = new LinearLayout(requireContext());
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(40, 40, 40, 40);

        // Dropdown for override type
        TextView labelType = new TextView(requireContext());
        labelType.setText("Jenis Override:");
        labelType.setTextSize(14);
        labelType.setPadding(0, 0, 0, 8);
        dialogLayout.addView(labelType);

        final String[] overrideTypes = {"Terlambat Check In", "Pulang Cepat"};
        final String[] overrideTypeValues = {"late_check_in", "early_check_out"};
        final int[] selectedType = {0}; // Default to first option

        android.widget.Spinner spinnerType = new android.widget.Spinner(requireContext());
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                overrideTypes
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapter);
        spinnerType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedType[0] = position;
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        dialogLayout.addView(spinnerType);

        // Spacer
        View spacer = new View(requireContext());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                30
        ));
        dialogLayout.addView(spacer);

        // EditText for reason
        TextView labelReason = new TextView(requireContext());
        labelReason.setText("Alasan (min. 10 karakter):");
        labelReason.setTextSize(14);
        labelReason.setPadding(0, 0, 0, 8);
        dialogLayout.addView(labelReason);

        android.widget.EditText inputReason = new android.widget.EditText(requireContext());
        inputReason.setHint("Contoh: Terlambat karena pergi ke lokasi project lalu balik ke kantor");
        inputReason.setMinLines(3);
        inputReason.setMaxLines(5);
        inputReason.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        dialogLayout.addView(inputReason);

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Request Override Absensi")
                .setView(dialogLayout)
                .setCancelable(true)
                .setPositiveButton("Kirim Request", (dialog, which) -> {
                    String reason = inputReason.getText() != null ? inputReason.getText().toString().trim() : "";
                    if (reason.length() < 10) {
                        CustomToast.showToast(requireContext(), "Alasan minimal 10 karakter!", Toast.LENGTH_SHORT);
                    } else {
                        String overrideType = overrideTypeValues[selectedType[0]];
                        sendOverrideRequest(idAbsensi, overrideType, reason);
                    }
                })
                .setNegativeButton("Batal", (dialog, which) -> dialog.dismiss());

        builder.create().show();
    }

    /**
     * Send override request to backend API
     */
    private void sendOverrideRequest(int idAbsensi, String overrideType, String reason) {
        try {
            okhttp3.FormBody.Builder formBuilder = new okhttp3.FormBody.Builder()
                    .add("id_absensi", String.valueOf(idAbsensi))
                    .add("override_type", overrideType)
                    .add("reason", reason);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(ApiClient.API_BASE_URL + "/v1/attendance/override-request")
                    .post(formBuilder.build());

            // Add Authorization header
            String authToken = ApiClient.getStoredAuthToken(requireContext());
            if (authToken != null && !authToken.isEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer " + authToken);
            }

            Request request = requestBuilder.build();

            ApiClient.getClient(requireContext()).newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("HistoryFragment", "Failed to send override request: " + e.getMessage(), e);
                    requireActivity().runOnUiThread(() -> {
                        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
                        builder.setTitle("Gagal Kirim Override")
                                .setMessage("Terjadi kesalahan jaringan: " + e.getMessage())
                                .setCancelable(false)
                                .setPositiveButton("Coba Lagi", (d, w) -> sendOverrideRequest(idAbsensi, overrideType, reason))
                                .setNegativeButton("Tutup", (d, w) -> d.dismiss());
                        builder.create().show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        int code = response.code();
                        String body = response.body() != null ? response.body().string() : "";

                        requireActivity().runOnUiThread(() -> {
                            if (code >= 200 && code < 300) {
                                CustomToast.showToast(requireContext(), 
                                    "✅ Permintaan override berhasil dikirim!\nMenunggu approval Manager SDM.", 
                                    Toast.LENGTH_LONG);
                                
                                // Refresh history to show updated status
                                LinearLayout listContainer = requireView().findViewById(R.id.listContainer);
                                fetchAttendanceHistory(listContainer);
                            } else {
                                String errorMsg = "Server Error (" + code + ")";
                                try {
                                    JSONObject jsonError = new JSONObject(body);
                                    errorMsg = jsonError.optString("message", errorMsg);
                                } catch (JSONException e) {
                                    errorMsg += "\n" + body;
                                }

                                final String finalErrorMsg = errorMsg;
                                androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
                                builder.setTitle("Gagal Kirim Override")
                                        .setMessage(finalErrorMsg)
                                        .setCancelable(false)
                                        .setPositiveButton("Coba Lagi", (d, w) -> sendOverrideRequest(idAbsensi, overrideType, reason))
                                        .setNegativeButton("Tutup", (d, w) -> d.dismiss());
                                builder.create().show();
                            }
                        });
                    } catch (Exception e) {
                        Log.e("HistoryFragment", "Error processing override response: " + e.getMessage(), e);
                        requireActivity().runOnUiThread(() -> {
                            CustomToast.showToast(requireContext(), 
                                "Error memproses response: " + e.getMessage(), 
                                Toast.LENGTH_SHORT);
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e("HistoryFragment", "Error building override request: " + e.getMessage(), e);
            CustomToast.showToast(requireContext(), 
                "Error membangun request: " + e.getMessage(), 
                Toast.LENGTH_LONG);
        }
    }
}
