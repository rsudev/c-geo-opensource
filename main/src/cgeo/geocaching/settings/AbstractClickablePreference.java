package cgeo.geocaching.settings;

import cgeo.geocaching.R;

import android.app.AlertDialog;
import android.content.Context;
import androidx.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

abstract class AbstractClickablePreference extends Preference implements View.OnLongClickListener {

    private final SettingsActivity2 activity;

    AbstractClickablePreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        activity = (SettingsActivity2) context;
    }

    AbstractClickablePreference(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        activity = (SettingsActivity2) context;
    }

    //@Override
    protected View onCreateView(final ViewGroup parent) {
        setOnPreferenceClickListener(getOnPreferenceClickListener(activity));

        final ListView listView = (ListView) parent;
        listView.setOnItemLongClickListener((parent1, view, position, id) -> {
            final ListView listView1 = (ListView) parent1;
            final ListAdapter listAdapter = listView1.getAdapter();
            final Object obj = listAdapter.getItem(position);
            if (obj instanceof View.OnLongClickListener) {
                final View.OnLongClickListener longListener = (View.OnLongClickListener) obj;
                return longListener.onLongClick(view);
            }
            return false;
        });

        return null;// super.onCreateView(parent);
    }

    protected abstract OnPreferenceClickListener getOnPreferenceClickListener(SettingsActivity2 settingsActivity);

    protected boolean isAuthorized() {
        return false;
    }

    protected void revokeAuthorization() {
    }

    @Override
    public boolean onLongClick(final View v) {
        if (!isAuthorized()) {
            return false;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
        builder.setMessage(R.string.auth_forget_message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.auth_forget_title)
                .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                    revokeAuthorization();
                    setSummary(R.string.auth_unconnected);
                })
                .setNegativeButton(android.R.string.cancel, (dialog, id) -> dialog.cancel());
        builder.create().show();

        return true;
    }
}
