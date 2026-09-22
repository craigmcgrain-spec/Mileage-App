package com.mileage.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.mileage.app.MainActivity;
import com.mileage.app.MileageApplication;
import com.mileage.app.R;
import com.mileage.app.data.Trip;
import com.mileage.app.data.TripRepository;

import java.util.Date;
import java.util.Locale;

public class TripService extends Service {

    public static final String ACTION_START = "com.mileage.app.action.START_TRIP";
    public static final String ACTION_STOP = "com.mileage.app.action.STOP_TRIP";
    // Legacy action sent from old notification / MainActivity
    private static final String LEGACY_STOP = "STOP_TRIP";

    public static final String BROADCAST_UPDATE = "com.mileage.app.TRIP_UPDATE";
    public static final String BROADCAST_STOPPED = "com.mileage.app.TRIP_STOPPED";
    public static final String EXTRA_DISTANCE_MILES = "extra_distance_miles";
    public static final String EXTRA_DISTANCE_METERS = "extra_distance_meters";
    public static final String EXTRA_ELAPSED_MILLIS = "extra_elapsed_millis";
    public static final String EXTRA_TRIP_ID = "extra_trip_id";

    private static final String CHANNEL_ID = "mileage_tracking_channel";
    private static final int NOTIFICATION_ID = 1;

    // GPS filtering thresholds
    private static final float MAX_ACCURACY_METERS = 25f;
    private static final float MIN_DISTANCE_METERS = 3f;
    // Reject absurd jumps (> 250 m/s ~ 560 mph between fixes)
    private static final float MAX_PLAUSIBLE_SPEED_MPS = 90f;

    private static final double METERS_TO_MILES = 0.000621371;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private TripRepository tripRepository;
    private Trip activeTrip;

    private double totalDistanceMeters = 0;
    private Location lastLocation;
    private long startTimeMillis;
    private boolean tracking = false;

