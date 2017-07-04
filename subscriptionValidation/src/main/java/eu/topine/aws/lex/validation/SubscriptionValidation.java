package eu.topine.aws.lex.validation;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Subscription initial validation to populate the fields based in the previous
 * flight status requested.
 */
public class SubscriptionValidation implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    @SuppressWarnings("unchecked")
    public Map<String, Object> handleRequest(Map<String, Object> requestMap,
                                             Context context) {

        Gson objGson = new GsonBuilder().create();

        Logger logger = Logger.getLogger(SubscriptionValidation.class);

        logger.debug("request : " + objGson.toJson(requestMap));

        Map<String, Object> response = buildEmptyResponse();


        try {

            String previousStatusString = null;


            if (null != requestMap.get("sessionAttributes")) {
                previousStatusString = ((Map<String, String>)
                        requestMap.get("sessionAttributes")).get("previousStatus");

            }
            if (null != previousStatusString) {

                Map<String, Object> previousStatus = objGson
                        .fromJson(previousStatusString, Map.class);

                response =  this.buildResponse(previousStatus);
            }

        } catch(Exception e) {
            logger.error("Error : ", e);
        } finally {
            logger.debug("response to lex : " + objGson.toJson(response));
        }
        return response;
    }


    /**
     * "dialogAction":{
     *   "type":"Delegate",
     *   "slots":{
     *           "RoomType":null,
     *           "CheckInDate":null,
     *           "Nights":null,
     *           "Location":null
     *          }
     *  }
     *
     * @param previousStatus
     * @return
     */
    private Map<String, Object> buildResponse(Map<String, Object> previousStatus) {
        Map<String, Object> response = new HashMap<>();

        Map<String, Object> dialogAction = new HashMap<>();

        dialogAction.put("type", "Delegate");

        Map<String, Object> slots = new HashMap<>();

        slots.put("airlineCode", previousStatus.get("airlineCode"));
        slots.put("flightNumber", previousStatus.get("flightNumber"));
        slots.put("flightDate", previousStatus.get("flightDate"));
        slots.put("departureAirport", previousStatus.get("departureAirport"));

        dialogAction.put("slots", slots);

        response.put("dialogAction", dialogAction);

        return response;
    }

    private Map<String, Object> buildEmptyResponse() {
        Map<String, Object> response = new HashMap<>();

        Map<String, Object> dialogAction = new HashMap<>();

        dialogAction.put("type", "Delegate");

        Map<String, Object> slots = new HashMap<>();

        slots.put("airlineCode", null);
        slots.put("flightNumber", null);
        slots.put("flightDate", null);
        slots.put("departureAirport", null);

        dialogAction.put("slots", slots);

        response.put("dialogAction", dialogAction);

        return response;
    }
}
