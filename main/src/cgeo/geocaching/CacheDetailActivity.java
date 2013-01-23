package cgeo.geocaching;

import cgeo.calendar.ICalendar;
import cgeo.geocaching.activity.AbstractViewPagerActivity;
import cgeo.geocaching.activity.Progress;
import cgeo.geocaching.apps.cache.navi.NavigationAppFactory;
import cgeo.geocaching.compatibility.Compatibility;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.connector.IConnector;
import cgeo.geocaching.connector.gc.GCConnector;
import cgeo.geocaching.enumerations.CacheAttribute;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.LoadFlags.SaveFlag;
import cgeo.geocaching.enumerations.LogType;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.geopoint.GeopointFormatter;
import cgeo.geocaching.geopoint.Units;
import cgeo.geocaching.network.HtmlImage;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.network.Parameters;
import cgeo.geocaching.ui.AbstractCachingPageViewCreator;
import cgeo.geocaching.ui.CacheDetailsCreator;
import cgeo.geocaching.ui.DecryptTextClickListener;
import cgeo.geocaching.ui.EditorDialog;
import cgeo.geocaching.ui.Formatter;
import cgeo.geocaching.ui.ImagesList;
import cgeo.geocaching.ui.ImagesList.ImageType;
import cgeo.geocaching.ui.LoggingUI;
import cgeo.geocaching.utils.BaseUtils;
import cgeo.geocaching.utils.CancellableHandler;
import cgeo.geocaching.utils.ClipboardUtils;
import cgeo.geocaching.utils.CryptUtils;
import cgeo.geocaching.utils.GeoDirHandler;
import cgeo.geocaching.utils.HtmlUtils;
import cgeo.geocaching.utils.ImageHelper;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.MatcherWrapper;
import cgeo.geocaching.utils.TranslationUtils;
import cgeo.geocaching.utils.UnknownTagsHandler;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import android.R.color;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * Activity to handle all single-cache-stuff.
 *
 * e.g. details, description, logs, waypoints, inventory...
 */
public class CacheDetailActivity extends AbstractViewPagerActivity<CacheDetailActivity.Page> {

    private static final int MENU_FIELD_COPY = 1;
    private static final int MENU_FIELD_TRANSLATE = 2;
    private static final int MENU_FIELD_TRANSLATE_EN = 3;
    private static final int MENU_FIELD_SHARE = 4;

    private static final int CONTEXT_MENU_WAYPOINT_EDIT = 1234;
    private static final int CONTEXT_MENU_WAYPOINT_DUPLICATE = 1235;
    private static final int CONTEXT_MENU_WAYPOINT_DELETE = 1236;
    private static final int CONTEXT_MENU_WAYPOINT_NAVIGATE = 1238;
    private static final int CONTEXT_MENU_WAYPOINT_CACHES_AROUND = 1239;
    private static final int CONTEXT_MENU_WAYPOINT_DEFAULT_NAVIGATION = 1240;
    private static final int CONTEXT_MENU_WAYPOINT_RESET_ORIGINAL_CACHE_COORDINATES = 1241;

    private static final Pattern DARK_COLOR_PATTERN = Pattern.compile(Pattern.quote("color=\"#") + "(0[0-9]){3}" + "\"");
    public static final String STATE_PAGE_INDEX = "cgeo.geocaching.pageIndex";

    private cgCache cache;
    private final Progress progress = new Progress();
    private SearchResult search;

    private final GeoDirHandler locationUpdater = new GeoDirHandler() {
        @Override
        public void updateGeoData(final IGeoData geo) {
            if (cacheDistanceView == null) {
                return;
            }

            try {
                final StringBuilder dist = new StringBuilder();

                if (geo.getCoords() != null && cache != null && cache.getCoords() != null) {
                    dist.append(Units.getDistanceFromKilometers(geo.getCoords().distanceTo(cache.getCoords())));
                }

                if (cache != null && cache.getElevation() != null) {
                    if (geo.getAltitude() != 0.0) {
                        final float diff = (float) (cache.getElevation() - geo.getAltitude());
                        dist.append(' ').append(Units.getElevation(diff));
                    }
                }

                cacheDistanceView.setText(dist.toString());
                cacheDistanceView.bringToFront();
            } catch (Exception e) {
                Log.w("Failed to update location.");
            }
        }
    };

    private CharSequence clickedItemText = null;
    private int contextMenuWPIndex = -1;

    /**
     * If another activity is called and can modify the data of this activity, we refresh it on resume.
     */
    private boolean refreshOnResume = false;

    // some views that must be available from everywhere // TODO: Reference can block GC?
    private TextView cacheDistanceView;

