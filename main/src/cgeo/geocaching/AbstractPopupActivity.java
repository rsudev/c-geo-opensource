package cgeo.geocaching;

import cgeo.geocaching.activity.AbstractActivity;
import cgeo.geocaching.apps.cache.navi.NavigationAppFactory;
import cgeo.geocaching.enumerations.CacheSize;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.gcvote.GCVote;
import cgeo.geocaching.gcvote.GCVoteRating;
import cgeo.geocaching.geopoint.Geopoint;
import cgeo.geocaching.geopoint.Units;
import cgeo.geocaching.ui.CacheDetailsCreator;
import cgeo.geocaching.ui.LoggingUI;
import cgeo.geocaching.utils.GeoDirHandler;
import cgeo.geocaching.utils.Log;

import org.apache.commons.lang3.StringUtils;

import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public abstract class AbstractPopupActivity extends AbstractActivity {

    private static final int MENU_CACHES_AROUND = 5;
    private static final int MENU_NAVIGATION = 3;
    private static final int MENU_DEFAULT_NAVIGATION = 2;
    private static final int MENU_SHOW_IN_BROWSER = 7;

    protected cgCache cache = null;
    protected String geocode = null;
    protected CacheDetailsCreator details;

    private TextView cacheDistance = null;
    private final int layout;

    private final Handler ratingHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            try {
                details.addRating(cache);
            } catch (Exception e) {
                // nothing
            }
        }
    };

    private final GeoDirHandler geoUpdate = new GeoDirHandler() {

        @Override
        protected void updateGeoData(final IGeoData geo) {
            try {
                if (geo.getCoords() != null && cache != null && cache.getCoords() != null) {
                    cacheDistance.setText(Units.getDistanceFromKilometers(geo.getCoords().distanceTo(cache.getCoords())));
                    cacheDistance.bringToFront();
                }
            } catch (Exception e) {
                Log.w("Failed to UpdateLocation location.");
            }
        }
    };

    protected AbstractPopupActivity(String helpTopic, int layout) {
        super(helpTopic);
        this.layout = layout;
    }

    private void aquireGCVote() {
        if (!Settings.isRatingWanted()) {
            return;
        }
        if (!cache.supportsGCVote()) {
            return;
        }
        (new Thread("Load GCVote") {

            @Override
            public void run() {
                final GCVoteRating rating = GCVote.getRating(cache.getGuid(), geocode);

                if (rating == null) {
                    return;
                }
                cache.setRating(rating.getRating());
                cache.setVotes(rating.getVotes());
                final Message msg = Message.obtain();
                ratingHandler.sendMessage(msg);
            }
        }).start();
    }

    @Override
    public void goManual(View view) {
        super.goManual(view);
        finish();
    }

    protected void init() {
        cache = cgData.loadCache(geocode, LoadFlags.LOAD_CACHE_OR_DB);

        if (cache == null) {
            showToast(res.getString(R.string.err_detail_cache_find));

            finish();
            return;
        }

        geocode = cache.getGeocode();
    }

    private void showInBrowser() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(cache.getUrl())));
    }

    protected abstract void navigateTo();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // set layout
        setContentView(layout);
        setTitle(res.getString(R.string.detail));

        // get parameters
        final Bundle extras = getIntent().getExtras();
        if (extras != null) {
            geocode = extras.getString(Intents.EXTRA_GEOCODE);
        }

        if (StringUtils.isBlank(geocode)) {
            showToast(res.getString(R.string.err_detail_cache_find));

            finish();
            return;
        }

        final ImageView defaultNavigationImageView = (ImageView) findViewById(R.id.defaultNavigation);
        defaultNavigationImageView.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startDefaultNavigation2();
                return true;
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_DEFAULT_NAVIGATION, 0, NavigationAppFactory.getDefaultNavigationApplication().getName()).setIcon(R.drawable.ic_menu_compass); // default navigation tool
        menu.add(0, MENU_NAVIGATION, 0, res.getString(R.string.cache_menu_navigate)).setIcon(R.drawable.ic_menu_mapmode);
        LoggingUI.addMenuItems(menu, cache);
        menu.add(0, MENU_CACHES_AROUND, 0, res.getString(R.string.cache_menu_around)).setIcon(R.drawable.ic_menu_rotate); // caches around
        menu.add(0, MENU_SHOW_IN_BROWSER, 0, res.getString(R.string.cache_menu_browser)).setIcon(R.drawable.ic_menu_info_details); // browser

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int menuItem = item.getItemId();

        switch (menuItem) {
            case MENU_DEFAULT_NAVIGATION:
                navigateTo();
                return true;
            case MENU_NAVIGATION:
                showNavigationMenu();
                return true;
            case MENU_CACHES_AROUND:
                cachesAround();
                return true;
            case MENU_SHOW_IN_BROWSER:
                showInBrowser();
                return true;
            default:
                if (LoggingUI.onMenuItemSelected(item, this, cache)) {
                    return true;
                }
        }

        return true;
    }

    @Override
    public void onPause() {
        geoUpdate.stopGeo();
        super.onPause();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        try {
            final boolean visible = getCoordinates() != null;
            menu.findItem(MENU_DEFAULT_NAVIGATION).setVisible(visible);
            menu.findItem(MENU_NAVIGATION).setVisible(visible);
            menu.findItem(MENU_CACHES_AROUND).setVisible(visible);

            LoggingUI.onPrepareOptionsMenu(menu);
        } catch (Exception e) {
            // nothing
        }

        return true;
    }

    protected abstract Geopoint getCoordinates();

    @Override
    public void onResume() {
        super.onResume();
        init();
        geoUpdate.startGeo();
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            final Rect r = new Rect(0, 0, 0, 0);
            getWindow().getDecorView().getHitRect(r);
            if (!r.contains((int) event.getX(), (int) event.getY())) {
                finish();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    protected abstract void showNavigationMenu();

    protected abstract void startDefaultNavigation2();

    protected final void addCacheDetails() {
        // cache type
        final String cacheType = cache.getType().getL10n();
        final String cacheSize = cache.getSize() != CacheSize.UNKNOWN ? " (" + cache.getSize().getL10n() + ")" : "";
        details.add(R.string.cache_type, cacheType + cacheSize);

        details.add(R.string.cache_geocode, cache.getGeocode());
        details.addCacheState(cache);

        details.addDistance(cache, cacheDistance);
        cacheDistance = details.getValueView();

        details.addDifficulty(cache);
        details.addTerrain(cache);

        // rating
        if (cache.getRating() > 0) {
            details.addRating(cache);
        } else {
            aquireGCVote();
        }

        // favourite count
        details.add(R.string.cache_favourite, cache.getFavoritePoints() + "×");

        // more details
        final Button buttonMore = (Button) findViewById(R.id.more_details);
        buttonMore.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                CacheDetailActivity.startActivity(AbstractPopupActivity.this, geocode);
                finish();
            }
        });
    }

    private void cachesAround() {
        final Geopoint coords = getCoordinates();
        if (coords == null) {
            showToast(res.getString(R.string.err_location_unknown));
            return;
        }
        cgeocaches.startActivityCoordinates(this, coords);
        finish();
    }

    /**
     * @param view
     *            unused here but needed since this method is referenced from XML layout
     */
    public final void goDefaultNavigation(View view) {
        navigateTo();
        finish();
    }

}
