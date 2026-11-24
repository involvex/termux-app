package com.invapp.app.utils;

import android.app.PendingIntent;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PendingIntentFlagsTest {

    @Test
    public void immutableIncludesFlagOnApi23Plus() {
        int flags = PendingIntentFlags.immutable();
        assertTrue((flags & PendingIntent.FLAG_IMMUTABLE) != 0);
    }

    @Test
    public void mutableUpdateKeepsUpdateCurrent() {
        int flags = PendingIntentFlags.mutableUpdate();
        assertTrue((flags & PendingIntent.FLAG_UPDATE_CURRENT) != 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            assertTrue((flags & PendingIntent.FLAG_IMMUTABLE) != 0);
        }
    }

    @Test
    @Config(sdk = 21)
    public void immutableOnApi21IsZero() {
        assertEquals(0, PendingIntentFlags.immutable());
    }
}
