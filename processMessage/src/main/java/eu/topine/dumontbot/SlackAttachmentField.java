package eu.topine.dumontbot;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Created by topine on 11/07/17.
 */
public class SlackAttachmentField {
    @SerializedName("title")
    @Expose
    private String title;
    @SerializedName("value")
    @Expose
    private String value;
    @SerializedName("short")
    @Expose
    private Boolean shortLabel;


    public SlackAttachmentField(String title, String value, Boolean shortLabel) {
        this.title = title;
        this.value = value;
        this.shortLabel = shortLabel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Boolean getShortLabel() {
        return shortLabel;
    }

    public void setShortLabel(Boolean _short) {
        this.shortLabel = shortLabel;
    }

}
