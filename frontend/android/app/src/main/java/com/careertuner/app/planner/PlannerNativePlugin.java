package com.careertuner.app.planner;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.careertuner.app.MainActivity;
import com.careertuner.app.R;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

@CapacitorPlugin(
    name = "PlannerNative",
    permissions = {
        @Permission(strings = { Manifest.permission.POST_NOTIFICATIONS }, alias = "notifications")
    }
)
public class PlannerNativePlugin extends Plugin {
    private static final String REMINDER_SOUND_CHANNEL = "ct_planner_reminder_sound";
    private static final String REMINDER_VIBRATE_CHANNEL = "ct_planner_reminder_vibrate";
    private static final String REMINDER_SOUND_VIBRATE_CHANNEL = "ct_planner_reminder_sound_vibrate";
    private static final String REMINDER_SILENT_CHANNEL = "ct_planner_reminder_silent";

    @PluginMethod
    public void startPlannerResident(PluginCall call) {
        startOrUpdateResident(call, PlannerForegroundService.ACTION_START);
    }

    @PluginMethod
    public void updatePlannerResident(PluginCall call) {
        startOrUpdateResident(call, PlannerForegroundService.ACTION_UPDATE);
    }

    @PluginMethod
    public void stopPlannerResident(PluginCall call) {
        Intent intent = new Intent(getContext(), PlannerForegroundService.class);
        intent.setAction(PlannerForegroundService.ACTION_STOP);
        getContext().startService(intent);
        call.resolve();
    }

    @PluginMethod
    public void notifyPlannerReminder(PluginCall call) {
        String title = call.getString("title", "일정 알림");
        String body = call.getString("body", "");
        boolean soundEnabled = Boolean.TRUE.equals(call.getBoolean("soundEnabled", false));
        boolean vibrationEnabled = Boolean.TRUE.equals(call.getBoolean("vibrationEnabled", false));
        int notificationId = call.getInt("notificationId", (int) (System.currentTimeMillis() % Integer.MAX_VALUE));

        String channelId = reminderChannelId(soundEnabled, vibrationEnabled);
        ensureReminderChannel(channelId, soundEnabled, vibrationEnabled);

        Intent openIntent = new Intent(getContext(), MainActivity.class);
        openIntent.setAction(Intent.ACTION_MAIN);
        openIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            getContext(),
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER);

        if (soundEnabled && vibrationEnabled) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE);
        } else if (soundEnabled) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND);
        } else if (vibrationEnabled) {
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE);
        } else {
            builder.setSilent(true);
        }

        NotificationManager manager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(notificationId, builder.build());
        call.resolve();
    }

    private void startOrUpdateResident(PluginCall call, String action) {
        String title = call.getString("title", "CareerTuner 플래너");
        String body = call.getString("body", "표시 중인 메모와 일정을 유지합니다.");
        Intent intent = new Intent(getContext(), PlannerForegroundService.class);
        intent.setAction(action);
        intent.putExtra(PlannerForegroundService.EXTRA_TITLE, title);
        intent.putExtra(PlannerForegroundService.EXTRA_BODY, body);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(getContext(), intent);
        } else {
            getContext().startService(intent);
        }
        JSObject result = new JSObject();
        result.put("active", true);
        call.resolve(result);
    }

    private String reminderChannelId(boolean soundEnabled, boolean vibrationEnabled) {
        if (soundEnabled && vibrationEnabled) return REMINDER_SOUND_VIBRATE_CHANNEL;
        if (soundEnabled) return REMINDER_SOUND_CHANNEL;
        if (vibrationEnabled) return REMINDER_VIBRATE_CHANNEL;
        return REMINDER_SILENT_CHANNEL;
    }

    private void ensureReminderChannel(String channelId, boolean soundEnabled, boolean vibrationEnabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
            channelId,
            reminderChannelName(soundEnabled, vibrationEnabled),
            NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("플래너 일정 알림");
        channel.enableVibration(vibrationEnabled);
        if (vibrationEnabled) {
            channel.setVibrationPattern(new long[] { 0, 250, 120, 250 });
        }
        if (soundEnabled) {
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            channel.setSound(sound, attrs);
        } else {
            channel.setSound(null, null);
        }
        manager.createNotificationChannel(channel);
    }

    private String reminderChannelName(boolean soundEnabled, boolean vibrationEnabled) {
        if (soundEnabled && vibrationEnabled) return "플래너 알림(소리+진동)";
        if (soundEnabled) return "플래너 알림(소리)";
        if (vibrationEnabled) return "플래너 알림(진동)";
        return "플래너 알림(조용히)";
    }
}
