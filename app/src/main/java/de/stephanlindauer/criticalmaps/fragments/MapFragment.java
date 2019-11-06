package de.stephanlindauer.criticalmaps.fragments;

import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import com.squareup.otto.Subscribe;

import de.stephanlindauer.criticalmaps.App;
import de.stephanlindauer.criticalmaps.R;
import de.stephanlindauer.criticalmaps.events.GpsStatusChangedEvent;
import de.stephanlindauer.criticalmaps.events.NetworkConnectivityChangedEvent;
import de.stephanlindauer.criticalmaps.events.NewLocationEvent;
import de.stephanlindauer.criticalmaps.events.NewServerResponseEvent;
import de.stephanlindauer.criticalmaps.managers.LocationUpdateManager;
import de.stephanlindauer.criticalmaps.model.OtherUsersLocationModel;
import de.stephanlindauer.criticalmaps.model.OwnLocationModel;
import de.stephanlindauer.criticalmaps.overlays.LocationMarker;
import de.stephanlindauer.criticalmaps.prefs.SharedPrefsKeys;
import de.stephanlindauer.criticalmaps.provider.EventBus;
import de.stephanlindauer.criticalmaps.utils.AlertBuilder;
import de.stephanlindauer.criticalmaps.utils.MapViewUtils;
import info.metadude.android.typedpreferences.BooleanPreference;

import javax.inject.Inject;

import org.osmdroid.tileprovider.modules.SqlTileWriter;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

public class MapFragment extends Fragment {

    // constants
    private final static String KEY_MAP_ZOOMLEVEL = "map_zoomlevel";
    private final static String KEY_MAP_POSITION = "map_position";
    private final static String KEY_MAP_ORIENTATION = "map_orientation";
    private final static String KEY_INITIAL_LOCATION_SET = "initial_location_set";

    //dependencies
    @Inject
    OwnLocationModel ownLocationModel;

    @Inject
    OtherUsersLocationModel otherUsersLocationModel;

    @Inject
    EventBus eventBus;

    @Inject
    LocationUpdateManager locationUpdateManager;

    @Inject
    SharedPreferences sharedPreferences;

    //view
    private MapView mapView;
    private InfoWindow oberserverInfowWindow;

    @BindView(R.id.set_current_location_center)
    FloatingActionButton setCurrentLocationCenter;

    @BindView(R.id.set_rotation_north)
    FloatingActionButton setRotationNorth;

    @BindView(R.id.map_container)
    FrameLayout mapContainer;

    @BindView(R.id.map_osm_notice)
    TextView osmNoticeOverlay;

    @BindView(R.id.map_no_data_connectivity)
    FloatingActionButton noDataConnectivityButton;

    @BindView(R.id.map_overlay_container)
    RelativeLayout mapOverlayContainer;

    //misc
    private boolean isInitialLocationSet = false;
    private boolean mightComeBackWithLocationPermission = false;
    private ObjectAnimator gpsSearchingAnimator;

    //cache drawables
    private Drawable locationIcon;
    private Drawable ownLocationIcon;
    private Drawable ownLocationIconObserver;

    private Unbinder unbinder;

