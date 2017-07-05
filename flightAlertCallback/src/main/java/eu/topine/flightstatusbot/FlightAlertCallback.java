package eu.topine.flightstatusbot;

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
import org.apache.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Flight Status Callback
 * <p>
 * Receive the flight alerts from the Status API and send it to slack
 */
public class FlightAlertCallback implements RequestHandler<Map<String, Object>, String> {

    private Logger logger = Logger.getLogger(FlightAlertCallback.class);
    private DateTimeFormatter dateTimeOutputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

    public String handleRequest(Map<String, Object> requestMap, Context context) {


        try {

            Gson gson = new GsonBuilder().create();

            logger.debug("Request :" + gson.toJson(requestMap));

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

            Item item = table.getItem("id", id);

            Map<String, String> request = item.getMap("request");

            Map<String, String> slackData = gson.fromJson(request.get("slackData"), Map.class);

            Map<String, Object> bodyMap = new HashMap<>();


            //get token to reply
            Table botTokenTable = dynamoDB.getTable("botToken");

            Item botItem = botTokenTable.getItem("team_id", slackData.get("team_id"));

            String botAccessToken = botItem.getString("bot_access_token");


            bodyMap.put("token", botAccessToken);
            bodyMap.put("channel", slackData.get("channel"));

            bodyMap.put("text", mapText(alertType,flightStatus,slackData));

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
        Map<String, String> airportResource = (Map)flightStatus.get("airportResources");


        StringBuilder builder = new StringBuilder();
        builder.append("Hello <@").append(slackData.get("clientId")).append(">\n");

        builder.append("New live update for the flight: *")
                .append(flightStatus.get("carrierFsCode"))
                .append(" ").append(flightStatus.get("flightNumber")).append("* \n");

        builder.append("Flight status : *").append(flightStatusCode.getLabel()).append("*\n");

        switch (alertType) {
            case "EN_ROUTE":

                //Departed (On-Time)|(Delayed 15 Minutes) at : date
                builder.append("Departed ");

                String statusDelayed = "(On-time) ";

                if (null != delays
                        && null != delays.get("departureGateDelayMinutes")
                        && (Integer.parseInt((String)delays.get("departureGateDelayMinutes")) >= 15)) {
                    statusDelayed = "(Delayed *" + delays.get("departureGateDelayMinutes") + " Minutes*)";
                }

                builder.append(statusDelayed).append(" at : *")
                        .append(LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                                .get("actualGateDeparture").get("dateLocal")).format(dateTimeOutputFormat)).append("*\n");

                builder.append("*Estimated Arrival Time : ")
                        .append(LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                                .get("estimatedGateArrival").get("dateLocal")).format(dateTimeOutputFormat)).append("*\n");
                break;

            case "LANDED":

                //Arrived (On-Time)|(Delayed 15 Minutes) at : date
                builder.append("Arrived ");

                String arrStatusDelayed = "(On-time) ";

                if (null != delays
                        && null != delays.get("arrivalGateDelayMinutes")
                        && (Integer.parseInt((String)delays.get("arrivalGateDelayMinutes")) >= 15)) {
                    arrStatusDelayed = "(Delayed *" + delays.get("arrivalGateDelayMinutes") + " Minutes*)\n";
                }

                builder.append(arrStatusDelayed).append(" at : *")
                        .append(LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                                .get("actualRunwayArrival").get("dateLocal")).format(dateTimeOutputFormat)).append("*\n");
                break;

            case "CANCELLED":
            case "DIVERTED":
                builder.append("For more information please contact the Airline. \n");
                break;

            case "DEPARTURE_DELAY":
                //The flight departure is delayed 15 minutes
                builder.append("The flight departure is delayed *")
                        .append(delays.get("departureGateDelayMinutes"))
                        .append(" Minutes*\n");

                builder.append("*New Estimated Departure Time : ")
                        .append(LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                                .get("estimatedGateDeparture").get("dateLocal")).format(dateTimeOutputFormat)).append("*\n");
                break;

            case "ARRIVAL_DELAY":
                //The flight arrival is delayed 15 minutes
                //The flight departure is delayed 15 minutes
                builder.append("The flight arrival is delayed *")
                        .append(delays.get("arrivalGateDelayMinutes"))
                        .append(" Minutes*\n");

                builder.append("*New Estimated Arrival Time : ")
                        .append(LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                                .get("estimatedGateArrival").get("dateLocal")).format(dateTimeOutputFormat)).append("*\n");
                break;

            case "BAGGAGE":
                // The bagage will be available in the 5;
                builder.append("The baggage will be available in the baggage carousel *")
                .append(airportResource.get("baggage")).append("*");
                break;

            case "DEPARTURE_GATE":
                // The flight will departure from terminal 1, gate 55;
                if (null != airportResource && null != airportResource.get("departureGate")) {
                    builder.append("New Departure Gate : *")
                            .append(airportResource.get("departureGate")).append("*\n");
                }

                if (null != airportResource && null != airportResource.get("departureTerminal")) {
                    builder.append("Terminal : *")
                            .append(airportResource.get("departureTerminal")).append("*\n");
                }

                break;
        }


        return builder.toString();

    }
}
