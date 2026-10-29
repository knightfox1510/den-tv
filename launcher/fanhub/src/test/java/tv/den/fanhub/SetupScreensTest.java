package tv.den.fanhub;

import static org.junit.Assume.assumeFalse;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Renders the setup's screens to PNGs (build/setup-screens/); -Pden.screens=1 --tests '*SetupScreensTest'. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-xhdpi")
public class SetupScreensTest {
    @Test public void screens() throws Exception {
        assumeFalse(System.getProperty("den.screens", "").isEmpty());
        File out = new File("build/setup-screens");
        out.mkdirs();
        SetupActivity a = Robolectric.buildActivity(SetupActivity.class).setup().get();
        Field step = SetupActivity.class.getDeclaredField("step");
        step.setAccessible(true);
        Method show = SetupActivity.class.getDeclaredMethod("show");
        show.setAccessible(true);
        String[] names = {"welcome", "wifi", "pair", "phone", "done"};
        for (int i = 0; i < names.length; i++) {
            step.setInt(a, i);
            show.invoke(a);
            View root = a.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1920, 1080);
            Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            try (FileOutputStream f = new FileOutputStream(new File(out, names[i] + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, f); }
        }
    }
}
