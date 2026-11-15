package com.invapp.app.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Encode a short URL / text as a QR {@link Bitmap} (ZXing core only — no camera).
 */
public final class QrCodeHelper {

    private QrCodeHelper() {}

    /**
     * @param content text to encode (e.g. {@code http://192.168.1.2:5173/})
     * @param sizePx  width/height in pixels (clamped to a sane range)
     * @return ARGB_8888 bitmap or {@code null} on failure / blank content
     */
    @Nullable
    public static Bitmap encodeQrBitmap(@Nullable String content, int sizePx) {
        if (content == null) {
            return null;
        }
        String text = content.trim();
        if (text.isEmpty()) {
            return null;
        }
        int size = Math.max(128, Math.min(sizePx, 1024));
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(
                text, BarcodeFormat.QR_CODE, size, size, hints);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                int offset = y * w;
                for (int x = 0; x < w; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }
}
