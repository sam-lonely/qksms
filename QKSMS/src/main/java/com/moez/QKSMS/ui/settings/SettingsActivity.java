package com.moez.QKSMS.ui.settings;

import android.app.FragmentManager;
import android.os.Bundle;
import com.moez.QKSMS.R;
import com.moez.QKSMS.ui.base.QKSwipeBackActivity;

public class SettingsActivity extends QKSwipeBackActivity {

    private SettingsFragment mSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentManager fm = getFragmentManager();
        mSettingsFragment = (SettingsFragment) fm.findFragmentById(R.id.content_frame);
        if (mSettingsFragment == null) {
            mSettingsFragment = SettingsFragment.newInstance(R.xml.settings_main);
        }

        fm.beginTransaction()
                .replace(R.id.content_frame, mSettingsFragment)
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            getFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
        }
    }
}
