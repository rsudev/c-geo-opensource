package cgeo.geocaching.settings;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.R;
import cgeo.geocaching.apps.navi.NavigationAppFactory.NavigationAppsEnum;
import cgeo.geocaching.connector.capability.ICredentials;
import cgeo.geocaching.connector.gc.GCConnector;
import cgeo.geocaching.connector.gc.GCConstants;
import cgeo.geocaching.connector.gc.GCMemberState;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.list.StoredList;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.log.LogTypeTrackable;
import cgeo.geocaching.log.TrackableComparator;
import cgeo.geocaching.maps.LivemapStrategy;
import cgeo.geocaching.maps.MapMode;
import cgeo.geocaching.maps.MapProviderFactory;
import cgeo.geocaching.maps.google.v1.GoogleMapProvider;
import cgeo.geocaching.maps.interfaces.GeoPointImpl;
import cgeo.geocaching.maps.interfaces.MapProvider;
import cgeo.geocaching.maps.interfaces.MapSource;
import cgeo.geocaching.maps.mapsforge.MapsforgeMapProvider;
import cgeo.geocaching.maps.mapsforge.MapsforgeMapProvider.OfflineMapSource;
import cgeo.geocaching.maps.routing.Routing;
import cgeo.geocaching.maps.routing.RoutingMode;
import cgeo.geocaching.network.HtmlImage;
import cgeo.geocaching.playservices.GooglePlayServices;
import cgeo.geocaching.sensors.MagnetometerAndAccelerometerProvider;
import cgeo.geocaching.sensors.OrientationProvider;
import cgeo.geocaching.sensors.RotationProvider;
import cgeo.geocaching.storage.DataStore;
import cgeo.geocaching.storage.LocalStorage;
import cgeo.geocaching.utils.CryptUtils;
import cgeo.geocaching.utils.FileUtils;
import cgeo.geocaching.utils.FileUtils.FileSelector;
import cgeo.geocaching.utils.Log;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * General c:geo preferences/settings set by the user
 */
public class Settings {

    /**
     * On opening a map, we limit the _initial_ zoom. The user can still zoom out afterwards.
     */
    private static final int INITIAL_MAP_ZOOM_LIMIT = 16;

    /**
     * Separator char for preferences with multiple elements.
     */
    private static final char SEPARATOR_CHAR = ',';

    private static final int SHOW_WP_THRESHOLD_DEFAULT = 10;
    public static final int SHOW_WP_THRESHOLD_MAX = 50;
    private static final int BROUTER_THRESHOLD_DEFAULT = 10;
    public static final int BROUTER_THRESHOLD_MAX = 120;
    private static final int MAP_SOURCE_DEFAULT = GoogleMapProvider.GOOGLE_MAP_ID.hashCode();

    private static final String PHONE_MODEL_AND_SDK = Build.MODEL + "/" + Build.VERSION.SDK_INT;

    // twitter api keys
    @NonNull private static final String TWITTER_KEY_CONSUMER_PUBLIC = CryptUtils.rot13("ESnsCvAv3kEupF1GCR3jGj");
    @NonNull private static final String TWITTER_KEY_CONSUMER_SECRET = CryptUtils.rot13("7vQWceACV9umEjJucmlpFe9FCMZSeqIqfkQ2BnhV9x");

    private static boolean useCompass = true;

    public enum CoordInputFormatEnum {
        Plain,
        Deg,
        Min,
        Sec;

        public static final int DEFAULT_INT_VALUE = Min.ordinal();

        public static CoordInputFormatEnum fromInt(final int id) {
            final CoordInputFormatEnum[] values = CoordInputFormatEnum.values();
            if (id < 0 || id >= values.length) {
                return Min;
            }
            return values[id];
        }
    }

    private static final SharedPreferences sharedPrefs = PreferenceManager
            .getDefaultSharedPreferences(CgeoApplication.getInstance().getBaseContext());
    static {
        migrateSettings();
        Log.setDebug(sharedPrefs.getBoolean(getKey(R.string.pref_debug), false));
    }

    /**
     * Cache the mapsource locally. If that is an offline map source, each request would potentially access the
     * underlying map file, leading to delays.
     */
    private static MapSource mapSource;

    protected Settings() {
        throw new InstantiationError();
    }

