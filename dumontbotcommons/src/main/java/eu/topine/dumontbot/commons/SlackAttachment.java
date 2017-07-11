package eu.topine.dumontbot.commons;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by topine on 11/07/17.
 */
public class SlackAttachment {


    @SerializedName("text")
    @Expose
    private String text;
    @SerializedName("fields")
    @Expose
    private List<SlackAttachmentField> fields = new ArrayList<>();
    @SerializedName("color")
    @Expose
    private String color;
    @SerializedName("mrkdwn_in")
    @Expose
    private List<String> mrkdwnIn = new ArrayList<>();
    @SerializedName("pretext")
    @Expose
    private String pretext;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<SlackAttachmentField> getFields() {
        return fields;
    }

    public void setFields(List<SlackAttachmentField> fields) {
        this.fields = fields;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public List<String> getMrkdwnIn() {
        return mrkdwnIn;
    }

    public void setMrkdwnIn(List<String> mrkdwnIn) {
        this.mrkdwnIn = mrkdwnIn;
    }

    public void addField(String title, String value, Boolean shortLabel) {
        SlackAttachmentField slackAttachmentField = new SlackAttachmentField(title,value,shortLabel);
        fields.add(slackAttachmentField);
    }

    public String getPretext() {
        return pretext;
    }

    public void setPretext(String pretext) {
        this.pretext = pretext;
    }
}
