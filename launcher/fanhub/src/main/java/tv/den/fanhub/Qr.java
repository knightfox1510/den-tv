package tv.den.fanhub;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/** A QR code of the phone page's address, black on white with a small quiet zone; null if it can't be made. */
final class Qr {
    private Qr() {}

    static BitmapDrawable of(Resources res, String text) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0);
            int w = m.getWidth(), h = m.getHeight(), quiet = 2;
            Bitmap b = Bitmap.createBitmap(w + 2 * quiet, h + 2 * quiet, Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (m.get(x, y)) b.setPixel(x + quiet, y + quiet, Color.BLACK);
            BitmapDrawable d = new BitmapDrawable(res, b);
            d.setFilterBitmap(false);   // crisp modules when scaled up
            return d;
        } catch (WriterException e) {
            return null;
        }
    }
}
