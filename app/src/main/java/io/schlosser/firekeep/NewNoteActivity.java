package io.schlosser.firekeep;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.util.Date;

public class NewNoteActivity extends AppCompatActivity implements TextWatcher {

    private static final String TAG = "NewNoteActivity";
    public static final String ARG_NOTE_ID = "note_id";
    private static final String COLOR_PICKER_ENABLED = "color_picker_enabled";
    private DatabaseReference database;
    private FirebaseRemoteConfig config;
    private EditText textField;
    private MenuItem saveButton;
    private Note note = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_note);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Get the database node at /notes/<uid>/
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user.getUid();
        database = FirebaseDatabase.getInstance().getReference("notes").child(uid);

        config = FirebaseRemoteConfig.getInstance();

        // Get Input Fields
        textField = (EditText) findViewById(R.id.text);
        textField.addTextChangedListener(this);

        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            setTitle("New Note");
        } else {
            setTitle("Edit Note");
            String noteId = extras.getString(ARG_NOTE_ID);
            database.child(noteId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    note = dataSnapshot.getValue(Note.class);
                    textField.setText(note.getText());
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, databaseError.getMessage());
                }
            });
        }

        if (config.getBoolean(COLOR_PICKER_ENABLED)) {
            View colorWrapper = findViewById(R.id.color_input_layout);
            colorWrapper.setVisibility(View.VISIBLE);
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_newnote, menu);
        saveButton = menu.findItem(R.id.action_save);
        setMenuEnabledDisabled();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.action_save:
                String text = textField.getText().toString();
                String color = "default";
                if (note == null) {
                    long dateCreated = new Date().getTime();
                    note = new Note(text, dateCreated, color);
                } else {
                    note.text = text;
                    note.color = color;
                }
                database.child(String.valueOf(note.getId())).setValue(note);
                startActivity(new Intent(NewNoteActivity.this, MainActivity.class));
                return true;

            default:
                Log.e(TAG, "Unknown menu item.");
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        // Pass
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        // Pass
    }

    @Override
    public void afterTextChanged(Editable s) {
        setMenuEnabledDisabled();
    }

    private void setMenuEnabledDisabled() {
        if (saveButton == null) {
            return;
        }

        Log.i(TAG, "Validity: " + textIsValid());
        if (textIsValid()) {
            saveButton.setEnabled(true);
        } else {
            saveButton.setEnabled(false);
        }
    }

    private boolean textIsValid() {
        String slug = textField.getText().toString();
        return slug.matches(".+");
    }
}
