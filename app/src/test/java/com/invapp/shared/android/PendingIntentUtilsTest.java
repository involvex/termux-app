package com.invapp.shared.android;

import android.app.PendingIntent;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PendingIntentUtilsTest {

    @Test
    public void immutableAddsFlag() {
        int flags = PendingIntentUtils.immutable(PendingIntent.FLAG_UPDATE_CURRENT);
        assertTrue((flags & PendingIntent.FLAG_UPDATE_CURRENT) != 0);
        assertTrue((flags & PendingIntent.FLAG_IMMUTABLE) != 0);
    }

    @Test
    @Config(sdk = 31)
    public void mutableAddsFlagOnS() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        int flags = PendingIntentUtils.mutable(PendingIntent.FLAG_UPDATE_CURRENT);
        assertTrue((flags & PendingIntent.FLAG_MUTABLE) != 0);
    }
}