    @Override
    public void onCreate() {
        super.onCreate();
        tripRepository = MileageApplication.getInstance().getTripRepository();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action) || LEGACY_STOP.equals(action)) {
            if (activeTrip == null) {
                activeTrip = tripRepository.getActiveTrip();
                if (activeTrip != null) {
                    restoreState(activeTrip);
                }
            }
            stopTrip();
            return START_NOT_STICKY;
        }

        // START (default): load active trip from DB
        Trip dbTrip = tripRepository.getActiveTrip();
        if (dbTrip == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // If we're already tracking this same trip (e.g. Activity resumed and
        // re-sent START), keep in-memory precision instead of resetting from DB.
        if (tracking && activeTrip != null && activeTrip.getId() == dbTrip.getId()) {
            startForegroundWithType(createNotification());
            return START_STICKY;
        }
        activeTrip = dbTrip;
        restoreState(activeTrip);

        startForegroundWithType(createNotification());
        startTrackingLocation();
        return START_STICKY;
    }

    private void restoreState(Trip trip) {
        // Resume distance if service was killed and restarted
        totalDistanceMeters = trip.getDistanceMiles() / METERS_TO_MILES;
        if (trip.getStartTime() != null) {
            startTimeMillis = trip.getStartTime().getTime();
        } else {
            startTimeMillis = System.currentTimeMillis();
        }
    }

    private void startForegroundWithType(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (SecurityException e) {
            // Missing location permission for FGS type on Android 14+.
            // Fall back to plain foreground start so the trip isn't lost.
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (Exception ignored) {
                stopSelf();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
    }

    private void startTrackingLocation() {
        if (tracking) return;

        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // No permission — can't track. Stop so UI can prompt.
            stopSelf();
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .setMinUpdateDistanceMeters(3f)
                .setWaitForAccurateLocation(false)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    handleLocation(location);
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            tracking = true;
        } catch (SecurityException e) {
            stopSelf();
        }
    }

    private void stopLocationUpdates() {
        tracking = false;
        if (locationCallback != null && fusedLocationClient != null) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback);
            } catch (Exception ignored) {
            }
            locationCallback = null;
        }
    }

    private void handleLocation(Location location) {
        if (location == null) return;

        // Drop inaccurate fixes
        if (location.hasAccuracy() && location.getAccuracy() > MAX_ACCURACY_METERS) {
            return;
        }

        if (lastLocation == null) {
            lastLocation = new Location(location);
            broadcastUpdate();
            return;
        }

        float delta = location.distanceTo(lastLocation);
        long deltaTimeMs = location.getTime() - lastLocation.getTime();

        // Drop drift / jitter when stationary
        if (delta < MIN_DISTANCE_METERS) {
            return;
        }

        // Drop teleport glitches
        if (deltaTimeMs > 0) {
            float speed = delta / (deltaTimeMs / 1000f);
            if (speed > MAX_PLAUSIBLE_SPEED_MPS) {
                lastLocation = new Location(location);
                return;
            }
        }

        totalDistanceMeters += delta;
        lastLocation = new Location(location);

        double miles = totalDistanceMeters * METERS_TO_MILES;
        persistProgress(miles);
        updateNotification();
        broadcastUpdate();
    }

    private void persistProgress(double miles) {
        if (activeTrip == null) return;
        // Round to 1 decimal for list/summary consistency, keep full precision in service
        double rounded = Math.round(miles * 10.0) / 10.0;
        activeTrip.setDistanceMiles(rounded);
        activeTrip.setEndOdometer(Math.round((activeTrip.getStartOdometer() + miles) * 10.0) / 10.0);
        tripRepository.updateTrip(activeTrip);
    }

    private void stopTrip() {
        stopLocationUpdates();
        if (activeTrip != null) {
            // Re-read in case UI updated purpose/work while tracking
            Trip fresh = tripRepository.getTripById(activeTrip.getId());
            if (fresh != null) activeTrip = fresh;

            double distanceMiles = totalDistanceMeters * METERS_TO_MILES;
            double rounded = Math.round(distanceMiles * 10.0) / 10.0;
            activeTrip.setEndTime(new Date());
            activeTrip.setDistanceMiles(rounded);
            double endOdometer = activeTrip.getStartOdometer() + distanceMiles;
            activeTrip.setEndOdometer(Math.round(endOdometer * 10.0) / 10.0);
            tripRepository.updateTrip(activeTrip);

            Intent stopped = new Intent(BROADCAST_STOPPED);
            stopped.setPackage(getPackageName());
            stopped.putExtra(EXTRA_TRIP_ID, activeTrip.getId());
            stopped.putExtra(EXTRA_DISTANCE_MILES, rounded);
            sendBroadcast(stopped);
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                //noinspection deprecation
                stopForeground(true);
            }
        } catch (Exception ignored) {
        }
        stopSelf();
    }

    private void broadcastUpdate() {
        Intent update = new Intent(BROADCAST_UPDATE);
        update.setPackage(getPackageName());
        if (activeTrip != null) {
            update.putExtra(EXTRA_TRIP_ID, activeTrip.getId());
        }
        update.putExtra(EXTRA_DISTANCE_METERS, totalDistanceMeters);
        update.putExtra(EXTRA_DISTANCE_MILES, totalDistanceMeters * METERS_TO_MILES);
        update.putExtra(EXTRA_ELAPSED_MILLIS, System.currentTimeMillis() - startTimeMillis);
        sendBroadcast(update);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        CharSequence name = getString(R.string.notification_channel_name);
        String description = getString(R.string.notification_channel_description);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(description);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopIntent = new Intent(this, TripService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String elapsed = formatElapsedTime(System.currentTimeMillis() - startTimeMillis);
        double miles = totalDistanceMeters * METERS_TO_MILES;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.active_trip))
                .setContentText(String.format(Locale.getDefault(),
                        "%s | %.2f mi", elapsed, miles))
                .setSmallIcon(R.drawable.ic_directions_car)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_stop, getString(R.string.stop), stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        String elapsed = formatElapsedTime(System.currentTimeMillis() - startTimeMillis);
        double miles = totalDistanceMeters * METERS_TO_MILES;

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.active_trip))
                .setContentText(String.format(Locale.getDefault(),
                        "%s | %.2f mi", elapsed, miles))
                .setSmallIcon(R.drawable.ic_directions_car)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private String formatElapsedTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs);
    }
}
