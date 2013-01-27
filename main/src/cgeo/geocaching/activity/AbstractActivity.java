package cgeo.geocaching.activity;

import cgeo.geocaching.R;
import cgeo.geocaching.Settings;
import cgeo.geocaching.cgeoapplication;
import cgeo.geocaching.apps.cache.navi.NavigationAppFactory;
import cgeo.geocaching.compatibility.Compatibility;
import cgeo.geocaching.network.Cookies;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

public abstract class AbstractActivity extends FragmentActivity implements IAbstractActivity {

    final private String helpTopic;

    protected cgeoapplication app = null;
    protected Resources res = null;
    private boolean keepScreenOn = false;

    protected AbstractActivity() {
        this(null);
    }

    protected AbstractActivity(final String helpTopic) {
        this.helpTopic = helpTopic;
    }

    protected AbstractActivity(final String helpTopic, final boolean keepScreenOn) {
        this(helpTopic);
        this.keepScreenOn = keepScreenOn;
    }

    @Override
    final public void goHome(final View view) {
        ActivityMixin.goHome(this);
    }

    @Override
    public void goManual(final View view) {
        ActivityMixin.goManual(this, helpTopic);
    }

    @TargetApi(11)
    public void addV11Actionitems(Menu menu, boolean navigation, boolean manual) {
        if (Build.VERSION.SDK_INT >= 11) {
            MenuItem item = menu.add(0, R.id.menu_default_navigation, 0, NavigationAppFactory.getDefaultNavigationApplication().getName()).setIcon(R.drawable.ic_menu_compass); // default navigation tool
            if (item != null) {
                item.setVisible(true);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            item = menu.findItem(R.id.menu_actionbar_manual);
            if (item != null) {
                item.setVisible(true);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            item = menu.findItem(R.id.menu_default_navigation);
            if (item != null) {
                item.setVisible(true);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
        }
    }

    @TargetApi(11)
    public void enableV11Actionitems(Menu menu) {
        if (Build.VERSION.SDK_INT >= 11) {
            MenuItem item = menu.findItem(R.id.menu_actionbar_search);
            if (item != null) {
                item.setVisible(true);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            item = menu.findItem(R.id.menu_actionbar_manual);
            if (item != null) {
                item.setVisible(true);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            item = menu.findItem(R.id.menu_default_navigation);
            if (item != null) {
                item.setVisible(true);
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
        }
    }

    final public void setTitle(final String title) {
        ActivityMixin.setTitle(this, title);
    }

    final public void showProgress(final boolean show) {
        ActivityMixin.showProgress(this, show);
    }

    final public void setTheme() {
        ActivityMixin.setTheme(this);
    }

    @Override
    public final void showToast(String text) {
        ActivityMixin.showToast(this, text);
    }

    @Override
    public final void showShortToast(String text) {
        ActivityMixin.showShortToast(this, text);
    }

    @Override
    public final void helpDialog(final String title, final String message) {
        ActivityMixin.helpDialog(this, title, message);
    }

    public final void helpDialog(final String title, final String message, final Drawable icon) {
        ActivityMixin.helpDialog(this, title, message, icon);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // init
        res = this.getResources();
        app = (cgeoapplication) this.getApplication();

        // Restore cookie store if needed
        Cookies.restoreCookieStore(Settings.getCookieStore());

        ActivityMixin.keepScreenOn(this, keepScreenOn);
    }

    protected static void disableSuggestions(final EditText edit) {
        Compatibility.disableSuggestions(edit);
    }

    protected void restartActivity() {
        Compatibility.restartActivity(this);
    }

    @Override
    public void invalidateOptionsMenuCompatible() {
        ActivityMixin.invalidateOptionsMenu(this);
    }

    /**
     * insert text into the EditText at the current cursor position
     *
     * @param editText
     * @param insertText
     * @param moveCursor
     *            place the cursor after the inserted text
     */
    public static void insertAtPosition(final EditText editText, final String insertText, final boolean moveCursor) {
        int selectionStart = editText.getSelectionStart();
        int selectionEnd = editText.getSelectionEnd();
        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);

        final String content = editText.getText().toString();
        String completeText;
        if (start > 0 && !Character.isWhitespace(content.charAt(start - 1))) {
            completeText = " " + insertText;
        } else {
            completeText = insertText;
        }

        editText.getText().replace(start, end, completeText);
        int newCursor = moveCursor ? start + completeText.length() : start;
        editText.setSelection(newCursor, newCursor);
    }

}
