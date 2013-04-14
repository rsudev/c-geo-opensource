package cgeo.geocaching;

import cgeo.geocaching.activity.AbstractActivity;
import cgeo.geocaching.apps.cache.navi.NavigationAppFactory;
import cgeo.geocaching.apps.cache.navi.NavigationAppFactory.NavigationAppsEnum;
import cgeo.geocaching.compatibility.Compatibility;
import cgeo.geocaching.connector.gc.Login;
import cgeo.geocaching.enumerations.StatusCode;
import cgeo.geocaching.files.SimpleDirChooser;
import cgeo.geocaching.maps.MapProviderFactory;
import cgeo.geocaching.maps.interfaces.MapSource;
import cgeo.geocaching.network.Cookies;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.network.Parameters;
import cgeo.geocaching.twitter.TwitterAuthorizationActivity;
import cgeo.geocaching.ui.Formatter;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.LogTemplateProvider;
import cgeo.geocaching.utils.LogTemplateProvider.LogTemplate;
import cgeo.geocaching.utils.RunnableWithArgument;

import ch.boye.httpclientandroidlib.HttpResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.openintents.intents.FileManagerIntents;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SettingsActivity extends AbstractActivity {

    private final static int SELECT_MAPFILE_REQUEST = 1;
    private final static int SELECT_GPX_EXPORT_REQUEST = 2;
    private final static int SELECT_GPX_IMPORT_REQUEST = 3;
    private final static int SELECT_THEMEFOLDER_REQUEST = 4;

    private ProgressDialog loginDialog = null;
    private ProgressDialog webDialog = null;
    private boolean enableTemplatesMenu = false;
    private Handler logInHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            try {
                if (loginDialog != null && loginDialog.isShowing()) {
                    loginDialog.dismiss();
                }

                if (msg.obj == null || (msg.obj instanceof Drawable)) {
                    helpDialog(res.getString(R.string.init_login_popup), res.getString(R.string.init_login_popup_ok),
                            (Drawable) msg.obj);
                } else {
                    helpDialog(res.getString(R.string.init_login_popup),
                            res.getString(R.string.init_login_popup_failed_reason) + " " +
                                    ((StatusCode) msg.obj).getErrorString(res) + ".");
                }
            } catch (Exception e) {
                showToast(res.getString(R.string.err_login_failed));

                Log.e("SettingsActivity.logInHandler", e);
            }

            if (loginDialog != null && loginDialog.isShowing()) {
                loginDialog.dismiss();
            }

            init();
        }
    };

    private Handler webAuthHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            try {
                if (webDialog != null && webDialog.isShowing()) {
                    webDialog.dismiss();
                }

                if (msg.what > 0) {
                    helpDialog(res.getString(R.string.init_sendToCgeo), res.getString(R.string.init_sendToCgeo_register_ok).replace("####", String.valueOf(msg.what)));
                } else {
                    helpDialog(res.getString(R.string.init_sendToCgeo), res.getString(R.string.init_sendToCgeo_register_fail));
                }
            } catch (Exception e) {
                showToast(res.getString(R.string.init_sendToCgeo_register_fail));

                Log.e("SettingsActivity.webHandler", e);
            }

            if (webDialog != null && webDialog.isShowing()) {
                webDialog.dismiss();
            }

            init();
        }
    };

    public SettingsActivity() {
        super("c:geo-configuration");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // init

        setTheme();
        setContentView(R.layout.init);
        setTitle(res.getString(R.string.settings));

        init();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        init();
    }

    @Override
    public void onPause() {
        saveValues();
        super.onPause();
    }

    @Override
    public void onStop() {
        saveValues();
        Compatibility.dataChanged(getPackageName());
        super.onStop();
    }

    @Override
    public void onDestroy() {
        saveValues();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, res.getString(R.string.init_clear)).setIcon(R.drawable.ic_menu_delete);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 0) {
            ((EditText) findViewById(R.id.username)).setText("");
            ((EditText) findViewById(R.id.password)).setText("");
            ((EditText) findViewById(R.id.passvote)).setText("");

            if (saveValues()) {
                showToast(res.getString(R.string.init_cleared));
            } else {
                showToast(res.getString(R.string.err_init_cleared));
            }

            finish();
        }

        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        if (enableTemplatesMenu) {
            menu.setHeaderTitle(R.string.init_signature_template_button);
            for (LogTemplate template : LogTemplateProvider.getTemplates()) {
                menu.add(0, template.getItemId(), 0, template.getResourceId());
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        LogTemplate template = LogTemplateProvider.getTemplate(item.getItemId());
        if (template != null) {
            return insertSignatureTemplate(template);
        }
        return super.onContextItemSelected(item);
    }

    private boolean insertSignatureTemplate(final LogTemplate template) {
        EditText sig = (EditText) findViewById(R.id.signature);
        String insertText = "[" + template.getTemplateString() + "]";
        insertAtPosition(sig, insertText, true);
        return true;
    }

    public void init() {

        // geocaching.com settings
        final CheckBox gcCheck = (CheckBox) findViewById(R.id.gc_option);
        gcCheck.setChecked(Settings.isGCConnectorActive());
        gcCheck.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setGCConnectorActive(gcCheck.isChecked());
            }
        });
        final ImmutablePair<String, String> login = Settings.getLogin();
        if (login != null) {
            ((EditText) findViewById(R.id.username)).setText(login.left);
            ((EditText) findViewById(R.id.password)).setText(login.right);
        }

        Button logMeIn = (Button) findViewById(R.id.log_me_in);
        logMeIn.setOnClickListener(new LoginListener());

        TextView legalNote = (TextView) findViewById(R.id.legal_note);
        legalNote.setClickable(true);
        legalNote.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.geocaching.com/about/termsofuse.aspx")));
            }
        });

        // opencaching.de settings
        final CheckBox ocCheck = (CheckBox) findViewById(R.id.oc_option);
        ocCheck.setChecked(Settings.isOCConnectorActive());
        ocCheck.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setOCConnectorActive(ocCheck.isChecked());
            }
        });
        EditText ocUserEdit = (EditText) findViewById(R.id.oc_username);
        if (ocUserEdit.getText().length() == 0) {
            ocUserEdit.setText(Settings.getOCConnectorUserName());
        }

        // gcvote settings
        final ImmutablePair<String, String> gcvoteLogin = Settings.getGCvoteLogin();
        if (null != gcvoteLogin && null != gcvoteLogin.right) {
            ((EditText) findViewById(R.id.passvote)).setText(gcvoteLogin.right);
        }

        // Twitter settings
        Button authorizeTwitter = (Button) findViewById(R.id.authorize_twitter);
        authorizeTwitter.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                Intent authIntent = new Intent(SettingsActivity.this, TwitterAuthorizationActivity.class);
                startActivity(authIntent);
            }
        });

        final CheckBox twitterButton = (CheckBox) findViewById(R.id.twitter_option);
        twitterButton.setChecked(Settings.isUseTwitter() && Settings.isTwitterLoginValid());
        twitterButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setUseTwitter(twitterButton.isChecked());
                if (Settings.isUseTwitter() && !Settings.isTwitterLoginValid()) {
                    Intent authIntent = new Intent(SettingsActivity.this, TwitterAuthorizationActivity.class);
                    startActivity(authIntent);
                }

                twitterButton.setChecked(Settings.isUseTwitter());
            }
        });

        // Signature settings
        EditText sigEdit = (EditText) findViewById(R.id.signature);
        if (sigEdit.getText().length() == 0) {
            sigEdit.setText(Settings.getSignature());
        }
        Button sigBtn = (Button) findViewById(R.id.signature_help);
        sigBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                helpDialog(res.getString(R.string.init_signature_help_title), res.getString(R.string.init_signature_help_text));
            }
        });
        Button templates = (Button) findViewById(R.id.signature_template);
        registerForContextMenu(templates);
        templates.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableTemplatesMenu = true;
                openContextMenu(v);
                enableTemplatesMenu = false;
            }
        });
        final CheckBox autoinsertButton = (CheckBox) findViewById(R.id.sigautoinsert);
        autoinsertButton.setChecked(Settings.isAutoInsertSignature());
        autoinsertButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setAutoInsertSignature(autoinsertButton.isChecked());
            }
        });

        // Cache details
        final CheckBox autoloadButton = (CheckBox) findViewById(R.id.autoload);
        autoloadButton.setChecked(Settings.isAutoLoadDescription());
        autoloadButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setAutoLoadDesc(autoloadButton.isChecked());
            }
        });

        final CheckBox ratingWantedButton = (CheckBox) findViewById(R.id.ratingwanted);
        ratingWantedButton.setChecked(Settings.isRatingWanted());
        ratingWantedButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setRatingWanted(ratingWantedButton.isChecked());
            }
        });

        final CheckBox elevationWantedButton = (CheckBox) findViewById(R.id.elevationwanted);
        elevationWantedButton.setChecked(Settings.isElevationWanted());
        elevationWantedButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setElevationWanted(elevationWantedButton.isChecked());
            }
        });

        final CheckBox friendLogsWantedButton = (CheckBox) findViewById(R.id.friendlogswanted);
        friendLogsWantedButton.setChecked(Settings.isFriendLogsWanted());
        friendLogsWantedButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setFriendLogsWanted(friendLogsWantedButton.isChecked());
            }
        });

        final CheckBox openLastDetailsPageButton = (CheckBox) findViewById(R.id.openlastdetailspage);
        openLastDetailsPageButton.setChecked(Settings.isOpenLastDetailsPage());
        openLastDetailsPageButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setOpenLastDetailsPage(openLastDetailsPageButton.isChecked());
            }
        });

        // Other settings
        final CheckBox skinButton = (CheckBox) findViewById(R.id.skin);
        skinButton.setChecked(Settings.isLightSkin());
        skinButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setLightSkin(skinButton.isChecked());
            }
        });

        final CheckBox addressButton = (CheckBox) findViewById(R.id.address);
        addressButton.setChecked(Settings.isShowAddress());
        addressButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setShowAddress(addressButton.isChecked());
            }
        });

        final CheckBox captchaButton = (CheckBox) findViewById(R.id.captcha);
        captchaButton.setEnabled(!Settings.isPremiumMember());
        captchaButton.setChecked(Settings.isShowCaptcha());
        captchaButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setShowCaptcha(captchaButton.isChecked());
            }
        });

        final CheckBox dirImgButton = (CheckBox) findViewById(R.id.loaddirectionimg);
        dirImgButton.setEnabled(!Settings.isPremiumMember());
        dirImgButton.setChecked(Settings.getLoadDirImg());
        dirImgButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setLoadDirImg(!Settings.getLoadDirImg());
                dirImgButton.setChecked(Settings.getLoadDirImg());
            }
        });

        final CheckBox useEnglishButton = (CheckBox) findViewById(R.id.useenglish);
        useEnglishButton.setChecked(Settings.isUseEnglish());
        useEnglishButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setUseEnglish(useEnglishButton.isChecked());
            }
        });

        final CheckBox excludeButton = (CheckBox) findViewById(R.id.exclude);
        excludeButton.setChecked(Settings.isExcludeMyCaches());
        excludeButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setExcludeMine(excludeButton.isChecked());
            }
        });

        final CheckBox disabledButton = (CheckBox) findViewById(R.id.disabled);
        disabledButton.setChecked(Settings.isExcludeDisabledCaches());
        disabledButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setExcludeDisabledCaches(disabledButton.isChecked());
            }
        });

        TextView showWaypointsThreshold = (TextView) findViewById(R.id.showwaypointsthreshold);
        showWaypointsThreshold.setText(String.valueOf(Settings.getWayPointsThreshold()));

        final CheckBox autovisitButton = (CheckBox) findViewById(R.id.trackautovisit);
        autovisitButton.setChecked(Settings.isTrackableAutoVisit());
        autovisitButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setTrackableAutoVisit(autovisitButton.isChecked());
            }
        });

        final CheckBox offlineButton = (CheckBox) findViewById(R.id.offline);
        offlineButton.setChecked(Settings.isStoreOfflineMaps());
        offlineButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setStoreOfflineMaps(offlineButton.isChecked());
            }
        });

        final CheckBox offlineWpButton = (CheckBox) findViewById(R.id.offline_wp);
        offlineWpButton.setChecked(Settings.isStoreOfflineWpMaps());
        offlineWpButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setStoreOfflineWpMaps(offlineWpButton.isChecked());
            }
        });

        final CheckBox saveLogImgButton = (CheckBox) findViewById(R.id.save_log_img);
        saveLogImgButton.setChecked(Settings.isStoreLogImages());
        saveLogImgButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setStoreLogImages(saveLogImgButton.isChecked());
            }
        });

        final CheckBox livelistButton = (CheckBox) findViewById(R.id.livelist);
        livelistButton.setChecked(Settings.isLiveList());
        livelistButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setLiveList(livelistButton.isChecked());
            }
        });

        final CheckBox unitsButton = (CheckBox) findViewById(R.id.units);
        unitsButton.setChecked(!Settings.isUseMetricUnits());
        unitsButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setUseMetricUnits(!unitsButton.isChecked());
            }
        });

        final CheckBox logOffline = (CheckBox) findViewById(R.id.log_offline);
        logOffline.setChecked(Settings.getLogOffline());
        logOffline.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setLogOffline(!Settings.getLogOffline());
                logOffline.setChecked(Settings.getLogOffline());
            }
        });

        final CheckBox chooseList = (CheckBox) findViewById(R.id.choose_list);
        chooseList.setChecked(Settings.getChooseList());
        chooseList.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setChooseList(!Settings.getChooseList());
                chooseList.setChecked(Settings.getChooseList());
            }
        });

        final CheckBox plainLogs = (CheckBox) findViewById(R.id.plain_logs);
        plainLogs.setChecked(Settings.getPlainLogs());
        plainLogs.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setPlainLogs(!Settings.getPlainLogs());
                plainLogs.setChecked(Settings.getPlainLogs());
            }
        });

        // Workaround for cspire customers on mobile connections #1843
        final CheckBox useNativeUserAgent = (CheckBox) findViewById(R.id.use_native_ua);
        useNativeUserAgent.setChecked(Settings.getUseNativeUa());
        useNativeUserAgent.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setUseNativeUa(!Settings.getUseNativeUa());
                useNativeUserAgent.setChecked(Settings.getUseNativeUa());
            }
        });

        // Altitude settings
        EditText altitudeEdit = (EditText) findViewById(R.id.altitude);
        altitudeEdit.setText(String.valueOf(Settings.getAltCorrection()));

        //Send2cgeo settings
        String webDeviceName = Settings.getWebDeviceName();

        if (StringUtils.isNotBlank(webDeviceName)) {
            ((EditText) findViewById(R.id.webDeviceName)).setText(webDeviceName);
        } else {
            String s = android.os.Build.MODEL;
            ((EditText) findViewById(R.id.webDeviceName)).setText(s);
        }

        Button webAuth = (Button) findViewById(R.id.sendToCgeo_register);
        webAuth.setOnClickListener(new WebAuthListener());

        // Map source settings
        updateMapSourceMenu();

        Button selectMapDirectory = (Button) findViewById(R.id.select_map_directory);
        selectMapDirectory.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent selectIntent = new Intent(SettingsActivity.this, SelectMapfileActivity.class);
                startActivityForResult(selectIntent, SELECT_MAPFILE_REQUEST);
            }
        });

        // Theme folder settings
        initThemefolderEdittext(false);

        Button selectThemefolder = (Button) findViewById(R.id.select_themefolder);
        selectThemefolder.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                selectDirectory(Settings.getCustomRenderThemeBaseFolder(), SELECT_THEMEFOLDER_REQUEST);
            }
        });

        // GPX Export directory
        final EditText gpxExportDir = (EditText) findViewById(R.id.gpx_exportdir);
        gpxExportDir.setText(Settings.getGpxExportDir());
        Button selectGpxExportDir = (Button) findViewById(R.id.select_gpx_exportdir);
        selectGpxExportDir.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                selectDirectory(Settings.getGpxExportDir(), SELECT_GPX_EXPORT_REQUEST);
            }
        });

        // GPX Import directory
        final EditText gpxImportDir = (EditText) findViewById(R.id.gpx_importdir);
        gpxImportDir.setText(Settings.getGpxImportDir());
        Button selectGpxImportDir = (Button) findViewById(R.id.select_gpx_importdir);
        selectGpxImportDir.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                selectDirectory(Settings.getGpxImportDir(), SELECT_GPX_IMPORT_REQUEST);
            }
        });

        // Display trail on map
        final CheckBox trailButton = (CheckBox) findViewById(R.id.trail);
        trailButton.setChecked(Settings.isMapTrail());
        trailButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setMapTrail(trailButton.isChecked());
            }
        });

        // Default navigation tool settings
        Spinner defaultNavigationToolSelector = (Spinner) findViewById(R.id.default_navigation_tool);
        final List<NavigationAppsEnum> apps = NavigationAppFactory.getInstalledDefaultNavigationApps();
        ArrayAdapter<NavigationAppsEnum> naviAdapter = new ArrayAdapter<NavigationAppsEnum>(this, android.R.layout.simple_spinner_item, apps) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getView(position, convertView, parent);
                textView.setText(getItem(position).app.getName());
                return textView;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getDropDownView(position, convertView, parent);
                textView.setText(getItem(position).app.getName());
                return textView;
            }
        };
        naviAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        defaultNavigationToolSelector.setAdapter(naviAdapter);
        int defaultNavigationTool = Settings.getDefaultNavigationTool();
        int ordinal = 0;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).id == defaultNavigationTool) {
                ordinal = i;
                break;
            }
        }
        defaultNavigationToolSelector.setSelection(ordinal);
        defaultNavigationToolSelector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                NavigationAppsEnum item = (NavigationAppsEnum) parent.getItemAtPosition(position);
                if (item != null) {
                    Settings.setDefaultNavigationTool(item.id);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // noop
            }
        });

        // 2nd Default navigation tool settings
        Spinner defaultNavigationTool2Selector = (Spinner) findViewById(R.id.default_navigation_tool_2);
        //        final List<NavigationAppsEnum> apps = NavigationAppFactory.getInstalledNavigationApps(this);
        ArrayAdapter<NavigationAppsEnum> navi2Adapter = new ArrayAdapter<NavigationAppsEnum>(this, android.R.layout.simple_spinner_item, apps) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getView(position, convertView, parent);
                textView.setText(getItem(position).app.getName());
                return textView;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getDropDownView(position, convertView, parent);
                textView.setText(getItem(position).app.getName());
                return textView;
            }
        };
        navi2Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        defaultNavigationTool2Selector.setAdapter(navi2Adapter);
        int defaultNavigationTool2 = Settings.getDefaultNavigationTool2();
        int ordinal2 = 0;
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).id == defaultNavigationTool2) {
                ordinal2 = i;
                break;
            }
        }
        defaultNavigationTool2Selector.setSelection(ordinal2);
        defaultNavigationTool2Selector.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                NavigationAppsEnum item = (NavigationAppsEnum) parent.getItemAtPosition(position);
                if (item != null) {
                    Settings.setDefaultNavigationTool2(item.id);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
                // noop
            }
        });

        refreshBackupLabel();

        // Database location
        refreshDbOnSDCardSetting();

        final CheckBox dbOnSDCardButton = (CheckBox) findViewById(R.id.dbonsdcard);
        dbOnSDCardButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                app.moveDatabase(SettingsActivity.this);
            }
        });

        // Debug settings
        final CheckBox debugButton = (CheckBox) findViewById(R.id.debug);
        debugButton.setChecked(Settings.isDebug());
        debugButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setDebug(!Settings.isDebug());
                debugButton.setChecked(Settings.isDebug());
            }
        });
    }

    private void updateMapSourceMenu() {
        Collection<String> mapSourceNames = new ArrayList<String>();
        for (MapSource mapSource : MapProviderFactory.getMapSources()) {
            mapSourceNames.add(mapSource.getName());
        }
        Spinner mapSourceSelector = (Spinner) findViewById(R.id.mapsource);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item, mapSourceNames.toArray(new String[mapSourceNames.size()]));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapSourceSelector.setAdapter(adapter);
        final int index = MapProviderFactory.getMapSources().indexOf(Settings.getMapSource());
        mapSourceSelector.setSelection(index);
        mapSourceSelector.setOnItemSelectedListener(new ChangeMapSourceListener());

        initMapDirectoryEdittext(false);
    }

    private void initMapDirectoryEdittext(boolean setFocus) {
        final EditText mapDirectoryEdit = (EditText) findViewById(R.id.map_directory);
        mapDirectoryEdit.setText(Settings.getMapFileDirectory());
        if (setFocus) {
            mapDirectoryEdit.requestFocus();
        }
    }

    private void initThemefolderEdittext(boolean setFocus) {
        EditText themeFileEdit = (EditText) findViewById(R.id.themefolder);
        themeFileEdit.setText(Settings.getCustomRenderThemeBaseFolder());
        if (setFocus) {
            themeFileEdit.requestFocus();
        }
    }

    /**
     * @param view
     *            unused here but needed since this method is referenced from XML layout
     */
    public void backup(View view) {
        // avoid overwriting an existing backup with an empty database (can happen directly after reinstalling the app)
        if (cgData.getAllCachesCount() == 0) {
            helpDialog(res.getString(R.string.init_backup), res.getString(R.string.init_backup_unnecessary));
            return;
        }

        final ProgressDialog dialog = ProgressDialog.show(this, res.getString(R.string.init_backup), res.getString(R.string.init_backup_running), true, false);
        new Thread() {
            @Override
            public void run() {
                final String backupFileName = cgData.backupDatabase();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        helpDialog(res.getString(R.string.init_backup_backup),
                                backupFileName != null ? res.getString(R.string.init_backup_success) + "\n" + backupFileName : res.getString(R.string.init_backup_failed));
                        refreshBackupLabel();
                    }
                });
            }
        }.start();
    }

    private void refreshBackupLabel() {
        TextView lastBackup = (TextView) findViewById(R.id.backup_last);
        File lastBackupFile = cgData.getRestoreFile();
        if (lastBackupFile != null) {
            lastBackup.setText(res.getString(R.string.init_backup_last) + " " + Formatter.formatTime(lastBackupFile.lastModified()) + ", " + Formatter.formatDate(lastBackupFile.lastModified()));
        } else {
            lastBackup.setText(res.getString(R.string.init_backup_last_no));
        }
    }

    private void refreshDbOnSDCardSetting() {
        final CheckBox dbOnSDCardButton = (CheckBox) findViewById(R.id.dbonsdcard);
        dbOnSDCardButton.setChecked(Settings.isDbOnSDCard());
    }

    /**
     * @param view
     *            unused here but needed since this method is referenced from XML layout
     */
    public void restore(View view) {
        app.restoreDatabase(this);
    }

    public boolean saveValues() {
        String usernameNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.username)).getText().toString());
        String passwordNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.password)).getText().toString());
        String passvoteNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.passvote)).getText().toString());
        // don't trim signature, user may want to have whitespace at the beginning
        String signatureNew = ((EditText) findViewById(R.id.signature)).getText().toString();
        String mapDirectoryNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.map_directory)).getText().toString());
        String themesDirectoryNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.themefolder)).getText().toString());
        String ocUserName = StringUtils.trimToEmpty(((EditText) findViewById(R.id.oc_username)).getText().toString());

        String altitudeNew = StringUtils.trimToNull(((EditText) findViewById(R.id.altitude)).getText().toString());
        int altitudeNewInt = parseNumber(altitudeNew, 0);

        TextView field = (TextView) findViewById(R.id.showwaypointsthreshold);
        final int waypointThreshold = parseNumber(field.getText().toString(), 5);

        final boolean status1 = Settings.setLogin(usernameNew, passwordNew);
        final boolean status2 = Settings.setGCvoteLogin(passvoteNew);
        final boolean status3 = Settings.setSignature(signatureNew);
        final boolean status4 = Settings.setAltCorrection(altitudeNewInt);
        final boolean status5 = Settings.setMapFileDirectory(mapDirectoryNew);
        final boolean status6 = Settings.setCustomRenderThemeBaseFolder(themesDirectoryNew);
        final boolean status7 = Settings.setOCConnectorUserName(ocUserName);
        Settings.setShowWaypointsThreshold(waypointThreshold);

        String importNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.gpx_importdir)).getText().toString());
        String exportNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.gpx_exportdir)).getText().toString());
        Settings.setGpxImportDir(importNew);
        Settings.setGpxExportDir(exportNew);

        return status1 && status2 && status3 && status4 && status5 && status6 && status7;
    }

    /**
     * Returns the integer value from the string
     *
     * @param field
     *            the field to retrieve the integer value from
     * @param defaultValue
     *            the default value
     * @return either the field content or the default value
     */

    static private int parseNumber(final String number, int defaultValue) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static class ChangeMapSourceListener implements OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> arg0, View arg1, int position,
                long arg3) {
            Settings.setMapSource(MapProviderFactory.getMapSources().get(position));
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
            arg0.setSelection(MapProviderFactory.getMapSources().indexOf(Settings.getMapSource()));
        }
    }

    private class LoginListener implements View.OnClickListener {

        @Override
        public void onClick(View arg0) {
            final String username = ((EditText) findViewById(R.id.username)).getText().toString();
            final String password = ((EditText) findViewById(R.id.password)).getText().toString();

            if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
                showToast(res.getString(R.string.err_missing_auth));
                return;
            }

            loginDialog = ProgressDialog.show(SettingsActivity.this, res.getString(R.string.init_login_popup), res.getString(R.string.init_login_popup_working), true);
            loginDialog.setCancelable(false);

            Settings.setLogin(username, password);
            Cookies.clearCookies();

            (new Thread() {

                @Override
                public void run() {
                    final StatusCode loginResult = Login.login();
                    Object payload = loginResult;
                    if (loginResult == StatusCode.NO_ERROR) {
                        Login.detectGcCustomDate();
                        payload = Login.downloadAvatarAndGetMemberStatus();
                    }
                    logInHandler.obtainMessage(0, payload).sendToTarget();
                }
            }).start();
        }
    }

    private class WebAuthListener implements View.OnClickListener {

        @Override
        public void onClick(View arg0) {
            final String deviceName = ((EditText) findViewById(R.id.webDeviceName)).getText().toString();
            final String deviceCode = Settings.getWebDeviceCode();

            if (StringUtils.isBlank(deviceName)) {
                showToast(res.getString(R.string.err_missing_device_name));
                return;
            }

            webDialog = ProgressDialog.show(SettingsActivity.this, res.getString(R.string.init_sendToCgeo), res.getString(R.string.init_sendToCgeo_registering), true);
            webDialog.setCancelable(false);

            (new Thread() {

                @Override
                public void run() {
                    int pin = 0;

                    final String nam = StringUtils.defaultString(deviceName);
                    final String cod = StringUtils.defaultString(deviceCode);

                    final Parameters params = new Parameters("name", nam, "code", cod);
                    HttpResponse response = Network.getRequest("http://send2.cgeo.org/auth.html", params);

                    if (response != null && response.getStatusLine().getStatusCode() == 200) {
                        //response was OK
                        String[] strings = Network.getResponseData(response).split(",");
                        try {
                            pin = Integer.parseInt(strings[1].trim());
                        } catch (Exception e) {
                            Log.e("webDialog", e);
                        }
                        String code = strings[0];
                        Settings.setWebNameCode(nam, code);
                    }

                    webAuthHandler.sendEmptyMessage(pin);
                }
            }).start();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case SELECT_MAPFILE_REQUEST:
                if (data.hasExtra(Intents.EXTRA_MAP_FILE)) {
                    final String mapFile = data.getStringExtra(Intents.EXTRA_MAP_FILE);
                    Settings.setMapFile(mapFile);
                    if (!Settings.isValidMapFile(Settings.getMapFile())) {
                        showToast(res.getString(R.string.warn_invalid_mapfile));
                    }
                }
                updateMapSourceMenu();
                initMapDirectoryEdittext(true);
                break;
            case SELECT_GPX_EXPORT_REQUEST:
                checkDirectory(resultCode, data, R.id.gpx_exportdir, new RunnableWithArgument<String>() {

                    @Override
                    public void run(String directory) {
                        Settings.setGpxExportDir(directory);
                    }
                });
                break;
            case SELECT_GPX_IMPORT_REQUEST:
                checkDirectory(resultCode, data, R.id.gpx_importdir, new RunnableWithArgument<String>() {

                    @Override
                    public void run(String directory) {
                        Settings.setGpxImportDir(directory);
                    }
                });
                break;
            case SELECT_THEMEFOLDER_REQUEST:
                checkDirectory(resultCode, data, R.id.themefolder, new RunnableWithArgument<String>() {

                    @Override
                    public void run(String directory) {
                        Settings.setCustomRenderThemeBaseFolder(directory);
                    }
                });
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void checkDirectory(int resultCode, Intent data, int textField, RunnableWithArgument<String> runnableSetDir) {
        if (resultCode != RESULT_OK) {
            return;
        }
        final String directory = new File(data.getData().getPath()).getAbsolutePath();
        if (StringUtils.isNotBlank(directory)) {
            runnableSetDir.run(directory);
            EditText directoryText = (EditText) findViewById(textField);
            directoryText.setText(directory);
            directoryText.requestFocus();
        }
    }

    private void selectDirectory(String startDirectory, int directoryKind) {
        try {
            final Intent dirChooser = new Intent(FileManagerIntents.ACTION_PICK_DIRECTORY);
            if (StringUtils.isNotBlank(startDirectory)) {
                dirChooser.setData(Uri.fromFile(new File(startDirectory)));
            }
            dirChooser.putExtra(FileManagerIntents.EXTRA_TITLE, res.getString(R.string.simple_dir_chooser_title));
            dirChooser.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, res.getString(android.R.string.ok));
            startActivityForResult(dirChooser, directoryKind);
        } catch (android.content.ActivityNotFoundException ex) {
            // OI file manager not available
            final Intent dirChooser = new Intent(this, SimpleDirChooser.class);
            dirChooser.putExtra(Intents.EXTRA_START_DIR, startDirectory);
            startActivityForResult(dirChooser, directoryKind);
        }
    }

    public static void startActivity(Context fromActivity) {
        final Intent initIntent = new Intent(fromActivity, SettingsActivity.class);
        fromActivity.startActivity(initIntent);
    }

}