    private static void migrateSettings() {
        final int latestPreferencesVersion = 4;
        final int currentVersion = getInt(R.string.pref_settingsversion, 0);

        // No need to migrate if we are up to date.
        if (currentVersion == latestPreferencesVersion) {
            return;
        }

        // No need to migrate if we don't have older settings, defaults will be used instead.
        final String preferencesNameV0 = "cgeo.pref";
        final SharedPreferences prefsV0 = CgeoApplication.getInstance().getSharedPreferences(preferencesNameV0, Context.MODE_PRIVATE);
        if (currentVersion == 0 && prefsV0.getAll().isEmpty()) {
            final Editor e = sharedPrefs.edit();
            e.putInt(getKey(R.string.pref_settingsversion), latestPreferencesVersion);
            e.apply();
            return;
        }

        if (currentVersion < 1) {
            // migrate from non standard file location and integer based boolean types
            final Editor e = sharedPrefs.edit();

            e.putString(getKey(R.string.pref_temp_twitter_token_secret), prefsV0.getString(getKey(R.string.pref_temp_twitter_token_secret), null));
            e.putString(getKey(R.string.pref_temp_twitter_token_public), prefsV0.getString(getKey(R.string.pref_temp_twitter_token_public), null));
            e.putBoolean(getKey(R.string.pref_help_shown), prefsV0.getInt(getKey(R.string.pref_help_shown), 0) != 0);
            e.putFloat(getKey(R.string.pref_anylongitude), prefsV0.getFloat(getKey(R.string.pref_anylongitude), 0));
            e.putFloat(getKey(R.string.pref_anylatitude), prefsV0.getFloat(getKey(R.string.pref_anylatitude), 0));
            e.putBoolean(getKey(R.string.pref_offlinemaps), prefsV0.getInt(getKey(R.string.pref_offlinemaps), 1) != 0);
            e.putBoolean(getKey(R.string.pref_offlinewpmaps), prefsV0.getInt(getKey(R.string.pref_offlinewpmaps), 0) != 0);
            e.putString(getKey(R.string.pref_webDeviceCode), prefsV0.getString(getKey(R.string.pref_webDeviceCode), null));
            e.putString(getKey(R.string.pref_webDeviceName), prefsV0.getString(getKey(R.string.pref_webDeviceName), null));
            e.putBoolean(getKey(R.string.pref_maplive), prefsV0.getInt(getKey(R.string.pref_maplive), 1) != 0);
            e.putInt(getKey(R.string.pref_mapsource), prefsV0.getInt(getKey(R.string.pref_mapsource), MAP_SOURCE_DEFAULT));
            e.putBoolean(getKey(R.string.pref_twitter), prefsV0.getInt(getKey(R.string.pref_twitter), 0) != 0);
            e.putBoolean(getKey(R.string.pref_showaddress), prefsV0.getInt(getKey(R.string.pref_showaddress), 1) != 0);
            e.putBoolean(getKey(R.string.pref_maptrail), prefsV0.getInt(getKey(R.string.pref_maptrail), 1) != 0);
            e.putInt(getKey(R.string.pref_lastmapzoom), prefsV0.getInt(getKey(R.string.pref_lastmapzoom), 14));
            e.putBoolean(getKey(R.string.pref_livelist), prefsV0.getInt(getKey(R.string.pref_livelist), 1) != 0);
            e.putBoolean(getKey(R.string.pref_units_imperial), prefsV0.getInt(getKey(R.string.pref_units_imperial), 1) != 1);
            e.putBoolean(getKey(R.string.pref_skin), prefsV0.getInt(getKey(R.string.pref_skin), 0) != 0);
            e.putInt(getKey(R.string.pref_lastusedlist), prefsV0.getInt(getKey(R.string.pref_lastusedlist), StoredList.STANDARD_LIST_ID));
            e.putString(getKey(R.string.pref_cachetype), prefsV0.getString(getKey(R.string.pref_cachetype), CacheType.ALL.id));
            e.putString(getKey(R.string.pref_twitter_token_secret), prefsV0.getString(getKey(R.string.pref_twitter_token_secret), null));
            e.putString(getKey(R.string.pref_twitter_token_public), prefsV0.getString(getKey(R.string.pref_twitter_token_public), null));
            e.putInt(getKey(R.string.pref_version), prefsV0.getInt(getKey(R.string.pref_version), 0));
            e.putBoolean(getKey(R.string.pref_ratingwanted), prefsV0.getBoolean(getKey(R.string.pref_ratingwanted), true));
            e.putBoolean(getKey(R.string.pref_friendlogswanted), prefsV0.getBoolean(getKey(R.string.pref_friendlogswanted), true));
            e.putBoolean(getKey(R.string.pref_useenglish), prefsV0.getBoolean(getKey(R.string.pref_useenglish), false));
            e.putBoolean(getKey(R.string.pref_usecompass), prefsV0.getInt(getKey(R.string.pref_usecompass), 1) != 0);
            e.putBoolean(getKey(R.string.pref_trackautovisit), prefsV0.getBoolean(getKey(R.string.pref_trackautovisit), false));
            e.putBoolean(getKey(R.string.pref_sigautoinsert), prefsV0.getBoolean(getKey(R.string.pref_sigautoinsert), false));
            e.putBoolean(getKey(R.string.pref_logimages), prefsV0.getBoolean(getKey(R.string.pref_logimages), false));
            e.putBoolean(getKey(R.string.pref_excludedisabled), prefsV0.getInt(getKey(R.string.pref_excludedisabled), 0) != 0);
            e.putBoolean(getKey(R.string.pref_excludemine), prefsV0.getInt(getKey(R.string.pref_excludemine), 0) != 0);
            e.putString(getKey(R.string.pref_mapfile), prefsV0.getString(getKey(R.string.pref_mapfile), null));
            e.putString(getKey(R.string.pref_signature), prefsV0.getString(getKey(R.string.pref_signature), null));
            e.putString(getKey(R.string.pref_pass_vote), prefsV0.getString(getKey(R.string.pref_pass_vote), null));
            e.putString(getKey(R.string.pref_password), prefsV0.getString(getKey(R.string.pref_password), null));
            e.putString(getKey(R.string.pref_username), prefsV0.getString(getKey(R.string.pref_username), null));
            e.putString(getKey(R.string.pref_memberstatus), prefsV0.getString(getKey(R.string.pref_memberstatus), ""));
            e.putInt(getKey(R.string.pref_coordinputformat), prefsV0.getInt(getKey(R.string.pref_coordinputformat), CoordInputFormatEnum.DEFAULT_INT_VALUE));
            e.putBoolean(getKey(R.string.pref_log_offline), prefsV0.getBoolean(getKey(R.string.pref_log_offline), false));
            e.putBoolean(getKey(R.string.pref_choose_list), prefsV0.getBoolean(getKey(R.string.pref_choose_list), true));
            e.putBoolean(getKey(R.string.pref_loaddirectionimg), prefsV0.getBoolean(getKey(R.string.pref_loaddirectionimg), true));
            e.putString(getKey(R.string.pref_gccustomdate), prefsV0.getString(getKey(R.string.pref_gccustomdate), GCConstants.DEFAULT_GC_DATE));
            e.putInt(getKey(R.string.pref_showwaypointsthreshold), prefsV0.getInt(getKey(R.string.pref_showwaypointsthreshold), SHOW_WP_THRESHOLD_DEFAULT));
            e.putBoolean(getKey(R.string.pref_opendetailslastpage), prefsV0.getBoolean(getKey(R.string.pref_opendetailslastpage), false));
            e.putInt(getKey(R.string.pref_lastdetailspage), prefsV0.getInt(getKey(R.string.pref_lastdetailspage), 1));
            e.putInt(getKey(R.string.pref_defaultNavigationTool), prefsV0.getInt(getKey(R.string.pref_defaultNavigationTool), NavigationAppsEnum.COMPASS.id));
            e.putInt(getKey(R.string.pref_defaultNavigationTool2), prefsV0.getInt(getKey(R.string.pref_defaultNavigationTool2), NavigationAppsEnum.INTERNAL_MAP.id));
            e.putInt(getKey(R.string.pref_livemapstrategy), prefsV0.getInt(getKey(R.string.pref_livemapstrategy), LivemapStrategy.AUTO.id));
            e.putBoolean(getKey(R.string.pref_debug), prefsV0.getBoolean(getKey(R.string.pref_debug), false));
            e.putInt(getKey(R.string.pref_livemaphintshowcount), prefsV0.getInt(getKey(R.string.pref_livemaphintshowcount), 0));

            e.putInt(getKey(R.string.pref_settingsversion), 1); // mark migrated
            e.apply();
        }

        // changes for new settings dialog
        if (currentVersion < 2) {
            final Editor e = sharedPrefs.edit();

            e.putBoolean(getKey(R.string.pref_units_imperial), useImperialUnits());

            // show waypoints threshold now as a slider
            int wpThreshold = getWayPointsThreshold();
            if (wpThreshold < 0) {
                wpThreshold = 0;
            } else if (wpThreshold > SHOW_WP_THRESHOLD_MAX) {
                wpThreshold = SHOW_WP_THRESHOLD_MAX;
            }
            e.putInt(getKey(R.string.pref_showwaypointsthreshold), wpThreshold);

            // KEY_MAP_SOURCE must be string, because it is the key for a ListPreference now
            final int ms = sharedPrefs.getInt(getKey(R.string.pref_mapsource), MAP_SOURCE_DEFAULT);
            e.remove(getKey(R.string.pref_mapsource));
            e.putString(getKey(R.string.pref_mapsource), String.valueOf(ms));

            // navigation tool ids must be string, because ListPreference uses strings as keys
            final int dnt1 = sharedPrefs.getInt(getKey(R.string.pref_defaultNavigationTool), NavigationAppsEnum.COMPASS.id);
            final int dnt2 = sharedPrefs.getInt(getKey(R.string.pref_defaultNavigationTool2), NavigationAppsEnum.INTERNAL_MAP.id);
            e.remove(getKey(R.string.pref_defaultNavigationTool));
            e.remove(getKey(R.string.pref_defaultNavigationTool2));
            e.putString(getKey(R.string.pref_defaultNavigationTool), String.valueOf(dnt1));
            e.putString(getKey(R.string.pref_defaultNavigationTool2), String.valueOf(dnt2));

            // defaults for gpx directories
            e.putString(getKey(R.string.pref_gpxImportDir), LocalStorage.getDefaultGpxDirectory().getPath());
            e.putString(getKey(R.string.pref_gpxExportDir), LocalStorage.getDefaultGpxDirectory().getPath());

            e.putInt(getKey(R.string.pref_settingsversion), 2); // mark migrated
            e.apply();
        }

        if (currentVersion < 3) {
            final Editor e = sharedPrefs.edit();

            Log.i("Moving field-notes");
            FileUtils.move(LocalStorage.getLegacyFieldNotesDirectory(), LocalStorage.getFieldNotesDirectory());

            Log.i("Moving gpx ex- and import dirs");
            if (getGpxExportDir().equals(LocalStorage.getLegacyGpxDirectory().getPath())) {
                e.putString(getKey(R.string.pref_gpxExportDir), LocalStorage.getDefaultGpxDirectory().getPath());
            }
            if (getGpxImportDir().equals(LocalStorage.getLegacyGpxDirectory().getPath())) {
                e.putString(getKey(R.string.pref_gpxImportDir), LocalStorage.getDefaultGpxDirectory().getPath());
            }
            FileUtils.move(LocalStorage.getLegacyGpxDirectory(), LocalStorage.getDefaultGpxDirectory());

            Log.i("Moving local spoilers");
            FileUtils.move(LocalStorage.getLegacyLocalSpoilersDirectory(), LocalStorage.getLocalSpoilersDirectory());

            Log.i("Moving db files");
            FileUtils.moveTo(new File(LocalStorage.getLegacyExternalCgeoDirectory(), DataStore.DB_FILE_NAME), LocalStorage.getExternalDbDirectory());
            FileUtils.moveTo(new File(LocalStorage.getLegacyExternalCgeoDirectory(), DataStore.DB_FILE_NAME + DataStore.DB_FILE_CORRUPTED_EXTENSION), LocalStorage.getBackupDirectory());
            FileUtils.moveTo(new File(LocalStorage.getLegacyExternalCgeoDirectory(), DataStore.DB_FILE_NAME_BACKUP), LocalStorage.getBackupDirectory());
            FileUtils.moveTo(new File(LocalStorage.getLegacyExternalCgeoDirectory(), DataStore.DB_FILE_NAME_BACKUP + DataStore.DB_FILE_CORRUPTED_EXTENSION), LocalStorage.getBackupDirectory());

            Log.i("Moving geocache data");
            final FileFilter geocacheDirectories = new FileFilter() {
                @Override
                public boolean accept(final File pathname) {
                    final String name = pathname.getName();
                    return pathname.isDirectory() &&
                            (HtmlImage.SHARED.equals(name) || LocalStorage.GEOCACHE_FILE_PATTERN.matcher(name).find());
                }
            };
            final File[] list = LocalStorage.getLegacyExternalCgeoDirectory().listFiles(geocacheDirectories);
            if (list != null) {
                for (final File file : list) {
                    FileUtils.moveTo(file, LocalStorage.getGeocacheDataDirectory());
                }
            }

            Log.i("Deleting legacy .cgeo dir");
            FileUtils.deleteIgnoringFailure(LocalStorage.getLegacyExternalCgeoDirectory());

            e.putString(getKey(R.string.pref_dataDir), LocalStorage.getExternalPrivateCgeoDirectory().getAbsolutePath());
            e.putInt(getKey(R.string.pref_settingsversion), 3); // mark migrated
            e.apply();
        }

        if (currentVersion < 4) {
            final Editor e = sharedPrefs.edit();

            if (Integer.parseInt(sharedPrefs.getString(getKey(R.string.pref_defaultNavigationTool), String.valueOf(NavigationAppsEnum.COMPASS.id))) == 25) {
                e.putString(getKey(R.string.pref_defaultNavigationTool), prefsV0.getString(getKey(R.string.pref_defaultNavigationTool), String.valueOf(NavigationAppsEnum.INTERNAL_MAP.id)));
            }

            if (Integer.parseInt(sharedPrefs.getString(getKey(R.string.pref_defaultNavigationTool2), String.valueOf(NavigationAppsEnum.INTERNAL_MAP.id))) == 25) {
                e.putString(getKey(R.string.pref_defaultNavigationTool2), prefsV0.getString(getKey(R.string.pref_defaultNavigationTool2), String.valueOf(NavigationAppsEnum.INTERNAL_MAP.id)));
            }

            e.putInt(getKey(R.string.pref_settingsversion), 4); // mark migrated
            e.apply();
        }
    }

