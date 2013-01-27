package cgeo.geocaching;

import cgeo.geocaching.activity.AbstractActivity;
import cgeo.geocaching.apps.cache.navi.NavigationAppFactory;
import cgeo.geocaching.geopoint.DistanceParser;
import cgeo.geocaching.geopoint.Geopoint;
import cgeo.geocaching.geopoint.GeopointFormatter;
import cgeo.geocaching.ui.Formatter;
import cgeo.geocaching.ui.dialog.CoordinatesInputDialog;
import cgeo.geocaching.utils.GeoDirHandler;
import cgeo.geocaching.utils.Log;

import org.apache.commons.lang3.StringUtils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

public class cgeopoint extends AbstractActivity {
    private static final int MENU_DEFAULT_NAVIGATION = 2;
    private static final int MENU_NAVIGATE = 0;
    private static final int MENU_CACHES_AROUND = 5;
    private static final int MENU_CLEAR_HISTORY = 6;

    private static class DestinationHistoryAdapter extends ArrayAdapter<Destination> {
        private LayoutInflater inflater = null;

        public DestinationHistoryAdapter(Context context,
                List<Destination> objects) {
            super(context, 0, objects);
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {

            Destination loc = getItem(position);

            View v = convertView;

            if (v == null) {
                v = getInflater().inflate(R.layout.simple_way_point,
                        null);
            }
            TextView longitude = (TextView) v
                    .findViewById(R.id.simple_way_point_longitude);
            TextView latitude = (TextView) v
                    .findViewById(R.id.simple_way_point_latitude);
            TextView date = (TextView) v.findViewById(R.id.date);

            String lonString = loc.getCoords().format(GeopointFormatter.Format.LON_DECMINUTE);
            String latString = loc.getCoords().format(GeopointFormatter.Format.LAT_DECMINUTE);

            longitude.setText(lonString);
            latitude.setText(latString);
            date.setText(Formatter.formatShortDateTime(getContext(), loc.getDate()));

            return v;
        }

        private LayoutInflater getInflater() {
            if (inflater == null) {
                inflater = ((Activity) getContext()).getLayoutInflater();
            }

            return inflater;
        }
    }

    private Button latButton = null;
    private Button lonButton = null;
    private boolean changed = false;
    private List<Destination> historyOfSearchedLocations;
    private DestinationHistoryAdapter destionationHistoryAdapter;
    private ListView historyListView;
    private TextView historyFooter;

    private static final int CONTEXT_MENU_NAVIGATE = 1;
    private static final int CONTEXT_MENU_DELETE_WAYPOINT = 2;
    private static final int CONTEXT_MENU_EDIT_WAYPOINT = 3;

    private int contextMenuItemPosition;

    String distanceUnit = "";

    public cgeopoint() {
        super("c:geo-navigate-any");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme();
        setContentView(R.layout.point);
        setTitle(res.getString(R.string.search_destination));

        createHistoryView();

        init();
    }

