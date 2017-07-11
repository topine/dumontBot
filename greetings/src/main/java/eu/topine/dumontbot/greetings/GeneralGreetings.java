package eu.topine.dumontbot.greetings;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Created by topine on 05/07/17.
 */
public class GeneralGreetings implements RequestHandler<Map<String, Object>, Map<String, Object>> {


    private Logger logger = Logger.getLogger(GeneralGreetings.class);
    private String status = "If you want a flight status, " +
            "let me know the flight information (2-letter airline code, flight number and date) " +
            "and I will get the status for you.";

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> requestMap, Context context) {

        Gson gson = new GsonBuilder().create();

        logger.debug("Request : " + gson.toJson(requestMap));
        Map<String, Object> currentIntent = (Map) requestMap.get("currentIntent");

        String text;
        switch ((String) currentIntent.get("name")) {
            case "Greeting":
                String[] greeting = {"Hello! ", "Hi! ", "Greetings! ", "Yo! ",
                        "Look who it is! "};
                int randomGreeting = new Random().nextInt(greeting.length);
                text = greeting[randomGreeting] + status;
                break;

            case "WhatCanYouDo":
            case "Help":
                text = "Do you want to verify a flight status and get live updates alerts in Slack ? \n" +
                        "\n" +
                        " DumontBot shows the flight status and can notify you with live updates about : \n" +
                        "\n" +
                        "  *Departure*: When the flight depart from the gate. \n" +
                        " *Departure Delay*: When the flight departure is delayed more then 15 minutes. \n" +
                        " *Departure Gate Change*: When the departure gate changes. \n" +
                        " *Arrival*: When the flight lands. \n" +
                        " *Arrival Delay*: When the flight arrival is delayed more then 15 minutes. \n" +
                        " *Baggage Carousel*: Belt number where you baggage will be available to be claimed.\n" +
                        "\n" +
                        "DumontBot can be invited to any channel. It will only reply to messages in public or group channels if mentioned : @DumontBot.\n" +
                        "\n" +
                        "e.g., @dumontbot I would like to know a flight status.\n" +
                        "\n" +
                        "For direct messages (DM), the mention is not needed.";
                break;

            case "ThankYou":

                String[] thankYou = {"You got it! ","You’re welcome! ",
                        "Don’t mention it! ", "No worries! ",
                        "Not a problem! ","My pleasure! ", "It was nothing! ",
                        "Not at all! ", "I’m happy to help! ","Not at all! ","Sure! ",
                        "Anytime! "};
                int randomThankYou = new Random().nextInt(thankYou.length);

                text = thankYou[randomThankYou];
                break;

            case "HowAreYou" :
                text = "I'm fine, thanks! " + status;
                break;


            case "WhoAreYou" :

                text = "My name is DumontBot. I am inspired in the aviation pioneer Alberto Santos-Dumont!\n" +
                        "For more information about Santos-Dumont, please check the wikipedia : <https://en.wikipedia.org/wiki/Alberto_Santos-Dumont>";
                break;

            default:
                text = "Sorry, I should be programed to reply to this.";
        }

        return getResponse(text);
    }


    private Map<String, Object> getResponse(String text) {

        Map<String, Object> dialogAction = new HashMap<>();

        dialogAction.put("type", "Close");
        dialogAction.put("fulfillmentState", "Fulfilled");

        Map<String, String> message = new HashMap<>();

        message.put("contentType", "PlainText");
        message.put("content", text);

        dialogAction.put("message", message);

        Map<String, Object> response = new HashMap<>();

        response.put("dialogAction", dialogAction);

        return response;
    }
}