    private static String getKey(final int prefKeyId) {
        return CgeoApplication.getInstance().getString(prefKeyId);
    }

    static String getString(final int prefKeyId, final String defaultValue) {
        return sharedPrefs.getString(getKey(prefKeyId), defaultValue);
    }

    private static List<String> getStringList(final int prefKeyId, final String defaultValue) {
        return Arrays.asList(StringUtils.split(getString(prefKeyId, defaultValue), SEPARATOR_CHAR));
    }

    private static int getInt(final int prefKeyId, final int defaultValue) {
        return sharedPrefs.getInt(getKey(prefKeyId), defaultValue);
    }

    private static long getLong(final int prefKeyId, final long defaultValue) {
        return sharedPrefs.getLong(getKey(prefKeyId), defaultValue);
    }

    private static boolean getBoolean(final int prefKeyId, final boolean defaultValue) {
        return sharedPrefs.getBoolean(getKey(prefKeyId), defaultValue);
    }

    private static float getFloat(final int prefKeyId, final float defaultValue) {
        return sharedPrefs.getFloat(getKey(prefKeyId), defaultValue);
    }

    protected static void putString(final int prefKeyId, final String value) {
        final SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putString(getKey(prefKeyId), value);
        edit.apply();
    }

    private static void putStringList(final int prefKeyId, final Iterable<?> elements) {
        putString(prefKeyId, StringUtils.join(elements, SEPARATOR_CHAR));
    }


    protected static void putBoolean(final int prefKeyId, final boolean value) {
        final SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putBoolean(getKey(prefKeyId), value);
        edit.apply();
    }

    private static void putInt(final int prefKeyId, final int value) {
        final SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putInt(getKey(prefKeyId), value);
        edit.apply();
    }

    private static void putLong(final int prefKeyId, final long value) {
        final SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putLong(getKey(prefKeyId), value);
        edit.apply();
    }

