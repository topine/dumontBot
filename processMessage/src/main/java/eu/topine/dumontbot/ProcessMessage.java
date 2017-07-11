package eu.topine.dumontbot;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lambda to process the incoming messages, calling lex for the analysis.
 */
public class ProcessMessage implements RequestHandler<Map<String, String>, String> {

    private static final String CLIENT_ID = "clientId";
    private static final String TEXT_INPUT = "textInput";
    private static final String TEST_SESSION = "testSession";
    private static final String FLIGIHT_STATUS = "FligihtStatus";
    private static final String PREVIOUS_STATUS = "previousStatus";
    private static final String CHANNEL = "channel";
    private static final String TEXT = "text";
    private static final String TOKEN = "token";
    private static final String FULFILLED = "Fulfilled";
    private static final String FAILED = "Failed";
    private static final String FLIGHT_STATUS = "FlightStatus";
    private static final String ID = "id";
    private static final String SLACK_DATA = "slackData";
    private static final String TEAM_ID = "team_id";


    private Logger logger = Logger.getLogger(ProcessMessage.class);
    private DateTimeFormatter dateTimeOutputFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.US);

    public String handleRequest(Map<String, String> requestMap, Context context) {

        try {

            GsonBuilder gsonBuilder = new GsonBuilder();

            Gson gson = gsonBuilder.create();

            //need to check if debug enabled to avoid
            if (logger.isInfoEnabled()) {
                logger.info("Request : " + gson.toJson(requestMap));
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

            if (logger.isInfoEnabled()) {
                logger.info("Lex request: " + gson.toJson(request));
            }
            PostTextResult postTextResult = amazonLexRuntimeClient.postText(request);

            if (logger.isInfoEnabled()) {
                logger.info("Lex response: " + gson.toJson(postTextResult));
            }

            if (FULFILLED.equals(postTextResult.getDialogState())
                    && FLIGHT_STATUS.equalsIgnoreCase(postTextResult.getIntentName())
                    && null != postTextResult.getSessionAttributes().get(PREVIOUS_STATUS)) {

                item = new Item()
                        .withPrimaryKey(ID, requestMap.get(CLIENT_ID))
                        .withJSON(PREVIOUS_STATUS,
                                gson.toJson(postTextResult.getSessionAttributes().get(PREVIOUS_STATUS)));

                table.putItem(item);
            }

            // remove from db if not the intent to subscribe for a previous status search.
            if (FAILED.equals(postTextResult.getDialogState())
                    && "Subscription".equalsIgnoreCase(postTextResult.getIntentName())) {

                table.deleteItem(new PrimaryKey(ID, requestMap.get(CLIENT_ID)));
            }



            String outputText = postTextResult.getMessage();
            //format the output date of the confirmation dialog.
            if ("ConfirmIntent".equalsIgnoreCase(postTextResult.getDialogState()) &&
                    (FLIGHT_STATUS.equalsIgnoreCase(postTextResult.getIntentName())
                    || "Subscription".equalsIgnoreCase(postTextResult.getIntentName())) ) {

                String unformattedDate = outputText.substring(outputText.length()-11,
                        outputText.length()-1);

                outputText = outputText.replace(unformattedDate,
                        LocalDate.parse(unformattedDate).format(dateTimeOutputFormat));
            }


            Map<String, Object> bodyMap = new HashMap<>();

            bodyMap.put(TOKEN,  botAccessToken);
            bodyMap.put(CHANNEL,requestMap.get(CHANNEL));

            // use attachment and not output text
            if (FULFILLED.equals(postTextResult.getDialogState())
                    && FLIGHT_STATUS.equalsIgnoreCase(postTextResult.getIntentName())) {

                bodyMap.put("attachments", outputText);
            } else {
                bodyMap.put(TEXT, outputText);
            }

            if (logger.isInfoEnabled()) {
                logger.info("Slack request : " + gson.toJson(bodyMap));
            }

            Unirest.post("https://slack.com/api/chat.postMessage")
                    .header("Content-Type", "application/json")
                    .queryString(bodyMap).asJson();

        } catch (Exception e) {
            logger.error("Error executing ProcessMessage : ", e);
        }

        return null;
    }
}
