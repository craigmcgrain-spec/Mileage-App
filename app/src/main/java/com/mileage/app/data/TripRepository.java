package com.mileage.app.data;

import java.util.Date;
import java.util.List;

public class TripRepository {
    private final TripDatabaseHelper databaseHelper;

    public TripRepository(TripDatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

    public long insertTrip(Trip trip) {
        return databaseHelper.insertTrip(trip);
    }

    public boolean updateTrip(Trip trip) {
        return databaseHelper.updateTrip(trip);
    }

    public Trip getTripById(long id) {
        return databaseHelper.getTripById(id);
    }

    public List<Trip> getAllTrips() {
        return databaseHelper.getAllTrips();
    }

    public List<Trip> getTripsByDateRange(Date startDate, Date endDate) {
        return databaseHelper.getTripsByDateRange(startDate, endDate);
    }

    public List<Trip> getWorkTripsByDateRange(Date startDate, Date endDate) {
        return databaseHelper.getWorkTripsByDateRange(startDate, endDate);
    }

    public double getTotalMilesByDateRange(Date startDate, Date endDate) {
        return databaseHelper.getTotalMilesByDateRange(startDate, endDate);
    }

    public double getWorkMilesByDateRange(Date startDate, Date endDate) {
        return databaseHelper.getWorkMilesByDateRange(startDate, endDate);
    }

    public double getPersonalMilesByDateRange(Date startDate, Date endDate) {
        return databaseHelper.getPersonalMilesByDateRange(startDate, endDate);
    }

    public int getTripCountByDateRange(Date startDate, Date endDate) {
        return databaseHelper.getTripCountByDateRange(startDate, endDate);
    }

    public double getAverageTripDistanceByDateRange(Date startDate, Date endDate) {
        return databaseHelper.getAverageTripDistanceByDateRange(startDate, endDate);
    }

    public List<Trip> getTripsByPurposeAndDateRange(String purpose, Date startDate, Date endDate) {
        return databaseHelper.getTripsByPurposeAndDateRange(purpose, startDate, endDate);
    }

    public boolean deleteTrip(long id) {
        return databaseHelper.deleteTrip(id);
    }

    public Trip getActiveTrip() {
        return databaseHelper.getActiveTrip();
    }
}
