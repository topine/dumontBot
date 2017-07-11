package eu.topine.dumontbot;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mashape.unirest.http.Unirest;
import eu.topine.dumontbot.commons.FlightStatusCode;
import eu.topine.dumontbot.commons.SlackAttachment;
import org.apache.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Flight Status Callback
 * <p>
 * Receive the flight alerts from the Status API and send it to slack
 */
public class FlightAlertCallback implements RequestHandler<Map<String, Object>, String> {

    private Logger logger = Logger.getLogger(FlightAlertCallback.class);
    private DateTimeFormatter dateTimeOutputFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a", Locale.US);
    private Gson gson = new GsonBuilder().create();

    public String handleRequest(Map<String, Object> requestMap, Context context) {


        try {


            logger.info("Request :" + gson.toJson(requestMap));

            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1)
                    .build();

            DynamoDB dynamoDB = new DynamoDB(client);

            Table table = dynamoDB.getTable("alertConfig");

            Map<String, Object> alert = (Map) requestMap.get("alert");
            Map<String, Object> rule = (Map) alert.get("rule");
            Map<String, Object> flightStatus = (Map) alert.get("flightStatus");

            String alertType = (String) ((Map) alert.get("event")).get("type");

            String id = (String) ((Map) ((Map) rule.get("nameValues")).get("nameValue")).get("value");

            logger.info("get slack dynamodb id:" + id);
            Item item = table.getItem("id", id);

            Map<String, String> request = item.getMap("request");

            Map<String, String> slackData = gson.fromJson(request.get("slackData"), Map.class);

            Map<String, Object> bodyMap = new HashMap<>();


            //get token to reply
            Table botTokenTable = dynamoDB.getTable("botToken");

            logger.info("get botitem dynamodb id:" + slackData.get("team_id"));

            Item botItem = botTokenTable.getItem("team_id", slackData.get("team_id"));

            String botAccessToken = botItem.getString("bot_access_token");


            bodyMap.put("token", botAccessToken);
            bodyMap.put("channel", slackData.get("channel"));

            bodyMap.put("attachments", mapText(alertType, flightStatus, slackData));

            logger.info("Slack post message : " + gson.toJson(bodyMap));

            Unirest.post("https://slack.com/api/chat.postMessage")
                    .header("Content-Type", "application/json")
                    .queryString(bodyMap).asJson();
        } catch (Exception e) {
            logger.error("Error with flight alert callback :", e);
        }

