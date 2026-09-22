package com.mileage.app;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mileage.app.adapter.TripAdapter;
import com.mileage.app.data.Trip;
import com.mileage.app.data.TripRepository;
import com.mileage.app.service.TripService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TripAdapter.OnTripClickListener {

    private TripRepository tripRepository;
    private TripAdapter tripAdapter;
    private List<Trip> allTrips;

    private RecyclerView recyclerTrips;
    private TextView tvTotalMiles;
    private TextView tvWorkMiles;
    private TextView tvWorkMilesDetail;
    private TextView tvPersonalMilesDetail;
    private TextView tvDateRangeLabel;
    private View layoutEmptyState;
    private View layoutQuickStats;

    private FloatingActionButton fabStartTrip;
    private FloatingActionButton fabStopTrip;

    private View layoutActiveTrip;
    private TextView tvActiveMiles;
    private TextView tvActiveElapsed;

    private Chip chipToday;
    private Chip chipWeek;
    private Chip chipMonth;
    private Chip chipCustom;

    private ImageButton btnClearFilters;
    private ImageButton btnFilterPurpose;

    private Date selectedStartDate;
    private Date selectedEndDate;
    private String selectedPurpose;
    private SimpleDateFormat dateFormat;

    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    // Live tracking state (updated via TripService broadcasts)
    private double currentTripMiles = 0;
    private long currentTripElapsedMs = 0;
    private final Handler elapsedHandler = new Handler(Looper.getMainLooper());
    private final Runnable elapsedRunnable = new Runnable() {
        @Override
        public void run() {
            updateActiveBannerElapsed();
            elapsedHandler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver tripUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (TripService.BROADCAST_UPDATE.equals(action)) {
                currentTripMiles = intent.getDoubleExtra(TripService.EXTRA_DISTANCE_MILES, 0);
                currentTripElapsedMs = intent.getLongExtra(TripService.EXTRA_ELAPSED_MILLIS, 0);
                updateActiveBanner();
            } else if (TripService.BROADCAST_STOPPED.equals(action)) {
                currentTripMiles = 0;
                currentTripElapsedMs = 0;
                checkActiveTrip();
                updateSummary();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tripRepository = MileageApplication.getInstance().getTripRepository();
        dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        allTrips = new ArrayList<>();

        initViews();
        setupRecyclerView();
        setupDateRange();
        setupListeners();
        setupPermissionLauncher();

        // Set initial date range to today
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        selectedStartDate = calendar.getTime();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        selectedEndDate = calendar.getTime();

        updateSummary();
        checkActiveTrip();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TripService.BROADCAST_UPDATE);
        filter.addAction(TripService.BROADCAST_STOPPED);
        ContextCompat.registerReceiver(this, tripUpdateReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        elapsedHandler.post(elapsedRunnable);
        checkActiveTrip();
        updateSummary();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(tripUpdateReceiver);
        } catch (Exception ignored) {
        }
        elapsedHandler.removeCallbacks(elapsedRunnable);
    }

    private void initViews() {
        recyclerTrips = findViewById(R.id.recycler_trips);
        tvTotalMiles = findViewById(R.id.tv_total_miles);
        tvWorkMiles = findViewById(R.id.tv_work_miles);
        tvWorkMilesDetail = findViewById(R.id.tv_work_miles_detail);
        tvPersonalMilesDetail = findViewById(R.id.tv_personal_miles_detail);
        tvDateRangeLabel = findViewById(R.id.tv_date_range_label);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        layoutQuickStats = findViewById(R.id.layout_quick_stats);

        fabStartTrip = findViewById(R.id.fab_start_trip);
        fabStopTrip = findViewById(R.id.fab_stop_trip);

        layoutActiveTrip = findViewById(R.id.layout_active_trip);
        tvActiveMiles = findViewById(R.id.tv_active_miles);
        tvActiveElapsed = findViewById(R.id.tv_active_elapsed);

        chipToday = findViewById(R.id.chip_today);
        chipWeek = findViewById(R.id.chip_week);
        chipMonth = findViewById(R.id.chip_month);
        chipCustom = findViewById(R.id.chip_custom);

        btnClearFilters = findViewById(R.id.btn_clear_filters);
        btnFilterPurpose = findViewById(R.id.btn_filter_purpose);
    }

    private void setupRecyclerView() {
        tripAdapter = new TripAdapter(allTrips, this);
        recyclerTrips.setLayoutManager(new LinearLayoutManager(this));
        recyclerTrips.setAdapter(tripAdapter);
    }

    private void setupDateRange() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, year1, month1, dayOfMonth) -> {
                    Calendar selectedCal = Calendar.getInstance();
                    selectedCal.set(year1, month1, dayOfMonth);
                    selectedCal.set(Calendar.HOUR_OF_DAY, 0);
                    selectedCal.set(Calendar.MINUTE, 0);
                    selectedCal.set(Calendar.SECOND, 0);

                    if (selectedStartDate == null || !selectedStartDate.before(selectedCal.getTime()) ||
                            selectedStartDate.equals(selectedCal.getTime())) {
                        selectedStartDate = selectedCal.getTime();
                    } else {
                        selectedEndDate = selectedStartDate;
                        selectedStartDate = selectedCal.getTime();
                    }

                    updateDateRangeLabel();
                    updateSummary();
                }, year, month, day);

        btnFilterPurpose.setOnClickListener(v -> {
            DatePickerDialog endDatePicker = new DatePickerDialog(this,
                    (view1, year1, month1, dayOfMonth) -> {
                        Calendar selectedCal = Calendar.getInstance();
                        selectedCal.set(year1, month1, dayOfMonth);
                        selectedCal.set(Calendar.HOUR_OF_DAY, 23);
                        selectedCal.set(Calendar.MINUTE, 59);
                        selectedCal.set(Calendar.SECOND, 59);

                        if (selectedEndDate == null || selectedCal.after(selectedEndDate) ||
                                selectedCal.equals(selectedEndDate)) {
                            selectedEndDate = selectedCal.getTime();
                        } else {
                            selectedStartDate = selectedEndDate;
                            selectedEndDate = selectedCal.getTime();
                        }

                        updateDateRangeLabel();
                        updateSummary();
                    }, year, month, day);

            endDatePicker.setTitle(R.string.end_date);
            endDatePicker.show();
        });

        btnFilterPurpose.setOnClickListener(v -> showDateRangeDialog());
    }

    private void showDateRangeDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_date_range, null);
        EditText etStartDate = dialogView.findViewById(R.id.et_start_date);
        EditText etEndDate = dialogView.findViewById(R.id.et_end_date);
        EditText etFilterPurpose = dialogView.findViewById(R.id.et_filter_purpose);

        Calendar calendar = Calendar.getInstance();

        DatePickerDialog startDatePicker = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    Calendar selectedCal = Calendar.getInstance();
                    selectedCal.set(year, month, day);
                    etStartDate.setText(dateFormat.format(selectedCal.getTime()));
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        etStartDate.setOnClickListener(v -> startDatePicker.show());

        DatePickerDialog endDatePicker = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    Calendar selectedCal = Calendar.getInstance();
                    selectedCal.set(year, month, day);
                    etEndDate.setText(dateFormat.format(selectedCal.getTime()));
                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));

        etEndDate.setOnClickListener(v -> endDatePicker.show());

        if (selectedPurpose != null) {
            etFilterPurpose.setText(selectedPurpose);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.apply, (dialog, which) -> {
                    try {
                        String startDateStr = etStartDate.getText().toString();
                        String endDateStr = etEndDate.getText().toString();

                        if (!startDateStr.isEmpty() && !endDateStr.isEmpty()) {
                            SimpleDateFormat inputFormat = new SimpleDateFormat("MM/dd/yy", Locale.getDefault());
                            Date start = inputFormat.parse(startDateStr);
                            Date end = inputFormat.parse(endDateStr);

                            if (start != null && end != null) {
                                selectedStartDate = start;
                                selectedEndDate = end;
                                selectedPurpose = etFilterPurpose.getText().toString().trim();
                                updateDateRangeLabel();
                                updateSummary();
                            }
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Invalid date format", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null);

        builder.show();
    }

    private void setupListeners() {
        chipToday.setOnClickListener(v -> {
            setChipActive(chipToday);
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            selectedStartDate = calendar.getTime();
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            selectedEndDate = calendar.getTime();
            selectedPurpose = null;
            updateDateRangeLabel();
            updateSummary();
        });

        chipWeek.setOnClickListener(v -> {
            setChipActive(chipWeek);
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            selectedStartDate = calendar.getTime();
            calendar.add(Calendar.DAY_OF_WEEK, 6);
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            selectedEndDate = calendar.getTime();
            selectedPurpose = null;
            updateDateRangeLabel();
            updateSummary();
        });

        chipMonth.setOnClickListener(v -> {
            setChipActive(chipMonth);
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            selectedStartDate = calendar.getTime();
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            selectedEndDate = calendar.getTime();
            selectedPurpose = null;
            updateDateRangeLabel();
            updateSummary();
        });

        chipCustom.setOnClickListener(v -> {
            setChipActive(chipCustom);
            showDateRangeDialog();
        });

        btnClearFilters.setOnClickListener(v -> {
            chipToday.setChecked(false);
            chipWeek.setChecked(false);
            chipMonth.setChecked(false);
            chipCustom.setChecked(false);
            selectedPurpose = null;
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            selectedStartDate = calendar.getTime();
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            selectedEndDate = calendar.getTime();
            updateDateRangeLabel();
            updateSummary();
        });

        fabStartTrip.setOnClickListener(v -> {
            Trip activeTrip = tripRepository.getActiveTrip();
            if (activeTrip != null) {
                Toast.makeText(this, R.string.trip_ongoing, Toast.LENGTH_SHORT).show();
                checkActiveTrip();
                return;
            }
            if (!ensureLocationPermissions()) {
                return;
            }
            showStartTripDialog();
        });

        fabStopTrip.setOnClickListener(v -> {
            showStopTripDialog();
        });
    }

    private void setChipActive(Chip activeChip) {
        chipToday.setChecked(false);
        chipWeek.setChecked(false);
        chipMonth.setChecked(false);
        chipCustom.setChecked(false);
        activeChip.setChecked(true);
    }

    private void updateDateRangeLabel() {
        if (selectedStartDate != null && selectedEndDate != null) {
            tvDateRangeLabel.setText(dateFormat.format(selectedStartDate) + " - " + dateFormat.format(selectedEndDate));
        }
        btnClearFilters.setVisibility(View.VISIBLE);
    }

    private void updateSummary() {
        if (selectedStartDate == null || selectedEndDate == null) return;

        double totalMiles = tripRepository.getTotalMilesByDateRange(selectedStartDate, selectedEndDate);
        double workMiles = tripRepository.getWorkMilesByDateRange(selectedStartDate, selectedEndDate);
        double personalMiles = tripRepository.getPersonalMilesByDateRange(selectedStartDate, selectedEndDate);

        tvTotalMiles.setText(String.format(Locale.getDefault(), "%.1f", totalMiles));
        tvWorkMiles.setText(String.format(Locale.getDefault(), "%.1f", workMiles));
        tvWorkMilesDetail.setText(String.format(Locale.getDefault(), "%.1f", workMiles));
        tvPersonalMilesDetail.setText(String.format(Locale.getDefault(), "%.1f", personalMiles));

        loadTrips();
    }

    private void loadTrips() {
        List<Trip> filteredTrips;

        if (selectedPurpose != null && !selectedPurpose.isEmpty()) {
            filteredTrips = tripRepository.getTripsByPurposeAndDateRange(selectedPurpose, selectedStartDate, selectedEndDate);
        } else {
            filteredTrips = tripRepository.getTripsByDateRange(selectedStartDate, selectedEndDate);
        }

        allTrips.clear();
        allTrips.addAll(filteredTrips);
        tripAdapter.updateTrips(allTrips);

        boolean hasTrips = !allTrips.isEmpty();
        layoutEmptyState.setVisibility(hasTrips ? View.GONE : View.VISIBLE);
        recyclerTrips.setVisibility(hasTrips ? View.VISIBLE : View.GONE);
    }

    private void checkActiveTrip() {
        Trip activeTrip = tripRepository.getActiveTrip();
        if (activeTrip != null) {
            fabStartTrip.setVisibility(View.GONE);
            fabStopTrip.setVisibility(View.VISIBLE);
            if (layoutActiveTrip != null) {
                layoutActiveTrip.setVisibility(View.VISIBLE);
            }
            // Ensure tracking service is running (idempotent)
            Intent serviceIntent = new Intent(this, TripService.class);
            serviceIntent.setAction(TripService.ACTION_START);
            try {
                ContextCompat.startForegroundService(this, serviceIntent);
            } catch (Exception e) {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
            }
            updateActiveBanner();
        } else {
            fabStartTrip.setVisibility(View.VISIBLE);
            fabStopTrip.setVisibility(View.GONE);
            if (layoutActiveTrip != null) {
                layoutActiveTrip.setVisibility(View.GONE);
            }
            currentTripMiles = 0;
            currentTripElapsedMs = 0;
        }
    }

    private void updateActiveBanner() {
        if (tvActiveMiles == null) return;
        Trip activeTrip = tripRepository.getActiveTrip();
        if (activeTrip == null) return;
        // Prefer live broadcast value; fall back to DB value persisted by service
        double miles = currentTripMiles > 0 ? currentTripMiles : activeTrip.getDistanceMiles();
        tvActiveMiles.setText(String.format(Locale.getDefault(), "%.2f mi", miles));
        updateActiveBannerElapsed();
    }

    private void updateActiveBannerElapsed() {
        if (tvActiveElapsed == null) return;
        Trip activeTrip = tripRepository.getActiveTrip();
        if (activeTrip == null || activeTrip.getStartTime() == null) return;
        if (layoutActiveTrip == null || layoutActiveTrip.getVisibility() != View.VISIBLE) return;
        long elapsed = System.currentTimeMillis() - activeTrip.getStartTime().getTime();
        long seconds = elapsed / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        tvActiveElapsed.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs));
    }

    private void showStartTripDialog() {
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_start_trip, null);
        EditText etOdometerStart = dialogView.findViewById(R.id.et_odometer_start);
        EditText etPurpose = dialogView.findViewById(R.id.et_purpose);
        SwitchMaterial switchWork = dialogView.findViewById(R.id.switch_work_trip);

        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.start, (dialog, which) -> {
                    try {
                        String startStr = etOdometerStart.getText().toString().trim();
                        String purpose = etPurpose.getText().toString().trim();
                        boolean isWorkTrip = switchWork != null && switchWork.isChecked();

                        double startOdometer = 0;
                        if (!startStr.isEmpty()) {
                            startOdometer = Double.parseDouble(startStr);
                        }

                        // GPS-tracked trip: distance starts at 0, filled in as we drive
                        Trip trip = new Trip();
                        trip.setStartOdometer(startOdometer);
                        trip.setEndOdometer(startOdometer);
                        trip.setDistanceMiles(0);
                        trip.setPurpose(purpose.isEmpty() ? null : purpose);
                        trip.setWorkTrip(isWorkTrip);
                        trip.setStartTime(new Date());
                        long id = tripRepository.insertTrip(trip);
                        trip.setId(id);

                        currentTripMiles = 0;
                        currentTripElapsedMs = 0;

                        Intent serviceIntent = new Intent(this, TripService.class);
                        serviceIntent.setAction(TripService.ACTION_START);
                        ContextCompat.startForegroundService(this, serviceIntent);

                        Toast.makeText(MainActivity.this, R.string.trip_started, Toast.LENGTH_SHORT).show();
                        checkActiveTrip();
                        updateSummary();
                    } catch (NumberFormatException e) {
                        Toast.makeText(MainActivity.this, "Invalid odometer reading", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showStopTripDialog() {
        Trip activeTrip = tripRepository.getActiveTrip();
        if (activeTrip == null) {
            checkActiveTrip();
            return;
        }

        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_stop_trip, null);
        TextView tvLive = dialogView.findViewById(R.id.tv_stop_live_distance);
        EditText etPurpose = dialogView.findViewById(R.id.et_purpose);
        SwitchMaterial switchWork = dialogView.findViewById(R.id.switch_work_trip);

        double liveMiles = currentTripMiles > 0 ? currentTripMiles : activeTrip.getDistanceMiles();
        tvLive.setText(String.format(Locale.getDefault(), "GPS so far: %.2f miles", liveMiles));
        if (activeTrip.getPurpose() != null) {
            etPurpose.setText(activeTrip.getPurpose());
        }
        if (switchWork != null) {
            switchWork.setChecked(activeTrip.isWorkTrip());
        }

        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.stop, (dialog, which) -> {
                    String purpose = etPurpose.getText().toString().trim();
                    boolean isWorkTrip = switchWork != null && switchWork.isChecked();

                    // Save any metadata edits now; service finalizes distance + end time
                    Trip fresh = tripRepository.getTripById(activeTrip.getId());
                    if (fresh != null) {
                        if (!purpose.isEmpty()) {
                            fresh.setPurpose(purpose);
                        }
                        fresh.setWorkTrip(isWorkTrip);
                        tripRepository.updateTrip(fresh);
                    }

                    Intent serviceIntent = new Intent(this, TripService.class);
                    serviceIntent.setAction(TripService.ACTION_STOP);
                    try {
                        ContextCompat.startForegroundService(this, serviceIntent);
                    } catch (Exception e) {
                        startService(serviceIntent);
                    }
                    Toast.makeText(MainActivity.this, R.string.trip_stopped, Toast.LENGTH_SHORT).show();
                    // UI refresh happens on BROADCAST_STOPPED; also refresh optimistically
                    elapsedHandler.postDelayed(() -> {
                        checkActiveTrip();
                        updateSummary();
                    }, 800);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onTripClick(Trip trip) {
        // Show trip details - could expand to a detail dialog
        StringBuilder message = new StringBuilder();
        message.append("Date: ").append(dateFormat.format(trip.getStartTime())).append("\n");
        if (trip.getEndTime() != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
            message.append(timeFormat.format(trip.getStartTime())).append(" - ")
                   .append(timeFormat.format(trip.getEndTime())).append("\n");
        }
        message.append("Distance: ").append(String.format(Locale.getDefault(), "%.1f", trip.getDistanceMiles())).append(" miles\n");
        message.append("Start Odometer: ").append(String.format(Locale.getDefault(), "%.1f", trip.getStartOdometer())).append(" mi\n");
        message.append("End Odometer: ").append(String.format(Locale.getDefault(), "%.1f", trip.getEndOdometer())).append(" mi\n");

        if (trip.getPurpose() != null && !trip.getPurpose().isEmpty()) {
            message.append("Purpose: ").append(trip.getPurpose()).append("\n");
        }

        message.append("Type: ").append(trip.isWorkTrip() ? "Work" : "Personal");

        new MaterialAlertDialogBuilder(this)
                .setTitle("Trip Details")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onTripDelete(Trip trip) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_trip)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    tripRepository.deleteTrip(trip.getId());
                    updateSummary();
                    Toast.makeText(MainActivity.this, R.string.trip_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void setupPermissionLauncher() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    if ((fine != null && fine) || (coarse != null && coarse)) {
                        // Permission granted — user can press Start again
                        Toast.makeText(this, "Location enabled — press Start again", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean ensureLocationPermissions() {
        boolean fineGranted = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (fineGranted || coarseGranted) {
            // Also request notification permission on Android 13+ so tracking status shows
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    locationPermissionLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.POST_NOTIFICATIONS});
                    return false;
                }
            }
            return true;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS});
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
        Toast.makeText(this, R.string.location_permission_rationale, Toast.LENGTH_LONG).show();
        return false;
    }
}