    private static void putFloat(final int prefKeyId, final float value) {
        final SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putFloat(getKey(prefKeyId), value);
        edit.apply();
    }

    private static void remove(final int prefKeyId) {
        final SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.remove(getKey(prefKeyId));
        edit.apply();
    }

    private static boolean contains(final int prefKeyId) {
        return sharedPrefs.contains(getKey(prefKeyId));
    }

    public static boolean hasGCCredentials() {
        return getGcCredentials().isValid();
    }

    /**
     * Get login and password information of Geocaching.com.
     *
     * @return a pair either with (login, password) or (empty, empty) if no valid information is stored
     */
    public static Credentials getGcCredentials() {
        return getCredentials(GCConnector.getInstance());
    }

    /**
     * Get login and password information from preference key id.
     *
     * @param usernamePreferenceKey Username preference key id
     * @param passwordPreferenceKey Password preference key id
     * @return the credential information
     */
    @NonNull
    public static Credentials getCredentials(final int usernamePreferenceKey, final int passwordPreferenceKey) {
        final String username = StringUtils.trim(getString(usernamePreferenceKey, StringUtils.EMPTY));
        final String password = getString(passwordPreferenceKey, StringUtils.EMPTY);
        return new Credentials(username, password);
    }

    /**
     * Get login and password information.
     *
     * @param connector the connector to retrieve the login information from
     * @return the credential information
     */
    @NonNull
    public static Credentials getCredentials(@NonNull final ICredentials connector) {
        return getCredentials(connector.getUsernamePreferenceKey(), connector.getPasswordPreferenceKey());
    }

    /**
     * Set login and password information.
     *
     * @param connector the connector to retrieve the login information from
     * @param credentials the credential information to store
     */
    public static void setCredentials(@NonNull final ICredentials connector, final Credentials credentials) {
        putString(connector.getUsernamePreferenceKey(), credentials.getUsernameRaw());
        putString(connector.getPasswordPreferenceKey(), credentials.getPasswordRaw());
    }

    public static String getUserName() {
        return StringUtils.trim(getString(R.string.pref_username, StringUtils.EMPTY));
    }

    public static boolean isGCConnectorActive() {
        return getBoolean(R.string.pref_connectorGCActive, true);
    }

    public static boolean isECConnectorActive() {
        return getBoolean(R.string.pref_connectorECActive, false);
    }

    public static boolean isSUConnectorActive() {
        return getBoolean(R.string.pref_connectorSUActive, false);
    }

    public static boolean isGCPremiumMember() {
        return getGCMemberStatus().isPremium();
    }

    public static GCMemberState getGCMemberStatus() {
        return GCMemberState.fromString(getString(R.string.pref_memberstatus, ""));
    }

    public static void setGCMemberStatus(final GCMemberState memberStatus) {
        putString(R.string.pref_memberstatus, memberStatus.englishWebsite);
    }

    @NonNull
    public static ImmutablePair<String, String> getTokenPair(final int tokenPublicPrefKey, final int tokenSecretPrefKey) {
        return new ImmutablePair<>(getString(tokenPublicPrefKey, null), getString(tokenSecretPrefKey, null));
    }

    public static void setTokens(final int tokenPublicPrefKey, @Nullable final String tokenPublic, final int tokenSecretPrefKey, @Nullable final String tokenSecret) {
        if (tokenPublic == null) {
            remove(tokenPublicPrefKey);
        } else {
            putString(tokenPublicPrefKey, tokenPublic);
        }
        if (tokenSecret == null) {
            remove(tokenSecretPrefKey);
        } else {
            putString(tokenSecretPrefKey, tokenSecret);
        }
    }

    public static boolean isOCConnectorActive(final int isActivePrefKeyId) {
        return getBoolean(isActivePrefKeyId, false);
    }

    public static boolean hasOCAuthorization(final int tokenPublicPrefKeyId, final int tokenSecretPrefKeyId) {
        return StringUtils.isNotBlank(getString(tokenPublicPrefKeyId, ""))
                && StringUtils.isNotBlank(getString(tokenSecretPrefKeyId, ""));
    }

    public static boolean isGCVoteLoginValid() {
        return getGCVoteLogin().isValid();
    }

    @NonNull
    public static Credentials getGCVoteLogin() {
        final String username = StringUtils.trimToNull(getString(R.string.pref_username, null));
        final String password = getString(R.string.pref_pass_vote, null);
        return new Credentials(username, password);
    }

    @NonNull
    public static String getSignature() {
        return StringUtils.defaultString(getString(R.string.pref_signature, StringUtils.EMPTY));
    }

    public static void setUseGooglePlayServices(final boolean value) {
        putBoolean(R.string.pref_googleplayservices, value);
    }

    public static boolean useGooglePlayServices() {
        if (!GooglePlayServices.isAvailable()) {
            return false;
        }
        return outdatedPhoneModelOrSdk() || getBoolean(R.string.pref_googleplayservices, true);
    }

    public static boolean useLowPowerMode() {
        return getBoolean(R.string.pref_lowpowermode, false);
    }

    /**
     * @param cacheType
     *            The cache type used for future filtering
     */
    public static void setCacheType(final CacheType cacheType) {
        if (cacheType == null) {
            remove(R.string.pref_cachetype);
        } else {
            putString(R.string.pref_cachetype, cacheType.id);
        }
    }

    public static int getLastDisplayedList() {
        return getInt(R.string.pref_lastusedlist, StoredList.STANDARD_LIST_ID);
    }

    /**
     * remember the last displayed cache list
     */
    public static void setLastDisplayedList(final int listId) {
        putInt(R.string.pref_lastusedlist, listId);
    }

    public static Set<Integer> getLastSelectedLists() {
        final Set<Integer> lastSelectedLists = new HashSet<>();
        for (final String lastSelectedListString : getStringList(R.string.pref_last_selected_lists, StringUtils.EMPTY)) {
            try {
                lastSelectedLists.add(Integer.valueOf(lastSelectedListString));
            } catch (final NumberFormatException ignored) { }
        }
        return lastSelectedLists;
    }

    /**
     * remember the last selection in the dialog that assigns a cache to certain lists
     */
    public static void setLastSelectedLists(final Set<Integer> lastSelectedLists) {
        putStringList(R.string.pref_last_selected_lists, lastSelectedLists);
    }

    public static void setWebNameCode(final String name, final String code) {
        putString(R.string.pref_webDeviceName, name);
        putString(R.string.pref_webDeviceCode, code);
    }

    public static MapProvider getMapProvider() {
        return getMapSource().getMapProvider();
    }

    public static String getMapFile() {
        return getString(R.string.pref_mapfile, null);
    }

    static void setMapFile(final String mapFile) {
        putString(R.string.pref_mapfile, mapFile);
        if (mapFile != null) {
            setMapFileDirectory(new File(mapFile).getParent());
        }
    }

    public static String getMapFileDirectory() {
        final String mapDir = getString(R.string.pref_mapDirectory, null);
        if (mapDir != null) {
            return mapDir;
        }
        final String mapFile = getMapFile();
        if (mapFile != null) {
            return new File(mapFile).getParent();
        }
        return null;
    }

