package eu.topine.aws.lex.fulfillment;

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
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.apache.log4j.Logger;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Subscription Fulfillment.
 * <p>
 * Creates the id for the callback and call the Flight Status API alert
 */
public class SubscriptionFulfillment implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    public Logger logger = Logger.getLogger(SubscriptionFulfillment.class);
    private Gson objGson = new GsonBuilder().create();

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> requestMap, Context context) {


        Map<String, Object> response;

        try {

            if (logger.isDebugEnabled()) {
                logger.debug("Request : " + objGson.toJson(requestMap));
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            Map<String, String> slots = (Map) ((Map) requestMap.get("currentIntent")).get("slots");


            String subId = UUID.randomUUID().toString();


            Map<String, Object> params = new HashMap<>();
            params.put("appId", System.getenv("APP_ID"));
            params.put("appKey", System.getenv("APP_KEY"));
            params.put("type", "JSON");
            params.put("deliverTo", "https://bk7xv2trg7.execute-api.us-east-1.amazonaws.com/prod/flightalertcallback");
            params.put("_subId", subId);
            params.put("events", "dep,arr,can,div,depDelay15,depDelayDelta15,arrDelay15,arrDelayDelta15,depGate,bag");

            LocalDate flightDate = LocalDate.parse(slots.get("flightDate"), formatter);

            String airlineCode = slots.get("airlineCode");
            String flightNumber = slots.get("flightNumber");
            String airportCode = slots.get("departureAirport");
            String year = String.valueOf(flightDate.getYear());
            String month = String.valueOf(flightDate.getMonthValue());
            String day = String.valueOf(flightDate.getDayOfMonth());


            HttpResponse<JsonNode> jsonResponse  = Unirest.get("https://api.flightstats.com/flex/alerts/rest/v1/json/create/{airlineCode}/{flightNumber}/from/{airportCode}/departing/{year}/{month}/{day}")
                    .routeParam("airlineCode", airlineCode)
                    .routeParam("flightNumber",flightNumber)
                    .routeParam("year", year)
                    .routeParam("month", month)
                    .routeParam("day", day)
                    .routeParam("airportCode", airportCode).queryString(params).asJson();


            if ( 200 == jsonResponse.getStatus()) {
                response = getResponse("Subscription created with success. You will now receive alerts about the flight.", new HashMap<>());
                saveSubscriptionRequestToDB(subId, (Map) requestMap.get("sessionAttributes"));
            } else {
                response = getResponse("Sorry, I was not able to create the subscription.", new HashMap<>());
            }


        } catch (Exception e) {
            logger.error("Error with alert subscription fulfillment : ", e);
            response = getResponse("Sorry, I was not able to create the subscription.", new HashMap<>());
        }

        return response;

    }

    private boolean saveSubscriptionRequestToDB(String subId,
                                                Map<String, String> sessionAttributes) {

        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .build();

        DynamoDB dynamoDB = new DynamoDB(client);

        Table table = dynamoDB.getTable("alertConfig");

        Item item = new Item()
                .withPrimaryKey("id", subId)
                .withJSON("request", objGson.toJson(sessionAttributes));

        return null != table.putItem(item);

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




}