    //OnClickListeners for location FAB
    private final View.OnClickListener centerLocationOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (ownLocationModel.ownLocation != null)
                animateToLocation(ownLocationModel.ownLocation);
        }
    };

    //OnClickListeners for rotate north FAB
    private final View.OnClickListener rotationNorthOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            float currentRotation = mapView.getMapOrientation() % 360;

            if (currentRotation == 0.0f) {
                // no animation required; also works around bug where map does a full rotation
                // because of mapView wrapping 360° to 0° while View allows 360°
                return;
            }

            if (currentRotation < 0.0f) {
                currentRotation = 360.0f + currentRotation;
                setRotationNorth.setRotation(currentRotation);
                mapView.setMapOrientation(currentRotation);
            }

            float destinationRotation = currentRotation > 180.0f ? 360.0f : 0.0f;
            ViewCompat.animate(setRotationNorth)
                    .rotation(destinationRotation)
                    .setDuration(300L)
                    .setUpdateListener(view -> mapView.setMapOrientation(view.getRotation()))
                    .start();
        }
    };

    private final View.OnClickListener noGpsOnClickListener = v -> AlertBuilder.show(getActivity(),
            R.string.map_no_gps_title,
            R.string.map_no_gps_text);

    private final View.OnClickListener GpsDisabledOnClickListener =
            v -> AlertBuilder.show(getActivity(),
                    R.string.map_gps_disabled_title,
                    R.string.map_gps_disabled_text);

    private final View.OnClickListener GpsNoPermissionsOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            locationUpdateManager.requestPermission();
        }
    };

    private final View.OnClickListener GpsPermissionsPermanentlyDeniedOnClickListener = v ->
            new AlertDialog.Builder(getActivity(), R.style.AlertDialogTheme)
                    .setTitle(R.string.map_gps_permissions_permanently_denied_title)
                    .setMessage(R.string.map_gps_permissions_permanently_denied_text)
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.permissions_open_settings, (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", getActivity().getPackageName(), null));
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);})
                    .create()
                    .show();

    private final View.OnClickListener searchingForLocationOnClickListener = v ->
            Toast.makeText(getActivity(), R.string.map_searching_for_location, Toast.LENGTH_SHORT)
                    .show();

    private final SharedPreferences.OnSharedPreferenceChangeListener observerModeOnSharedPreferenceChangeListener =
            (sharedPreferences, key) -> {
                if (SharedPrefsKeys.OBSERVER_MODE_ACTIVE.equals(key)) {
                    refreshView();
                }
            };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View view = inflater.inflate(R.layout.fragment_map, container, false);
        unbinder = ButterKnife.bind(this, view);

        //noinspection ConstantConditions
        locationIcon = AppCompatResources.getDrawable(getActivity(), R.drawable.ic_map_marker);
        ownLocationIcon = AppCompatResources.getDrawable(
                getActivity(), R.drawable.ic_map_marker_own);
        ownLocationIconObserver = AppCompatResources.getDrawable(
                getActivity(), R.drawable.ic_map_marker_observer);

        return view;
    }

    @Override
    public void onActivityCreated(final Bundle savedState) {
        super.onActivityCreated(savedState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            adjustToWindowsInsets();
        }

        App.components().inject(this);

        osmNoticeOverlay.setMovementMethod(LinkMovementMethod.getInstance());

        mapView = MapViewUtils.createMapView(getActivity());
        mapContainer.addView(mapView);

        oberserverInfowWindow = MapViewUtils.createObserverInfoWindow(mapView);

        setCurrentLocationCenter.setOnClickListener(centerLocationOnClickListener);
        setRotationNorth.setOnClickListener(rotationNorthOnClickListener);

        noDataConnectivityButton.setOnClickListener(v -> AlertBuilder.show(getActivity(),
                R.string.map_no_internet_connection_title,
                R.string.map_no_internet_connection_text));

        if (new BooleanPreference(sharedPreferences, SharedPrefsKeys.DISABLE_MAP_ROTATION).get()) {
            setRotationNorth.setVisibility(View.GONE);
        } else {
            RotationGestureOverlay rotationGestureOverlay = new RotationGestureOverlay(mapView) {
                @Override
                public void onRotate(float deltaAngle) {
                    super.onRotate(deltaAngle);
                    setRotationNorth.setRotation(mapView.getMapOrientation());
                }
            };
            rotationGestureOverlay.setEnabled(true);
            mapView.setMultiTouchControls(true);
            mapView.getOverlays().add(rotationGestureOverlay);
        }

        if (savedState != null) {
            Double zoomLevel = (Double) savedState.get(KEY_MAP_ZOOMLEVEL);
            GeoPoint position = savedState.getParcelable(KEY_MAP_POSITION);
            Float orientation = (Float) savedState.get(KEY_MAP_ORIENTATION);

            if (zoomLevel != null && position != null && orientation != null) {
                mapView.getController().setZoom(zoomLevel);
                if (!new BooleanPreference(sharedPreferences, SharedPrefsKeys.DISABLE_MAP_ROTATION)
                        .get()) {
                    mapView.setMapOrientation(orientation);
                }
                setToLocation(position);
            }

            isInitialLocationSet = savedState.getBoolean(KEY_INITIAL_LOCATION_SET, false);
        }
        setRotationNorth.setRotation(mapView.getMapOrientation());
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void adjustToWindowsInsets() {
        // No-op on < API21
        ViewCompat.setOnApplyWindowInsetsListener(mapOverlayContainer, (v, insets) -> {
            // inset the map overlays for the status bar
            v.setPaddingRelative(
                    v.getPaddingStart(), v.getPaddingTop() + insets.getSystemWindowInsetTop(),
                    v.getPaddingEnd(), v.getPaddingBottom());

            // clear this listener so insets aren't re-applied
            ViewCompat.setOnApplyWindowInsetsListener(mapOverlayContainer, null);
            return insets;
        });

        // without this insets aren't reapplied on fragment changes
        ViewCompat.requestApplyInsets(mapOverlayContainer);
    }

    private void refreshView() {
        for (Overlay overlay : mapView.getOverlays()) {
            if (overlay instanceof LocationMarker) {
                mapView.getOverlays().remove(overlay);
            }
        }

        for (GeoPoint currentOtherUsersLocation : otherUsersLocationModel.getOtherUsersLocations()) {
            LocationMarker otherPeoplesMarker = new LocationMarker(mapView);
            otherPeoplesMarker.setPosition(currentOtherUsersLocation);
            otherPeoplesMarker.setIcon(locationIcon);
            mapView.getOverlays().add(otherPeoplesMarker);
        }

        if (ownLocationModel.ownLocation != null) {
            GeoPoint currentUserLocation = ownLocationModel.ownLocation;
            LocationMarker ownMarker = new LocationMarker(mapView);
            ownMarker.setPosition(currentUserLocation);
            if (new BooleanPreference(
                    sharedPreferences, SharedPrefsKeys.OBSERVER_MODE_ACTIVE).get()) {
                ownMarker.setIcon(ownLocationIconObserver);
                ownMarker.setInfoWindow(oberserverInfowWindow);
                // since we're currently creating new markers on every refresh, this workaround
                // is needed to update the info window's position if it's open
                if (oberserverInfowWindow.isOpen()) {
                    ownMarker.showInfoWindow();
                }
            } else {
                oberserverInfowWindow.close();
                ownMarker.setIcon(ownLocationIcon);
            }
            mapView.getOverlays().add(ownMarker);
        }

        mapView.invalidate();
    }

    @Override
    public void onResume() {
        super.onResume();
        eventBus.register(this);

        // Workaround to handle case when location permission was granted via app ops while the
        // app is running.
        if (mightComeBackWithLocationPermission) {
            // additional check needed because requesting permission will always trigger onResume()
            // even if no dialog is shown. This would send us into an infinite loop
            if (locationUpdateManager.checkPermission()) {
                locationUpdateManager.requestPermission();
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(
                observerModeOnSharedPreferenceChangeListener);
    }

    private void handleFirstLocationUpdate() {
        setGpsStatusFixed();
        zoomToLocation(ownLocationModel.ownLocation, 12.0d);
        isInitialLocationSet = true;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putDouble(KEY_MAP_ZOOMLEVEL, mapView.getZoomLevelDouble());
        outState.putParcelable(KEY_MAP_POSITION, (GeoPoint) mapView.getMapCenter());
        outState.putFloat(KEY_MAP_ORIENTATION, mapView.getMapOrientation());
        outState.putBoolean(KEY_INITIAL_LOCATION_SET, isInitialLocationSet);
    }

    @Override
    public void onPause() {
        super.onPause();
        eventBus.unregister(this);

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(
                observerModeOnSharedPreferenceChangeListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // properly closes the cache db since it's stored in a static field in osmdroid...
        ((SqlTileWriter) mapView.getTileProvider().getTileWriter()).refreshDb();
        mapView = null;
        unbinder.unbind();
    }

    @Subscribe
    public void handleNewServerData(NewServerResponseEvent e) {
        refreshView();
    }

    @Subscribe
    public void handleNewLocation(NewLocationEvent e) {
        // if this is the first location update handle it accordingly
        if (ownLocationModel.ownLocation != null && !isInitialLocationSet) {
            handleFirstLocationUpdate();
        }

        refreshView();
    }

    @Subscribe
    public void handleNetworkConnectivityChanged(NetworkConnectivityChangedEvent e) {
        if (e.isConnected) {
            noDataConnectivityButton.hide();
        } else {
            noDataConnectivityButton.show();
        }
    }

    @Subscribe
    public void handleGpsStatusChangedEvent(GpsStatusChangedEvent e) {
        mightComeBackWithLocationPermission = false;
        if (e.status == GpsStatusChangedEvent.Status.NONEXISTENT) {
            setGpsStatusNonexistent();
        } else if (e.status == GpsStatusChangedEvent.Status.DISABLED) {
            setGpsStatusDisabled();
        } else if (e.status == GpsStatusChangedEvent.Status.PERMISSION_PERMANENTLY_DENIED) {
            mightComeBackWithLocationPermission = true;
            setGpsStatusPermissionsPermanentlyDenied();
        } else if (e.status == GpsStatusChangedEvent.Status.NO_PERMISSIONS) {
            mightComeBackWithLocationPermission = true;
            setGpsStatusNoPermissions();
        } else if (e.status == GpsStatusChangedEvent.Status.LOW_ACCURACY ||
                e.status == GpsStatusChangedEvent.Status.HIGH_ACCURACY) {
            if (ownLocationModel.ownLocation != null) {
                setGpsStatusFixed();
            } else {
                setGpsStatusSearching();
            }
        }
    }

    private void setGpsStatusNonexistent() {
        cancelGpsSearchingAnimationIfRunning();
        setGpsStatusCommon(R.color.map_fab_warning, R.drawable.ic_map_no_gps,
                noGpsOnClickListener);
    }

    private void setGpsStatusDisabled() {
        cancelGpsSearchingAnimationIfRunning();
        setGpsStatusCommon(R.color.map_fab_warning, R.drawable.ic_map_no_gps,
                GpsDisabledOnClickListener);
    }

    private void setGpsStatusNoPermissions() {
        cancelGpsSearchingAnimationIfRunning();
        setGpsStatusCommon(R.color.map_fab_warning, R.drawable.ic_map_no_gps,
                GpsNoPermissionsOnClickListener);
    }

    private void setGpsStatusPermissionsPermanentlyDenied() {
        cancelGpsSearchingAnimationIfRunning();
        setGpsStatusCommon(R.color.map_fab_warning, R.drawable.ic_map_no_gps,
                GpsPermissionsPermanentlyDeniedOnClickListener);
    }

    private void setGpsStatusFixed() {
        cancelGpsSearchingAnimationIfRunning();
        setGpsStatusCommon(R.color.colorSecondary, R.drawable.ic_map_center_location,
                centerLocationOnClickListener);
    }

    private void setGpsStatusSearching() {
        cancelGpsSearchingAnimationIfRunning();
        setGpsStatusCommon(R.color.map_fab_searching, R.drawable.ic_map_gps_not_fixed,
                searchingForLocationOnClickListener);

        gpsSearchingAnimator = (ObjectAnimator) AnimatorInflater.loadAnimator(
                getActivity(),
                R.animator.map_gps_fab_searching_animation);
        gpsSearchingAnimator.setTarget(setCurrentLocationCenter);
        gpsSearchingAnimator.start();
    }

    private void setGpsStatusCommon(@ColorRes int colorResId, @DrawableRes int iconResId,
                                    View.OnClickListener onClickListener) {
        //noinspection ConstantConditions
        setCurrentLocationCenter.setBackgroundTintList(
                ContextCompat.getColorStateList(getActivity(), colorResId));
        setCurrentLocationCenter.setImageResource(iconResId);
        setCurrentLocationCenter.setOnClickListener(onClickListener);
    }

    private void cancelGpsSearchingAnimationIfRunning() {
        if (gpsSearchingAnimator != null) {
            gpsSearchingAnimator.cancel();
            setCurrentLocationCenter.setAlpha(1.0f);
        }
    }

    private void zoomToLocation(final GeoPoint location, final double zoomLevel) {
        // TODO use setCenter() + zoomTo() here; currently broken and ends up in a wrong location
        mapView.getController().setZoom(zoomLevel);
        animateToLocation(location);
    }

    private void animateToLocation(final GeoPoint location) {
        mapView.getController().animateTo(location);
    }

    private void setToLocation(final GeoPoint location) {
        mapView.getController().setCenter(location);
    }
}
