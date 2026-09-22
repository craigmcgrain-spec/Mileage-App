package com.mileage.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TripDatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "mileage.db";
    public static final int DATABASE_VERSION = 1;

    public static final String TABLE_TRIPS = "trips";

    public static final String COLUMN_ID = BaseColumns._ID;
    public static final String COLUMN_START_TIME = "start_time";
    public static final String COLUMN_END_TIME = "end_time";
    public static final String COLUMN_START_ODOMETER = "start_odometer";
    public static final String COLUMN_END_ODOMETER = "end_odometer";
    public static final String COLUMN_DISTANCE_MILES = "distance_miles";
    public static final String COLUMN_PURPOSE = "purpose";
    public static final String COLUMN_IS_WORK_TRIP = "is_work_trip";
    public static final String COLUMN_CREATED_AT = "created_at";

    private static final String CREATE_TABLE_TRIPS = "CREATE TABLE " + TABLE_TRIPS + " (" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_START_TIME + " INTEGER, " +
            COLUMN_END_TIME + " INTEGER, " +
            COLUMN_START_ODOMETER + " REAL NOT NULL, " +
            COLUMN_END_ODOMETER + " REAL NOT NULL, " +
            COLUMN_DISTANCE_MILES + " REAL, " +
            COLUMN_PURPOSE + " TEXT, " +
            COLUMN_IS_WORK_TRIP + " INTEGER DEFAULT 0, " +
            COLUMN_CREATED_AT + " INTEGER NOT NULL)";

    public TripDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_TRIPS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRIPS);
        onCreate(db);
    }

    public long insertTrip(Trip trip) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_START_TIME, trip.getStartTime() != null ? trip.getStartTime().getTime() : System.currentTimeMillis());
        values.put(COLUMN_END_TIME, trip.getEndTime() != null ? trip.getEndTime().getTime() : (long) 0);
        values.put(COLUMN_START_ODOMETER, trip.getStartOdometer());
        values.put(COLUMN_END_ODOMETER, trip.getEndOdometer());
        values.put(COLUMN_DISTANCE_MILES, trip.getDistanceMiles());
        values.put(COLUMN_PURPOSE, trip.getPurpose());
        values.put(COLUMN_IS_WORK_TRIP, trip.isWorkTrip() ? 1 : 0);
        values.put(COLUMN_CREATED_AT, trip.getCreatedAt().getTime());
        long id = db.insert(TABLE_TRIPS, null, values);
        db.close();
        return id;
    }

    public boolean updateTrip(Trip trip) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_END_TIME, trip.getEndTime() != null ? trip.getEndTime().getTime() : (long) 0);
        values.put(COLUMN_END_ODOMETER, trip.getEndOdometer());
        values.put(COLUMN_DISTANCE_MILES, trip.getDistanceMiles());
        values.put(COLUMN_PURPOSE, trip.getPurpose());
        values.put(COLUMN_IS_WORK_TRIP, trip.isWorkTrip() ? 1 : 0);
        int rowsAffected = db.update(TABLE_TRIPS, values, COLUMN_ID + "=?", new String[]{String.valueOf(trip.getId())});
        db.close();
        return rowsAffected > 0;
    }

    public Trip getTripById(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TRIPS, null, COLUMN_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor == null) return null;
        Trip trip = null;
        if (cursor.moveToFirst()) {
            trip = cursorToTrip(cursor);
        }
        cursor.close();
        db.close();
        return trip;
    }

    public List<Trip> getAllTrips() {
        return getTripsOrdered(COLUMN_CREATED_AT + " DESC");
    }

    public List<Trip> getTripsByDateRange(Date startDate, Date endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COLUMN_START_TIME + " >= ? AND " + COLUMN_START_TIME + " <= ?";
        String[] selectionArgs = {String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime())};
        List<Trip> trips = new ArrayList<>();
        Cursor cursor = db.query(TABLE_TRIPS, null, selection, selectionArgs, null, null, COLUMN_CREATED_AT + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                trips.add(cursorToTrip(cursor));
            }
            cursor.close();
        }
        db.close();
        return trips;
    }

    public List<Trip> getWorkTripsByDateRange(Date startDate, Date endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COLUMN_START_TIME + " >= ? AND " + COLUMN_START_TIME + " <= ? AND " + COLUMN_IS_WORK_TRIP + " = 1";
        String[] selectionArgs = {String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime())};
        List<Trip> trips = new ArrayList<>();
        Cursor cursor = db.query(TABLE_TRIPS, null, selection, selectionArgs, null, null, COLUMN_CREATED_AT + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                trips.add(cursorToTrip(cursor));
            }
            cursor.close();
        }
        db.close();
        return trips;
    }

    public double getTotalMilesByDateRange(Date startDate, Date endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COLUMN_START_TIME + " >= ? AND " + COLUMN_START_TIME + " <= ?";
        String[] selectionArgs = {String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime())};
        Cursor cursor = db.query(TABLE_TRIPS, new String[]{"SUM(" + COLUMN_DISTANCE_MILES + ")"}, selection, selectionArgs, null, null, null);
        double total = 0.0;
        if (cursor != null && cursor.moveToFirst()) {
            total = cursor.getDouble(0);
            if (Double.isNaN(total)) total = 0.0;
        }
        if (cursor != null) cursor.close();
        db.close();
        return total;
    }

    public double getWorkMilesByDateRange(Date startDate, Date endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COLUMN_START_TIME + " >= ? AND " + COLUMN_START_TIME + " <= ? AND " + COLUMN_IS_WORK_TRIP + " = 1";
        String[] selectionArgs = {String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime())};
        Cursor cursor = db.query(TABLE_TRIPS, new String[]{"SUM(" + COLUMN_DISTANCE_MILES + ")"}, selection, selectionArgs, null, null, null);
        double total = 0.0;
        if (cursor != null && cursor.moveToFirst()) {
            total = cursor.getDouble(0);
            if (Double.isNaN(total)) total = 0.0;
        }
        if (cursor != null) cursor.close();
        db.close();
        return total;
    }

    public double getPersonalMilesByDateRange(Date startDate, Date endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COLUMN_START_TIME + " >= ? AND " + COLUMN_START_TIME + " <= ? AND " + COLUMN_IS_WORK_TRIP + " = 0";
        String[] selectionArgs = {String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime())};
        Cursor cursor = db.query(TABLE_TRIPS, new String[]{"SUM(" + COLUMN_DISTANCE_MILES + ")"}, selection, selectionArgs, null, null, null);
        double total = 0.0;
        if (cursor != null && cursor.moveToFirst()) {
            total = cursor.getDouble(0);
            if (Double.isNaN(total)) total = 0.0;
        }
        if (cursor != null) cursor.close();
        db.close();
        return total;
    }

    public int getTripCountByDateRange(Date startDate, Date endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COLUMN_START_TIME + " >= ? AND " + COLUMN_START_TIME + " <= ?";
        String[] selectionArgs = {String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime())};
        Cursor cursor = db.query(TABLE_TRIPS, new String[]{"COUNT(" + COLUMN_ID + ")"}, selection, selectionArgs, null, null, null);
        int count = 0;
        if (cursor != null && cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        if (cursor != null) cursor.close();
        db.close();
        return count;
    }

    public double getAverageTripDistanceByDateRange(Date startDate, Date endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COLUMN_START_TIME + " >= ? AND " + COLUMN_START_TIME + " <= ?";
        String[] selectionArgs = {String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime())};
        Cursor cursor = db.query(TABLE_TRIPS, new String[]{"AVG(" + COLUMN_DISTANCE_MILES + ")"}, selection, selectionArgs, null, null, null);
        double avg = 0.0;
        if (cursor != null && cursor.moveToFirst()) {
            avg = cursor.getDouble(0);
            if (Double.isNaN(avg)) avg = 0.0;
        }
        if (cursor != null) cursor.close();
        db.close();
        return avg;
    }

    public List<Trip> getTripsByPurposeAndDateRange(String purpose, Date startDate, Date endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = COLUMN_START_TIME + " >= ? AND " + COLUMN_START_TIME + " <= ? AND " + COLUMN_PURPOSE + " = ?";
        String[] selectionArgs = {String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime()), purpose};
        List<Trip> trips = new ArrayList<>();
        Cursor cursor = db.query(TABLE_TRIPS, null, selection, selectionArgs, null, null, COLUMN_CREATED_AT + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                trips.add(cursorToTrip(cursor));
            }
            cursor.close();
        }
        db.close();
        return trips;
    }

    public boolean deleteTrip(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = db.delete(TABLE_TRIPS, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
        db.close();
        return rowsAffected > 0;
    }

    public Trip getActiveTrip() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_TRIPS, null, COLUMN_END_TIME + " IS NULL OR " + COLUMN_END_TIME + " = 0", null, null, null, COLUMN_START_TIME + " DESC LIMIT 1");
        Trip trip = null;
        if (cursor != null && cursor.moveToFirst()) {
            trip = cursorToTrip(cursor);
        }
        if (cursor != null) cursor.close();
        db.close();
        return trip;
    }

    public List<Trip> getTripsOrdered(String orderBy) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<Trip> trips = new ArrayList<>();
        Cursor cursor = db.query(TABLE_TRIPS, null, null, null, null, null, orderBy);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                trips.add(cursorToTrip(cursor));
            }
            cursor.close();
        }
        db.close();
        return trips;
    }

    private Trip cursorToTrip(Cursor cursor) {
        Trip trip = new Trip();
        trip.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
        long startTimeMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_START_TIME));
        trip.setStartTime(new Date(startTimeMillis));
        long endTimeMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_END_TIME));
        if (endTimeMillis > 0) {
            trip.setEndTime(new Date(endTimeMillis));
        }
        trip.setStartOdometer(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_START_ODOMETER)));
        trip.setEndOdometer(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_END_ODOMETER)));
        trip.setDistanceMiles(cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_DISTANCE_MILES)));
        trip.setPurpose(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PURPOSE)));
        trip.setWorkTrip(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_WORK_TRIP)) == 1);
        long createdAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
        trip.setCreatedAt(new Date(createdAtMillis));
        return trip;
    }
}
