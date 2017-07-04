package eu.topine.flightstatusbot;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lexruntime.AmazonLexRuntime;
import com.amazonaws.services.lexruntime.AmazonLexRuntimeClient;
import com.amazonaws.services.lexruntime.model.PostTextRequest;
import com.amazonaws.services.lexruntime.model.PostTextResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mashape.unirest.http.Unirest;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda to process the incoming messages, calling lex for the analysis.
 */
public class ProcessMessage implements RequestHandler<Map<String, String>, String> {

    public static final String CLIENT_ID = "clientId";
    public static final String TEXT_INPUT = "textInput";
    public static final String TEST_SESSION = "testSession";
    public static final String FLIGIHT_STATUS = "FligihtStatus";
    public static final String PREVIOUS_STATUS = "previousStatus";
    public static final String CHANNEL = "channel";
    public static final String TEXT = "text";
    public static final String TOKEN = "token";
    public static final String FULFILLED = "Fulfilled";
    public static final String FLIGHT_STATUS = "FlightStatus";
    public static final String ID = "id";
    public static final String SLACK_DATA = "slackData";
    public static final String SLACK_TOKEN = "SLACK_TOKEN";
    public static final String TEAM_ID = "team_id";

    public Logger logger = Logger.getLogger(ProcessMessage.class);

    public String handleRequest(Map<String, String> requestMap, Context context) {

        try {

            GsonBuilder gsonBuilder = new GsonBuilder();

            Gson gson = gsonBuilder.create();

            //need to check if debug enabled to avoid
            if (logger.isDebugEnabled()) {
                logger.debug("Request : " + gson.toJson(requestMap));
            }


            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1)
                    .build();

            DynamoDB dynamoDB = new DynamoDB(client);

            Table botTokenTable = dynamoDB.getTable("botToken");

            Item botItem = botTokenTable.getItem(TEAM_ID, requestMap.get(TEAM_ID));

            String botUserId = botItem.getString("bot_user_id");

            String botAccessToken = botItem.getString("bot_access_token");


            if ( (requestMap.get(CHANNEL).startsWith("C")
                    ||  requestMap.get(CHANNEL).startsWith("G"))
                    && !requestMap.get(TEXT_INPUT).contains("<@"+botUserId+">")){
                return "NOT BOT MESSAGE";
            }

            String text = requestMap.get(TEXT_INPUT).replace("<@"+botUserId+">", "");


            AmazonLexRuntime amazonLexRuntimeClient = AmazonLexRuntimeClient.builder().standard()
                    .withRegion(Regions.US_EAST_1).build();
            PostTextRequest request = new PostTextRequest();
            request.setBotName(FLIGIHT_STATUS);
            request.setBotAlias(TEST_SESSION);
            request.setUserId(requestMap.get(CLIENT_ID));
            request.setInputText(text);

            Table table = dynamoDB.getTable(PREVIOUS_STATUS);


            Map<String,String> sessionAttributes = new HashMap<>();

            Map<String,String> slackData = new HashMap<>();
            slackData.put(CLIENT_ID, requestMap.get(CLIENT_ID));
            slackData.put(CHANNEL, requestMap.get(CHANNEL));
            slackData.put(TEAM_ID, requestMap.get(TEAM_ID));

            sessionAttributes.put(SLACK_DATA, gson.toJson(slackData));


            // get the previousStatus if available
            Item item = table.getItem(ID, requestMap.get(CLIENT_ID));
            if (null != item) {
                sessionAttributes.put(PREVIOUS_STATUS,item.getString(PREVIOUS_STATUS));
            }

            request.setSessionAttributes(sessionAttributes);
            PostTextResult postTextResult = amazonLexRuntimeClient.postText(request);


            if (FULFILLED.equals(postTextResult.getDialogState())
                    && FLIGHT_STATUS.equalsIgnoreCase(postTextResult.getIntentName())
                    && null != postTextResult.getSessionAttributes().get(PREVIOUS_STATUS)) {

                item = new Item()
                        .withPrimaryKey(ID, requestMap.get(CLIENT_ID))
                        .withJSON(PREVIOUS_STATUS,
                                gson.toJson(postTextResult.getSessionAttributes().get(PREVIOUS_STATUS)));

                table.putItem(item);
            }


            Map<String, Object> bodyMap = new HashMap<>();

            bodyMap.put(TOKEN,  botAccessToken);
            bodyMap.put(CHANNEL,requestMap.get(CHANNEL));
            bodyMap.put(TEXT, postTextResult.getMessage());

            Unirest.post("https://slack.com/api/chat.postMessage")
                    .header("Content-Type", "application/json")
                    .queryString(bodyMap).asJson();

        } catch (Exception e) {
            logger.error("Error executing ProcessMessage : ", e);
        }

        return null;
    }
}
