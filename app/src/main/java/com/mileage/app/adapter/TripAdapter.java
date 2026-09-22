package com.mileage.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.mileage.app.R;
import com.mileage.app.data.Trip;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private List<Trip> trips;
    private OnTripClickListener listener;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;

    public interface OnTripClickListener {
        void onTripClick(Trip trip);
        void onTripDelete(Trip trip);
    }

    public TripAdapter(List<Trip> trips, OnTripClickListener listener) {
        this.trips = trips;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        this.timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);
        holder.bind(trip);
    }

    @Override
    public int getItemCount() {
        return trips == null ? 0 : trips.size();
    }

    public void updateTrips(List<Trip> newTrips) {
        this.trips = newTrips;
        notifyDataSetChanged();
    }

    class TripViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        CardView cardView;
        TextView tvTripDate;
        TextView tvTripTime;
        TextView tvTripPurpose;
        TextView tvTripDistance;
        Chip chipPurpose;
        View ivStatusIndicator;

        public TripViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.root_card);
            tvTripDate = itemView.findViewById(R.id.tv_trip_date);
            tvTripTime = itemView.findViewById(R.id.tv_trip_time);
            tvTripPurpose = itemView.findViewById(R.id.tv_trip_purpose);
            tvTripDistance = itemView.findViewById(R.id.tv_trip_distance);
            chipPurpose = itemView.findViewById(R.id.chip_purpose);
            ivStatusIndicator = itemView.findViewById(R.id.iv_status_indicator);

            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(v -> {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onTripDelete(trips.get(getAdapterPosition()));
                }
                return true;
            });
        }

        public void bind(Trip trip) {
            tvTripDate.setText(dateFormat.format(trip.getStartTime()));

            if (trip.getEndTime() != null) {
                String startTimeStr = timeFormat.format(trip.getStartTime());
                String endTimeStr = timeFormat.format(trip.getEndTime());
                tvTripTime.setText(startTimeStr + " - " + endTimeStr);
            } else {
                tvTripTime.setText(timeFormat.format(trip.getStartTime()) + " - " + trip.getStartTime().toString());
            }

            if (trip.getPurpose() != null && !trip.getPurpose().isEmpty()) {
                tvTripPurpose.setText(trip.getPurpose());
                tvTripPurpose.setVisibility(View.VISIBLE);
            } else {
                tvTripPurpose.setVisibility(View.GONE);
            }

            tvTripDistance.setText(String.format(Locale.getDefault(), "%.1f", trip.getDistanceMiles()));

            if (trip.isWorkTrip()) {
                chipPurpose.setText(R.string.work);
                chipPurpose.setVisibility(View.VISIBLE);
                ivStatusIndicator.setBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.mileage_accent));
            } else {
                chipPurpose.setVisibility(View.GONE);
                ivStatusIndicator.setBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.mileage_trip_completed));
            }

            if (trip.isActive()) {
                tvTripTime.setText(timeFormat.format(trip.getStartTime()) + " - Ongoing");
                ivStatusIndicator.setBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.mileage_trip_active));
            }
        }

        @Override
        public void onClick(View v) {
            if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                listener.onTripClick(trips.get(getAdapterPosition()));
            }
        }

        @Override
        public boolean onLongClick(View v) {
            return true;
        }
    }
}
