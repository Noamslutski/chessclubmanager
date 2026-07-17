package com.ptchess.club.ui.common;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import com.ptchess.club.util.LocaleHelper;

/** Base class that applies the persisted UI language to every screen. */
public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }
}
