package io.schlosser.firekeep;

import com.google.firebase.database.IgnoreExtraProperties;

import java.util.Date;

/**
 * Created by danrs on 2/21/17.
 */

@IgnoreExtraProperties
public class Note {
    public String text;
    public long dateCreated;
    public String color;

    public Note() {

    }

    public Note(String text, long dateCreated, String color) {
        this.text = text;
        this.dateCreated = dateCreated;
        this.color = color;
    }

    public String getText() {
        return this.text;
    }

    public String getColor() {
        return this.color;
    }

    public String getId() {
        return String.valueOf(this.dateCreated);
    }
}
