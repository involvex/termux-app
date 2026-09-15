package com.invapp.api;

import static com.invapp.shared.termux.TermuxConstants.TERMUX_PACKAGE_NAME;

public class TermuxAPIConstants {

    /**
     * Fully-qualified Termux:API receiver class name.
     */
    public static final String TERMUX_API_RECEIVER_NAME =
            "com.invapp.api.TermuxApiReceiver";

    /** The Uri authority for Termux:API app file shares. */
    public static final String TERMUX_API_FILE_SHARE_URI_AUTHORITY =
            TERMUX_PACKAGE_NAME + ".sharedfiles";
}
