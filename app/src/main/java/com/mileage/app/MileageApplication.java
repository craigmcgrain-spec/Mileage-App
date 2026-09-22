package com.mileage.app;

import android.app.Application;
import com.mileage.app.data.TripDatabaseHelper;
import com.mileage.app.data.TripRepository;

public class MileageApplication extends Application {
    private static MileageApplication instance;
    private TripRepository tripRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        TripDatabaseHelper databaseHelper = new TripDatabaseHelper(this);
        tripRepository = new TripRepository(databaseHelper);
    }

    public static MileageApplication getInstance() {
        return instance;
    }

    public TripRepository getTripRepository() {
        return tripRepository;
    }
}