    static void setMapFileDirectory(final String mapFileDirectory) {
        putString(R.string.pref_mapDirectory, mapFileDirectory);
        MapsforgeMapProvider.getInstance().updateOfflineMaps();
    }

    private static boolean isValidMapFile() {
        return isValidMapFile(getMapFile());
    }

    public static boolean isValidMapFile(final String mapFileIn) {
        return MapsforgeMapProvider.isValidMapFile(mapFileIn);
    }

    public static boolean isScaleMapsforgeText() {
        return getBoolean(R.string.pref_mapsforge_scale_text, true);
    }

    public static CoordInputFormatEnum getCoordInputFormat() {
        return CoordInputFormatEnum.fromInt(getInt(R.string.pref_coordinputformat, CoordInputFormatEnum.DEFAULT_INT_VALUE));
    }

    public static void setCoordInputFormat(final CoordInputFormatEnum format) {
        putInt(R.string.pref_coordinputformat, format.ordinal());
    }

    public static boolean getLogOffline() {
        return getBoolean(R.string.pref_log_offline, false);
    }

    public static boolean getChooseList() {
        return getBoolean(R.string.pref_choose_list, false);
    }

    public static boolean getLoadDirImg() {
        return !isGCPremiumMember() && getBoolean(R.string.pref_loaddirectionimg, true);
    }

    public static void setGcCustomDate(final String format) {
        putString(R.string.pref_gccustomdate, format);
    }

    /**
     * @return User selected date format on GC.com
     */
    public static String getGcCustomDate() {
        // We might have some users whose stored value is null, which is invalid. In this case, we use the default.
        return StringUtils.defaultString(getString(R.string.pref_gccustomdate, GCConstants.DEFAULT_GC_DATE),
                GCConstants.DEFAULT_GC_DATE);
    }

    public static boolean isExcludeMyCaches() {
        return getBoolean(R.string.pref_excludemine, false);
    }

    public static boolean useEnglish() {
        return getBoolean(R.string.pref_useenglish, false);
    }

    public static void setUseEnglish(final boolean useEnglish) {
        putBoolean(R.string.pref_useenglish, useEnglish);
    }

    public static boolean isShowAddress() {
        return getBoolean(R.string.pref_showaddress, true);
    }

    public static boolean isExcludeDisabledCaches() {
        return getBoolean(R.string.pref_excludedisabled, false);
    }

    public static boolean isStoreOfflineMaps() {
        return getBoolean(R.string.pref_offlinemaps, true);
    }

    public static boolean isStoreOfflineWpMaps() {
        return getBoolean(R.string.pref_offlinewpmaps, false);
    }

    public static boolean isStoreLogImages() {
        return getBoolean(R.string.pref_logimages, false);
    }

    public static boolean isRatingWanted() {
        return getBoolean(R.string.pref_ratingwanted, true);
    }

    public static boolean isGeokretyConnectorActive() {
        return getBoolean(R.string.pref_connectorGeokretyActive, false);
    }

    public static boolean isGeokretyCacheActive() {
        return getBoolean(R.string.pref_geokrety_cache, true);
    }

    static boolean hasGeokretyAuthorization() {
        return StringUtils.isNotBlank(getGeokretySecId());
    }

    public static String getGeokretySecId() {
        return getString(R.string.pref_fakekey_geokrety_authorization, null);
    }

    public static void setGeokretySecId(final String secid) {
        putString(R.string.pref_fakekey_geokrety_authorization, secid);
    }

    public static String getTokenSecret(final int prefKeyId) {
        return getString(prefKeyId, StringUtils.EMPTY);
    }

    public static void setTokenSecret(final int prefKeyId, final String secretToken) {
        putString(prefKeyId, secretToken);
    }

    public static boolean isRegisteredForGeokretyLogging() {
        return getGeokretySecId() != null;
    }

    /**
     * Retrieve showed popup counter for warning about logging Trackable recommend Geocode
     *
     * @return number of times the popup has appeared
     */
    public static int getLogTrackableWithoutGeocodeShowCount() {
        return getInt(R.string.pref_logtrackablewithoutgeocodeshowcount, 0);
    }

    /**
     * Store showed popup counter for warning about logging Trackable recommend Geocode
     *
     * @param showCount the count to save
     */
    public static void setLogTrackableWithoutGeocodeShowCount(final int showCount) {
        putInt(R.string.pref_logtrackablewithoutgeocodeshowcount, showCount);
    }

    public static boolean isFriendLogsWanted() {
        if (!hasGCCredentials()) {
            // don't show a friends log if the user is anonymous
            return false;
        }
        return getBoolean(R.string.pref_friendlogswanted, true);
    }

    public static boolean isLiveList() {
        return getBoolean(R.string.pref_livelist, true);
    }

    public static boolean isTrackableAutoVisit() {
        return getBoolean(R.string.pref_trackautovisit, false);
    }

    public static boolean isAutoInsertSignature() {
        return getBoolean(R.string.pref_sigautoinsert, false);
    }

    static void setUseImperialUnits(final boolean useImperialUnits) {
        putBoolean(R.string.pref_units_imperial, useImperialUnits);
    }

    public static boolean useImperialUnits() {
        return getBoolean(R.string.pref_units_imperial, useImperialUnitsByDefault());
    }

    private static boolean useImperialUnitsByDefault() {
        final String countryCode = Locale.getDefault().getCountry();
        return "US".equals(countryCode)  // USA
            || "LR".equals(countryCode)  // Liberia
            || "MM".equals(countryCode); // Burma
    }

    public static boolean isLiveMap() {
        return getBoolean(R.string.pref_maplive, true);
    }

    public static void setLiveMap(final boolean live) {
        putBoolean(R.string.pref_maplive, live);
    }

    public static boolean isMapTrail() {
        return getBoolean(R.string.pref_maptrail, false);
    }

    public static void setMapTrail(final boolean showTrail) {
        putBoolean(R.string.pref_maptrail, showTrail);
    }

    public static boolean isDotMode() {
        return getBoolean(R.string.pref_dot_mode, false);
    }

    public static void setDotMode(final boolean dotMode) {
        putBoolean(R.string.pref_dot_mode, dotMode);
    }

    /**
     * whether to show a direction line on the map
     */
    public static boolean isMapDirection() {
        return getBoolean(R.string.pref_map_direction, true);
    }

    public static void setMapDirection(final boolean showDirection) {
        putBoolean(R.string.pref_map_direction, showDirection);
    }

    /**
     * Get last used zoom of the internal map. Differentiate between two use cases for a map of multiple caches (e.g.
     * live map) and the map of a single cache (which is often zoomed in more deep).
     */
    public static int getMapZoom(final MapMode mapMode) {
        if (mapMode == MapMode.SINGLE || mapMode == MapMode.COORDS) {
            return getCacheZoom();
        }
        return getMapZoom();
    }