    private Handler cacheChangeNotificationHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            notifyDataSetChanged();
        }
    };
    protected ImagesList imagesList;

    public CacheDetailActivity() {
        // identifier for manual
        super("c:geolocation-cache-details");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // initialize the main view and set a default title
        setTheme();
        setContentView(R.layout.cacheview);
        setTitle(res.getString(R.string.cache));

        String geocode = null;

        // TODO Why can it happen that search is not null? onCreate should be called only once and it is not set before.
        if (search != null) {
            cache = search.getFirstCacheFromResult(LoadFlags.LOAD_ALL_DB_ONLY);
            if (cache != null && cache.getGeocode() != null) {
                geocode = cache.getGeocode();
            }
        }

        // get parameters
        final Bundle extras = getIntent().getExtras();
        final Uri uri = getIntent().getData();

        // try to get data from extras
        String name = null;
        String guid = null;
        if (geocode == null && extras != null) {
            geocode = extras.getString("geocode");
            name = extras.getString("name");
            guid = extras.getString("guid");
        }

        // try to get data from URI
        if (geocode == null && guid == null && uri != null) {
            String uriHost = uri.getHost().toLowerCase(Locale.US);
            String uriPath = uri.getPath().toLowerCase(Locale.US);
            String uriQuery = uri.getQuery();

            if (uriQuery != null) {
                Log.i("Opening URI: " + uriHost + uriPath + "?" + uriQuery);
            } else {
                Log.i("Opening URI: " + uriHost + uriPath);
            }

            if (uriHost.contains("geocaching.com")) {
                geocode = uri.getQueryParameter("wp");
                guid = uri.getQueryParameter("guid");

                if (StringUtils.isNotBlank(geocode)) {
                    geocode = geocode.toUpperCase(Locale.US);
                    guid = null;
                } else if (StringUtils.isNotBlank(guid)) {
                    geocode = null;
                    guid = guid.toLowerCase(Locale.US);
                } else {
                    showToast(res.getString(R.string.err_detail_open));
                    finish();
                    return;
                }
            } else if (uriHost.contains("coord.info")) {
                if (uriPath != null && uriPath.startsWith("/gc")) {
                    geocode = uriPath.substring(1).toUpperCase(Locale.US);
                } else {
                    showToast(res.getString(R.string.err_detail_open));
                    finish();
                    return;
                }
            }
        }

        // no given data
        if (geocode == null && guid == null) {
            showToast(res.getString(R.string.err_detail_cache));
            finish();
            return;
        }

        final LoadCacheHandler loadCacheHandler = new LoadCacheHandler();

        try {
            String title = res.getString(R.string.cache);
            if (StringUtils.isNotBlank(name)) {
                title = name;
            } else if (null != geocode && StringUtils.isNotBlank(geocode)) { // can't be null, but the compiler doesn't understand StringUtils.isNotBlank()
                title = geocode;
            }

            progress.show(this, title, res.getString(R.string.cache_dialog_loading_details), true, loadCacheHandler.cancelMessage());
        } catch (Exception e) {
            // nothing, we lost the window
        }

        if (Build.VERSION.SDK_INT<11) {
            // In v11 this is a menu item in the action bar
            ImageView defaultNavigationImageView = (ImageView) findViewById(R.id.defaultNavigation);
            defaultNavigationImageView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    startDefaultNavigation2();
                    return true;
                }
            });
        }

        final int pageToOpen = savedInstanceState != null ?
                savedInstanceState.getInt(STATE_PAGE_INDEX, 0) :
                Settings.isOpenLastDetailsPage() ? Settings.getLastDetailsPage() : 1;
        createViewPager(pageToOpen, new OnPageSelectedListener() {

            @Override
            public void onPageSelected(int position) {
                if (Settings.isOpenLastDetailsPage()) {
                    Settings.setLastDetailsPage(position);
                }
                // lazy loading of cache images
                if (getPage(position) == Page.IMAGES) {
                    loadCacheImages();
                }
            }
        });

        // Initialization done. Let's load the data with the given information.
        new LoadCacheThread(geocode, guid, loadCacheHandler).start();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PAGE_INDEX, getCurrentItem());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (refreshOnResume) {
            notifyDataSetChanged();
            refreshOnResume = false;
        }
        locationUpdater.startGeo();
    }

    @Override
    public void onStop() {
        if (cache != null) {
            cache.setChangeNotificationHandler(null);
        }
        super.onStop();
    }

    @Override
    public void onPause() {
        locationUpdater.stopGeo();
        super.onPause();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info) {
        super.onCreateContextMenu(menu, view, info);
        final int viewId = view.getId();
        contextMenuWPIndex = -1;
        switch (viewId) {
            case R.id.value: // coordinates, gc-code, name
                clickedItemText = ((TextView) view).getText();
                String itemTitle = (String) ((TextView) ((View) view.getParent()).findViewById(R.id.name)).getText();
                buildOptionsContextmenu(menu, viewId, itemTitle, true);
                break;
            case R.id.shortdesc:
                clickedItemText = ((TextView) view).getText();
                buildOptionsContextmenu(menu, viewId, res.getString(R.string.cache_description), false);
                break;
            case R.id.longdesc:
                // combine short and long description
                String shortDesc = cache.getShortDescription();
                if (StringUtils.isBlank(shortDesc)) {
                    clickedItemText = ((TextView) view).getText();
                } else {
                    clickedItemText = shortDesc + "\n\n" + ((TextView) view).getText();
                }
                buildOptionsContextmenu(menu, viewId, res.getString(R.string.cache_description), false);
                break;
            case R.id.personalnote:
                clickedItemText = ((TextView) view).getText();
                buildOptionsContextmenu(menu, viewId, res.getString(R.string.cache_personal_note), true);
                break;
            case R.id.hint:
                clickedItemText = ((TextView) view).getText();
                buildOptionsContextmenu(menu, viewId, res.getString(R.string.cache_hint), false);
                break;
            case R.id.log:
                clickedItemText = ((TextView) view).getText();
                buildOptionsContextmenu(menu, viewId, res.getString(R.string.cache_logs), false);
                break;
            case -1:
                if (null != cache.getWaypoints()) {
                    try {
                        final ViewGroup parent = ((ViewGroup) view.getParent());
                        for (int i = 0; i < parent.getChildCount(); i++) {
                            if (parent.getChildAt(i) == view) {
                                final List<Waypoint> sortedWaypoints = new ArrayList<Waypoint>(cache.getWaypoints());
                                Collections.sort(sortedWaypoints);
                                final Waypoint waypoint = sortedWaypoints.get(i);
                                final int index = cache.getWaypoints().indexOf(waypoint);
                                menu.setHeaderTitle(res.getString(R.string.waypoint));
                                if (waypoint.getWaypointType().equals(WaypointType.ORIGINAL)) {
                                    menu.add(CONTEXT_MENU_WAYPOINT_RESET_ORIGINAL_CACHE_COORDINATES, index, 0, R.string.waypoint_reset_cache_coords);
                                } else {
                                    menu.add(CONTEXT_MENU_WAYPOINT_EDIT, index, 0, R.string.waypoint_edit);
                                    menu.add(CONTEXT_MENU_WAYPOINT_DUPLICATE, index, 0, R.string.waypoint_duplicate);
                                }
                                contextMenuWPIndex = index;
                                if (waypoint.isUserDefined() && !waypoint.getWaypointType().equals(WaypointType.ORIGINAL)) {
                                    menu.add(CONTEXT_MENU_WAYPOINT_DELETE, index, 0, R.string.waypoint_delete);
                                }
                                if (waypoint.getCoords() != null) {
                                    menu.add(CONTEXT_MENU_WAYPOINT_DEFAULT_NAVIGATION, index, 0, NavigationAppFactory.getDefaultNavigationApplication().getName());
                                    menu.add(CONTEXT_MENU_WAYPOINT_NAVIGATE, index, 0, R.string.cache_menu_navigate).setIcon(R.drawable.ic_menu_mapmode);
                                    menu.add(CONTEXT_MENU_WAYPOINT_CACHES_AROUND, index, 0, R.string.cache_menu_around);
                                }
                                break;
                            }
                        }
                    } catch (Exception e) {
                    }
                }
                break;
            default:
                if (imagesList != null) {
                    imagesList.onCreateContextMenu(menu, view);
                }
                break;
        }
    }

    private void buildOptionsContextmenu(ContextMenu menu, int viewId, String fieldTitle, boolean copyOnly) {
        menu.setHeaderTitle(fieldTitle);
        menu.add(viewId, MENU_FIELD_COPY, 0, res.getString(android.R.string.copy));
        if (!copyOnly) {
            if (clickedItemText.length() > TranslationUtils.translationTextLengthToWarn) {
                showToast(res.getString(R.string.translate_length_warning));
            }
            menu.add(viewId, MENU_FIELD_TRANSLATE, 0, res.getString(R.string.translate_to_sys_lang, Locale.getDefault().getDisplayLanguage()));
            if (Settings.isUseEnglish() && !StringUtils.equals(Locale.getDefault().getLanguage(), Locale.ENGLISH.getLanguage())) {
                menu.add(viewId, MENU_FIELD_TRANSLATE_EN, 0, res.getString(R.string.translate_to_english));
            }

        }
        menu.add(viewId, MENU_FIELD_SHARE, 0, res.getString(R.string.cache_share_field));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final int groupId = item.getGroupId();
        final int index = item.getItemId();
        switch (groupId) {
            case R.id.value:
            case R.id.shortdesc:
            case R.id.longdesc:
            case R.id.personalnote:
            case R.id.hint:
            case R.id.log:
                switch (index) {
                    case MENU_FIELD_COPY:
                        ClipboardUtils.copyToClipboard(clickedItemText);
                        showToast(res.getString(R.string.clipboard_copy_ok));
                        return true;
                    case MENU_FIELD_TRANSLATE:
                        TranslationUtils.startActivityTranslate(this, Locale.getDefault().getLanguage(), HtmlUtils.extractText(clickedItemText));
                        return true;
                    case MENU_FIELD_TRANSLATE_EN:
                        TranslationUtils.startActivityTranslate(this, Locale.ENGLISH.getLanguage(), HtmlUtils.extractText(clickedItemText));
                        return true;
                    case MENU_FIELD_SHARE:
                        final Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, clickedItemText.toString());
                        startActivity(Intent.createChooser(intent, res.getText(R.string.cache_share_field)));
                        return true;
                    default:
                        break;
                }

                break;
            case CONTEXT_MENU_WAYPOINT_EDIT:
                final Waypoint waypointEdit = cache.getWaypoint(index);
                if (waypointEdit != null) {
                    EditWaypointActivity.startActivityEditWaypoint(this, waypointEdit.getId());
                    refreshOnResume = true;
                }
                break;
            case CONTEXT_MENU_WAYPOINT_DUPLICATE:
                final Waypoint waypointDuplicate = cache.getWaypoint(index);
                if (cache.duplicateWaypoint(waypointDuplicate)) {
                    cgData.saveCache(cache, EnumSet.of(SaveFlag.SAVE_DB));
                    notifyDataSetChanged();
                }
                break;
            case CONTEXT_MENU_WAYPOINT_DELETE:
                final Waypoint waypointDelete = cache.getWaypoint(index);
                if (cache.deleteWaypoint(waypointDelete)) {
                    cgData.saveCache(cache, EnumSet.of(SaveFlag.SAVE_DB));
                    notifyDataSetChanged();
                }
                break;
            case CONTEXT_MENU_WAYPOINT_DEFAULT_NAVIGATION:
                final Waypoint waypointNavigation = cache.getWaypoint(index);
                if (waypointNavigation != null) {
                    NavigationAppFactory.startDefaultNavigationApplication(1, this, waypointNavigation);
                }
                break;
            case CONTEXT_MENU_WAYPOINT_NAVIGATE:
                final Waypoint waypointNav = cache.getWaypoint(contextMenuWPIndex);
                if (waypointNav != null) {
                    NavigationAppFactory.showNavigationMenu(this, null, waypointNav, null);
                }
                break;
            case CONTEXT_MENU_WAYPOINT_CACHES_AROUND:
                final Waypoint waypointAround = cache.getWaypoint(index);
                if (waypointAround != null) {
                    cgeocaches.startActivityCoordinates(this, waypointAround.getCoords());
                }
                break;

            case CONTEXT_MENU_WAYPOINT_RESET_ORIGINAL_CACHE_COORDINATES:
                new ResetCacheCoordinatesDialog(cache, cache.getWaypoint(index), this).show();
                break;

            default:
                if (imagesList != null && imagesList.onContextItemSelected(item)) {
                    return true;
                }
                return onOptionsItemSelected(item);
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.cache_detail,menu);
        enableV11Actionitems(menu);
        menu.findItem(R.id.menu_default_navigation).setTitle(NavigationAppFactory.getDefaultNavigationApplication().getName());
        if (null != cache) {

            final SubMenu subMenu = menu.findItem(R.id.menu_sub_mapmode).getSubMenu();
            NavigationAppFactory.addMenuItems(subMenu, cache);
        }
        LoggingUI.addMenuItems(menu, cache);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // TODO In v11 a non-offline-stored cache can be null at this point -why?
        if (cache==null) {
            // For whatever reasons, the menu items can't be found
            menu.findItem(R.id.menu_default_navigation).setVisible(false);
            menu.findItem(R.id.menu_calendar).setVisible(false);
            menu.findItem(R.id.menu_caches_around).setVisible(false);
            menu.findItem(R.id.menu_cache_browser).setVisible(false);

        } else {
            menu.findItem(R.id.menu_default_navigation).setVisible(null != cache.getCoords());
            menu.findItem(R.id.menu_calendar).setVisible(cache.canBeAddedToCalendar());
            menu.findItem(R.id.menu_caches_around).setVisible(null != cache.getCoords() && cache.supportsCachesAround());
            menu.findItem(R.id.menu_cache_browser).setVisible(cache.canOpenInBrowser());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int menuItem = item.getItemId();

        switch (menuItem) {
            case 0:
                // no menu selected, but a new sub menu shown
                return false;
            case R.id.menu_default_navigation:
                startDefaultNavigation();
                return true;
            case R.id.menu_cache_browser:
                cache.openInBrowser(this);
                return true;
            case R.id.menu_caches_around:
                cgeocaches.startActivityCoordinates(this, cache.getCoords());
                return true;
            case R.id.menu_calendar:
                addToCalendarWithIntent();
                return true;
            case R.id.menu_share:
                if (cache != null) {
                    cache.shareCache(this, res);
                    return true;
                }
                return false;
            default:
                if (NavigationAppFactory.onMenuItemSelected(item, this, cache)) {
                    return true;
                }
                if (LoggingUI.onMenuItemSelected(item, this, cache)) {
                    refreshOnResume = true;
                    return true;
                }
        }

        return true;
    }

    private class LoadCacheHandler extends CancellableHandler {
        @Override
        public void handleRegularMessage(final Message msg) {
            if (UPDATE_LOAD_PROGRESS_DETAIL == msg.what && msg.obj instanceof String) {
                updateStatusMsg((String) msg.obj);
            } else {
                if (search == null) {
                    showToast(res.getString(R.string.err_dwld_details_failed));

                    progress.dismiss();
                    finish();
                    return;
                }

                if (search.getError() != null) {
                    showToast(res.getString(R.string.err_dwld_details_failed) + " " + search.getError().getErrorString(res) + ".");

                    progress.dismiss();
                    finish();
                    return;
                }

                updateStatusMsg(res.getString(R.string.cache_dialog_loading_details_status_render));

                // Data loaded, we're ready to show it!
                notifyDataSetChanged();
            }
        }

        private void updateStatusMsg(final String msg) {
            progress.setMessage(res.getString(R.string.cache_dialog_loading_details)
                    + "\n\n"
                    + msg);
        }

        @Override
        public void handleCancel(final Object extra) {
            finish();
        }

    }

    private void notifyDataSetChanged() {
        if (search == null) {
            return;
        }

        cache = search.getFirstCacheFromResult(LoadFlags.LOAD_ALL_DB_ONLY);

        if (cache == null) {
            progress.dismiss();
            showToast(res.getString(R.string.err_detail_cache_find_some));
            finish();
            return;
        }

        // allow cache to notify CacheDetailActivity when it changes so it can be reloaded
        cache.setChangeNotificationHandler(cacheChangeNotificationHandler);

        // action bar: title and icon
        if (StringUtils.isNotBlank(cache.getName())) {
            setTitle(cache.getName() + " (" + cache.getGeocode() + ')');
        } else {
            setTitle(cache.getGeocode());
        }
// TODO        ((TextView) findViewById(R.id.actionbar_title)).setCompoundDrawablesWithIntrinsicBounds(getResources().getDrawable(cache.getType().markerId), null, null, null);

        reinitializeViewPager();

        // rendering done! remove progress popup if any there
        invalidateOptionsMenuCompatible();
        progress.dismiss();
    }

    /**
     * Loads the cache with the given geocode or guid.
     */
    private class LoadCacheThread extends Thread {

        private CancellableHandler handler = null;
        private String geocode;
        private String guid;

        public LoadCacheThread(final String geocode, final String guid, final CancellableHandler handlerIn) {
            handler = handlerIn;

            if (StringUtils.isBlank(geocode) && StringUtils.isBlank(guid)) {
                showToast(res.getString(R.string.err_detail_cache_forgot));

                progress.dismiss();
                finish();
                return;
            }

            this.geocode = geocode;
            this.guid = guid;
        }

        @Override
        public void run() {
            search = cgCache.searchByGeocode(geocode, StringUtils.isBlank(geocode) ? guid : null, 0, false, handler);
            handler.sendMessage(Message.obtain());
        }
    }

    /**
     * Indicates whether the specified action can be used as an intent. This
     * method queries the package manager for installed packages that can
     * respond to an intent with the specified action. If no suitable package is
     * found, this method returns false.
     *
     * @param context
     *            The application's environment.
     * @param action
     *            The Intent action to check for availability.
     * @param uri
     *            The Intent URI to check for availability.
     *
     * @return True if an Intent with the specified action can be sent and
     *         responded to, false otherwise.
     */
    private static boolean isIntentAvailable(Context context, String action, Uri uri) {
        final PackageManager packageManager = context.getPackageManager();
        final Intent intent;
        if (uri == null) {
            intent = new Intent(action);
        } else {
            intent = new Intent(action, uri);
        }
        final List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }

    private void addToCalendarWithIntent() {

        final boolean calendarAddOnAvailable = isIntentAvailable(this, ICalendar.INTENT, Uri.parse(ICalendar.URI_SCHEME + "://" + ICalendar.URI_HOST));

        if (calendarAddOnAvailable) {
            final Parameters params = new Parameters(
                    ICalendar.PARAM_NAME, cache.getName(),
                    ICalendar.PARAM_NOTE, StringUtils.defaultString(cache.getPersonalNote()),
                    ICalendar.PARAM_HIDDEN_DATE, String.valueOf(cache.getHiddenDate().getTime()),
                    ICalendar.PARAM_URL, StringUtils.defaultString(cache.getUrl()),
                    ICalendar.PARAM_COORDS, cache.getCoords() == null ? "" : cache.getCoords().format(GeopointFormatter.Format.LAT_LON_DECMINUTE_RAW),
                    ICalendar.PARAM_LOCATION, StringUtils.defaultString(cache.getLocation()),
                    ICalendar.PARAM_SHORT_DESC, StringUtils.defaultString(cache.getShortDescription()),
                    ICalendar.PARAM_START_TIME_MINUTES, StringUtils.defaultString(cache.guessEventTimeMinutes())
                    );

            startActivity(new Intent(ICalendar.INTENT,
                    Uri.parse(ICalendar.URI_SCHEME + "://" + ICalendar.URI_HOST + "?" + params.toString())));
        } else {
            // Inform user the calendar add-on is not installed and let them get it from Google Play
            new AlertDialog.Builder(this)
                    .setTitle(res.getString(R.string.addon_missing_title))
                    .setMessage(new StringBuilder(res.getString(R.string.helper_calendar_missing))
                            .append(' ')
                            .append(res.getString(R.string.addon_download_prompt))
                            .toString())
                    .setPositiveButton(getString(android.R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(ICalendar.CALENDAR_ADDON_URI));
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton(getString(android.R.string.no), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    })
                    .create()
                    .show();
        }
    }

    /**
     * Tries to navigate to the {@link cgCache} of this activity.
     */
    private void startDefaultNavigation() {
        NavigationAppFactory.startDefaultNavigationApplication(1, this, cache);
    }

    /**
     * Tries to navigate to the {@link cgCache} of this activity.
     */
    private void startDefaultNavigation2() {
        NavigationAppFactory.startDefaultNavigationApplication(2, this, cache);
    }

    /**
     * Wrapper for the referenced method in the xml-layout.
     */
    public void startDefaultNavigation(@SuppressWarnings("unused") View view) {
        startDefaultNavigation();
    }

    /**
     * referenced from XML view
     */
    public void showNavigationMenu(@SuppressWarnings("unused") View view) {
        NavigationAppFactory.showNavigationMenu(this, cache, null, null, true, true);
    }

    /**
     * Listener for clicks on username
     */
    private class UserActionsClickListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            if (view == null) {
                return;
            }
            if (!cache.supportsUserActions()) {
                return;
            }

            clickedItemText = ((TextView) view).getText().toString();
            showUserActionsDialog(clickedItemText);
        }
    }

    /**
     * Listener for clicks on owner name
     */
    private class OwnerActionsClickListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            if (view == null) {
                return;
            }
            if (!cache.supportsUserActions()) {
                return;
            }

            // Use real owner name vice the one owner chose to display
            if (StringUtils.isNotBlank(cache.getOwnerUserId())) {
                clickedItemText = cache.getOwnerUserId();
            } else {
                clickedItemText = ((TextView) view).getText().toString();
            }
            showUserActionsDialog(clickedItemText);
        }
    }

    /**
     * Opens a dialog to do actions on an username
     */
    private void showUserActionsDialog(final CharSequence name) {
        final CharSequence[] items = { res.getString(R.string.user_menu_view_hidden),
                res.getString(R.string.user_menu_view_found),
                res.getString(R.string.user_menu_open_browser),
                res.getString(R.string.user_menu_send_message)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(res.getString(R.string.user_menu_title) + " " + name);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                switch (item) {
                    case 0:
                        cgeocaches.startActivityOwner(CacheDetailActivity.this, name.toString());
                        return;
                    case 1:
                        cgeocaches.startActivityUserName(CacheDetailActivity.this, name.toString());
                        return;
                    case 2:
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.geocaching.com/profile/?u=" + Network.encode(name.toString()))));
                        return;
                    case 3:
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.geocaching.com/email/?u=" + Network.encode(name.toString()))));
                        return;
                    default:
                        break;
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void loadCacheImages() {
        if (imagesList != null) {
            return;
        }
        PageViewCreator creator = getViewCreator(Page.IMAGES);
        if (creator == null) {
            return;
        }
        View imageView = creator.getView();
        if (imageView == null) {
            return;
        }
        imagesList = new ImagesList(this, cache.getGeocode());
        imagesList.loadImages(imageView, cache.getImages(), ImageType.AllImages, false);
    }

    public static void startActivity(final Context context, final String geocode) {
        final Intent detailIntent = new Intent(context, CacheDetailActivity.class);
        detailIntent.putExtra("geocode", geocode);
        context.startActivity(detailIntent);
    }

    /**
     * Enum of all possible pages with methods to get the view and a title.
     */
    public enum Page {
        DETAILS(R.string.detail),
        DESCRIPTION(R.string.cache_description),
        LOGS(R.string.cache_logs),
        LOGSFRIENDS(R.string.cache_logsfriends),
        WAYPOINTS(R.string.cache_waypoints),
        INVENTORY(R.string.cache_inventory),
        IMAGES(R.string.cache_images);

        final private int titleStringId;

        Page(final int titleStringId) {
            this.titleStringId = titleStringId;
        }
    }

    private class AttributeViewBuilder {
        private ViewGroup attributeIconsLayout; // layout for attribute icons
        private ViewGroup attributeDescriptionsLayout; // layout for attribute descriptions
        private boolean attributesShowAsIcons = true; // default: show icons
        /**
         * If the cache is from a non GC source, it might be without icons. Disable switching in those cases.
         */
        private boolean noAttributeIconsFound = false;
        private int attributeBoxMaxWidth;

        public void fillView(final LinearLayout attributeBox) {
            // first ensure that the view is empty
            attributeBox.removeAllViews();

            // maximum width for attribute icons is screen width - paddings of parents
            attributeBoxMaxWidth = Compatibility.getDisplayWidth();
            ViewParent child = attributeBox;
            do {
                if (child instanceof View) {
                    attributeBoxMaxWidth -= ((View) child).getPaddingLeft() + ((View) child).getPaddingRight();
                }
                child = child.getParent();
            } while (child != null);

            // delete views holding description / icons
            attributeDescriptionsLayout = null;
            attributeIconsLayout = null;

            attributeBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // toggle between attribute icons and descriptions
                    toggleAttributeDisplay(attributeBox, attributeBoxMaxWidth);
                }
            });

            // icons or text?
            //
            // also show icons when noAttributeImagesFound == true. Explanation:
            //  1. no icons could be found in the first invocation of this method
            //  2. user refreshes cache from web
            //  3. now this method is called again
            //  4. attributeShowAsIcons is false but noAttributeImagesFound is true
            //     => try to show them now
            if (attributesShowAsIcons || noAttributeIconsFound) {
                showAttributeIcons(attributeBox, attributeBoxMaxWidth);
            } else {
                showAttributeDescriptions(attributeBox);
            }
        }

        /**
         * lazy-creates the layout holding the icons of the caches attributes
         * and makes it visible
         */
        private void showAttributeIcons(LinearLayout attribBox, int parentWidth) {
            if (attributeIconsLayout == null) {
                attributeIconsLayout = createAttributeIconsLayout(parentWidth);
                // no matching icons found? show text
                if (noAttributeIconsFound) {
                    showAttributeDescriptions(attribBox);
                    return;
                }
            }
            attribBox.removeAllViews();
            attribBox.addView(attributeIconsLayout);
            attributesShowAsIcons = true;
        }

        /**
         * lazy-creates the layout holding the descriptions of the caches attributes
         * and makes it visible
         */
        private void showAttributeDescriptions(LinearLayout attribBox) {
            if (attributeDescriptionsLayout == null) {
                attributeDescriptionsLayout = createAttributeDescriptionsLayout();
            }
            attribBox.removeAllViews();
            attribBox.addView(attributeDescriptionsLayout);
            attributesShowAsIcons = false;
        }

        /**
         * toggle attribute descriptions and icons
         */
        private void toggleAttributeDisplay(LinearLayout attribBox, int parentWidth) {
            // Don't toggle when there are no icons to show.
            if (noAttributeIconsFound) {
                return;
            }

            // toggle
            if (attributesShowAsIcons) {
                showAttributeDescriptions(attribBox);
            } else {
                showAttributeIcons(attribBox, parentWidth);
            }
        }

        private ViewGroup createAttributeIconsLayout(int parentWidth) {
            final LinearLayout rows = new LinearLayout(CacheDetailActivity.this);
            rows.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            rows.setOrientation(LinearLayout.VERTICAL);

            LinearLayout attributeRow = newAttributeIconsRow();
            rows.addView(attributeRow);

            noAttributeIconsFound = true;

            for (String attributeName : cache.getAttributes()) {
                // check if another attribute icon fits in this row
                attributeRow.measure(0, 0);
                int rowWidth = attributeRow.getMeasuredWidth();
                FrameLayout fl = (FrameLayout) getLayoutInflater().inflate(R.layout.attribute_image, null);
                ImageView iv = (ImageView) fl.getChildAt(0);
                if ((parentWidth - rowWidth) < iv.getLayoutParams().width) {
                    // make a new row
                    attributeRow = newAttributeIconsRow();
                    rows.addView(attributeRow);
                }

                final boolean strikethru = !CacheAttribute.isEnabled(attributeName);
                final CacheAttribute attrib = CacheAttribute.getByGcRawName(CacheAttribute.trimAttributeName(attributeName));
                if (attrib != CacheAttribute.UNKNOWN) {
                    noAttributeIconsFound = false;
                    Drawable d = res.getDrawable(attrib.drawableId);
                    iv.setImageDrawable(d);
                    // strike through?
                    if (strikethru) {
                        // generate strikethru image with same properties as attribute image
                        ImageView strikethruImage = new ImageView(CacheDetailActivity.this);
                        strikethruImage.setLayoutParams(iv.getLayoutParams());
                        d = res.getDrawable(R.drawable.attribute__strikethru);
                        strikethruImage.setImageDrawable(d);
                        fl.addView(strikethruImage);
                    }
                } else {
                    Drawable d = res.getDrawable(R.drawable.attribute_icon_not_found);
                    iv.setImageDrawable(d);
                }

                attributeRow.addView(fl);
            }

            return rows;
        }

        private LinearLayout newAttributeIconsRow() {
            LinearLayout rowLayout = new LinearLayout(CacheDetailActivity.this);
            rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            return rowLayout;
        }

        private ViewGroup createAttributeDescriptionsLayout() {
            final LinearLayout descriptions = (LinearLayout) getLayoutInflater().inflate(
                    R.layout.attribute_descriptions, null);
            final TextView attribView = (TextView) descriptions.getChildAt(0);

            final StringBuilder buffer = new StringBuilder();
            for (String attributeName : cache.getAttributes()) {
                final boolean enabled = CacheAttribute.isEnabled(attributeName);
                // search for a translation of the attribute
                CacheAttribute attrib = CacheAttribute.getByGcRawName(CacheAttribute.trimAttributeName(attributeName));
                if (attrib != CacheAttribute.UNKNOWN) {
                    attributeName = attrib.getL10n(enabled);
                }
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(attributeName);
            }

            attribView.setText(buffer);

            return descriptions;
        }
    }

    /**
     * Creator for details-view.
     */
    private class DetailsViewCreator extends AbstractCachingPageViewCreator<ScrollView> {
        /**
         * Reference to the details list, so that the helper-method can access it without an additional argument
         */
        private LinearLayout detailsList;

        // TODO Do we need this thread-references?
        private StoreCacheThread storeThread;
        private RefreshCacheThread refreshThread;
        private Thread watchlistThread;

        @Override
        public ScrollView getDispatchedView() {
            if (cache == null) {
                // something is really wrong
                return null;
            }

            view = (ScrollView) getLayoutInflater().inflate(R.layout.cacheview_details, null);

            // Start loading preview map
            if (Settings.isStoreOfflineMaps()) {
                new PreviewMapTask().execute((Void) null);
            }

            detailsList = (LinearLayout) view.findViewById(R.id.details_list);
            final CacheDetailsCreator details = new CacheDetailsCreator(CacheDetailActivity.this, detailsList);

            // cache name (full name)
            Spannable span = (new Spannable.Factory()).newSpannable(Html.fromHtml(cache.getName()).toString());
            if (cache.isDisabled() || cache.isArchived()) { // strike
                span.setSpan(new StrikethroughSpan(), 0, span.toString().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (cache.isArchived()) {
                span.setSpan(new ForegroundColorSpan(res.getColor(R.color.archived_cache_color)), 0, span.toString().length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            registerForContextMenu(details.add(R.string.cache_name, span));
            details.add(R.string.cache_type, cache.getType().getL10n());
            details.addSize(cache);
            registerForContextMenu(details.add(R.string.cache_geocode, cache.getGeocode()));
            details.addCacheState(cache);

            details.addDistance(cache, cacheDistanceView);
            cacheDistanceView = details.getValueView();

            details.addDifficulty(cache);
            details.addTerrain(cache);
            details.addRating(cache);

            // favorite count
            if (cache.getFavoritePoints() > 0) {
                details.add(R.string.cache_favourite, cache.getFavoritePoints() + "×");
            }

            // own rating
            if (cache.getMyVote() > 0) {
                details.addStars(R.string.cache_own_rating, cache.getMyVote());
            }

            // cache author
            if (StringUtils.isNotBlank(cache.getOwnerDisplayName()) || StringUtils.isNotBlank(cache.getOwnerUserId())) {
                TextView ownerView = details.add(R.string.cache_owner, "");
                if (StringUtils.isNotBlank(cache.getOwnerDisplayName())) {
                    ownerView.setText(cache.getOwnerDisplayName(), TextView.BufferType.SPANNABLE);
                } else { // OwnerReal guaranteed to be not blank based on above
                    ownerView.setText(cache.getOwnerUserId(), TextView.BufferType.SPANNABLE);
                }
                ownerView.setOnClickListener(new OwnerActionsClickListener());
            }

            // cache hidden
            if (cache.getHiddenDate() != null) {
                long time = cache.getHiddenDate().getTime();
                if (time > 0) {
                    String dateString = Formatter.formatFullDate(time);
                    if (cache.isEventCache()) {
                        dateString = DateUtils.formatDateTime(cgeoapplication.getInstance().getBaseContext(), time, DateUtils.FORMAT_SHOW_WEEKDAY) + ", " + dateString;
                    }
                    details.add(cache.isEventCache() ? R.string.cache_event : R.string.cache_hidden, dateString);
                }
            }

            // cache location
            if (StringUtils.isNotBlank(cache.getLocation())) {
                details.add(R.string.cache_location, cache.getLocation());
            }

            // cache coordinates
            if (cache.getCoords() != null) {
                TextView valueView = details.add(R.string.cache_coordinates, cache.getCoords().toString());
                valueView.setOnClickListener(new View.OnClickListener() {
                    private int position = 0;
                    private GeopointFormatter.Format[] availableFormats = new GeopointFormatter.Format[] {
                            GeopointFormatter.Format.LAT_LON_DECMINUTE,
                            GeopointFormatter.Format.LAT_LON_DECSECOND,
                            GeopointFormatter.Format.LAT_LON_DECDEGREE
                    };

                    // rotate coordinate formats on click
                    @Override
                    public void onClick(View view) {
                        position = (position + 1) % availableFormats.length;

                        final TextView valueView = (TextView) view.findViewById(R.id.value);
                        valueView.setText(cache.getCoords().format(availableFormats[position]));
                    }
                });
                registerForContextMenu(valueView);
            }

            // cache attributes
            if (cache.getAttributes().isNotEmpty()) {
                new AttributeViewBuilder().fillView((LinearLayout) view.findViewById(R.id.attributes_innerbox));
                view.findViewById(R.id.attributes_box).setVisibility(View.VISIBLE);
            }

            updateOfflineBox(view, cache, res, new RefreshCacheClickListener(), new DropCacheClickListener(), new StoreCacheClickListener());

            // watchlist
            Button buttonWatchlistAdd = (Button) view.findViewById(R.id.add_to_watchlist);
            Button buttonWatchlistRemove = (Button) view.findViewById(R.id.remove_from_watchlist);
            buttonWatchlistAdd.setOnClickListener(new AddToWatchlistClickListener());
            buttonWatchlistRemove.setOnClickListener(new RemoveFromWatchlistClickListener());
            updateWatchlistBox();

            // favorite points
            Button buttonFavPointAdd = (Button) view.findViewById(R.id.add_to_favpoint);
            Button buttonFavPointRemove = (Button) view.findViewById(R.id.remove_from_favpoint);
            buttonFavPointAdd.setOnClickListener(new FavoriteAddClickListener());
            buttonFavPointRemove.setOnClickListener(new FavoriteRemoveClickListener());
            updateFavPointBox();

            // data license
            IConnector connector = ConnectorFactory.getConnector(cache);
            if (connector != null) {
                String license = connector.getLicenseText(cache);
                if (StringUtils.isNotBlank(license)) {
                    view.findViewById(R.id.license_box).setVisibility(View.VISIBLE);
                    TextView licenseView = ((TextView) view.findViewById(R.id.license));
                    licenseView.setText(Html.fromHtml(license), BufferType.SPANNABLE);
                    licenseView.setClickable(true);
                    licenseView.setMovementMethod(LinkMovementMethod.getInstance());
                } else {
                    view.findViewById(R.id.license_box).setVisibility(View.GONE);
                }
            }

            return view;
        }

        private class StoreCacheHandler extends CancellableHandler {
            @Override
            public void handleRegularMessage(Message msg) {
                if (UPDATE_LOAD_PROGRESS_DETAIL == msg.what && msg.obj instanceof String) {
                    updateStatusMsg((String) msg.obj);
                } else {
                    storeThread = null;
                    CacheDetailActivity.this.notifyDataSetChanged(); // reload cache details
                }
            }

            private void updateStatusMsg(final String msg) {
                progress.setMessage(res.getString(R.string.cache_dialog_offline_save_message)
                        + "\n\n"
                        + msg);
            }
        }

        private class RefreshCacheHandler extends CancellableHandler {
            @Override
            public void handleRegularMessage(Message msg) {
                if (UPDATE_LOAD_PROGRESS_DETAIL == msg.what && msg.obj instanceof String) {
                    updateStatusMsg((String) msg.obj);
                } else {
                    refreshThread = null;
                    CacheDetailActivity.this.notifyDataSetChanged(); // reload cache details
                }
            }

            private void updateStatusMsg(final String msg) {
                progress.setMessage(res.getString(R.string.cache_dialog_refresh_message)
                        + "\n\n"
                        + msg);
            }
        }

        private class DropCacheHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                CacheDetailActivity.this.notifyDataSetChanged();
            }
        }

        private class StoreCacheClickListener implements View.OnClickListener {
            @Override
            public void onClick(View arg0) {
                if (progress.isShowing()) {
                    showToast(res.getString(R.string.err_detail_still_working));
                    return;
                }

                final StoreCacheHandler storeCacheHandler = new StoreCacheHandler();

                progress.show(CacheDetailActivity.this, res.getString(R.string.cache_dialog_offline_save_title), res.getString(R.string.cache_dialog_offline_save_message), true, storeCacheHandler.cancelMessage());

                if (storeThread != null) {
                    storeThread.interrupt();
                }

                storeThread = new StoreCacheThread(storeCacheHandler);
                storeThread.start();
            }
        }

        private class RefreshCacheClickListener implements View.OnClickListener {
            @Override
            public void onClick(View arg0) {
                if (progress.isShowing()) {
                    showToast(res.getString(R.string.err_detail_still_working));
                    return;
                }

                final RefreshCacheHandler refreshCacheHandler = new RefreshCacheHandler();

                progress.show(CacheDetailActivity.this, res.getString(R.string.cache_dialog_refresh_title), res.getString(R.string.cache_dialog_refresh_message), true, refreshCacheHandler.cancelMessage());

                if (refreshThread != null) {
                    refreshThread.interrupt();
                }

                refreshThread = new RefreshCacheThread(refreshCacheHandler);
                refreshThread.start();
            }
        }

        private class StoreCacheThread extends Thread {
            final private CancellableHandler handler;

            public StoreCacheThread(final CancellableHandler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                cache.store(handler);
            }
        }

        private class RefreshCacheThread extends Thread {
            final private CancellableHandler handler;

            public RefreshCacheThread(final CancellableHandler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                cache.refresh(cache.getListId(), handler);

                handler.sendEmptyMessage(0);
            }
        }

        private class DropCacheClickListener implements View.OnClickListener {
            @Override
            public void onClick(View arg0) {
                if (progress.isShowing()) {
                    showToast(res.getString(R.string.err_detail_still_working));
                    return;
                }

                final DropCacheHandler dropCacheHandler = new DropCacheHandler();

                progress.show(CacheDetailActivity.this, res.getString(R.string.cache_dialog_offline_drop_title), res.getString(R.string.cache_dialog_offline_drop_message), true, null);
                new DropCacheThread(dropCacheHandler).start();
            }
        }

        private class DropCacheThread extends Thread {

            private Handler handler = null;

            public DropCacheThread(Handler handlerIn) {
                handler = handlerIn;
            }

            @Override
            public void run() {
                cache.drop(handler);
            }
        }

        /**
         * Abstract Listener for add / remove buttons for watchlist
         */
        private abstract class AbstractWatchlistClickListener implements View.OnClickListener {
            public void doExecute(int titleId, int messageId, Thread thread) {
                if (progress.isShowing()) {
                    showToast(res.getString(R.string.err_watchlist_still_managing));
                    return;
                }
                progress.show(CacheDetailActivity.this, res.getString(titleId), res.getString(messageId), true, null);

                if (watchlistThread != null) {
                    watchlistThread.interrupt();
                }

                watchlistThread = thread;
                watchlistThread.start();
            }
        }

        /**
         * Listener for "add to watchlist" button
         */
        private class AddToWatchlistClickListener extends AbstractWatchlistClickListener {
            @Override
            public void onClick(View arg0) {
                doExecute(R.string.cache_dialog_watchlist_add_title,
                        R.string.cache_dialog_watchlist_add_message,
                        new WatchlistAddThread(new WatchlistHandler()));
            }
        }

        /**
         * Listener for "remove from watchlist" button
         */
        private class RemoveFromWatchlistClickListener extends AbstractWatchlistClickListener {
            @Override
            public void onClick(View arg0) {
                doExecute(R.string.cache_dialog_watchlist_remove_title,
                        R.string.cache_dialog_watchlist_remove_message,
                        new WatchlistRemoveThread(new WatchlistHandler()));
            }
        }

        /** Thread to add this cache to the watchlist of the user */
        private class WatchlistAddThread extends Thread {
            private final Handler handler;

            public WatchlistAddThread(Handler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.sendEmptyMessage(GCConnector.addToWatchlist(cache) ? 1 : -1);
            }
        }

        /** Thread to remove this cache from the watchlist of the user */
        private class WatchlistRemoveThread extends Thread {
            private final Handler handler;

            public WatchlistRemoveThread(Handler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.sendEmptyMessage(GCConnector.removeFromWatchlist(cache) ? 1 : -1);
            }
        }

        /** Thread to add this cache to the favourite list of the user */
        private class FavoriteAddThread extends Thread {
            private final Handler handler;

            public FavoriteAddThread(Handler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.sendEmptyMessage(GCConnector.addToFavorites(cache) ? 1 : -1);
            }
        }

        /** Thread to remove this cache to the favourite list of the user */
        private class FavoriteRemoveThread extends Thread {
            private final Handler handler;

            public FavoriteRemoveThread(Handler handler) {
                this.handler = handler;
            }

            @Override
            public void run() {
                handler.sendEmptyMessage(GCConnector.removeFromFavorites(cache) ? 1 : -1);
            }
        }

        private class FavoriteUpdateHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                progress.dismiss();
                if (msg.what == -1) {
                    showToast(res.getString(R.string.err_favorite_failed));
                } else {
                    CacheDetailActivity.this.notifyDataSetChanged(); // reload cache details
                }
            }
        }

        /**
         * Listener for "add to favourites" button
         */
        private class FavoriteAddClickListener extends AbstractWatchlistClickListener {
            @Override
            public void onClick(View arg0) {
                doExecute(R.string.cache_dialog_favourite_add_title,
                        R.string.cache_dialog_favourite_add_message,
                        new FavoriteAddThread(new FavoriteUpdateHandler()));
            }
        }

        /**
         * Listener for "remove from favourites" button
         */
        private class FavoriteRemoveClickListener extends AbstractWatchlistClickListener {
            @Override
            public void onClick(View arg0) {
                doExecute(R.string.cache_dialog_favourite_remove_title,
                        R.string.cache_dialog_favourite_remove_message,
                        new FavoriteRemoveThread(new FavoriteUpdateHandler()));
            }
        }

        /**
         * shows/hides buttons, sets text in watchlist box
         */
        private void updateWatchlistBox() {
            LinearLayout layout = (LinearLayout) view.findViewById(R.id.watchlist_box);
            boolean supportsWatchList = cache.supportsWatchList();
            layout.setVisibility(supportsWatchList ? View.VISIBLE : View.GONE);
            if (!supportsWatchList) {
                return;
            }
            Button buttonAdd = (Button) view.findViewById(R.id.add_to_watchlist);
            Button buttonRemove = (Button) view.findViewById(R.id.remove_from_watchlist);
            TextView text = (TextView) view.findViewById(R.id.watchlist_text);

            if (cache.isOnWatchlist() || cache.isOwner()) {
                buttonAdd.setVisibility(View.GONE);
                buttonRemove.setVisibility(View.VISIBLE);
                text.setText(R.string.cache_watchlist_on);
            } else {
                buttonAdd.setVisibility(View.VISIBLE);
                buttonRemove.setVisibility(View.GONE);
                text.setText(R.string.cache_watchlist_not_on);
            }

            // the owner of a cache has it always on his watchlist. Adding causes an error
            if (cache.isOwner()) {
                buttonAdd.setEnabled(false);
                buttonAdd.setVisibility(View.GONE);
                buttonRemove.setEnabled(false);
                buttonRemove.setVisibility(View.GONE);
            }

        }

        /**
         * shows/hides buttons, sets text in watchlist box
         */
        private void updateFavPointBox() {
            LinearLayout layout = (LinearLayout) view.findViewById(R.id.favpoint_box);
            boolean supportsFavoritePoints = cache.supportsFavoritePoints();
            layout.setVisibility(supportsFavoritePoints ? View.VISIBLE : View.GONE);
            if (!supportsFavoritePoints || cache.isOwner() || !Settings.isPremiumMember()) {
                return;
            }
            Button buttonAdd = (Button) view.findViewById(R.id.add_to_favpoint);
            Button buttonRemove = (Button) view.findViewById(R.id.remove_from_favpoint);
            TextView text = (TextView) view.findViewById(R.id.favpoint_text);

            if (cache.isFavorite()) {
                buttonAdd.setVisibility(View.GONE);
                buttonRemove.setVisibility(View.VISIBLE);
                text.setText(R.string.cache_favpoint_on);
            } else {
                buttonAdd.setVisibility(View.VISIBLE);
                buttonRemove.setVisibility(View.GONE);
                text.setText(R.string.cache_favpoint_not_on);
            }

            // Add/remove to Favorites is only possible if the cache has been found
            if (!cache.isFound()) {
                buttonAdd.setEnabled(false);
                buttonAdd.setVisibility(View.GONE);
                buttonRemove.setEnabled(false);
                buttonRemove.setVisibility(View.GONE);
            }
        }

        /**
         * Handler, called when watchlist add or remove is done
         */
        private class WatchlistHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                watchlistThread = null;
                progress.dismiss();
                if (msg.what == -1) {
                    showToast(res.getString(R.string.err_watchlist_failed));
                } else {
                    CacheDetailActivity.this.notifyDataSetChanged(); // reload cache details
                }
            }
        }

        private class PreviewMapTask extends AsyncTask<Void, Void, BitmapDrawable> {
            @Override
            protected BitmapDrawable doInBackground(Void... parameters) {
                try {
                    // persistent preview from storage
                    Bitmap image = decode(cache);

                    if (image == null) {
                        StaticMapsProvider.storeCachePreviewMap(cache);
                        image = decode(cache);
                        if (image == null) {
                            return null;
                        }
                    }

                    return ImageHelper.scaleBitmapToFitDisplay(image);
                } catch (Exception e) {
                    Log.w("CacheDetailActivity.PreviewMapTask", e);
                    return null;
                }
            }

            private Bitmap decode(final cgCache cache) {
                return StaticMapsProvider.getPreviewMap(cache.getGeocode());
            }

            @Override
            protected void onPostExecute(BitmapDrawable image) {
                if (image == null) {
                    return;
                }

                try {
                    final Bitmap bitmap = image.getBitmap();
                    if (bitmap == null || bitmap.getWidth() <= 10) {
                        return;
                    }

                    ((ImageView) view.findViewById(R.id.map_preview)).setImageDrawable(image);
                    view.findViewById(R.id.map_preview_box).setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    Log.e("CacheDetailActivity.PreviewMapTask", e);
                }
            }
        }

    }

    private class DescriptionViewCreator extends AbstractCachingPageViewCreator<ScrollView> {

        @Override
        public ScrollView getDispatchedView() {
            if (cache == null) {
                // something is really wrong
                return null;
            }

            view = (ScrollView) getLayoutInflater().inflate(R.layout.cacheview_description, null);

            // cache short description
            if (StringUtils.isNotBlank(cache.getShortDescription())) {
                new LoadDescriptionTask().execute(cache.getShortDescription(), view.findViewById(R.id.shortdesc), null);
                registerForContextMenu(view.findViewById(R.id.shortdesc));
            }

            // long description
            if (StringUtils.isNotBlank(cache.getDescription())) {
                if (Settings.isAutoLoadDescription()) {
                    loadLongDescription();
                } else {
                    Button showDesc = (Button) view.findViewById(R.id.show_description);
                    showDesc.setVisibility(View.VISIBLE);
                    showDesc.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View arg0) {
                            loadLongDescription();
                        }
                    });
                }
            }

            // cache personal note
            final TextView personalNoteView = (TextView) view.findViewById(R.id.personalnote);
            setPersonalNote(personalNoteView);
            personalNoteView.setMovementMethod(LinkMovementMethod.getInstance());
            registerForContextMenu(personalNoteView);
            final Button personalNoteEdit = (Button) view.findViewById(R.id.edit_personalnote);
            if (cache.isOffline()) {
                personalNoteEdit.setVisibility(View.VISIBLE);
                personalNoteEdit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EditorDialog editor = new EditorDialog(CacheDetailActivity.this, personalNoteView.getText());
                        editor.setOnEditorUpdate(new EditorDialog.EditorUpdate() {
                            @Override
                            public void update(CharSequence editorText) {
                                cache.setPersonalNote(editorText.toString());
                                setPersonalNote(personalNoteView);
                                cgData.saveCache(cache, EnumSet.of(SaveFlag.SAVE_DB));
                            }
                        });
                        editor.show();
                    }
                });
            } else {
                personalNoteEdit.setVisibility(View.INVISIBLE);
            }

            // cache hint and spoiler images
            final View hintBoxView = view.findViewById(R.id.hint_box);
            if (StringUtils.isNotBlank(cache.getHint()) || CollectionUtils.isNotEmpty(cache.getSpoilers())) {
                hintBoxView.setVisibility(View.VISIBLE);
            } else {
                hintBoxView.setVisibility(View.GONE);
            }

            final TextView hintView = ((TextView) view.findViewById(R.id.hint));
            if (StringUtils.isNotBlank(cache.getHint())) {
                if (BaseUtils.containsHtml(cache.getHint())) {
                    hintView.setText(Html.fromHtml(cache.getHint(), new HtmlImage(cache.getGeocode(), false, cache.getListId(), false), null), TextView.BufferType.SPANNABLE);
                    hintView.setText(CryptUtils.rot13((Spannable) hintView.getText()));
                }
                else {
                    hintView.setText(CryptUtils.rot13(cache.getHint()));
                }
                hintView.setVisibility(View.VISIBLE);
                hintView.setClickable(true);
                hintView.setOnClickListener(new DecryptTextClickListener());
                registerForContextMenu(hintView);
            } else {
                hintView.setVisibility(View.GONE);
                hintView.setClickable(false);
                hintView.setOnClickListener(null);
            }

            final TextView spoilerlinkView = ((TextView) view.findViewById(R.id.hint_spoilerlink));
            if (CollectionUtils.isNotEmpty(cache.getSpoilers())) {
                spoilerlinkView.setVisibility(View.VISIBLE);
                spoilerlinkView.setClickable(true);
                spoilerlinkView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        if (cache == null || CollectionUtils.isEmpty(cache.getSpoilers())) {
                            showToast(res.getString(R.string.err_detail_no_spoiler));
                            return;
                        }

                        ImagesActivity.startActivitySpoilerImages(CacheDetailActivity.this, cache.getGeocode(), cache.getSpoilers());
                    }
                });
            } else {
                spoilerlinkView.setVisibility(View.GONE);
                spoilerlinkView.setClickable(true);
                spoilerlinkView.setOnClickListener(null);
            }

            return view;
        }

        private void setPersonalNote(final TextView personalNoteView) {
            final String personalNote = cache.getPersonalNote();
            personalNoteView.setText(personalNote, TextView.BufferType.SPANNABLE);
            if (StringUtils.isNotBlank(personalNote)) {
                personalNoteView.setVisibility(View.VISIBLE);
            }
            else {
                personalNoteView.setVisibility(View.GONE);
            }
        }

        private void loadLongDescription() {
            Button showDesc = (Button) view.findViewById(R.id.show_description);
            showDesc.setVisibility(View.GONE);
            showDesc.setOnClickListener(null);
            view.findViewById(R.id.loading).setVisibility(View.VISIBLE);

            new LoadDescriptionTask().execute(cache.getDescription(), view.findViewById(R.id.longdesc), view.findViewById(R.id.loading));
            registerForContextMenu(view.findViewById(R.id.longdesc));
        }

    }

    /**
     * Loads the description in background. <br />
     * <br />
     * Params:
     * <ol>
     * <li>description string (String)</li>
     * <li>target description view (TextView)</li>
     * <li>loading indicator view (View, may be null)</li>
     * </ol>
     */
    private class LoadDescriptionTask extends AsyncTask<Object, Void, Void> {
        private View loadingIndicatorView;
        private TextView descriptionView;
        private String descriptionString;
        private Spanned description;

        private class HtmlImageCounter implements Html.ImageGetter {

            private int imageCount = 0;

            @Override
            public Drawable getDrawable(String url) {
                imageCount++;
                return null;
            }

            public int getImageCount() {
                return imageCount;
            }
        }

        @Override
        protected Void doInBackground(Object... params) {
            try {
                descriptionString = ((String) params[0]);
                descriptionView = (TextView) params[1];
                loadingIndicatorView = (View) params[2];

                // Fast preview: parse only HTML without loading any images
                HtmlImageCounter imageCounter = new HtmlImageCounter();
                final UnknownTagsHandler unknownTagsHandler = new UnknownTagsHandler();
                description = Html.fromHtml(descriptionString, imageCounter, unknownTagsHandler);
                publishProgress();
                if (imageCounter.getImageCount() > 0) {
                    // Complete view: parse again with loading images - if necessary ! If there are any images causing problems the user can see at least the preview
                    description = Html.fromHtml(descriptionString, new HtmlImage(cache.getGeocode(), true, cache.getListId(), false), unknownTagsHandler);
                    publishProgress();
                }

                // If description has an HTML construct which may be problematic to render, add a note at the end of the long description.
                // Technically, it may not be a table, but a pre, which has the same problems as a table, so the message is ok even though
                // sometimes technically incorrect.
                if (unknownTagsHandler.isProblematicDetected() && descriptionView != null) {
                    final int startPos = description.length();
                    ((Editable) description).append("\n\n").append(res.getString(R.string.cache_description_table_note));
                    ((Editable) description).setSpan(new StyleSpan(Typeface.ITALIC), startPos, description.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    publishProgress();
                }
            } catch (Exception e) {
                Log.e("LoadDescriptionTask: ", e);
            }
            return null;
        }

        /*
         * (non-Javadoc)
         *
         * @see android.os.AsyncTask#onProgressUpdate(Progress[])
         */
        @Override
        protected void onProgressUpdate(Void... values) {
            if (description != null) {
                if (StringUtils.isNotBlank(descriptionString)) {
                    descriptionView.setText(description, TextView.BufferType.SPANNABLE);
                    descriptionView.setMovementMethod(LinkMovementMethod.getInstance());
                    fixBlackTextColor(descriptionView, descriptionString);
                }

                descriptionView.setVisibility(View.VISIBLE);
            } else {
                showToast(res.getString(R.string.err_load_descr_failed));
            }

            if (null != loadingIndicatorView) {
                loadingIndicatorView.setVisibility(View.GONE);
            }
        }

        /**
         * handle caches with black font color
         *
         * @param view
         * @param text
         */
        private void fixBlackTextColor(final TextView view, final String text) {
            if (Settings.isLightSkin()) {
                return;
            }
            int backcolor = color.black;
            if (-1 != StringUtils.indexOfAny(text, new String[] { "color=\"black", "color=\"#000080\"" })) {
                backcolor = color.darker_gray;
            }
            else {
                MatcherWrapper matcher = new MatcherWrapper(DARK_COLOR_PATTERN, text);
                if (matcher.find()) {
                    backcolor = color.darker_gray;
                }
            }
            view.setBackgroundResource(backcolor);
        }
    }

    private class LogsViewCreator extends AbstractCachingPageViewCreator<ListView> {
        private final boolean allLogs;

        LogsViewCreator(boolean allLogs) {
            this.allLogs = allLogs;
        }

        @Override
        public ListView getDispatchedView() {
            if (cache == null) {
                // something is really wrong
                return null;
            }

            view = (ListView) getLayoutInflater().inflate(R.layout.cacheview_logs, null);

            // log count
            final Map<LogType, Integer> logCounts = cache.getLogCounts();
            if (logCounts != null) {
                final List<Entry<LogType, Integer>> sortedLogCounts = new ArrayList<Entry<LogType, Integer>>(logCounts.size());
                for (Entry<LogType, Integer> entry : logCounts.entrySet()) {
                    // it may happen that the label is unknown -> then avoid any output for this type
                    if (entry.getKey() != LogType.PUBLISH_LISTING && entry.getKey().getL10n() != null) {
                        sortedLogCounts.add(entry);
                    }
                }

                if (!sortedLogCounts.isEmpty()) {
                    // sort the log counts by type id ascending. that way the FOUND, DNF log types are the first and most visible ones
                    Collections.sort(sortedLogCounts, new Comparator<Entry<LogType, Integer>>() {

                        @Override
                        public int compare(Entry<LogType, Integer> logCountItem1, Entry<LogType, Integer> logCountItem2) {
                            return logCountItem1.getKey().compareTo(logCountItem2.getKey());
                        }
                    });

                    ArrayList<String> labels = new ArrayList<String>(sortedLogCounts.size());
                    for (Entry<LogType, Integer> pair : sortedLogCounts) {
                        labels.add(pair.getValue() + "× " + pair.getKey().getL10n());
                    }

                    final TextView countView = new TextView(CacheDetailActivity.this);
                    countView.setText(res.getString(R.string.cache_log_types) + ": " + StringUtils.join(labels, ", "));
                    view.addHeaderView(countView, null, false);
                }
            }

            final List<LogEntry> logs = allLogs ? cache.getLogs().asList() : cache.getFriendsLogs();
            view.setAdapter(new ArrayAdapter<LogEntry>(CacheDetailActivity.this, R.layout.cacheview_logs_item, logs) {
                final UserActionsClickListener userActionsClickListener = new UserActionsClickListener();
                final DecryptTextClickListener decryptTextClickListener = new DecryptTextClickListener();

                @Override
                public View getView(final int position, final View convertView, final ViewGroup parent) {
                    View rowView = convertView;
                    if (null == rowView) {
                        rowView = getLayoutInflater().inflate(R.layout.cacheview_logs_item, null);
                    }
                    LogViewHolder holder = (LogViewHolder) rowView.getTag();
                    if (null == holder) {
                        holder = new LogViewHolder(rowView);
                        rowView.setTag(holder);
                    }

                    final LogEntry log = getItem(position);

                    if (log.date > 0) {
                        holder.date.setText(Formatter.formatShortDate(log.date));
                        holder.date.setVisibility(View.VISIBLE);
                    } else {
                        holder.date.setVisibility(View.GONE);
                    }

                    holder.type.setText(log.type.getL10n());
                    holder.author.setText(StringEscapeUtils.unescapeHtml4(log.author));

                    // finds count
                    holder.count.setVisibility(View.VISIBLE);
                    if (log.found == -1) {
                        holder.count.setVisibility(View.GONE);
                    } else {
                        holder.count.setText(res.getQuantityString(R.plurals.cache_counts, log.found, log.found));
                    }

                    // logtext, avoid parsing HTML if not necessary
                    String logText = log.log;
                    if (BaseUtils.containsHtml(logText)) {
                        logText = log.getDisplayText();
                        holder.text.setText(Html.fromHtml(logText, new HtmlImage(cache.getGeocode(), false, cache.getListId(), false), null), TextView.BufferType.SPANNABLE);
                    }
                    else {
                        holder.text.setText(logText);
                    }

                    // images
                    if (log.hasLogImages()) {
                        holder.images.setText(log.getImageTitles());
                        holder.images.setVisibility(View.VISIBLE);
                        holder.images.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ImagesActivity.startActivityLogImages(CacheDetailActivity.this, cache.getGeocode(), new ArrayList<Image>(log.getLogImages()));
                            }
                        });
                    } else {
                        holder.images.setVisibility(View.GONE);
                    }

                    // colored marker
                    int marker = log.type.markerId;
                    if (marker != 0) {
                        holder.statusMarker.setVisibility(View.VISIBLE);
                        holder.statusMarker.setImageResource(marker);
                    }
                    else {
                        holder.statusMarker.setVisibility(View.GONE);
                    }

                    if (null == convertView) {
                        // if convertView != null then this listeners are already set
                        holder.author.setOnClickListener(userActionsClickListener);
                        holder.text.setMovementMethod(LinkMovementMethod.getInstance());
                        holder.text.setOnClickListener(decryptTextClickListener);
                        registerForContextMenu(holder.text);
                    }

                    return rowView;
                }
            });

            return view;
        }

        private class LogViewHolder {
            final TextView date;
            final TextView type;
            final TextView author;
            final TextView count;
            final TextView text;
            final TextView images;
            final ImageView statusMarker;

            public LogViewHolder(View base) {
                date = (TextView) base.findViewById(R.id.added);
                type = (TextView) base.findViewById(R.id.type);
                author = (TextView) base.findViewById(R.id.author);
                count = (TextView) base.findViewById(R.id.count);
                text = (TextView) base.findViewById(R.id.log);
                images = (TextView) base.findViewById(R.id.log_images);
                statusMarker = (ImageView) base.findViewById(R.id.log_mark);
            }
        }
    }

    private class WaypointsViewCreator extends AbstractCachingPageViewCreator<ScrollView> {

        @Override
        public ScrollView getDispatchedView() {
            if (cache == null) {
                // something is really wrong
                return null;
            }

            view = (ScrollView) getLayoutInflater().inflate(R.layout.cacheview_waypoints, null);

            final LinearLayout waypoints = (LinearLayout) view.findViewById(R.id.waypoints);

            // sort waypoints: PP, Sx, FI, OWN
            final List<Waypoint> sortedWaypoints = new ArrayList<Waypoint>(cache.getWaypoints());
            Collections.sort(sortedWaypoints);

            for (final Waypoint wpt : sortedWaypoints) {
                final LinearLayout waypointView = (LinearLayout) getLayoutInflater().inflate(R.layout.waypoint_item, null);

                // coordinates
                if (null != wpt.getCoords()) {
                    final TextView coordinatesView = (TextView) waypointView.findViewById(R.id.coordinates);
                    coordinatesView.setText(wpt.getCoords().toString());
                    coordinatesView.setVisibility(View.VISIBLE);
                }

                // info
                final String waypointInfo = Formatter.formatWaypointInfo(wpt);
                if (StringUtils.isNotBlank(waypointInfo)) {
                    final TextView infoView = (TextView) waypointView.findViewById(R.id.info);
                    infoView.setText(waypointInfo);
                    infoView.setVisibility(View.VISIBLE);
                }

                // title
                final TextView nameView = (TextView) waypointView.findViewById(R.id.name);
                if (StringUtils.isNotBlank(wpt.getName())) {
                    nameView.setText(StringEscapeUtils.unescapeHtml4(wpt.getName()));
                } else if (null != wpt.getCoords()) {
                    nameView.setText(wpt.getCoords().toString());
                } else {
                    nameView.setText(res.getString(R.string.waypoint));
                }
                wpt.setIcon(res, nameView);

                // note
                if (StringUtils.isNotBlank(wpt.getNote())) {
                    final TextView noteView = (TextView) waypointView.findViewById(R.id.note);
                    noteView.setVisibility(View.VISIBLE);
                    if (BaseUtils.containsHtml(wpt.getNote())) {
                        noteView.setText(Html.fromHtml(wpt.getNote()), TextView.BufferType.SPANNABLE);
                    }
                    else {
                        noteView.setText(wpt.getNote());
                    }
                }

                final View wpNavView = waypointView.findViewById(R.id.wpDefaultNavigation);
                wpNavView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        NavigationAppFactory.startDefaultNavigationApplication(1, CacheDetailActivity.this, wpt);
                    }
                });
                wpNavView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        NavigationAppFactory.startDefaultNavigationApplication(2, CacheDetailActivity.this, wpt);
                        return true;
                    }
                });

                registerForContextMenu(waypointView);
                waypointView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openContextMenu(v);
                    }
                });

                waypoints.addView(waypointView);
            }

            final Button addWaypoint = (Button) view.findViewById(R.id.add_waypoint);
            addWaypoint.setClickable(true);
            addWaypoint.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    EditWaypointActivity.startActivityAddWaypoint(CacheDetailActivity.this, cache);
                    refreshOnResume = true;
                }
            });

            return view;
        }
    }

    private class InventoryViewCreator extends AbstractCachingPageViewCreator<ListView> {

        @Override
        public ListView getDispatchedView() {
            if (cache == null) {
                // something is really wrong
                return null;
            }

            view = (ListView) getLayoutInflater().inflate(R.layout.cacheview_inventory, null);

            // TODO: fix layout, then switch back to Android-resource and delete copied one
            // this copy is modified to respect the text color
            view.setAdapter(new ArrayAdapter<Trackable>(CacheDetailActivity.this, R.layout.simple_list_item_1, cache.getInventory()));
            view.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                    Object selection = arg0.getItemAtPosition(arg2);
                    if (selection instanceof Trackable) {
                        Trackable trackable = (Trackable) selection;
                        TrackableActivity.startActivity(CacheDetailActivity.this, trackable.getGuid(), trackable.getGeocode(), trackable.getName());
                    }
                }
            });

            return view;
        }
    }

    private class ImagesViewCreator extends AbstractCachingPageViewCreator<View> {

        @Override
        public View getDispatchedView() {
            if (cache == null) {
                return null; // something is really wrong
            }

            view = getLayoutInflater().inflate(R.layout.caches_images, null);
            if (imagesList == null && isCurrentPage(Page.IMAGES)) {
                loadCacheImages();
            }
            return view;
        }
    }

    public static void startActivity(final Context context, final String geocode, final String cacheName) {
        final Intent cachesIntent = new Intent(context, CacheDetailActivity.class);
        cachesIntent.putExtra("geocode", geocode);
        cachesIntent.putExtra("name", cacheName);
        context.startActivity(cachesIntent);
    }

    public static void startActivityGuid(final Context context, final String guid, final String cacheName) {
        final Intent cacheIntent = new Intent(context, CacheDetailActivity.class);
        cacheIntent.putExtra("guid", guid);
        cacheIntent.putExtra("name", cacheName);
        context.startActivity(cacheIntent);
    }

    /**
     * A dialog to allow the user to select reseting coordinates local/remote/both.
     */
    private class ResetCacheCoordinatesDialog extends AlertDialog {

        final RadioButton resetBoth;
        final RadioButton resetLocal;

        public ResetCacheCoordinatesDialog(final cgCache cache, final Waypoint wpt, final Activity activity) {
            super(activity);

            View layout = activity.getLayoutInflater().inflate(R.layout.reset_cache_coords_dialog, null);
            setView(layout);

            resetLocal = (RadioButton) layout.findViewById(R.id.reset_cache_coordinates_local);
            resetBoth = (RadioButton) layout.findViewById(R.id.reset_cache_coordinates_local_and_remote);

            if (ConnectorFactory.getConnector(cache).supportsOwnCoordinates()) {
                resetBoth.setVisibility(View.VISIBLE);
            }

            layout.findViewById(R.id.reset).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                    final ProgressDialog p = ProgressDialog.show(CacheDetailActivity.this, res.getString(R.string.cache), res.getString(R.string.waypoint_reset), true);
                    final Handler h = new Handler() {
                        private boolean remoteFinished = false;
                        private boolean localFinished = false;

                        @Override
                        public void handleMessage(Message msg) {
                            if (msg.what == ResetCoordsThread.LOCAL) {
                                localFinished = true;
                            } else {
                                remoteFinished = true;
                            }

                            if ((localFinished) && (remoteFinished || !resetBoth.isChecked())) {
                                p.dismiss();
                                notifyDataSetChanged();
                            }
                        }

                    };
                    new ResetCoordsThread(cache, h, wpt, resetLocal.isChecked() || resetBoth.isChecked(), resetBoth.isChecked(), p).start();
                }
            });
        }
    }

    private class ResetCoordsThread extends Thread {

        private final cgCache cache;
        private final Handler handler;
        private final boolean local;
        private final boolean remote;
        private final Waypoint wpt;
        private ProgressDialog progress;
        public static final int LOCAL = 0;
        public static final int ON_WEBSITE = 1;

        public ResetCoordsThread(cgCache cache, Handler handler, final Waypoint wpt, boolean local, boolean remote, final ProgressDialog progress) {
            this.cache = cache;
            this.handler = handler;
            this.local = local;
            this.remote = remote;
            this.wpt = wpt;
            this.progress = progress;
        }

        @Override
        public void run() {

            if (local) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.setMessage(res.getString(R.string.waypoint_reset_cache_coords));
                    }
                });
                cache.setCoords(wpt.getCoords());
                cache.setUserModifiedCoords(false);
                cache.deleteWaypointForce(wpt);
                cgData.saveChangedCache(cache);
                handler.sendEmptyMessage(LOCAL);
            }

            IConnector con = ConnectorFactory.getConnector(cache);
            if (remote && con.supportsOwnCoordinates()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.setMessage(res.getString(R.string.waypoint_coordinates_being_reset_on_website));
                    }
                });

                final boolean result = con.deleteModifiedCoordinates(cache);

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        if (result) {
                            showToast(getString(R.string.waypoint_coordinates_has_been_reset_on_website));
                        } else {
                            showToast(getString(R.string.waypoint_coordinates_upload_error));
                        }
                        handler.sendEmptyMessage(ON_WEBSITE);
                        notifyDataSetChanged();
                    }

                });

            }
        }
    }

    @Override
    protected String getTitle(Page page) {
        // show number of waypoints directly in waypoint title
        if (page == Page.WAYPOINTS) {
            final int waypointCount = cache.getWaypoints().size();
            return res.getQuantityString(R.plurals.waypoints, waypointCount, waypointCount);
        }
        return res.getString(page.titleStringId);
    }

    @Override
    protected Pair<List<? extends Page>, Integer> getOrderedPages() {
        final ArrayList<Page> pages = new ArrayList<Page>();
        pages.add(Page.WAYPOINTS);
        pages.add(Page.DETAILS);
        final int detailsIndex = pages.size() - 1;
        pages.add(Page.DESCRIPTION);
        if (cache.getLogs().isNotEmpty()) {
            pages.add(Page.LOGS);
        }
        if (CollectionUtils.isNotEmpty(cache.getFriendsLogs())) {
            pages.add(Page.LOGSFRIENDS);
        }
        if (CollectionUtils.isNotEmpty(cache.getInventory())) {
            pages.add(Page.INVENTORY);
        }
        if (CollectionUtils.isNotEmpty(cache.getImages())) {
            pages.add(Page.IMAGES);
        }
        return new ImmutablePair<List<? extends Page>, Integer>(pages, detailsIndex);
    }

    @Override
    protected AbstractViewPagerActivity.PageViewCreator createViewCreator(Page page) {
        switch (page) {
            case DETAILS:
                return new DetailsViewCreator();

            case DESCRIPTION:
                return new DescriptionViewCreator();

            case LOGS:
                return new LogsViewCreator(true);

            case LOGSFRIENDS:
                return new LogsViewCreator(false);

            case WAYPOINTS:
                return new WaypointsViewCreator();

            case INVENTORY:
                return new InventoryViewCreator();

            case IMAGES:
                return new ImagesViewCreator();

            default:
                throw new IllegalArgumentException();
        }
    }

    static void updateOfflineBox(final View view, final cgCache cache, final Resources res,
                                 final OnClickListener refreshCacheClickListener,
                                 final OnClickListener dropCacheClickListener,
                                 final OnClickListener storeCacheClickListener) {
        // offline use
        final TextView offlineText = (TextView) view.findViewById(R.id.offline_text);
        final Button offlineRefresh = (Button) view.findViewById(R.id.offline_refresh);
        final Button offlineStore = (Button) view.findViewById(R.id.offline_store);

        if (cache.isOffline()) {
            long diff = (System.currentTimeMillis() / (60 * 1000)) - (cache.getDetailedUpdate() / (60 * 1000)); // minutes

            String ago;
            if (diff < 15) {
                ago = res.getString(R.string.cache_offline_time_mins_few);
            } else if (diff < 50) {
                ago = res.getString(R.string.cache_offline_time_about) + " " + diff + " " + res.getString(R.string.cache_offline_time_mins);
            } else if (diff < 90) {
                ago = res.getString(R.string.cache_offline_time_about) + " " + res.getString(R.string.cache_offline_time_hour);
            } else if (diff < (48 * 60)) {
                ago = res.getString(R.string.cache_offline_time_about) + " " + (diff / 60) + " " + res.getString(R.string.cache_offline_time_hours);
            } else {
                ago = res.getString(R.string.cache_offline_time_about) + " " + (diff / (24 * 60)) + " " + res.getString(R.string.cache_offline_time_days);
            }

            offlineText.setText(res.getString(R.string.cache_offline_stored) + "\n" + ago);
            offlineRefresh.setOnClickListener(refreshCacheClickListener);

            offlineStore.setText(res.getString(R.string.cache_offline_drop));
            offlineStore.setClickable(true);
            offlineStore.setOnClickListener(dropCacheClickListener);
        } else {
            offlineText.setText(res.getString(R.string.cache_offline_not_ready));
            offlineRefresh.setOnClickListener(refreshCacheClickListener);

            offlineStore.setText(res.getString(R.string.cache_offline_store));
            offlineStore.setClickable(true);
            offlineStore.setOnClickListener(storeCacheClickListener);
        }
        offlineRefresh.setVisibility(cache.supportsRefresh() ? View.VISIBLE : View.GONE);
        offlineRefresh.setClickable(true);
    }

}
