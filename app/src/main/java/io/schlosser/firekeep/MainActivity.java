package io.schlosser.firekeep;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.ErrorCodes;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.ResultCodes;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigFetchThrottledException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final long DEFAULT_CACHE_EXPIRY_S = 60 * 12; // 5 requests / hr
    private FirebaseAnalytics analytics;
    private FirebaseAuth auth;
    private FirebaseRecyclerAdapter mAdapter;
    private FirebaseRemoteConfig config;
    private View rootView;
    private long configCacheExpiry;
    private static final String COLOR_PICKER_ENABLED = "color_picker_enabled";

    /** User properties **/
    private static final String BUILD_DEBUG = "BUILD_DEBUG";
    private static final String BUILD_VERSION_NAME = "BUILD_VERSION_NAME";
    private static final String DEVICE_BOARD = "DEVICE_BOARD";
    private static final String DEVICE_BRAND = "DEVICE_BRAND";
    private static final String DEVICE_LOCALE = "DEVICE_LOCALE";
    private static final String API_LEVEL = "API_LEVEL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rootView = findViewById(R.id.content_main);

        analytics = FirebaseAnalytics.getInstance(this);
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, MainActivity.class));
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        View recyclerView = findViewById(R.id.note_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                analytics.logEvent("click_create_button", new Bundle());
                startActivity(new Intent(MainActivity.this, NewNoteActivity.class));
            }
        });

        setUserProperties();

        config = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        configCacheExpiry = configSettings.isDeveloperModeEnabled() ? 0 : DEFAULT_CACHE_EXPIRY_S;
        config.setConfigSettings(configSettings);
        config.setDefaults(R.xml.remote_config_defaults);
        refreshConfig();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAdapter.cleanup();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_signout) {
            auth.signOut();
            startActivity(new Intent(MainActivity.this, SignedOutActivity.class));
            return true;
        }

        if (id == R.id.action_refreshconfig) {
            refreshConfig();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        Log.e(TAG, auth.getCurrentUser().getUid());
        FirebaseDatabase mFirebaseDatabase = FirebaseDatabase.getInstance();
        DatabaseReference database = mFirebaseDatabase.getReference("notes").child(auth.getCurrentUser().getUid());

        mAdapter = new FirebaseRecyclerAdapter<Note, NoteHolder>(Note.class, R.layout.note_item, NoteHolder.class, database) {
            @Override
            protected void populateViewHolder(NoteHolder noteHolder, Note note, int position) {

                final String text = note.getText();
                final String color = note.getColor();
                final String id = note.getId();

                noteHolder.setText(text);
                if (config.getBoolean(COLOR_PICKER_ENABLED)) {
                    noteHolder.setColor(color);
                }

                noteHolder.mView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Context context = v.getContext();
                        Intent intent = new Intent(context, NewNoteActivity.class);
                        intent.putExtra(NewNoteActivity.ARG_NOTE_ID, id);

                        context.startActivity(intent);
                    }
                });
            }
        };

        recyclerView.setAdapter(mAdapter);
    }

    public static class NoteHolder extends RecyclerView.ViewHolder {
        View mView;

        public NoteHolder(View itemView) {
            super(itemView);
            mView = itemView;

        }

        public void setText(String text) {
            TextView textView = (TextView) mView.findViewById(R.id.text);
            textView.setText(text);
        }

        public void setColor(String color) {
            CardView noteCard = (CardView) mView.findViewById(R.id.card_view);
            noteCard.setCardBackgroundColor(NoteColor.getColor(color));
        }
    }

    private void setUserProperties() {
        analytics.setUserProperty(BUILD_DEBUG, String.valueOf(BuildConfig.DEBUG));
        analytics.setUserProperty(DEVICE_BOARD, Build.BOARD);
        analytics.setUserProperty(DEVICE_BRAND, Build.BRAND);
        analytics.setUserProperty(DEVICE_LOCALE, Locale.getDefault().getLanguage());
        analytics.setUserProperty(API_LEVEL, String.valueOf(Build.VERSION.SDK_INT));

        try {
            // Set version name, if we can get it
            PackageManager pm = this.getPackageManager();
            PackageInfo info = pm.getPackageInfo(this.getPackageName(), 0);
            analytics.setUserProperty(BUILD_VERSION_NAME, info.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Could not get package info", e);
        }
    }

    private void refreshConfig() {
        config.fetch(configCacheExpiry)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "fetchConfig:SUCCESS");
                        showSnackbar(R.string.config_success);

                        // Activate config
                        config.activateFetched();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (e instanceof FirebaseRemoteConfigFetchThrottledException) {
                            // Store throttle end time
                            FirebaseRemoteConfigFetchThrottledException ex =
                                    (FirebaseRemoteConfigFetchThrottledException) e;
                            Log.w(TAG, "fetchConfig:THROTTLED until " + ex.getThrottleEndTimeMillis());
                            showSnackbar(R.string.config_throttled);
                        } else {
                            Log.w(TAG, "fetchConfig:UNEXPECTED_ERROR", e);
                        }
                    }
                });
    }

    @MainThread
    private void showSnackbar(@StringRes int errorMessageRes) {
        Snackbar.make(rootView, errorMessageRes, Snackbar.LENGTH_LONG).show();
    }

    public enum NoteColor {
        BLUE("BLUE", "#03A9F4"),
        ORANGE("ORANGE", "#FFC107"),
        GREEN("GREEN", "#CDDC39"),
        WHITE("WHITE", "#FFFFFF"),
        DEFAULT("DEFAULT", "#FFFFFF"),
        RED("RED", "#EF5350"),
        GRAY("GRAY", "#E0E0E0"),
        YELLOW("YELLOW", "#FFEB3B");

        private final String name;
        private final String hex;

        NoteColor(String name, String hex) {
            this.name = name;
            this.hex = hex;
        }

        public static int getColor(String color) {
            String colorKey = "";
            if (color == null || color.trim() == "") {
                colorKey = "DEFAULT";
            } else {
                colorKey = color.toUpperCase();
            }
//            if (Arrays.asList(NoteColor.values()).contains(color.toUpperCase())) {
//            }
//            else {
//                throw new IllegalArgumentException("Unknown color value.");
//            }

            return Color.parseColor(NoteColor.valueOf(colorKey).hex);
        }
    }
}
