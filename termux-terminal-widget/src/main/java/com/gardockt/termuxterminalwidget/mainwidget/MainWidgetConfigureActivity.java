package com.gardockt.termuxterminalwidget.mainwidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.gardockt.termuxterminalwidget.ColorPickerDialogInvoker;
import com.gardockt.termuxterminalwidget.ColorScheme;
import com.gardockt.termuxterminalwidget.GlobalPreferences;
import com.gardockt.termuxterminalwidget.GlobalPreferencesUtils;
import com.gardockt.termuxterminalwidget.R;
import com.gardockt.termuxterminalwidget.components.ColorButton;
import com.gardockt.termuxterminalwidget.databinding.MainWidgetConfigureBinding;
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainWidgetConfigureActivity extends AppCompatActivity implements ColorPickerDialogListener {

    private static final String TAG = MainWidgetConfigureActivity.class.getSimpleName();
    private static final int WORK_REFRESH_INTERVAL_MIN_SECS = 15 * 60;  // minimum interval supported by Work API

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private final MainWidgetPreferences defaultPreferences = new MainWidgetPreferences();
    private final GlobalPreferences globalPreferences = GlobalPreferencesUtils.get(this);

    private EditText commandField;
    private SwitchCompat customColorsSwitch;
    private LinearLayout customColorsLayout;
    private ColorButton colorForegroundButton;
    private ColorButton colorBackgroundButton;
    private EditText textSizeField;
    private EditText refreshIntervalSecsField;
    private TextView refreshIntervalHumanReadableText;
    private Button confirmButton;

    private final View.OnClickListener onConfirmButtonClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            MainWidgetPreferences preferences = new MainWidgetPreferences();
            final Context context = MainWidgetConfigureActivity.this;

            // command
            String command = commandField.getText().toString();
            preferences.setCommand(command);

            // color scheme
            if (customColorsSwitch.isChecked()) {
                ColorScheme colorScheme = new ColorScheme(
                        colorForegroundButton.getColor(),
                        colorBackgroundButton.getColor()
                );
                preferences.setColorScheme(colorScheme);
            }

            // text size
            try {
                int textSize = Integer.parseInt(textSizeField.getText().toString());
                if (textSize > 0) {
                    preferences.setTextSizeSp(textSize);
                }
            } catch (NumberFormatException ignored) {}

            // refresh interval
            try {
                int refreshIntervalSecs = Integer.parseInt(refreshIntervalSecsField.getText().toString());
                if (refreshIntervalSecs > 0) {
                    preferences.setRefreshIntervalSecs(refreshIntervalSecs);
                }
            } catch (NumberFormatException ignored) {}

            MainWidget.createWidget(context, widgetId, preferences);

            // Make sure we pass back the original widgetId
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        }
    };
    private MainWidgetConfigureBinding binding;

    public MainWidgetConfigureActivity() {
        super();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED);

        binding = MainWidgetConfigureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        commandField = binding.commandField;

        customColorsLayout = binding.customColorsLayout;
        customColorsSwitch = binding.customColorsSwitch;
        customColorsSwitch.setOnCheckedChangeListener((view, checked) -> {
            customColorsLayout.setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        colorForegroundButton = binding.colorForegroundButton;
        colorForegroundButton.setOnClickListener(
                (view) -> ColorPickerDialogInvoker.showForegroundColorPicker(
                        this,
                        colorForegroundButton.getColor()
                )
        );

        colorBackgroundButton = binding.colorBackgroundButton;
        colorBackgroundButton.setOnClickListener(
                (view) -> ColorPickerDialogInvoker.showBackgroundColorPicker(
                        this,
                        colorBackgroundButton.getColor()
                )
        );

        textSizeField = binding.fieldTextSize;

        refreshIntervalSecsField = binding.fieldRefreshIntervalSecs;
        refreshIntervalHumanReadableText = binding.textRefreshIntervalHumanReadable;
        refreshIntervalSecsField.setHint(Integer.toString(defaultPreferences.getRefreshIntervalSecs()));
        refreshIntervalSecsField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int intervalSecs = parseIntOrDefault(s.toString(), defaultPreferences.getRefreshIntervalSecs());
                    refreshIntervalHumanReadableText.setText(getHumanReadableTime(intervalSecs));
                } catch (NumberFormatException ex) {
                    refreshIntervalHumanReadableText.setText(null);
                }
                checkForErrors();
            }
        });

        confirmButton = binding.confirmButton;
        confirmButton.setOnClickListener(onConfirmButtonClickListener);

        // Find the widget id from the intent.
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            widgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        MainWidgetPreferences preferences = MainWidgetPreferencesManager.load(this, widgetId);
        fillSettings(preferences);
    }

    private void fillSettings(@NonNull MainWidgetPreferences widgetPreferences) {
        // command
        commandField.setText(widgetPreferences.getCommand());

        // color scheme
        ColorScheme colorScheme = widgetPreferences.getColorScheme();
        boolean customColorsEnabled = (colorScheme != null);

        if (!customColorsEnabled) {
            colorScheme = globalPreferences.getColorScheme();
        }

        colorForegroundButton.setColor(colorScheme.getColorForeground());
        colorBackgroundButton.setColor(colorScheme.getColorBackground());

        customColorsSwitch.setChecked(customColorsEnabled);

        // text size
        Integer textSize = widgetPreferences.getTextSizeSp();
        int globalTextSize = globalPreferences.getTextSizeSp();

        if (textSize != null) {
            textSizeField.setText(String.format(Locale.getDefault(), "%d", textSize));
        }
        textSizeField.setHint(String.format(Locale.getDefault(), "%d", globalTextSize));

        // refresh interval
        String refreshIntervalSecsStr = String.format(Locale.getDefault(), "%d", widgetPreferences.getRefreshIntervalSecs());
        refreshIntervalSecsField.setText(refreshIntervalSecsStr);
    }

    private String getHumanReadableTime(int timeSecs) {
        if (timeSecs == 0) {
            return String.format(getString(R.string.fmt_seconds), 0);
        }

        int seconds = timeSecs % 60;
        int minutes = (timeSecs / 60) % 60;
        int hours = timeSecs / 3600;

        List<String> outputParts = new ArrayList<>();
        if (hours > 0) {
            outputParts.add(String.format(getString(R.string.fmt_hours), hours));
        }
        if (minutes > 0) {
            outputParts.add(String.format(getString(R.string.fmt_minutes), minutes));
        }
        if (seconds > 0) {
            outputParts.add(String.format(getString(R.string.fmt_seconds), seconds));
        }

        return String.join(" ", outputParts);
    }

    private void checkForErrors() {
        boolean errorsFound = false;

        {
            String errorInvalidRefreshInterval = getString(R.string.error_invalid_refresh_interval);
            String errorInvalidRefreshIntervalWork = getString(R.string.error_invalid_refresh_interval_work);

            boolean errorFound = false;

            try {
                String refreshIntervalSecsStr = refreshIntervalSecsField.getText().toString();
                int refreshIntervalSecs = parseIntOrDefault(refreshIntervalSecsStr, defaultPreferences.getRefreshIntervalSecs());
                if (refreshIntervalSecs <= 0) {
                    refreshIntervalSecsField.setError(errorInvalidRefreshInterval);
                    errorFound = true;
                } else if (!globalPreferences.isAlarmManagerBackendEnabled() && refreshIntervalSecs < WORK_REFRESH_INTERVAL_MIN_SECS) {
                    refreshIntervalSecsField.setError(errorInvalidRefreshIntervalWork);
                    errorFound = true;
                }
            } catch (NumberFormatException ex) {
                refreshIntervalSecsField.setError(errorInvalidRefreshInterval);
                errorFound = true;
            }

            if (errorFound) {
                errorsFound = true;
            } else {
                refreshIntervalSecsField.setError(null);
            }
        }

        confirmButton.setEnabled(!errorsFound);
    }

    /**
     * @return Return value of {@link Integer#parseInt(String)} if str is a non-empty string. If str
     * is an empty string or null, defaultValue is returned instead.
     * @throws NumberFormatException str is non-empty string, which cannot be parsed.
     */
    private int parseIntOrDefault(@Nullable String str, int defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(str);
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
}
