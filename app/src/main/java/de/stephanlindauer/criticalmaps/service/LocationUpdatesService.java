package de.stephanlindauer.criticalmaps.service;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.osmdroid.util.GeoPoint;

import java.util.Date;

import de.stephanlindauer.criticalmaps.events.Events;
import de.stephanlindauer.criticalmaps.model.OwnLocationModel;
import de.stephanlindauer.criticalmaps.provider.EventBusProvider;
import de.stephanlindauer.criticalmaps.utils.DateUtils;
import de.stephanlindauer.criticalmaps.utils.LocationUtils;

public class LocationUpdatesService {

    //dependencies
    private final OwnLocationModel ownLocationModel = OwnLocationModel.getInstance();
    private final EventBusProvider eventService = EventBusProvider.getInstance();

    //const
    private static final float LOCATION_REFRESH_DISTANCE = 20; //20 meters
    private static final long LOCATION_REFRESH_TIME = 12 * 1000; //12 seconds

    //misc
    private LocationManager locationManager;
    private SharedPreferences sharedPreferences;
    private boolean isRegisteredForLocationUpdates;

    //singleton
    private static LocationUpdatesService instance;

    private LocationUpdatesService() {
    }

    public static LocationUpdatesService getInstance() {
        if (LocationUpdatesService.instance == null) {
            LocationUpdatesService.instance = new LocationUpdatesService();
        }
        return LocationUpdatesService.instance;
    }

    public void initializeAndStartListening(@NonNull Application application) {
        locationManager = (LocationManager) application.getSystemService(Context.LOCATION_SERVICE);
        sharedPreferences = application.getSharedPreferences("Main", Context.MODE_PRIVATE);
        startLocationListening();
    }

    private void startLocationListening() {
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_REFRESH_TIME, LOCATION_REFRESH_DISTANCE, locationListener);
        }

        isRegisteredForLocationUpdates = true;
    }

    public void stopLocationListening() {
        if (!isRegisteredForLocationUpdates) {
            return;
        }

        locationManager.removeUpdates(locationListener);
        isRegisteredForLocationUpdates = false;
    }

    @Nullable
    public GeoPoint getLastKnownLocation() {
        if (sharedPreferences.contains("latitude") && sharedPreferences.contains("longitude") && sharedPreferences.contains("timestamp")) {
            Date timestampLastCoords = new Date(sharedPreferences.getLong("timestamp", 0));
            if (!DateUtils.isLongerAgoThen5Minutes(timestampLastCoords)) {
                return new GeoPoint(
                        Double.parseDouble(sharedPreferences.getString("latitude", "")),
                        Double.parseDouble(sharedPreferences.getString("longitude", "")));
            }
        } else {
            return LocationUtils.getBestLastKnownLocation(locationManager);
        }
        return null;
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            ownLocationModel.ownLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
            eventService.post(Events.NEW_LOCATION_EVENT);
            sharedPreferences.edit()
                    .putString("latitude", String.valueOf(location.getLatitude()))
                    .putString("longitude", String.valueOf(location.getLongitude()))
                    .putLong("timestamp", new Date().getTime())
                    .apply();
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
        }

        @Override
        public void onProviderEnabled(String s) {
        }

        @Override
        public void onProviderDisabled(String s) {
        }
    };
}
