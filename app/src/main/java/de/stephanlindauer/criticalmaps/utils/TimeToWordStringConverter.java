package de.stephanlindauer.criticalmaps.utils;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.stephanlindauer.criticalmaps.R;

public class TimeToWordStringConverter {
    private static final int SECOND_MILLIS = 1000;
    private static final int MINUTE_MILLIS = 60 * SECOND_MILLIS;
    private static final int HOUR_MILLIS = 60 * MINUTE_MILLIS;
    private static final int DAY_MILLIS = 24 * HOUR_MILLIS;

    public static String getTimeAgo(Date date, Context context) {
        long past = date.getTime();
        long now = new Date().getTime();

        final long diff = now - past;
        if (diff < MINUTE_MILLIS) {
            return context.getString(R.string.timetoword_justnow);
        } else if (diff < 2 * MINUTE_MILLIS) {
            return context.getString(R.string.timetoword_aminuteago);
        } else if (diff < 50 * MINUTE_MILLIS) {
            return String.format(context.getString(R.string.timetoword_minutesago), diff / MINUTE_MILLIS);
        } else if (diff < 90 * MINUTE_MILLIS) {
            return context.getString(R.string.timetoword_anhourago);
        } else if (diff < 24 * HOUR_MILLIS) {
            return String.format(context.getString(R.string.timetoword_hoursago), diff / HOUR_MILLIS);
        } else if (diff < 48 * HOUR_MILLIS) {
            return context.getString(R.string.timetoword_yesterday);
        } else {
            return String.format(context.getString(R.string.timetoword_daysago), diff / DAY_MILLIS);
        }
    }

    public static String getTimeAgoShort(Date date, Context context) {
        long past = date.getTime();
        long now = new Date().getTime();

        final long diff = now - past;
        if (diff < 50 * MINUTE_MILLIS) {
            return context.getString(R.string.timetoword_minutesago_short, diff / MINUTE_MILLIS);
        } else if (diff < 24 * HOUR_MILLIS) {
            return String.format(context.getString(R.string.timetoword_hoursago_short), diff / HOUR_MILLIS);
        } else if (diff < 7 * DAY_MILLIS) {
            return context.getString(R.string.timetoword_daysago_short, diff / DAY_MILLIS);
        } else {
            return new SimpleDateFormat("dd. MMM", Locale.US).format(past);
        }
    }
}