    public static void setMapZoom(final MapMode mapMode, final int zoomLevel) {
        if (mapMode == MapMode.SINGLE || mapMode == MapMode.COORDS) {
            setCacheZoom(zoomLevel);
        } else {
            setMapZoom(zoomLevel);
        }
    }

    /**
     * @return zoom used for the (live) map
     */
    private static int getMapZoom() {
        return Math.max(getInt(R.string.pref_lastmapzoom, 14), INITIAL_MAP_ZOOM_LIMIT);
    }

    private static void setMapZoom(final int mapZoomLevel) {
        putInt(R.string.pref_lastmapzoom, mapZoomLevel);
    }

    /**
     * @return zoom used for the map of a single cache
     */
    private static int getCacheZoom() {
        return Math.max(getInt(R.string.pref_cache_zoom, 14), INITIAL_MAP_ZOOM_LIMIT);
    }

    private static void setCacheZoom(final int zoomLevel) {
        putInt(R.string.pref_cache_zoom, zoomLevel);
    }

    public static GeoPointImpl getMapCenter() {
        return getMapProvider().getMapItemFactory()
                .getGeoPointBase(new Geopoint(getInt(R.string.pref_lastmaplat, 0) / 1e6,
                        getInt(R.string.pref_lastmaplon, 0) / 1e6));
    }

    public static void setMapCenter(final GeoPointImpl mapViewCenter) {
        putInt(R.string.pref_lastmaplat, mapViewCenter.getLatitudeE6());
        putInt(R.string.pref_lastmaplon, mapViewCenter.getLongitudeE6());
    }

    @NonNull
    public static synchronized MapSource getMapSource() {
        if (mapSource != null) {
            return mapSource;
        }
        final int id = getConvertedMapId();
        mapSource = MapProviderFactory.getMapSource(id);
        if (mapSource != null) {
            // don't use offline maps if the map file is not valid
            if (!(mapSource instanceof OfflineMapSource) || isValidMapFile()) {
                return mapSource;
            }
        }
        // fallback to first available map
        return MapProviderFactory.getDefaultSource();
    }

    private static final int GOOGLEMAP_BASEID = 30;
    private static final int MAP = 1;
    private static final int SATELLITE = 2;

    private static final int MFMAP_BASEID = 40;
    private static final int MAPNIK = 1;
    private static final int OFFLINE = 4;
    private static final int HISTORY_SIZE = 10;

    /**
     * Convert old preference ids for maps (based on constant values) into new hash based ids.
     */
    private static int getConvertedMapId() {
        final int id = Integer.parseInt(getString(R.string.pref_mapsource,
                String.valueOf(MAP_SOURCE_DEFAULT)));
        switch (id) {
            case GOOGLEMAP_BASEID + MAP:
                return GoogleMapProvider.GOOGLE_MAP_ID.hashCode();
            case GOOGLEMAP_BASEID + SATELLITE:
                return GoogleMapProvider.GOOGLE_SATELLITE_ID.hashCode();
            case MFMAP_BASEID + MAPNIK:
                return MapsforgeMapProvider.MAPSFORGE_MAPNIK_ID.hashCode();
            case MFMAP_BASEID + OFFLINE: {
                final String mapFile = getMapFile();
                if (StringUtils.isNotEmpty(mapFile)) {
                    return mapFile.hashCode();
                }
                break;
            }
            default:
                break;
        }
        return id;
    }

    public static synchronized void setMapSource(final MapSource newMapSource) {
        putString(R.string.pref_mapsource, String.valueOf(newMapSource.getNumericalId()));
        if (newMapSource instanceof OfflineMapSource) {
            setMapFile(((OfflineMapSource) newMapSource).getFileName());
        }
        // cache the value
        mapSource = newMapSource;
    }

    public static void setAnyCoordinates(final Geopoint coords) {
        if (coords != null) {
            putFloat(R.string.pref_anylatitude, (float) coords.getLatitude());
            putFloat(R.string.pref_anylongitude, (float) coords.getLongitude());
        } else {
            remove(R.string.pref_anylatitude);
            remove(R.string.pref_anylongitude);
        }
    }

    public static Geopoint getAnyCoordinates() {
        if (contains(R.string.pref_anylatitude) && contains(R.string.pref_anylongitude)) {
            final float lat = getFloat(R.string.pref_anylatitude, 0);
            final float lon = getFloat(R.string.pref_anylongitude, 0);
            return new Geopoint(lat, lon);
        }
        return null;
    }

    public static boolean isUseCompass() {
        return useCompass;
    }

    public static void setUseCompass(final boolean value) {
        useCompass = value;
    }

    public static boolean isLightSkin() {
        return getBoolean(R.string.pref_skin, false);
    }

    @NonNull
    public static String getTwitterKeyConsumerPublic() {
        return TWITTER_KEY_CONSUMER_PUBLIC;
    }

    @NonNull
    public static String getTwitterKeyConsumerSecret() {
        return TWITTER_KEY_CONSUMER_SECRET;
    }

    public static String getWebDeviceCode() {
        return getString(R.string.pref_webDeviceCode, null);
    }

    public static boolean isRegisteredForSend2cgeo() {
        return getWebDeviceCode() != null;
    }

    static String getWebDeviceName() {
        return getString(R.string.pref_webDeviceName, Build.MODEL);
    }

    /**
     * @return The cache type used for filtering or ALL if no filter is active.
     *         Returns never null
     */
    @NonNull
    public static CacheType getCacheType() {
        return CacheType.getById(getString(R.string.pref_cachetype, CacheType.ALL.id));
    }

    /**
     * The Threshold for the showing of child waypoints
     */
    public static int getWayPointsThreshold() {
        return getInt(R.string.pref_showwaypointsthreshold, SHOW_WP_THRESHOLD_DEFAULT);
    }

    static void setShowWaypointsThreshold(final int threshold) {
        putInt(R.string.pref_showwaypointsthreshold, threshold);
    }

    /**
     * The Threshold for brouter routing (max. distance)
     */
    public static int getBrouterThreshold() {
        return getInt(R.string.pref_brouterDistanceThreshold, BROUTER_THRESHOLD_DEFAULT);
    }

    static void setBrouterThreshold(final int threshold) {
        putInt(R.string.pref_brouterDistanceThreshold, threshold);
    }

    public static boolean isUseTwitter() {
        return getBoolean(R.string.pref_twitter, false);
    }

    private static void setUseTwitter(final boolean useTwitter) {
        putBoolean(R.string.pref_twitter, useTwitter);
    }

    public static boolean isTwitterLoginValid() {
        return !StringUtils.isBlank(getTokenPublic())
                && !StringUtils.isBlank(getTokenSecret());
    }

    public static String getTokenPublic() {
        return getString(R.string.pref_twitter_token_public, null);
    }

    public static String getTokenSecret() {
        return getString(R.string.pref_twitter_token_secret, null);

    }

    static boolean hasTwitterAuthorization() {
        return StringUtils.isNotBlank(getTokenPublic())
                && StringUtils.isNotBlank(getTokenSecret());
    }

