package cgeo.geocaching.activity;

import butterknife.ButterKnife;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.MainActivity;
import cgeo.geocaching.R;
import cgeo.geocaching.network.Cookies;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.ClipboardUtils;
import cgeo.geocaching.utils.EditUtils;
import cgeo.geocaching.utils.HtmlUtils;
import cgeo.geocaching.utils.TextUtils;
import cgeo.geocaching.utils.TranslationUtils;

import org.apache.commons.lang3.StringUtils;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.res.Resources;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBarActivity;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

import rx.Subscription;
import rx.subscriptions.Subscriptions;

import java.util.Locale;

public abstract class AbstractActivity extends ActionBarActivity implements IAbstractActivity {

    protected CgeoApplication app = null;
    protected Resources res = null;
    private boolean keepScreenOn = false;
    private Subscription resumeSubscription = Subscriptions.empty();

    protected AbstractActivity() {
        this(false);
    }

    protected AbstractActivity(final boolean keepScreenOn) {
        this.keepScreenOn = keepScreenOn;
    }

    @Override
    final public void goHome(final View view) {
        ActivityMixin.navigateToMain(this);
    }

    final protected void showProgress(final boolean show) {
        ActivityMixin.showProgress(this, show);
    }

    final protected void setTheme() {
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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        initializeCommonFields();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId()== android.R.id.home) {
            ActivityMixin.navigateToMain(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onResume(final Subscription resumeSubscription) {
        super.onResume();
        this.resumeSubscription = resumeSubscription;
    }

    @Override
    public void onPause() {
        resumeSubscription.unsubscribe();
        super.onPause();
    }

    protected static void disableSuggestions(final EditText edit) {
        EditUtils.disableSuggestions(edit);
    }

    protected void restartActivity() {
        final Intent intent = getIntent();
        overridePendingTransition(0, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
    }

    @Override
    public void invalidateOptionsMenuCompatible() {
        ActivityMixin.invalidateOptionsMenu(this);
    }

    protected void onCreate(final Bundle savedInstanceState, final int resourceLayoutID) {
        onCreate(savedInstanceState, resourceLayoutID, false);
    }

    protected void onCreate(final Bundle savedInstanceState, final int resourceLayoutID, boolean useDialogTheme) {

        super.onCreate(savedInstanceState);

        initializeCommonFields();

        // non declarative part of layout
        if (useDialogTheme) {
            setTheme(ActivityMixin.getDialogTheme());
        } else {
            setTheme();
        }
        setContentView(resourceLayoutID);

        // create view variables
        ButterKnife.inject(this);
    }

    private void initializeCommonFields() {
        // initialize commonly used members
        res = this.getResources();
        app = (CgeoApplication) this.getApplication();

        // only needed in some activities, but implemented in super class nonetheless
        Cookies.restoreCookieStore(Settings.getCookieStore());
        ActivityMixin.keepScreenOn(this, keepScreenOn);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);

        // initialize the action bar title with the activity title for single source
        ActivityMixin.setTitle(this, getTitle());
    }

    protected void hideKeyboard() {
        new Keyboard(this).hide();
    }

    public void showKeyboard(final View view) {
        new Keyboard(this).show(view);
    }

    protected void buildDetailsContextMenu(final ContextMenu menu, final CharSequence clickedItemText, final CharSequence fieldTitle, final boolean copyOnly) {
        menu.setHeaderTitle(fieldTitle);
        getMenuInflater().inflate(R.menu.details_context, menu);
        menu.findItem(R.id.menu_translate_to_sys_lang).setVisible(!copyOnly);
        if (!copyOnly) {
            if (clickedItemText.length() > TranslationUtils.TRANSLATION_TEXT_LENGTH_WARN) {
                showToast(res.getString(R.string.translate_length_warning));
            }
            menu.findItem(R.id.menu_translate_to_sys_lang).setTitle(res.getString(R.string.translate_to_sys_lang, Locale.getDefault().getDisplayLanguage()));
        }
        final boolean localeIsEnglish = StringUtils.equals(Locale.getDefault().getLanguage(), Locale.ENGLISH.getLanguage());
        menu.findItem(R.id.menu_translate_to_english).setVisible(!copyOnly && !localeIsEnglish);
    }

    protected boolean onClipboardItemSelected(final MenuItem item, final CharSequence clickedItemText) {
        switch (item.getItemId()) {
            // detail fields
            case R.id.menu_copy:
                ClipboardUtils.copyToClipboard(clickedItemText);
                showToast(res.getString(R.string.clipboard_copy_ok));
                return true;
            case R.id.menu_translate_to_sys_lang:
                TranslationUtils.startActivityTranslate(this, Locale.getDefault().getLanguage(), HtmlUtils.extractText(clickedItemText));
                return true;
            case R.id.menu_translate_to_english:
                TranslationUtils.startActivityTranslate(this, Locale.ENGLISH.getLanguage(), HtmlUtils.extractText(clickedItemText));
                return true;
            case R.id.menu_cache_share_field:
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, clickedItemText.toString());
                startActivity(Intent.createChooser(intent, res.getText(R.string.cache_share_field)));
                return true;
            default:
                return false;
        }
    }

    // Do not support older devices than Android 4.0
    // Although there even are 2.3 devices  (Nexus S)
    // these are so few that we don't want to deal with the older (non Android Beam) API

    public interface ActivitySharingInterface {
        /** Return an URL that represent the current activity for sharing */
        public String getUri();
    }

    protected void initializeAndroidBeam(ActivitySharingInterface sharingInterface) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            initializeICSAndroidBeam(sharingInterface);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    protected void initializeICSAndroidBeam(final ActivitySharingInterface sharingInterface) {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            return;
        }
        nfcAdapter.setNdefPushMessageCallback(new NfcAdapter.CreateNdefMessageCallback() {
            @Override
            public NdefMessage createNdefMessage(NfcEvent event) {
                NdefRecord record = NdefRecord.createUri(sharingInterface.getUri());
                return new NdefMessage(new NdefRecord[]{record});
            }
        }, this);

    }
}
