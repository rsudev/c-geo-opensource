package cgeo.geocaching.maps.mapsforge;

import cgeo.geocaching.activity.FilteredActivity;
import cgeo.geocaching.maps.AbstractMap;
import cgeo.geocaching.maps.CGeoMap;
import cgeo.geocaching.maps.interfaces.MapActivityImpl;

import org.mapsforge.android.maps.MapActivity;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;

public class MapsforgeMapActivity extends MapActivity implements MapActivityImpl, FilteredActivity {

    private AbstractMap mapBase;

    public MapsforgeMapActivity() {
        mapBase = new CGeoMap(this);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        // TODO: Move to a more sane place
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB) {
            requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        }
        mapBase.onCreate(icicle);
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        mapBase.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        mapBase.onDestroy();
    }

    @Override
    protected void onPause() {
        mapBase.onPause();
    }

    @Override
    protected void onResume() {
        mapBase.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return mapBase.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mapBase.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return mapBase.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onStop() {
        mapBase.onStop();
    }

    @Override
    public void superOnCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean superOnCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void superOnDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean superOnOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void superOnResume() {
        super.onResume();
    }

    @Override
    public void superOnStop() {
        super.onStop();
    }

    @Override
    public void superOnPause() {
        super.onPause();
    }

    @Override
    public boolean superOnPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    // close activity and open homescreen
    @Override
    public void goHome(View view) {
        mapBase.goHome(view);
    }

    @Override
    public void showFilterMenu(View view) {
        // do nothing, the filter bar only shows the global filter
    }
}
