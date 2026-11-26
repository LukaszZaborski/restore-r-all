package app.morphe.extension.reddit.rall;

import android.app.Activity;
import android.os.Bundle;

/**
 * Invisible activity behind the "r/all" launcher shortcut.
 * Opens old.reddit.com/r/all in the browser and closes itself immediately.
 */
@SuppressWarnings("unused")
public final class RAllActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RAll.open(this, RAll.OLD_REDDIT_ALL);
        finish();
    }
}
