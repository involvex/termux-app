package com.invapp.app.utils;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class QrCodeHelperTest {

    @Test
    public void encodeQrBitmap_returnsBitmapForUrl() {
        Bitmap bmp = QrCodeHelper.encodeQrBitmap("http://192.168.1.10:5173/", 256);
        assertNotNull(bmp);
        assertTrue(bmp.getWidth() >= 128);
        assertTrue(bmp.getHeight() >= 128);
    }

    @Test
    public void encodeQrBitmap_rejectsBlank() {
        assertNull(QrCodeHelper.encodeQrBitmap(null, 256));
        assertNull(QrCodeHelper.encodeQrBitmap("   ", 256));
    }
}
