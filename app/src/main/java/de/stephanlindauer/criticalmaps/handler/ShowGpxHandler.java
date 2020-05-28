package de.stephanlindauer.criticalmaps.handler;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.widget.Toast;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.FileNotFoundException;
import java.io.InputStream;

import javax.inject.Inject;

import de.stephanlindauer.criticalmaps.App;
import de.stephanlindauer.criticalmaps.model.gpx.GpxModel;
import de.stephanlindauer.criticalmaps.model.gpx.GpxPoi;
import de.stephanlindauer.criticalmaps.model.gpx.GpxTrack;
import de.stephanlindauer.criticalmaps.prefs.SharedPrefsKeys;
import de.stephanlindauer.criticalmaps.utils.GpxReader;
import info.metadude.android.typedpreferences.BooleanPreference;
import info.metadude.android.typedpreferences.StringPreference;

public class ShowGpxHandler {

    private final SharedPreferences sharedPreferences;
    private final GpxModel gpxModel;
    private final App app;


    @Inject
    public ShowGpxHandler(SharedPreferences sharedPreferences, GpxModel gpxModel, App app) {
        this.sharedPreferences = sharedPreferences;
        this.gpxModel = gpxModel;
        this.app = app;
    }

    public void showGpx(MapView mapView) {
        boolean showTrack = new BooleanPreference(sharedPreferences, SharedPrefsKeys.SHOW_TRACK).get();
        if (!showTrack) {
            return;
        }

        String trackPath = new StringPreference(sharedPreferences, SharedPrefsKeys.TRACK_PATH).get();
        if (gpxModel.getUri() == null || !gpxModel.getUri().equals(trackPath)) {
            readFile(trackPath);
        }

        for (GpxTrack track : gpxModel.getTracks()) {
            addTrackToMap(mapView, track);
        }

        for (GpxPoi poi : gpxModel.getPoiList()) {
            addPoiToMap(mapView, poi);
        }
    }

    private void addTrackToMap(MapView mapView, GpxTrack track) {
        Polyline trackLine = new Polyline(mapView);
        trackLine.setPoints(track.getWaypoints());
        trackLine.setTitle(track.getName());
        trackLine.getOutlinePaint().setColor(Color.RED);
        mapView.getOverlayManager().add(trackLine);
    }

    private void addPoiToMap(MapView mapView, GpxPoi poi) {
        Marker marker = new Marker(mapView);
        marker.setPosition(poi.getPosition());
        marker.setTitle(poi.getName());
        mapView.getOverlayManager().add(marker);
    }

    private void readFile(String trackPath) {
        try {
            InputStream gpxInputStream = app.getContentResolver().openInputStream(Uri.parse(trackPath));
            GpxReader.readTrackFromGpx(gpxInputStream, gpxModel, trackPath);
        } catch (FileNotFoundException | SecurityException e) {
            Toast.makeText(app, e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
}