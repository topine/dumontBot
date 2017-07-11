package eu.topine.aws.lex.fulfillment;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import eu.topine.dumontbot.commons.FlightStatusCode;
import eu.topine.dumontbot.commons.SlackAttachment;
import org.apache.log4j.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fulfillment action for the flight status
 */
@SuppressWarnings("unchecked")
public class FlightStatusFulfillment implements RequestHandler<Map<String, Object>, Map<String, Object>> {


    private Logger logger = Logger.getLogger(FlightStatusFulfillment.class);

    private DateTimeFormatter dateTimeOutputFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy hh:mm a", Locale.US);

    private Gson objGson = new GsonBuilder().create();

    public Map<String, Object> handleRequest(Map<String, Object> requestMap, Context context) {


        Map<String, Object> response;

        logger.info("Request : " + objGson.toJson(requestMap));


        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            Map<String, String> slots = (Map) ((Map) requestMap.get("currentIntent")).get("slots");


            Map<String, Object> params = new HashMap<>();
            params.put("appId", System.getenv("APP_ID"));
            params.put("appKey", System.getenv("APP_KEY"));
            params.put("utc", "false");


            LocalDate flightDate = LocalDate.parse(slots.get("FlightDate"), formatter);


            String airlineCode = slots.get("airlineCode");
            String flightNumber = slots.get("FlightNumber");
            String year = String.valueOf(flightDate.getYear());
            String month = String.valueOf(flightDate.getMonthValue());
            String day = String.valueOf(flightDate.getDayOfMonth());


            Map<String, String> sessionAttributes = new HashMap<>();
            sessionAttributes.put("airlineCode", airlineCode);
            sessionAttributes.put("flightNumber", flightNumber);
            sessionAttributes.put("flightDate", slots.get("FlightDate"));
            sessionAttributes.put("year", year);
            sessionAttributes.put("month", month);
            sessionAttributes.put("day", day);

            // its not possible to map from AWS Lex if its a departure or arrival
            // in this case, I try first as a departure and then as arrival

            HttpResponse<JsonNode> jsonResponse = getFlightStatus(params, airlineCode, flightNumber,
                    year, month, day, true);

            Map<String, Object> responseMap = objGson.fromJson(jsonResponse.getBody().toString(), Map.class);

            logger.info("Flight stats response dep: " + objGson.toJson(jsonResponse));

            List<Map<String, Object>> flightStatuses = (ArrayList) responseMap.get("flightStatuses");

            if (null == flightStatuses
                    || flightStatuses.isEmpty()) {

                jsonResponse = getFlightStatus(params, airlineCode, flightNumber,
                        year, month, day, false);

                logger.info("Flight stats response arr: " + objGson.toJson(jsonResponse));

                responseMap = objGson.fromJson(jsonResponse.getBody().toString(), Map.class);

                flightStatuses = (ArrayList) responseMap.get("flightStatuses");
            }


            if (null != flightStatuses
                    && !flightStatuses.isEmpty()) {

                logger.info("Flight stats response : " + objGson.toJson(flightStatuses));

                Map<String, Object> flightStatus = flightStatuses.get(0);

                response = buildResponseWithText(flightStatus, sessionAttributes);


            } else {

                logger.error("Flight not found");
                response = getResponseError();
            }

        } catch (Exception e) {
            logger.error("Error with flight status fulfillment : ", e);
            response = getResponseError();
        }


        logger.info("Fulfillment response : " + response);

