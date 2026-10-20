package com.kantoi.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Pengurus Lencana Notifikasi KANTOI (Launcher Icon Badge Manager)
 * Mengendalikan paparan bilangan panggilan terlepas (missed calls count)
 * pada ikon aplikasi di pelancar Android (Pixel, Samsung One UI, Xiaomi HyperOS,
 * Huawei EMUI, Oppo/Realme ColorOS, Vivo FuntouchOS, Sony Xperia, dsb).
 */
public class KantoiBadgeManager {
    private static final String TAG = "KantoiBadgeManager";
    public static final String BADGE_CHANNEL_ID = "kantoi_badge_channel";
    public static final int BADGE_NOTIFICATION_ID = 888101;

    /**
     * Memastikan Saluran Notifikasi Lencana Disediakan dengan Betul.
     * PENTING: Menurut piawaian Android 8.0+ (Oreo ke atas), saluran MESTI
     * mempunyai sekurang-kurangnya IMPORTANCE_DEFAULT supaya OS memaparkan lencana nombor/titik.
     * IMPORTANCE_LOW atau MIN akan menyembunyikan lencana pelancar secara automatik!
     */
    private static void ensureBadgeChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel existing = nm.getNotificationChannel(BADGE_CHANNEL_ID);
                if (existing == null || existing.getImportance() < NotificationManager.IMPORTANCE_DEFAULT) {
                    NotificationChannel channel = new NotificationChannel(
                        BADGE_CHANNEL_ID,
                        "Lencana Panggilan Terlepas",
                        NotificationManager.IMPORTANCE_DEFAULT // WAJIB untuk lencana ikon launcher
                    );
                    channel.setDescription("Memaparkan nombor lencana panggilan terlepas pada ikon KANTOI");
                    channel.setShowBadge(true); // Membenarkan nombor pada ikon pelancar
                    channel.setSound(null, null); // Senyap agar tidak mengganggu pengguna setiap kali badge dikemaskini
                    channel.enableVibration(false);
                    channel.enableLights(false);
                    nm.createNotificationChannel(channel);
                }
            }
        }
    }

    /**
     * Dapatkan jumlah panggilan terlepas yang belum dilihat dari CallLog Android
     */
    public static int getUnreadMissedCallsCount(Context context) {
        if (context == null) return 0;
        if (context.checkCallingOrSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return 0;
        }

        int count = 0;
        Cursor cursor = null;
        try {
            String selection = CallLog.Calls.TYPE + " = ? AND (" + CallLog.Calls.IS_READ + " = 0 OR " + CallLog.Calls.NEW + " = 1)";
            String[] selectionArgs = new String[] { String.valueOf(CallLog.Calls.MISSED_TYPE) };
            cursor = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[] { CallLog.Calls._ID },
                selection,
                selectionArgs,
                null
            );
            if (cursor != null) {
                count = cursor.getCount();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Ralat mengira panggilan terlepas: " + t.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return count;
    }

    /**
     * Tandakan semua panggilan terlepas sebagai sudah dilihat / dibaca dan bersihkan lencana
     */
    public static void markMissedCallsAsRead(final Context context) {
        if (context == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ContentValues cv = new ContentValues();
                    cv.put(CallLog.Calls.IS_READ, 1);
                    cv.put(CallLog.Calls.NEW, 0);

                    context.getContentResolver().update(
                        CallLog.Calls.CONTENT_URI,
                        cv,
                        CallLog.Calls.TYPE + " = ? AND (" + CallLog.Calls.IS_READ + " = 0 OR " + CallLog.Calls.NEW + " = 1)",
                        new String[] { String.valueOf(CallLog.Calls.MISSED_TYPE) }
                    );
                } catch (Throwable ignored) {}
                updateBadgeWithCount(context, 0);
            }
        }).start();
    }

    /**
     * Kemaskini lencana pada ikon launcher berasaskan bilangan panggilan terlepas semasa (Asynchronous)
     */
    public static void updateBadge(final Context context) {
        if (context == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int count = getUnreadMissedCallsCount(context);
                    updateBadgeWithCount(context, count);
                } catch (Throwable t) {
                    Log.e(TAG, "Ralat updateBadge: " + t.getMessage(), t);
                }
            }
        }).start();
    }

    /**
     * Kemaskini nombor lencana secara langsung pada semua pelancar OEM & Standard Android
     */
    public static void updateBadgeWithCount(Context context, int count) {
        if (context == null) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        ensureBadgeChannel(context);

        if (count <= 0) {
            // Padam notifikasi lencana jika tiada panggilan terlepas
            try {
                nm.cancel(BADGE_NOTIFICATION_ID);
            } catch (Throwable ignored) {}
            sendAllOemBadges(context, 0, null);
            return;
        }

        // Bina notifikasi standard Android 8.0+ dengan setShowBadge dan setNumber
        try {
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mainIntent.putExtra("target_tab", 0); // Buka tab Panggilan

            int pFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pFlags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getActivity(context, 8881, mainIntent, pFlags);

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, BADGE_CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context);
            }

            builder.setSmallIcon(R.drawable.ic_stat_call) // Monochrome silhouette icon rasmi
                .setContentTitle(count + " Panggilan Terlepas")
                .setContentText("Ketik untuk menyemak log panggilan di KANTOI.")
                .setNumber(count) // Inilah fungsi rasmi Android untuk menetapkan nombor lencana pada ikon launcher
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi);

            Notification notif = builder.build();
            nm.notify(BADGE_NOTIFICATION_ID, notif);

            sendAllOemBadges(context, count, notif);
        } catch (Throwable t) {
            Log.e(TAG, "Ralat notify badge: " + t.getMessage(), t);
        }
    }

    /**
     * Menghantar isyarat lencana ke semua antaramuka pelancar OEM (Samsung, Xiaomi, Huawei, Oppo, Vivo, Sony, dsb)
     */
    private static void sendAllOemBadges(Context context, int count, Notification notification) {
        final String pkg = context.getPackageName();
        final String cls = "com.kantoi.app.MainActivity";

        // 1. Samsung One UI / TouchWiz
        try {
            ContentValues cv = new ContentValues();
            cv.put("package", pkg);
            cv.put("class", cls);
            cv.put("badgenumber", count);
            context.getContentResolver().insert(Uri.parse("content://com.sec.badge/apps"), cv);
        } catch (Throwable ignored) {}

        // 2. Xiaomi HyperOS / MIUI
        if (notification != null) {
            try {
                Field field = notification.getClass().getDeclaredField("extraNotification");
                Object extraNotification = field.get(notification);
                Method method = extraNotification.getClass().getDeclaredMethod("setMessageCount", int.class);
                method.invoke(extraNotification, count);
            } catch (Throwable ignored) {}
        }
        try {
            Intent miIntent = new Intent("android.intent.action.APPLICATION_MESSAGE_UPDATE");
            miIntent.putExtra("android.intent.extra.update_application_component_name", pkg + "/" + cls);
            miIntent.putExtra("android.intent.extra.update_application_message_text", String.valueOf(count == 0 ? "" : count));
            context.sendBroadcast(miIntent);
        } catch (Throwable ignored) {}

        // 3. Huawei / Honor (EMUI / MagicOS)
        try {
            Bundle bundle = new Bundle();
            bundle.putString("package", pkg);
            bundle.putString("class", cls);
            bundle.putInt("badgenumber", count);
            context.getContentResolver().call(Uri.parse("content://com.huawei.android.launcher.settings/badge/"), "change_badge", null, bundle);
        } catch (Throwable ignored) {}

        // 4. Oppo / Realme / OnePlus (ColorOS / OxygenOS)
        try {
            Bundle extras = new Bundle();
            extras.putInt("app_badge_count", count);
            context.getContentResolver().call(Uri.parse("content://com.android.badge/badge"), "setAppBadgeCount", null, extras);
        } catch (Throwable ignored) {}

        // 5. Vivo / iQOO (FuntouchOS / OriginOS)
        try {
            Intent vivoIntent = new Intent("launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM");
            vivoIntent.putExtra("packageName", pkg);
            vivoIntent.putExtra("className", cls);
            vivoIntent.putExtra("notificationNum", count);
            context.sendBroadcast(vivoIntent);
        } catch (Throwable ignored) {}

        // 6. Sony Xperia Home
        try {
            Intent sonyIntent = new Intent("com.sonyericsson.home.action.UPDATE_BADGE");
            sonyIntent.putExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME", pkg);
            sonyIntent.putExtra("com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME", cls);
            sonyIntent.putExtra("com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE", count > 0);
            sonyIntent.putExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE", String.valueOf(count));
            context.sendBroadcast(sonyIntent);
        } catch (Throwable ignored) {}

        // 7. HTC
        try {
            Intent htcIntent = new Intent("com.htc.launcher.action.UPDATE_SHORTCUT");
            htcIntent.putExtra("packagename", pkg);
            htcIntent.putExtra("count", count);
            context.sendBroadcast(htcIntent);
        } catch (Throwable ignored) {}

        // 8. Pelancar Pihak Ketiga (Nova, Apex, EverythingMe, SmartLauncher)
        try {
            Intent intent = new Intent("android.intent.action.BADGE_COUNT_UPDATE");
            intent.putExtra("badge_count", count);
            intent.putExtra("badge_count_package_name", pkg);
            intent.putExtra("badge_count_class_name", cls);
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {}

        try {
            Intent apexIntent = new Intent("com.anddoes.launcher.COUNTER_CHANGED");
            apexIntent.putExtra("package", pkg);
            apexIntent.putExtra("count", count);
            apexIntent.putExtra("class", cls);
            context.sendBroadcast(apexIntent);
        } catch (Throwable ignored) {}
    }
}