    public static void setTwitterTokens(@Nullable final String tokenPublic,
            @Nullable final String tokenSecret, final boolean enableTwitter) {
        putString(R.string.pref_twitter_token_public, tokenPublic);
        putString(R.string.pref_twitter_token_secret, tokenSecret);
        if (tokenPublic != null) {
            remove(R.string.pref_temp_twitter_token_public);
            remove(R.string.pref_temp_twitter_token_secret);
        }
        setUseTwitter(enableTwitter);
    }

    public static void setTwitterTempTokens(@Nullable final String tokenPublic,
            @Nullable final String tokenSecret) {
        putString(R.string.pref_temp_twitter_token_public, tokenPublic);
        putString(R.string.pref_temp_twitter_token_secret, tokenSecret);
    }

    @NonNull
    public static ImmutablePair<String, String> getTempToken() {
        final String tokenPublic = getString(R.string.pref_temp_twitter_token_public, null);
        final String tokenSecret = getString(R.string.pref_temp_twitter_token_secret, null);
        return new ImmutablePair<>(tokenPublic, tokenSecret);
    }

    public static int getVersion() {
        return getInt(R.string.pref_version, 0);
    }

    public static void setVersion(final int version) {
        putInt(R.string.pref_version, version);
    }

    public static boolean isOpenLastDetailsPage() {
        return getBoolean(R.string.pref_opendetailslastpage, false);
    }

    public static int getLastDetailsPage() {
        return getInt(R.string.pref_lastdetailspage, 1);
    }

    public static void setLastDetailsPage(final int index) {
        putInt(R.string.pref_lastdetailspage, index);
    }

    public static int getDefaultNavigationTool() {
        return Integer.parseInt(getString(
                R.string.pref_defaultNavigationTool,
                String.valueOf(NavigationAppsEnum.COMPASS.id)));
    }

    public static int getDefaultNavigationTool2() {
        return Integer.parseInt(getString(
                R.string.pref_defaultNavigationTool2,
                String.valueOf(NavigationAppsEnum.INTERNAL_MAP.id)));
    }

    @NonNull
    public static LivemapStrategy getLiveMapStrategy() {
        return LivemapStrategy.getById(getInt(R.string.pref_livemapstrategy, LivemapStrategy.AUTO.id));
    }

    public static void setLiveMapStrategy(final LivemapStrategy strategy) {
        putInt(R.string.pref_livemapstrategy, strategy.id);
    }

    public static boolean isDebug() {
        return Log.isDebug();
    }

    public static void setDebug(final boolean debug) {
        Log.setDebug(debug);
        putBoolean(R.string.pref_debug, debug);
    }

    public static int getLiveMapHintShowCount() {
        return getInt(R.string.pref_livemaphintshowcount, 0);
    }

    public static void setLiveMapHintShowCount(final int showCount) {
        putInt(R.string.pref_livemaphintshowcount, showCount);
    }

    public static boolean isDbOnSDCard() {
        return getBoolean(R.string.pref_dbonsdcard, false);
    }

    public static void setDbOnSDCard(final boolean dbOnSDCard) {
        putBoolean(R.string.pref_dbonsdcard, dbOnSDCard);
    }

    public static String getGpxExportDir() {
        return getString(R.string.pref_gpxExportDir,
                LocalStorage.getDefaultGpxDirectory().getPath());
    }

    public static String getGpxImportDir() {
        return getString(R.string.pref_gpxImportDir,
                LocalStorage.getDefaultGpxDirectory().getPath());
    }

    public static String getExternalPrivateCgeoDirectory() {
        return getString(R.string.pref_dataDir, null);
    }

    public static void setExternalPrivateCgeoDirectory(final String extDir) {
        putString(R.string.pref_dataDir, extDir);
    }

    public static boolean getShareAfterExport() {
        return getBoolean(R.string.pref_shareafterexport, true);
    }

    public static void setShareAfterExport(final boolean shareAfterExport) {
        putBoolean(R.string.pref_shareafterexport, shareAfterExport);
    }
    public static boolean getIncludeFoundStatus() {
        return getBoolean(R.string.pref_includefoundstatus, true);
    }

    public static void setIncludeFoundStatus(final boolean includeFoundStatus) {
        putBoolean(R.string.pref_includefoundstatus, includeFoundStatus);
    }

    /**
     * Get Trackable inventory sort method based on the last Trackable inventory sort method.
     *
     * @return
     *         The Trackable Sort Method previously used.
     */
    public static TrackableComparator getTrackableComparator() {
        return TrackableComparator.findByName(getString(R.string.pref_trackable_inventory_sort, ""));
    }

    /**
     * Set Trackable inventory sort method.
     *
     * @param trackableSortMethod
     *          The Trackable Sort Method to remember
     */
    public static void setTrackableComparator(final TrackableComparator trackableSortMethod) {
        putString(R.string.pref_trackable_inventory_sort, trackableSortMethod.name());
    }
    /**
     * Obtain Trackable action from the last Trackable log.
     *
     * @return
     *          The last Trackable Action or RETRIEVED_IT
     */
    public static int getTrackableAction() {
        return getInt(R.string.pref_trackableaction, LogTypeTrackable.RETRIEVED_IT.id);
    }

    /**
     * Save Trackable action from the last Trackable log.
     *
     * @param trackableAction
     *          The Trackable Action to remember
     */
    public static void setTrackableAction(final int trackableAction) {
        putInt(R.string.pref_trackableaction, trackableAction);
    }

    private static String getCustomRenderThemeBaseFolder() {
        return getString(R.string.pref_renderthemepath, "");
    }

    public static String getCustomRenderThemeFilePath() {
        return getString(R.string.pref_renderthemefile, "");
    }

    public static void setCustomRenderThemeFile(final String customRenderThemeFile) {
        putString(R.string.pref_renderthemefile, customRenderThemeFile);
    }

    public static File[] getMapThemeFiles() {
        final File directory = new File(getCustomRenderThemeBaseFolder());
        final List<File> result = new ArrayList<>();
        FileUtils.listDir(result, directory, new ExtensionsBasedFileSelector(new String[] { "xml" }), null);

        return result.toArray(new File[result.size()]);
    }

    private static class ExtensionsBasedFileSelector implements FileSelector {
        private final String[] extensions;

        ExtensionsBasedFileSelector(final String[] extensions) {
            this.extensions = extensions;
        }