        return response;
    }

    private HttpResponse<JsonNode> getFlightStatus(Map<String, Object> params, String airlineCode,
                                                   String flightNumber, String year, String month,
                                                   String day, boolean departure) throws UnirestException {

        String depOrArr = departure ? "dep" : "arr";

        return Unirest.get("https://api.flightstats.com/flex/flightstatus/rest/v2/json/flight/status/{airlineCode}/{flightNumber}/{depOrArr}/{year}/{month}/{day}")
                .routeParam("airlineCode", airlineCode)
                .routeParam("flightNumber", flightNumber)
                .routeParam("year", year)
                .routeParam("month", month)
                .routeParam("day", day)
                .routeParam("depOrArr", depOrArr).queryString(params).asJson();
    }


    private Map<String, Object> getResponseError() {

        SlackAttachment errorAttachment = new SlackAttachment();
        errorAttachment.setText("Sorry, we are not able to find the status of your flight.");
        errorAttachment.setColor("danger");
        //errorAttachment.addField("","",false);

        return getResponse(objGson.toJson(Collections.singletonList(errorAttachment)), new HashMap<>());
    }


    private Map<String, Object> getResponse(String text, Map<String, String> sessionAttributes) {

        Gson objGson = new GsonBuilder().create();

        Map<String, Object> dialogAction = new HashMap<>();

        dialogAction.put("type", "Close");
        dialogAction.put("fulfillmentState", "Fulfilled");

        Map<String, String> message = new HashMap<>();

        message.put("contentType", "PlainText");
        message.put("content", text);

        dialogAction.put("message", message);

        Map<String, Object> response = new HashMap<>();
        Map<String, String> previousStatus = new HashMap<>();

        previousStatus.put("previousStatus", objGson.toJson(sessionAttributes));
        response.put("sessionAttributes", previousStatus);
        response.put("dialogAction", dialogAction);

        return response;
    }


    private Map<String, Object> buildResponseWithText(Map<String, Object> flightStatus, Map<String, String> sessionAttributes) {

        FlightStatusCode flightStatusCode = FlightStatusCode.findByShortCode((String) flightStatus.get("status"));

        String departureAirport = (String) flightStatus.get("departureAirportFsCode");
        String arrivalAirport = (String) flightStatus.get("arrivalAirportFsCode");

        sessionAttributes.put("departureAirport", departureAirport);
        sessionAttributes.put("arrivalAirport", arrivalAirport);

        SlackAttachment statusAttachment = new SlackAttachment();
        statusAttachment.setColor("good");
        statusAttachment.getMrkdwnIn().add("text");

        StringBuilder text = new StringBuilder();
        // Flight AA 1234
        text.append("\nFlight *").append(flightStatus.get("carrierFsCode"))
                .append("*  *").append(flightStatus.get("flightNumber")).append("* \n");

        // from NCE to CDG
        text.append("From *").append(departureAirport)
                .append("* to *").append(arrivalAirport).append("*\n");

        // Flight Status is En-Route (On-time) / (Delayed)
        text.append("Flight status is : *").append(flightStatusCode.getLabel())
                .append("* ");


        Map<String, Object> delays = (Map) flightStatus.get("delays");

        String statusDelayed = "(On-time)\n";

        if (null != delays) {

            if (FlightStatusCode.SCHEDULED.equals(flightStatusCode)
                    && null != delays.get("departureGateDelayMinutes")
                    && ((Double) delays.get("departureGateDelayMinutes")).intValue() >= 15) {
                statusDelayed = "(Delayed *" + ((Double) delays.get("departureGateDelayMinutes")).intValue() + " Minutes*)\n";
            }

            if ((FlightStatusCode.EN_ROUTE.equals(flightStatusCode) || FlightStatusCode.LANDED.equals(flightStatusCode))
                    && null != delays.get("arrivalGateDelayMinutes")
                    && ((Double) delays.get("arrivalGateDelayMinutes")).intValue() >= 15) {
                statusDelayed = "(Delayed *" + ((Double) delays.get("arrivalGateDelayMinutes")).intValue() + " Minutes*)\n";
            }
        }


        text.append(statusDelayed);

        statusAttachment.setText(text.toString());

        statusAttachment.addField("", "", false);
        statusAttachment.addField("Departure", "", false);
        statusAttachment.addField("", "", false);

        Map<String, String> airportResource = (Map) flightStatus.get("airportResources");

        if (null != airportResource && null != airportResource.get("departureTerminal")) {
            statusAttachment.addField("Departure Terminal",
                    airportResource.get("departureTerminal"), true);
        }

        if (null != airportResource && null != airportResource.get("departureGate")) {
            statusAttachment.addField("Departure Gate",
                    airportResource.get("departureGate"), true);
        }

        //empty line
        statusAttachment.addField("", "", false);

        if (null != ((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                .get("actualGateDeparture")) {
            statusAttachment.addField("Departed at",
                    LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                            .get("actualGateDeparture").get("dateLocal")).format(dateTimeOutputFormat), true);

        } else if (null != ((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                .get("estimatedGateDeparture")) {

            statusAttachment.addField("Estimated Departure Time",
                    LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                            .get("estimatedGateDeparture").get("dateLocal")).format(dateTimeOutputFormat), true);
        }

        statusAttachment.addField("Scheduled Departure Time",
                LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                        .get("scheduledGateDeparture").get("dateLocal")).format(dateTimeOutputFormat), true);


        //empty line
        statusAttachment.addField("", "", false);

        statusAttachment.addField("Arrival", "", false);
        statusAttachment.addField("", "", false);

        if (null != airportResource && null != airportResource.get("arrivalTerminal")) {
            statusAttachment.addField("Arrival Terminal", airportResource.get("arrivalTerminal"), true);
        }

        if (null != airportResource && null != airportResource.get("arrivalGate")) {
            statusAttachment.addField("Arrival Gate", airportResource.get("arrivalGate"), true);
        }

        statusAttachment.addField("", "", false);


        if (null != ((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                .get("actualGateArrival")) {
            statusAttachment.addField("Arrived at", LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                    .get("actualGateArrival").get("dateLocal")).format(dateTimeOutputFormat), true);

        } else if (null != ((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                .get("estimatedGateArrival")) {
            statusAttachment.addField("Estimated Arrival Time", LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                    .get("estimatedGateArrival").get("dateLocal")).format(dateTimeOutputFormat), true);
        }

        statusAttachment.addField("Scheduled Arrival Time", LocalDateTime.parse(((Map<String, Map<String, String>>) flightStatus.get("operationalTimes"))
                .get("scheduledGateArrival").get("dateLocal")).format(dateTimeOutputFormat), true);

        statusAttachment.addField("", "", false);

        statusAttachment.addField("Please let me know if you want to get live updates about this flight.", "", false);


        return getResponse(objGson.toJson(Collections.singletonList(statusAttachment)), sessionAttributes);
    }


}