    private void createHistoryView() {
        historyListView = (ListView) findViewById(R.id.historyList);

        final View pointControls = getLayoutInflater().inflate(
                R.layout.point_controls, null);
        historyListView.addHeaderView(pointControls, null, false);

        if (getHistoryOfSearchedLocations().isEmpty()) {
            historyListView.addFooterView(getEmptyHistoryFooter(), null, false);
        }

        historyListView.setAdapter(getDestionationHistoryAdapter());
        historyListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                    long arg3) {
                final Object selection = arg0.getItemAtPosition(arg2);
                if (selection instanceof Destination) {
                    navigateTo(((Destination) selection).getCoords());
                }
            }
        });

        historyListView.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v,
                                            ContextMenuInfo menuInfo) {
                menu.add(Menu.NONE, CONTEXT_MENU_NAVIGATE, Menu.NONE, res.getString(R.string.cache_menu_navigate));
                menu.add(Menu.NONE, CONTEXT_MENU_EDIT_WAYPOINT, Menu.NONE, R.string.waypoint_edit);
                menu.add(Menu.NONE, CONTEXT_MENU_DELETE_WAYPOINT, Menu.NONE, R.string.waypoint_delete);
            }
        });
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        final int position = (null != menuInfo) ? menuInfo.position : contextMenuItemPosition;
        Object destination = historyListView.getItemAtPosition(position);

        switch (item.getItemId()) {
            case CONTEXT_MENU_NAVIGATE:
                contextMenuItemPosition = position;
                if (destination instanceof Destination) {
                    NavigationAppFactory.showNavigationMenu(this, null, null, ((Destination) destination).getCoords());
                    return true;
                }
                break;

            case CONTEXT_MENU_DELETE_WAYPOINT:
                if (destination instanceof Destination) {
                    removeFromHistory((Destination) destination);
                }
                return true;

            case CONTEXT_MENU_EDIT_WAYPOINT:
                if (destination instanceof Destination) {
                    final Geopoint gp = ((Destination) destination).getCoords();
                    latButton.setText(gp.format(GeopointFormatter.Format.LAT_DECMINUTE));
                    lonButton.setText(gp.format(GeopointFormatter.Format.LON_DECMINUTE));
                }
                return true;
            default:
        }

        return super.onContextItemSelected(item);
    }

    private TextView getEmptyHistoryFooter() {
        if (historyFooter == null) {
            historyFooter = (TextView) getLayoutInflater().inflate(
                    R.layout.caches_footer, null);
            historyFooter.setText(R.string.search_history_empty);
        }
        return historyFooter;
    }

    private DestinationHistoryAdapter getDestionationHistoryAdapter() {
        if (destionationHistoryAdapter == null) {
            destionationHistoryAdapter = new DestinationHistoryAdapter(this,
                    getHistoryOfSearchedLocations());
        }
        return destionationHistoryAdapter;
    }

    private List<Destination> getHistoryOfSearchedLocations() {
        if (historyOfSearchedLocations == null) {
            // Load from database
            historyOfSearchedLocations = cgData.loadHistoryOfSearchedLocations();
        }

        return historyOfSearchedLocations;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        init();
    }

    @Override
    public void onResume() {
        super.onResume();
        geoDirHandler.startGeo();
        init();
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

    private void init() {
        latButton = (Button) findViewById(R.id.buttonLatitude);
        lonButton = (Button) findViewById(R.id.buttonLongitude);

        latButton.setOnClickListener(new coordDialogListener());
        lonButton.setOnClickListener(new coordDialogListener());

        final Geopoint coords = Settings.getAnyCoordinates();
        if (coords != null) {
            latButton.setText(coords.format(GeopointFormatter.Format.LAT_DECMINUTE));
            lonButton.setText(coords.format(GeopointFormatter.Format.LON_DECMINUTE));
        }

        Button buttonCurrent = (Button) findViewById(R.id.current);
        buttonCurrent.setOnClickListener(new currentListener());

        getDestionationHistoryAdapter().notifyDataSetChanged();
        disableSuggestions((EditText) findViewById(R.id.distance));

        initializeDistanceUnitSelector();
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

    private class coordDialogListener implements View.OnClickListener {

        @Override
        public void onClick(View arg0) {
            Geopoint gp = null;
            if (latButton.getText().length() > 0 && lonButton.getText().length() > 0) {
                gp = new Geopoint(latButton.getText().toString() + " " + lonButton.getText().toString());
            }
            CoordinatesInputDialog coordsDialog = new CoordinatesInputDialog(cgeopoint.this, null, gp, app.currentGeo());
            coordsDialog.setCancelable(true);
            coordsDialog.setOnCoordinateUpdate(new CoordinatesInputDialog.CoordinateUpdate() {
                @Override
                public void update(Geopoint gp) {
                    latButton.setText(gp.format(GeopointFormatter.Format.LAT_DECMINUTE));
                    lonButton.setText(gp.format(GeopointFormatter.Format.LON_DECMINUTE));
                    changed = true;
                }
            });
            coordsDialog.show();
        }
    }

    private static class changeDistanceUnit implements OnItemSelectedListener {

        private changeDistanceUnit(cgeopoint unitView) {
            this.unitView = unitView;
        }

        private cgeopoint unitView;

        @Override
        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
                long arg3) {
            unitView.distanceUnit = (String) arg0.getItemAtPosition(arg2);
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_DEFAULT_NAVIGATION, 0, NavigationAppFactory.getDefaultNavigationApplication().getName()).setIcon(R.drawable.ic_menu_compass); // default navigation tool

        menu.add(0, MENU_NAVIGATE, 0, res.getString(R.string.cache_menu_navigate)).setIcon(R.drawable.ic_menu_mapmode);

        menu.add(0, MENU_CACHES_AROUND, 0, res.getString(R.string.cache_menu_around)).setIcon(R.drawable.ic_menu_rotate); // caches around

        menu.add(0, MENU_CLEAR_HISTORY, 0, res.getString(R.string.search_clear_history)).setIcon(R.drawable.ic_menu_delete); // clear history

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        try {
            boolean visible = getDestination() != null;
            menu.findItem(MENU_NAVIGATE).setVisible(visible);
            menu.findItem(MENU_DEFAULT_NAVIGATION).setVisible(visible);
            menu.findItem(MENU_CACHES_AROUND).setVisible(visible);

            menu.findItem(MENU_CLEAR_HISTORY).setEnabled(!getHistoryOfSearchedLocations().isEmpty());
        } catch (Exception e) {
            // nothing
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int menuItem = item.getItemId();

        final Geopoint coords = getDestination();

        if (coords != null) {
            addToHistory(coords);
        }

        switch (menuItem) {
            case MENU_DEFAULT_NAVIGATION:
                navigateTo();
                return true;

            case MENU_CACHES_AROUND:
                cachesAround();
                return true;

            case MENU_CLEAR_HISTORY:
                clearHistory();
                return true;

            case MENU_NAVIGATE:
                NavigationAppFactory.showNavigationMenu(this, null, null, coords);
                return true;
            default:
                return false;
        }
    }

    private void addToHistory(final Geopoint coords) {
        // Add locations to history
        final Destination loc = new Destination(coords);

        if (!getHistoryOfSearchedLocations().contains(loc)) {
            getHistoryOfSearchedLocations().add(0, loc);

            // Save location
            cgData.saveSearchedDestination(loc);

            // Ensure to remove the footer
            historyListView.removeFooterView(getEmptyHistoryFooter());
        }
    }

    private void removeFromHistory(Destination destination) {
        if (getHistoryOfSearchedLocations().contains(destination)) {
            getHistoryOfSearchedLocations().remove(destination);

            // Save
            cgData.removeSearchedDestination(destination);

            if (getHistoryOfSearchedLocations().isEmpty()) {
                if (historyListView.getFooterViewsCount() == 0) {
                    historyListView.addFooterView(getEmptyHistoryFooter());
                }
            }

            getDestionationHistoryAdapter().notifyDataSetChanged();

            showToast(res.getString(R.string.search_remove_destination));
        }
    }

    private void clearHistory() {
        if (!getHistoryOfSearchedLocations().isEmpty()) {
            getHistoryOfSearchedLocations().clear();

            // Save
            cgData.clearSearchedDestinations();

            if (historyListView.getFooterViewsCount() == 0) {
                historyListView.addFooterView(getEmptyHistoryFooter());
            }

            getDestionationHistoryAdapter().notifyDataSetChanged();

            showToast(res.getString(R.string.search_history_cleared));
        }
    }

    private void navigateTo() {
        navigateTo(getDestination());
    }

    private void navigateTo(Geopoint geopoint) {
        NavigationAppFactory.startDefaultNavigationApplication(1, this, geopoint);
    }

    private void cachesAround() {
        final Geopoint coords = getDestination();

        if (coords == null) {
            showToast(res.getString(R.string.err_location_unknown));
            return;
        }

        cgeocaches.startActivityCoordinates(this, coords);

        finish();
    }

    private final GeoDirHandler geoDirHandler = new GeoDirHandler() {
        @Override
        public void updateGeoData(final IGeoData geo) {
            try {
                latButton.setHint(geo.getCoords().format(GeopointFormatter.Format.LAT_DECMINUTE_RAW));
                lonButton.setHint(geo.getCoords().format(GeopointFormatter.Format.LON_DECMINUTE_RAW));
            } catch (final Exception e) {
                Log.w("Failed to update location.");
            }
        }
    };

    private class currentListener implements View.OnClickListener {

        @Override
        public void onClick(View arg0) {
            final Geopoint coords = app.currentGeo().getCoords();
            if (coords == null) {
                showToast(res.getString(R.string.err_point_unknown_position));
                return;
            }

            latButton.setText(coords.format(GeopointFormatter.Format.LAT_DECMINUTE));
            lonButton.setText(coords.format(GeopointFormatter.Format.LON_DECMINUTE));

            changed = false;
        }
    }

    private Geopoint getDestination() {

        String bearingText = ((EditText) findViewById(R.id.bearing)).getText().toString();
        // combine distance from EditText and distanceUnit saved from Spinner
        String distanceText = ((EditText) findViewById(R.id.distance)).getText().toString() + distanceUnit;
        String latText = latButton.getText().toString();
        String lonText = lonButton.getText().toString();

        if (StringUtils.isBlank(bearingText) && StringUtils.isBlank(distanceText)
                && StringUtils.isBlank(latText) && StringUtils.isBlank(lonText)) {
            showToast(res.getString(R.string.err_point_no_position_given));
            return null;
        }

        Geopoint coords;
        if (StringUtils.isNotBlank(latText) && StringUtils.isNotBlank(lonText)) {
            try {
                coords = new Geopoint(latText, lonText);
            } catch (Geopoint.ParseException e) {
                showToast(res.getString(e.resource));
                return null;
            }
        } else {
            if (app.currentGeo().getCoords() == null) {
                showToast(res.getString(R.string.err_point_curr_position_unavailable));
                return null;
            }

            coords = app.currentGeo().getCoords();
        }

        Geopoint result;
        if (StringUtils.isNotBlank(bearingText) && StringUtils.isNotBlank(distanceText)) {
            // bearing & distance
            double bearing;
            try {
                bearing = Double.parseDouble(bearingText);
            } catch (NumberFormatException e) {
                helpDialog(res.getString(R.string.err_point_bear_and_dist_title), res.getString(R.string.err_point_bear_and_dist));
                return null;
            }

            double distance;
            try {
                distance = DistanceParser.parseDistance(distanceText, Settings.isUseMetricUnits());
            } catch (NumberFormatException e) {
                showToast(res.getString(R.string.err_parse_dist));
                return null;
            }

            final Geopoint coordsDst = coords.project(bearing, distance);

            if (coordsDst == null) {
                showToast(res.getString(R.string.err_point_location_error));
                return null;
            }

            result = coordsDst;
        } else if (coords != null) {
            result = coords;
        } else {
            return null;
        }

        saveCoords(result);

        return result;
    }

    private void saveCoords(final Geopoint coords) {
        if (!changed) {
            return;
        }
        Settings.setAnyCoordinates(coords);
    }
}