        @Override
        public boolean isSelected(final File file) {
            final String filename = file.getName();
            for (final String ext : extensions) {
                if (StringUtils.endsWithIgnoreCase(filename, ext)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean shouldEnd() {
            return false;
        }
    }

    /**
     * @return true if plain text log wanted
     */
    public static boolean getPlainLogs() {
        return getBoolean(R.string.pref_plainLogs, false);
    }

    /**
     * Force set the plain text log preference
     *
     * @param plainLogs
     *            wanted or not
     */
    public static void setPlainLogs(final boolean plainLogs) {
        putBoolean(R.string.pref_plainLogs, plainLogs);
    }

    public static boolean getUseNativeUa() {
        return getBoolean(R.string.pref_nativeUa, false);
    }

    @NonNull
    public static String getCacheTwitterMessage() {
        return StringUtils.defaultString(getString(R.string.pref_twitter_cache_message, "I found [NAME] ([URL])."));
    }

    @NonNull
    public static String getTrackableTwitterMessage() {
        return StringUtils.defaultString(getString(R.string.pref_twitter_trackable_message, "I touched [NAME] ([URL])."));
    }

    public static int getLogImageScale() {
        return getInt(R.string.pref_logImageScale, -1);
    }

    public static void setLogImageScale(final int scale) {
        putInt(R.string.pref_logImageScale, scale);
    }

    public static void setExcludeMine(final boolean exclude) {
        putBoolean(R.string.pref_excludemine, exclude);
    }

    public static void setExcludeDisabled(final boolean exclude) {
        putBoolean(R.string.pref_excludedisabled, exclude);
    }

    static void setLogin(final String username, final String password) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            // erase username and password
            remove(R.string.pref_username);
            remove(R.string.pref_password);
            return;
        }
        // save username and password
        putString(R.string.pref_username, StringUtils.trim(username));
        putString(R.string.pref_password, password);
    }

    public static long getFieldnoteExportDate() {
        return getLong(R.string.pref_fieldNoteExportDate, 0);
    }

    /**
     * Remember date of last field note export.
     */
    public static void setFieldnoteExportDate(final long date) {
        putLong(R.string.pref_fieldNoteExportDate, date);
    }

    public static boolean isUseNavigationApp(final NavigationAppsEnum navApp) {
        return getBoolean(navApp.preferenceKey, true);
    }

    /**
     * Remember the state of the "Upload" checkbox in the field notes export dialog.
     */
    public static void setFieldNoteExportUpload(final boolean upload) {
        putBoolean(R.string.pref_fieldNoteExportUpload, upload);
    }

    public static boolean getFieldNoteExportUpload() {
        return getBoolean(R.string.pref_fieldNoteExportUpload, true);
    }

    /**
     * Remember the state of the "Only new" checkbox in the field notes export dialog.
     */
    public static void setFieldNoteExportOnlyNew(final boolean onlyNew) {
        putBoolean(R.string.pref_fieldNoteExportOnlyNew, onlyNew);
    }

    public static boolean getFieldNoteExportOnlyNew() {
        return getBoolean(R.string.pref_fieldNoteExportOnlyNew, false);
    }

    public static String getECIconSet() {
        return getString(R.string.pref_ec_icons, "1");
    }

    /* Store last checksum of changelog for changelog display */
    public static long getLastChangelogChecksum() {
        return getLong(R.string.pref_changelog_last_checksum, 0);
    }

    public static void setLastChangelogChecksum(final long checksum) {
        putLong(R.string.pref_changelog_last_checksum, checksum);
    }

    public static List<String> getLastOpenedCaches() {
        final List<String> history = getStringList(R.string.pref_caches_history, StringUtils.EMPTY);
        return history.subList(0, Math.min(HISTORY_SIZE, history.size()));
    }

    public static void addCacheToHistory(@NonNull final String geocode) {
        final List<String> history = new ArrayList<>(getLastOpenedCaches());
        // bring entry to front, if it already existed
        history.remove(geocode);
        history.add(0, geocode);
        putStringList(R.string.pref_caches_history, history);
    }

    public static boolean useOldMapsforgeAPI() {
        return getBoolean(R.string.pref_old_mapsforge_api, false);
    }

    static void setOldMapsforgeAPI(final boolean useOldMapsforgeAPI) {
        putBoolean(R.string.pref_old_mapsforge_api, useOldMapsforgeAPI);
    }

    public static boolean useHardwareAcceleration() {
        return outdatedPhoneModelOrSdk() ? HwAccel.hwAccelShouldBeEnabled() :
                getBoolean(R.string.pref_hardware_acceleration, HwAccel.hwAccelShouldBeEnabled());
    }

    static void setUseHardwareAcceleration(final boolean useHardwareAcceleration) {
        putBoolean(R.string.pref_hardware_acceleration, useHardwareAcceleration);
        storePhoneModelAndSdk();
    }

    private static boolean outdatedPhoneModelOrSdk() {
        return !StringUtils.equals(PHONE_MODEL_AND_SDK, getString(R.string.pref_phone_model_and_sdk, null));
    }

    private static void storePhoneModelAndSdk() {
        putString(R.string.pref_phone_model_and_sdk, PHONE_MODEL_AND_SDK);
    }

    public static String getLastCacheLog() {
        return getString(R.string.pref_last_cache_log, StringUtils.EMPTY);
    }

    public static void setLastCacheLog(final String log) {
        putString(R.string.pref_last_cache_log, log);
    }

    public static String getLastTrackableLog() {
        return getString(R.string.pref_last_trackable_log, StringUtils.EMPTY);
    }

    public static void setLastTrackableLog(final String log) {
        putString(R.string.pref_last_trackable_log, log);
    }

    @Nullable
    public static String getHomeLocation() {
        return getString(R.string.pref_home_location, null);
    }

    public static void setHomeLocation(@NonNull final String homeLocation) {
        putString(R.string.pref_home_location, homeLocation);
    }

    public static void setForceOrientationSensor(final boolean forceOrientationSensor) {
        putBoolean(R.string.pref_force_orientation_sensor, forceOrientationSensor);
    }

    public static boolean useOrientationSensor(final Context context) {
        return OrientationProvider.hasOrientationSensor(context) &&
                (getBoolean(R.string.pref_force_orientation_sensor, false) ||
                    !(RotationProvider.hasRotationSensor(context) || MagnetometerAndAccelerometerProvider.hasMagnetometerAndAccelerometerSensors(context))
                );
    }

    /**
     * Get avatar URL by connector.
     *
     * @param connector the connector to retrieve the avatar information from
     * @return the avatar url
     */
    @NonNull
    public static String getAvatarUrl(@NonNull final ICredentials connector) {
        return getString(connector.getAvatarPreferenceKey(), null);
    }

    /**
     * Set avatar URL by connector.
     *
     * @param connector the connector to retrieve the avatar information from
     * @param avatarUrl the avatar url information to store
     */
    public static void setAvatarUrl(@NonNull final ICredentials connector, final String avatarUrl) {
        putString(connector.getAvatarPreferenceKey(), avatarUrl);
    }

    /**
     * Return the locale that should be used to display information to the user.
     *
     * @return either the system locale or an English one, depending on the settings
     */
    public static Locale getApplicationLocale() {
        return Settings.useEnglish() ? Locale.ENGLISH : Locale.getDefault();
    }

    public static void setRoutingMode(@NonNull final RoutingMode mode) {
        putString(R.string.pref_map_routing, mode.parameterValue);
        Routing.invalidateRouting();
    }

    public static RoutingMode getRoutingMode() {
        return RoutingMode.fromString(getString(R.string.pref_map_routing, "foot"));
    }

}
