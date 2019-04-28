package de.stephanlindauer.criticalmaps.prefs;

import de.stephanlindauer.criticalmaps.BuildConfig;

public interface SharedPrefsKeys {
    String INTRODUCTION_ALREADY_SHOWN =
            BuildConfig.APPLICATION_ID + ".INTRODUCTION_ALREADY_SHOWN";
    String OSMDROID_BASE_PATH =
            BuildConfig.APPLICATION_ID + ".OSMDROID_BASE_PATH";
    String OBSERVER_MODE_ACTIVE =
            BuildConfig.APPLICATION_ID + ".OBSERVER_MODE_ACTIVE";
    String SHOW_ON_LOCKSCREEN =
            BuildConfig.APPLICATION_ID + ".SHOW_ON_LOCKSCREEN";
    String KEEP_SCREEN_ON =
            BuildConfig.APPLICATION_ID + ".KEEP_SCREEN_ON";
    String DISABLE_MAP_ROTATION =
            BuildConfig.APPLICATION_ID + ".DISABLE_MAP_ROTATION";
}
