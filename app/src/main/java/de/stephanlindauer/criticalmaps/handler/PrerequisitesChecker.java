package de.stephanlindauer.criticalmaps.handler;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;

import javax.inject.Inject;

import de.stephanlindauer.criticalmaps.App;
import de.stephanlindauer.criticalmaps.R;
import de.stephanlindauer.criticalmaps.prefs.SharedPrefsKeys;
import info.metadude.android.typedpreferences.BooleanPreference;

public class PrerequisitesChecker {

    @Inject
    SharedPreferences sharedPreferences;

    private final Activity activity;

    public PrerequisitesChecker(Activity activity) {
        this.activity = activity;
        App.components().inject(this);
    }

    public void execute() {
        showIntroductionIfNotShownBefore();
    }

    private void showIntroductionIfNotShownBefore() {
        final BooleanPreference introductionAlreadyShownPreference = new BooleanPreference(
                sharedPreferences, SharedPrefsKeys.INTRODUCTION_ALREADY_SHOWN);

        if (introductionAlreadyShownPreference.isSet() &&
                introductionAlreadyShownPreference.get()) {
            return;
        }

        LayoutInflater factory = LayoutInflater.from(activity);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        final View view = factory.inflate(R.layout.view_introduction, null);
        builder.setView(view);

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        introductionAlreadyShownPreference.set(true);
                        break;
                }
            }
        };

        builder.setPositiveButton(R.string.ok, dialogClickListener);
        builder.setCancelable(false);
        builder.show();

        return;
    }
}
