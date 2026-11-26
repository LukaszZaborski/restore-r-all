package app.morphe.extension.reddit.rall;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Helpers for sending r/all to old.reddit.com, which (unlike the app and new web) still serves it.
 */
@SuppressWarnings("unused")
public final class RAll {

    public static final String OLD_REDDIT_ALL = "https://old.reddit.com/r/all";

    /** Hosts whose /r/all links are redirected to Home by Reddit. old.reddit.com is deliberately absent. */
    private static final Set<String> REDDIT_HOSTS = new HashSet<>(Arrays.asList(
            "reddit.com", "www.reddit.com", "new.reddit.com", "np.reddit.com", "m.reddit.com"
    ));

    private RAll() {
    }

    /**
     * @return the old.reddit.com equivalent of an r/all URL (sort and query are preserved),
     * or null if the URI is not an r/all URL.
     */
    public static Uri toOldReddit(Uri uri) {
        if (uri == null) return null;

        String host = uri.getHost();
        if (host == null || !REDDIT_HOSTS.contains(host.toLowerCase(Locale.ROOT))) return null;

        List<String> segments = uri.getPathSegments();
        if (segments.size() < 2
                || !segments.get(0).equalsIgnoreCase("r")
                || !segments.get(1).equalsIgnoreCase("all")) {
            return null;
        }

        return uri.buildUpon().scheme("https").authority("old.reddit.com").build();
    }

    /**
     * Hook target for link handling: opens the link in the browser if it is an r/all link.
     *
     * @return true if the link was handled and the app should not process it any further.
     */
    public static boolean openIfRAll(Context context, Uri uri) {
        Uri target = toOldReddit(uri);
        if (target == null) return false;
        return open(context, target.toString());
    }

    /**
     * Opens the URL in the default browser. The browser selector is used so that Android does not hand
     * the link straight back to the Reddit app (which claims reddit.com links) and land on Home again.
     */
    public static boolean open(Context context, String url) {
        try {
            Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER);
            intent.setData(Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException ignored) {
            // No default browser registered: fall back to whatever can view the link.
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                return true;
            } catch (ActivityNotFoundException ignoredToo) {
                return false;
            }
        }
    }
}
