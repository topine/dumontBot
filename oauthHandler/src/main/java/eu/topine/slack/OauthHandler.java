package eu.topine.slack;

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

import java.util.HashMap;
import java.util.Map;

/**
 * Oauth handler for slack
 */
public class OauthHandler implements RequestHandler<Map<String, String>, String> {


    Gson gson = new GsonBuilder().create();

    public String handleRequest(Map<String, String> requestMap, Context context) {

        System.out.println("Request : " + gson.toJson(requestMap));


        Map<String, Object> authRequest = new HashMap<>();

        authRequest.put("client_id", System.getenv("CLIENT_ID"));
        authRequest.put("client_secret", System.getenv("CLIENT_SECRET"));
        authRequest.put("code", requestMap.get("code"));

        HttpResponse<JsonNode> oauthResponse = null;
        try {


            System.out.println(" Request to slack : " + gson.toJson(authRequest));
            oauthResponse = Unirest.get("https://slack.com/api/oauth.access")
                    .queryString(authRequest).asJson();

            if (oauthResponse.getStatus() == 200) {
                Map<String, Object> responseMap = gson.fromJson(oauthResponse.getBody().toString(), Map.class);

                System.out.println(gson.toJson(responseMap));

                saveResponseToDb(responseMap);
            }

        } catch (Exception e) {
            System.err.println("Error getting bot token.");
            e.printStackTrace(System.err);
        }

        return "SUCCESS";
    }

    private boolean saveResponseToDb(Map<String, Object> responseMap) {


        Map<String, String> bot = (Map)responseMap.get("bot");


        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .build();

        DynamoDB dynamoDB = new DynamoDB(client);

        Table table = dynamoDB.getTable("botToken");




        Item item = new Item()
                .withPrimaryKey("team_id", responseMap.get("team_id"))
                .withString("bot_user_id", bot.get("bot_user_id"))
                .withString("bot_access_token",  bot.get("bot_access_token"));

        return null != table.putItem(item);

    }
}
