package cgeo.geocaching;

import cgeo.geocaching.activity.AbstractActivity;
import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.connector.ConnectorFactory;
import cgeo.geocaching.connector.IConnector;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.enumerations.LoadFlags;
import cgeo.geocaching.enumerations.LoadFlags.SaveFlag;
import cgeo.geocaching.enumerations.WaypointType;
import cgeo.geocaching.geopoint.DistanceParser;
import cgeo.geocaching.geopoint.Geopoint;
import cgeo.geocaching.geopoint.GeopointFormatter;
import cgeo.geocaching.utils.BaseUtils;
import cgeo.geocaching.utils.GeoDirHandler;
import cgeo.geocaching.utils.Log;

import org.apache.commons.lang3.StringUtils;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class EditWaypointActivity extends AbstractActivity {

    private String geocode = null;
    private int id = -1;
    private ProgressDialog waitDialog = null;
    private Waypoint waypoint = null;
    private Geopoint gpTemp = null;
    private WaypointType type = WaypointType.OWN;
    private String prefix = "OWN";
    private String lookup = "---";
    private boolean own = true;
    ArrayList<WaypointType> wpTypes = null;
    String distanceUnit = "";

    /**
     * number of waypoints that the corresponding cache has until now
     */
    private int wpCount = 0;
    private Handler loadWaypointHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            try {
                if (waypoint == null) {
                    id = -1;
                } else {
                    geocode = waypoint.getGeocode();
                    type = waypoint.getWaypointType();
                    prefix = waypoint.getPrefix();
                    lookup = waypoint.getLookup();
                    own = waypoint.isUserDefined();

                    if (waypoint.getCoords() != null) {
                        ((Button) findViewById(R.id.buttonLatitude)).setText(waypoint.getCoords().format(GeopointFormatter.Format.LAT_DECMINUTE));
                        ((Button) findViewById(R.id.buttonLongitude)).setText(waypoint.getCoords().format(GeopointFormatter.Format.LON_DECMINUTE));
                    }
                    ((EditText) findViewById(R.id.name)).setText(Html.fromHtml(StringUtils.trimToEmpty(waypoint.getName())).toString());
                    if (BaseUtils.containsHtml(waypoint.getNote())) {
                        ((EditText) findViewById(R.id.note)).setText(Html.fromHtml(StringUtils.trimToEmpty(waypoint.getNote())).toString());
                    }
                    else {
                        ((EditText) findViewById(R.id.note)).setText(StringUtils.trimToEmpty(waypoint.getNote()));
                    }
                    cgCache cache = cgData.loadCache(geocode, LoadFlags.LOAD_CACHE_ONLY);
                    setCoordsModificationVisibility(ConnectorFactory.getConnector(geocode), cache);
                }

                if (own) {
                    initializeWaypointTypeSelector();
                }

                initializeDistanceUnitSelector();
            } catch (Exception e) {
                Log.e("EditWaypointActivity.loadWaypointHandler", e);
            } finally {
                if (waitDialog != null) {
                    waitDialog.dismiss();
                    waitDialog = null;
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme();
        setContentView(R.layout.edit_waypoint_activity);
        setTitle("waypoint");

        // get parameters
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            geocode = extras.getString("geocode");
            wpCount = extras.getInt("count", 0);
            id = extras.getInt("waypoint");
        }

        if (StringUtils.isBlank(geocode) && id <= 0) {
            showToast(res.getString(R.string.err_waypoint_cache_unknown));

            finish();
            return;
        }

        if (id <= 0) {
            setTitle(res.getString(R.string.waypoint_add_title));
        } else {
            setTitle(res.getString(R.string.waypoint_edit_title));
        }

        Button buttonLat = (Button) findViewById(R.id.buttonLatitude);
        buttonLat.setOnClickListener(new coordDialogListener());
        Button buttonLon = (Button) findViewById(R.id.buttonLongitude);
        buttonLon.setOnClickListener(new coordDialogListener());

        Button addWaypoint = (Button) findViewById(R.id.add_waypoint);
        addWaypoint.setOnClickListener(new coordsListener());

        List<String> wayPointNames = new ArrayList<String>();
        for (WaypointType wpt : WaypointType.ALL_TYPES_EXCEPT_OWN_AND_ORIGINAL) {
            wayPointNames.add(wpt.getL10n());
        }
        AutoCompleteTextView textView = (AutoCompleteTextView) findViewById(R.id.name);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, wayPointNames);
        textView.setAdapter(adapter);

        if (id > 0) {
            Spinner waypointTypeSelector = (Spinner) findViewById(R.id.type);
            waypointTypeSelector.setVisibility(View.GONE);

            waitDialog = ProgressDialog.show(this, null, res.getString(R.string.waypoint_loading), true);
            waitDialog.setCancelable(true);

            (new loadWaypoint()).start();
        } else {
            initializeWaypointTypeSelector();
        }

        if (geocode != null) {
            cgCache cache = cgData.loadCache(geocode, LoadFlags.LOAD_CACHE_OR_DB);
            IConnector con = ConnectorFactory.getConnector(geocode);
            setCoordsModificationVisibility(con, cache);
        }

        initializeDistanceUnitSelector();

        disableSuggestions((EditText) findViewById(R.id.distance));
    }

    private void setCoordsModificationVisibility(IConnector con, cgCache cache) {
        if (cache != null && (cache.getType() == CacheType.MYSTERY || cache.getType() == CacheType.MULTI)) {
            findViewById(R.id.modify_cache_coordinates_group).setVisibility(View.VISIBLE);
            findViewById(R.id.modify_cache_coordinates_local_and_remote).setVisibility(con.supportsOwnCoordinates() ? View.VISIBLE : View.GONE);
        } else {
            findViewById(R.id.modify_cache_coordinates_group).setVisibility(View.GONE);
            findViewById(R.id.modify_cache_coordinates_local_and_remote).setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        geoDirHandler.startGeo();

        if (id > 0) {
            if (waitDialog == null) {
                waitDialog = ProgressDialog.show(this, null, res.getString(R.string.waypoint_loading), true);
                waitDialog.setCancelable(true);

                (new loadWaypoint()).start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onPause() {
        geoDirHandler.stopGeo();
        super.onPause();
    }

    private void initializeWaypointTypeSelector() {

        Spinner waypointTypeSelector = (Spinner) findViewById(R.id.type);

        wpTypes = new ArrayList<WaypointType>(WaypointType.ALL_TYPES_EXCEPT_OWN_AND_ORIGINAL);
        ArrayAdapter<WaypointType> wpAdapter = new ArrayAdapter<WaypointType>(this, android.R.layout.simple_spinner_item, wpTypes.toArray(new WaypointType[wpTypes.size()]));
        wpAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        waypointTypeSelector.setAdapter(wpAdapter);

        int typeIndex = wpTypes.indexOf(type);
        if (typeIndex < 0) {
            typeIndex = wpTypes.indexOf(WaypointType.WAYPOINT);
        }

        waypointTypeSelector.setSelection(typeIndex);
        waypointTypeSelector.setOnItemSelectedListener(new changeWaypointType(this));

        waypointTypeSelector.setVisibility(View.VISIBLE);
    }

    private void initializeDistanceUnitSelector() {

        Spinner distanceUnitSelector = (Spinner) findViewById(R.id.distanceUnit);

        if (StringUtils.isBlank(distanceUnit)) {
            if (Settings.isUseMetricUnits()) {
                distanceUnitSelector.setSelection(0); // m
                distanceUnit = res.getStringArray(R.array.distance_units)[0];
            } else {
                distanceUnitSelector.setSelection(2); // ft
                distanceUnit = res.getStringArray(R.array.distance_units)[2];
            }
        }

        distanceUnitSelector.setOnItemSelectedListener(new changeDistanceUnit(this));
    }

    final private GeoDirHandler geoDirHandler = new GeoDirHandler() {
        @Override
        public void updateGeoData(final IGeoData geo) {
            if (geo.getCoords() == null) {
                return;
            }

            try {
                Button bLat = (Button) findViewById(R.id.buttonLatitude);
                Button bLon = (Button) findViewById(R.id.buttonLongitude);
                bLat.setHint(geo.getCoords().format(GeopointFormatter.Format.LAT_DECMINUTE_RAW));
                bLon.setHint(geo.getCoords().format(GeopointFormatter.Format.LON_DECMINUTE_RAW));
            } catch (final Exception e) {
                Log.e("failed to update location", e);
            }
        }
    };

    private class loadWaypoint extends Thread {

        @Override
        public void run() {
            try {
                waypoint = cgData.loadWaypoint(id);

                loadWaypointHandler.sendMessage(Message.obtain());
            } catch (Exception e) {
                Log.e("cgeowaypoint.loadWaypoint.run", e);
            }
        }
    }

    private class coordDialogListener implements View.OnClickListener {

        @Override
        public void onClick(View arg0) {
            Geopoint gp = null;
            if (waypoint != null && waypoint.getCoords() != null) {
                gp = waypoint.getCoords();
            } else if (gpTemp != null) {
                gp = gpTemp;
            }
            cgCache cache = cgData.loadCache(geocode, LoadFlags.LOAD_WAYPOINTS);
            cgeocoords coordsDialog = new cgeocoords(EditWaypointActivity.this, cache, gp, app.currentGeo());
            coordsDialog.setCancelable(true);
            coordsDialog.setOnCoordinateUpdate(new cgeocoords.CoordinateUpdate() {
                @Override
                public void update(final Geopoint gp) {
                    ((Button) findViewById(R.id.buttonLatitude)).setText(gp.format(GeopointFormatter.Format.LAT_DECMINUTE));
                    ((Button) findViewById(R.id.buttonLongitude)).setText(gp.format(GeopointFormatter.Format.LON_DECMINUTE));
                    if (waypoint != null) {
                        waypoint.setCoords(gp);
                    } else {
                        gpTemp = gp;
                    }
                }
            });
            coordsDialog.show();
        }
    }

    private static class changeWaypointType implements OnItemSelectedListener {

        private changeWaypointType(EditWaypointActivity wpView) {
            this.wpView = wpView;
        }

        private EditWaypointActivity wpView;

        @Override
        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
                long arg3) {
            if (null != wpView.wpTypes) {
                wpView.type = wpView.wpTypes.get(arg2);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
            if (null != wpView.wpTypes) {
                arg0.setSelection(wpView.wpTypes.indexOf(wpView.type));
            }
        }
    }

    private static class changeDistanceUnit implements OnItemSelectedListener {

        private changeDistanceUnit(EditWaypointActivity unitView) {
            this.unitView = unitView;
        }

        private EditWaypointActivity unitView;

        @Override
        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
                long arg3) {
            unitView.distanceUnit = (String) arg0.getItemAtPosition(arg2);
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
        }
    }

    public static final int SUCCESS = 0;
    public static final int UPLOAD_START = 1;
    public static final int UPLOAD_ERROR = 2;
    public static final int UPLOAD_NOT_POSSIBLE = 3;
    public static final int UPLOAD_SUCCESS = 4;
    public static final int SAVE_ERROR = 5;

    private class coordsListener implements View.OnClickListener {

        @Override
        public void onClick(View arg0) {
            final String bearingText = ((EditText) findViewById(R.id.bearing)).getText().toString();
            // combine distance from EditText and distanceUnit saved from Spinner
            final String distanceText = ((EditText) findViewById(R.id.distance)).getText().toString() + distanceUnit;
            final String latText = ((Button) findViewById(R.id.buttonLatitude)).getText().toString();
            final String lonText = ((Button) findViewById(R.id.buttonLongitude)).getText().toString();

            if (StringUtils.isBlank(bearingText) && StringUtils.isBlank(distanceText)
                    && StringUtils.isBlank(latText) && StringUtils.isBlank(lonText)) {
                helpDialog(res.getString(R.string.err_point_no_position_given_title), res.getString(R.string.err_point_no_position_given));
                return;
            }

            Geopoint coords;

            if (StringUtils.isNotBlank(latText) && StringUtils.isNotBlank(lonText)) {
                try {
                    coords = new Geopoint(latText, lonText);
                } catch (Geopoint.ParseException e) {
                    showToast(res.getString(e.resource));
                    return;
                }
            } else {
                final IGeoData geo = app.currentGeo();
                if (geo.getCoords() == null) {
                    showToast(res.getString(R.string.err_point_curr_position_unavailable));
                    return;
                }
                coords = geo.getCoords();
            }

            if (StringUtils.isNotBlank(bearingText) && StringUtils.isNotBlank(distanceText)) {
                // bearing & distance
                double bearing;
                try {
                    bearing = Double.parseDouble(bearingText);
                } catch (NumberFormatException e) {
                    helpDialog(res.getString(R.string.err_point_bear_and_dist_title), res.getString(R.string.err_point_bear_and_dist));
                    return;
                }

                double distance;
                try {
                    distance = DistanceParser.parseDistance(distanceText, Settings.isUseMetricUnits());
                } catch (NumberFormatException e) {
                    showToast(res.getString(R.string.err_parse_dist));
                    return;
                }

                coords = coords.project(bearing, distance);
            }

            // if no name is given, just give the waypoint its number as name
            final String givenName = ((EditText) findViewById(R.id.name)).getText().toString().trim();
            final String name = StringUtils.isNotEmpty(givenName) ? givenName : res.getString(R.string.waypoint) + " " + (wpCount + 1);
            final String note = ((EditText) findViewById(R.id.note)).getText().toString().trim();
            final Geopoint coordsToSave = coords;
            final ProgressDialog progress = ProgressDialog.show(EditWaypointActivity.this, getString(R.string.cache), getString(R.string.waypoint_being_saved), true);
            final Handler finishHandler = new Handler() {

                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case UPLOAD_SUCCESS:
                            showToast(getString(R.string.waypoint_coordinates_has_been_modified_on_website, coordsToSave));
                            //$FALL-THROUGH$
                        case SUCCESS:
                            progress.dismiss();
                            finish();
                            break;
                        case UPLOAD_START:
                            progress.setMessage(getString(R.string.waypoint_coordinates_uploading_to_website, coordsToSave));
                            break;
                        case UPLOAD_ERROR:
                            progress.dismiss();
                            finish();
                            showToast(getString(R.string.waypoint_coordinates_upload_error));
                            break;
                        case UPLOAD_NOT_POSSIBLE:
                            progress.dismiss();
                            finish();
                            showToast(getString(R.string.waypoint_coordinates_couldnt_be_modified_on_website));
                            break;
                        case SAVE_ERROR:
                            progress.dismiss();
                            finish(); //TODO: should we close activity here ?
                            showToast(res.getString(R.string.err_waypoint_add_failed));
                            break;
                    }
                }
            };

            class SaveWptTask extends AsyncTask<Void, Void, Void> {

                @Override
                protected Void doInBackground(Void... params) {
                    final Waypoint waypoint = new Waypoint(name, type, own);
                    waypoint.setGeocode(geocode);
                    waypoint.setPrefix(prefix);
                    waypoint.setLookup(lookup);
                    waypoint.setCoords(coordsToSave);
                    waypoint.setNote(note);
                    waypoint.setId(id);

                    cgCache cache = cgData.loadCache(geocode, LoadFlags.LOAD_WAYPOINTS);
                    if (null != cache && cache.addOrChangeWaypoint(waypoint, true)) {
                        cgData.saveCache(cache, EnumSet.of(SaveFlag.SAVE_DB));
                        StaticMapsProvider.removeWpStaticMaps(id, geocode);
                        if (Settings.isStoreOfflineWpMaps()) {
                            StaticMapsProvider.storeWaypointStaticMap(cache, waypoint, false);
                        }
                        final RadioButton modifyLocal = (RadioButton) findViewById(R.id.modify_cache_coordinates_local);
                        final RadioButton modifyBoth = (RadioButton) findViewById(R.id.modify_cache_coordinates_local_and_remote);
                        if (modifyLocal.isChecked() || modifyBoth.isChecked()) {
                            if (!cache.hasUserModifiedCoords()) {
                                final Waypoint origWaypoint = new Waypoint(cgeoapplication.getInstance().getString(R.string.cache_coordinates_original), WaypointType.ORIGINAL, false);
                                origWaypoint.setCoords(cache.getCoords());
                                cache.addOrChangeWaypoint(origWaypoint, false);
                                cache.setUserModifiedCoords(true);
                            }
                            cache.setCoords(waypoint.getCoords());
                            cgData.saveChangedCache(cache);
                        }
                        if (modifyBoth.isChecked() && waypoint.getCoords() != null) {
                            finishHandler.sendEmptyMessage(UPLOAD_START);

                            if (cache.supportsOwnCoordinates()) {
                                boolean result = uploadModifiedCoords(cache, waypoint.getCoords());
                                finishHandler.sendEmptyMessage(result ? SUCCESS : UPLOAD_ERROR);
                            } else {
                                showToast(getString(R.string.waypoint_coordinates_couldnt_be_modified_on_website));
                                finishHandler.sendEmptyMessage(UPLOAD_NOT_POSSIBLE);
                            }
                        } else {
                            finishHandler.sendEmptyMessage(SUCCESS);
                        }
                    } else {
                        finishHandler.sendEmptyMessage(SAVE_ERROR);
                    }
                    return null;
                }
            }
            new SaveWptTask().execute();
        }
    }

    private static boolean uploadModifiedCoords(final cgCache cache, final Geopoint waypointUploaded) {
        final IConnector con = ConnectorFactory.getConnector(cache);
        return con.supportsOwnCoordinates() && con.uploadModifiedCoordinates(cache, waypointUploaded);
    }

    @Override
    public void goManual(final View view) {
        ActivityMixin.goManual(this, id >= 0 ? "c:geo-waypoint-edit" : "c:geo-waypoint-new");
    }

    public static void startActivityEditWaypoint(final Context context, final int waypointId) {
        context.startActivity(new Intent(context, EditWaypointActivity.class)
                .putExtra("waypoint", waypointId));
    }

    public static void startActivityAddWaypoint(final Context context, final cgCache cache) {
        context.startActivity(new Intent(context, EditWaypointActivity.class)
                .putExtra("geocode", cache.getGeocode())
                .putExtra("count", cache.getWaypoints().size()));
    }
}
