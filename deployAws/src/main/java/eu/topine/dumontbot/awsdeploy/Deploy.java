package eu.topine.dumontbot.awsdeploy;

/**
 * Main class for aws artifacts deployment.
 */
public class Deploy {



    public static void main(String[] args) {

        DynamoDbDeploy dynamoDbDeploy = new DynamoDbDeploy();


        //database deploy
        dynamoDbDeploy.deploy("id","alertConfig");
        dynamoDbDeploy.deploy("team_id","botToken");

        //lambdas deploy




    }
}
