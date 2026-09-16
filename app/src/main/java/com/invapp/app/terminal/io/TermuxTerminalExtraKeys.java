package com.invapp.app.terminal.io;

import android.annotation.SuppressLint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.invapp.app.TermuxActivity;
import com.invapp.app.terminal.TermuxTerminalSessionActivityClient;
import com.invapp.app.terminal.TermuxTerminalViewClient;
import com.invapp.app.utils.WorkflowHelper;
import com.invapp.shared.logger.Logger;
import com.invapp.shared.termux.extrakeys.ExtraKeysConstants;
import com.invapp.shared.termux.extrakeys.ExtraKeysInfo;
import com.invapp.shared.termux.settings.properties.TermuxPropertyConstants;
import com.invapp.shared.termux.settings.properties.TermuxSharedProperties;
import com.invapp.shared.termux.terminal.io.TerminalExtraKeys;
import com.invapp.view.TerminalView;

import org.json.JSONException;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        setExtraKeys();
    }


    /**
     * Set the terminal extra keys and style.
     */
    public void setExtraKeys() {
        mExtraKeysInfo = null;

        try {
            String extrakeys = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
            String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
                Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" is invalid. Using default style instead.");
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
            }

            mExtraKeysInfo = new ExtraKeysInfo(extrakeys, extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Logger.showToast(mActivity, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: " + e.toString(), true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e);

            try {
                mExtraKeysInfo = new ExtraKeysInfo(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS, TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mActivity, "Could not create default extra keys",true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e);
                mExtraKeysInfo = null;
            }
        }
    }

    /** Re-read properties from disk and reload the extra-keys view + toolbar height. */
    public void reloadFromProperties() {
        mActivity.getProperties().loadTermuxPropertiesFromDisk();
        setExtraKeys();
        if (mActivity.getExtraKeysView() != null && mExtraKeysInfo != null) {
            mActivity.getExtraKeysView().reload(mExtraKeysInfo, mActivity.getTerminalToolbarDefaultHeight());
        }
        mActivity.setTerminalToolbarHeightPublic();
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if ("KEYBOARD".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.closeDrawer(GravityCompat.START);
            else
                drawerLayout.openDrawer(GravityCompat.START);
        } else if ("DRAWER_RIGHT".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(GravityCompat.END))
                drawerLayout.closeDrawer(GravityCompat.END);
            else
                drawerLayout.openDrawer(GravityCompat.END);
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else if ("PULL".equals(key)) {
            mActivity.sendWorkflowCommandFromExtraKeys(WorkflowHelper.CMD_GIT_PULL);
        } else if ("BUNI".equals(key)) {
            mActivity.sendWorkflowCommandFromExtraKeys(WorkflowHelper.CMD_BUN_INSTALL);
        } else if ("DEV".equals(key)) {
            mActivity.sendWorkflowCommandFromExtraKeys(WorkflowHelper.CMD_BUN_RUN_DEV);
        } else if ("REPOS".equals(key)) {
            mActivity.showReposPickerFromExtraKeys();
        } else if ("CLONE".equals(key)) {
            mActivity.showCloneRepoDialogFromExtraKeys();
        } else if ("AI".equals(key)) {
            mActivity.startAiSessionFromExtraKeys();
        } else if ("NEW".equals(key)) {
            mActivity.showNewViteProjectDialogFromExtraKeys();
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

}