        return "SUCCESS";
    }


    public String mapText(String alertType,
                          Map<String, Object> flightStatus,
                          Map<String, String> slackData) {

        FlightStatusCode flightStatusCode = FlightStatusCode.findByShortCode((String) flightStatus.get("status"));
        Map<String, Object> delays = (Map) flightStatus.get("delays");
        Map<String, String> airportResource = (Map) flightStatus.get("airportResources");


        SlackAttachment alertAttachment = new SlackAttachment();

        alertAttachment.getMrkdwnIn().add("text");
        alertAttachment.getMrkdwnIn().add("pretext");

        StringBuilder builder = new StringBuilder();
        builder.append("Hello <@").append(slackData.get("clientId")).append(">\n");

        builder.append("New live update for the flight: *")
                .append(flightStatus.get("carrierFsCode"))
                .append(" ").append(flightStatus.get("flightNumber")).append("* \n");

        alertAttachment.setPretext(builder.toString());

        alertAttachment.addField("", "", false);

        alertAttachment.addField("Flight status", flightStatusCode.getLabel(), false);

        alertAttachment.addField("", "", false);

        switch (alertType) {
            case "EN_ROUTE":

                alertAttachment.setText("*Flight Departure Alert*");
                alertAttachment.setColor("#1874CD");
                StringBuilder builderEnRoute = new StringBuilder();
                //Departed (On-Time)|(Delayed 15 Minutes) at : date
                builderEnRoute.append("Departed ");

                String statusDelayed = "(On-time) ";

                if (null != delays
                        && null != delays.get("departureGateDelayMinutes")
                        && (Integer.parseInt((String) delays.get("departureGateDelayMinutes")) >= 15)) {
                    statusDelayed = "(Delayed " + delays.get("departureGateDelayMinutes") + " Minutes)";
                }

                builderEnRoute.append(statusDelayed).append(" at");

                alertAttachment.addField(builderEnRoute.toString(),
                        LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                                .get("actualGateDeparture").get("dateLocal")).format(dateTimeOutputFormat), false);


                alertAttachment.addField("Estimated Arrival Time",
                        LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                                .get("estimatedGateArrival").get("dateLocal")).format(dateTimeOutputFormat), false);

                break;

            case "LANDED":

                alertAttachment.setText("*Flight Arrival Alert*");
                alertAttachment.setColor("#008B00");
                StringBuilder builderLanded = new StringBuilder();

                builderLanded.append("Arrived ");

                String arrStatusDelayed = "(On-time) ";

                if (null != delays
                        && null != delays.get("arrivalGateDelayMinutes")
                        && (Integer.parseInt((String) delays.get("arrivalGateDelayMinutes")) >= 15)) {
                    arrStatusDelayed = "(Delayed " + delays.get("arrivalGateDelayMinutes") + " Minutes)";
                }

                builderLanded.append(arrStatusDelayed).append(" at");

                alertAttachment.addField(builderLanded.toString(),
                        LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                                .get("actualRunwayArrival").get("dateLocal")).format(dateTimeOutputFormat), false);

                break;

            case "CANCELLED":
            case "DIVERTED":
                alertAttachment.setText("*Flight Cancellation Alert*");
                alertAttachment.setColor("danger");
                alertAttachment.addField("", "", false);
                alertAttachment.addField("For more information please contact the Airline.", "", false);
                break;

            case "DEPARTURE_DELAY":
                alertAttachment.setColor("#FFFF00");
                alertAttachment.setText("*Flight Departure Delay Alert*");
                StringBuilder builderDepDelay = new StringBuilder();
                //The flight departure is delayed 15 minutes
                builderDepDelay.append("The flight departure is delayed ")
                        .append(delays.get("departureGateDelayMinutes"))
                        .append(" Minutes\n");

                alertAttachment.addField(builderDepDelay.toString(), "", false);

                alertAttachment.addField("New Estimated Departure Time",
                        LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                                .get("estimatedGateDeparture").get("dateLocal")).format(dateTimeOutputFormat), false);

                break;

            case "ARRIVAL_DELAY":

                alertAttachment.setColor("#FFFF00");
                alertAttachment.setText("*Flight Arrival Delay Alert*");

                StringBuilder builderArrDelay = new StringBuilder();
                //The flight arrival is delayed 15 minutes
                builderArrDelay.append("The flight arrival is delayed ")
                        .append(delays.get("arrivalGateDelayMinutes"))
                        .append(" Minutes\n");

                alertAttachment.addField(builderArrDelay.toString(), "", false);

                alertAttachment.addField("New Estimated Arrival Time",
                        LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                                .get("estimatedGateArrival").get("dateLocal")).format(dateTimeOutputFormat), false);

                break;

            case "BAGGAGE":
                alertAttachment.setColor("#FFFF00");
                alertAttachment.setText("*Baggage Alert*");

                alertAttachment.addField("The baggage will be available on the baggage carousel", airportResource.get("baggage"), false);

                break;

            case "DEPARTURE_GATE":
                alertAttachment.setColor("#228B22");
                alertAttachment.setText("*Gate Change Alert*");

                if (null != airportResource && null != airportResource.get("departureTerminal")) {
                    alertAttachment.addField("Terminal", airportResource.get("departureTerminal"), true);

                }

                // The flight will departure from terminal 1, gate 55;
                if (null != airportResource && null != airportResource.get("departureGate")) {

                    alertAttachment.addField("New Departure Gate",
                            airportResource.get("departureGate"), true);

                }

                break;
        }

        return gson.toJson(Collections.singletonList(alertAttachment));

    }
}
