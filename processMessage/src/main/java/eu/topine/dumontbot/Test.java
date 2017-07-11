package eu.topine.dumontbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.awt.SystemColor.text;

/**
 * Created by topine on 10/07/17.
 */
public class Test {



    public static void main (String[] arg) throws UnirestException {


        /*
        [
        {
            "text": "Flight *AA*  *1617* \n From *LAX* to *LAS*\n Flight status is : *Scheduled* (On-time) ",
            "fields": [
                {
                    "title": "Departure",
                    "value": "",
                    "short": false
                },
                {
                    "title": "Departure Terminal",
                    "value": "5",
                    "short": true
                },
                {
                    "title": "Departure Gate",
                    "value": "53B",
                    "short": true
                },
                {
                    "title": "Estimated Departure Time",
                    "value": "2017-07-04 08:30 AM",
                    "short": true
                },
                {
                    "title": "Scheduled Departure Time",
                    "value": "2017-07-04 08:30 AM",
                    "short": true
                },
                {
                    "title": "Arrival",
                    "value": "",
                    "short": false
                },
                {
                    "title": "Arrival Terminal",
                    "value": "5",
                    "short": false
                },
                {
                    "title": "Arrived at",
                    "value": "July 08, 2017 04:16 PM",
                    "short": true
                },
                {
                    "title": "Scheduled Arrival Time",
                    "value": "July 08, 2017 04:15 PM",
                    "short": true
                }
            ],
            "color": "good",
            "mrkdwn_in": ["text"]
        }
    ]
         */


        Gson gson = new GsonBuilder().create();


        SlackAttachment slackAttachment = new SlackAttachment();
        slackAttachment.getMrkdwnIn().add("text");
        slackAttachment.setPretext("Flight Status");
        slackAttachment.setColor("good");
        slackAttachment.setText("Flight *AA*  *1617* \n From *LAX* to *LAS*\n Flight status is : *Scheduled* (On-time) ");
        slackAttachment.addField("Departure","", false);



        SlackAttachment message = new SlackAttachment();
        message.setText("Please let me know if you want to subscribe for alert" );
        message.setColor("good");

        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("token", "xoxb-176951315639-D6GKycq5bEtw4hzm9lXi5gYv" );
        bodyMap.put("channel", "bot-test");

        List<SlackAttachment> slackAttachments = new ArrayList<>();
        slackAttachments.add(slackAttachment);
        slackAttachments.add(message);
        bodyMap.put("attachments", gson.toJson(slackAttachments));

        Unirest.post("https://slack.com/api/chat.postMessage")
                .header("Content-Type", "application/json")
                .queryString(bodyMap).asJson();

    }

}
