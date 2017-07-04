package eu.topine.slack;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mashape.unirest.http.Unirest;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slack Event Handler.
 * Tentative to decrease the cold start!
 */
public class EventHandler implements RequestHandler<Map<String, Object>, String> {

    private Logger logger = Logger.getLogger(EventHandler.class);

    public String handleRequest(Map<String, Object> requestMap, Context context) {


        GsonBuilder gsonBuilder = new GsonBuilder();

        Gson gson = gsonBuilder.create();

        if (logger.isDebugEnabled()) {
            logger.debug("request: " + gson.toJson(requestMap));
        }

        if ("url_verification".equals(requestMap.get("type"))) {
            return (String) requestMap.get("challenge");
        }

        Map<String, String> event = (Map<String, String>) requestMap.get("event");


        if (null != event &&
                "message".equals(event.get("type")) &&
                null == event.get("subtype")) {


            Map<String, String> processMessageRequest = new HashMap<>();

            String textInput = event.get("text");

            Pattern flightNumberPattern = Pattern.compile("(.*)\\s(([A-Za-z]{2}|[A-Za-z]\\d|\\d[A-Za-z])\\d{3,4})(.*)");
            Matcher matcher = flightNumberPattern.matcher(textInput);

            if (matcher.matches()) {
                String flightNumber = matcher.group(2);
                String wFlightNumber = flightNumber.substring(0, 2).toUpperCase() + " " +
                        flightNumber.substring(2, flightNumber.length()).trim();

                textInput = textInput.replace(flightNumber, wFlightNumber);
            }

            processMessageRequest.put("clientId", event.get("user"));
            processMessageRequest.put("textInput", textInput);
            processMessageRequest.put("channel", event.get("channel"));
            processMessageRequest.put("team_id", (String)requestMap.get("team_id"));


            if (logger.isDebugEnabled()) {
                logger.debug("ProcessMessageRequest :  " + gson.toJson(processMessageRequest));
            }

            //Call an async service to not block the slack hook ( cold start also).
            try {
                Unirest.setTimeouts(100, 1000);
                Unirest.post("https://bk7xv2trg7.execute-api.us-east-1.amazonaws.com/prod/processmessage").body(gson.toJson(processMessageRequest)).asJson();
            } catch (Exception e) {
                logger.error("Error with processMessage post : ", e);
            }

        }
        return "SUCCESS";
    }

}
