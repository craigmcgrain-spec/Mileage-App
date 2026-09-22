package com.mileage.app.data;

import java.util.Date;

public class Trip {
    private long id;
    private Date startTime;
    private Date endTime;
    private double startOdometer;
    private double endOdometer;
    private double distanceMiles;
    private String purpose;
    private boolean isWorkTrip;
    private Date createdAt;

    public Trip() {
        this.createdAt = new Date();
    }

    public Trip(double startOdometer, double endOdometer, String purpose, boolean isWorkTrip) {
        this();
        this.startOdometer = startOdometer;
        this.endOdometer = endOdometer;
        this.distanceMiles = endOdometer - startOdometer;
        this.purpose = purpose;
        this.isWorkTrip = isWorkTrip;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public double getStartOdometer() {
        return startOdometer;
    }

    public void setStartOdometer(double startOdometer) {
        this.startOdometer = startOdometer;
    }

    public double getEndOdometer() {
        return endOdometer;
    }

    public void setEndOdometer(double endOdometer) {
        this.endOdometer = endOdometer;
    }

    public double getDistanceMiles() {
        return distanceMiles;
    }

    public void setDistanceMiles(double distanceMiles) {
        this.distanceMiles = distanceMiles;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public boolean isWorkTrip() {
        return isWorkTrip;
    }

    public void setWorkTrip(boolean workTrip) {
        isWorkTrip = workTrip;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return endTime == null;
    }

    @Override
    public String toString() {
        return "Trip{" +
                "id=" + id +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", distanceMiles=" + distanceMiles +
                ", purpose='" + purpose + '\'' +
                ", isWorkTrip=" + isWorkTrip +
                '}';
    }
}
