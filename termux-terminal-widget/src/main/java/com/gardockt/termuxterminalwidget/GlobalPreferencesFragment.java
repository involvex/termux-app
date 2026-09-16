package com.gardockt.termuxterminalwidget;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.gardockt.termuxterminalwidget.components.ColorButton;
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener;

import java.util.Locale;
import java.util.Objects;

public class GlobalPreferencesFragment extends Fragment implements ColorPickerDialogListener {

    private static final String TAG = GlobalPreferencesFragment.class.getSimpleName();

    private ColorButton colorForegroundButton;
    private ColorButton colorBackgroundButton;
    private EditText textSizeField;
    @SuppressLint("UseSwitchCompatOrMaterialCode")  // using SwitchCompat makes no difference on Android 5.0+
    private Switch alarmManagerSwitch;
    private TextView exactAlarmsPermissionText;
    private Button saveButton;

    private BroadcastReceiver scheduleExactAlarmPermissionBroadcastReceiver = null;

    private GlobalPreferences preferences;

    public GlobalPreferencesFragment() {
        super(R.layout.global_configure);
    }

    private void save() {
        GlobalPreferences newPreferences = new GlobalPreferences();
        Context context = requireContext();

        // color scheme
        ColorScheme colorScheme = new ColorScheme(
                colorForegroundButton.getColor(),
                colorBackgroundButton.getColor()
        );
        newPreferences.setColorScheme(colorScheme);

        // text size
        try {
            int textSizeSp = Integer.parseInt(textSizeField.getText().toString());
            newPreferences.setTextSizeSp(textSizeSp);
        } catch (NumberFormatException ignored) {}

        // widget refresh backend
        newPreferences.setAlarmManagerBackendEnabled(alarmManagerSwitch.isChecked());

        GlobalPreferencesUtils.save(context, newPreferences);
        Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show();
    }

    private void load() {
        // color scheme
        prepareColors();

        // text size
        textSizeField.setText(
                String.format(Locale.getDefault(), "%d", preferences.getTextSizeSp())
        );

        // widget refresh backend
        alarmManagerSwitch.setChecked(preferences.isAlarmManagerBackendEnabled());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        preferences = GlobalPreferencesUtils.get(requireContext());

        colorForegroundButton = view.findViewById(R.id.color_foreground_button);
        colorBackgroundButton = view.findViewById(R.id.color_background_button);
        textSizeField = view.findViewById(R.id.field_text_size);
        alarmManagerSwitch = view.findViewById(R.id.alarmmanager_switch);
        exactAlarmsPermissionText = view.findViewById(R.id.exact_alarms_permission_text);
        saveButton = view.findViewById(R.id.save_button);

        colorForegroundButton.setOnClickListener(
                (v) -> ColorPickerDialogInvoker.showForegroundColorPicker(
                        getActivity(),
                        colorForegroundButton.getColor()
                )
        );

        colorBackgroundButton.setOnClickListener(
                (v) -> ColorPickerDialogInvoker.showBackgroundColorPicker(
                        getActivity(),
                        colorBackgroundButton.getColor()
                )
        );

        if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && isOnAlarmListenerScheduleExactAlarmPermissionRequired()
        ) {
            setupScheduleExactAlarmPermissionBroadcastReceiver();
            alarmManagerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updateExactAlarmsPermissionTextVisibility();
            });
        }

        saveButton.setOnClickListener((v) -> save());

        load();
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void setupScheduleExactAlarmPermissionBroadcastReceiver() {
        scheduleExactAlarmPermissionBroadcastReceiver =
                new ScheduleExactAlarmPermissionBroadcastReceiver(this::updateExactAlarmsPermissionTextVisibility);
        IntentFilter intentFilter = new IntentFilter(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Context.RECEIVER_NOT_EXPORTED : 0);
        requireContext().registerReceiver(scheduleExactAlarmPermissionBroadcastReceiver, intentFilter, flags);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void updateExactAlarmsPermissionTextVisibility() {
        boolean visible;
        if (isOnAlarmListenerScheduleExactAlarmPermissionRequired()) {
            AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
            boolean alarmManagerEnabled = alarmManagerSwitch.isChecked();
            boolean permissionGranted = alarmManager.canScheduleExactAlarms();

            visible = (alarmManagerEnabled && !permissionGranted);
        } else {
            visible = false;
        }

        exactAlarmsPermissionText.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * @return Whether <code>SCHEDULE_EXACT_ALARM</code> permission is required for scheduling exact
     * alarms with <code>AlarmManager.OnAlarmListener</code> callback.
     * @see <a href="https://developer.android.com/reference/android/app/AlarmManager#setExact(int,%20long,%20java.lang.String,%20android.app.AlarmManager.OnAlarmListener,%20android.os.Handler)">Android documentation on AlarmManager#setExact(int, long, String, AlarmManager.OnAlarmListener, Handler)</a>
     */
    private boolean isOnAlarmListenerScheduleExactAlarmPermissionRequired() {
        int sdkVersion = Build.VERSION.SDK_INT;
        return sdkVersion >= Build.VERSION_CODES.S && sdkVersion <= Build.VERSION_CODES.TIRAMISU;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        colorForegroundButton = null;
        colorBackgroundButton = null;
        textSizeField = null;
        alarmManagerSwitch = null;
        exactAlarmsPermissionText = null;
        saveButton = null;

        if (scheduleExactAlarmPermissionBroadcastReceiver != null) {
            requireContext().unregisterReceiver(scheduleExactAlarmPermissionBroadcastReceiver);
        }
    }

    private void prepareColors() {
        ColorScheme colorScheme = preferences.getColorScheme();

        colorForegroundButton.setColor(colorScheme.getColorForeground());
        colorBackgroundButton.setColor(colorScheme.getColorBackground());
    }

    @Override
    public void onColorSelected(int dialogId, int color) {
        switch (dialogId) {
            case ColorPickerDialogInvoker.COLOR_PICKER_CALLBACK_FOREGROUND:
                colorForegroundButton.setColor(color);
                break;
            case ColorPickerDialogInvoker.COLOR_PICKER_CALLBACK_BACKGROUND:
                colorBackgroundButton.setColor(color);
                break;
            default:
                Log.e(TAG, "onColorSelected attempted to handle unknown dialog ID: " + dialogId);
        }
    }

    @Override
    public void onDialogDismissed(int dialogId) {
        // intentionally left empty
    }

    private static class ScheduleExactAlarmPermissionBroadcastReceiver extends BroadcastReceiver {
        private final Runnable callback;

        public ScheduleExactAlarmPermissionBroadcastReceiver(@NonNull Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) {
                callback.run();
            }
        }
    }
}
