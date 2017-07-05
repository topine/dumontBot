package eu.topine.dumontbot.awsdeploy;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;

/**
 * Create a DynamoBd table
 */
public class DynamoDbDeploy {


    public void deploy(String idColumnName, String tableName) {

        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .build();

         AttributeDefinition idAttr = new AttributeDefinition().withAttributeName(idColumnName)
                .withAttributeType(ScalarAttributeType.S);
         ProvisionedThroughput throughput = new ProvisionedThroughput().withReadCapacityUnits(5L)
                .withWriteCapacityUnits(5L);

         KeySchemaElement idKey = new KeySchemaElement().withAttributeName(idColumnName).withKeyType(KeyType.HASH);

         CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                .withAttributeDefinitions(idAttr)
                .withKeySchema(idKey)
                .withProvisionedThroughput(throughput);

        client.createTable(createTableRequest);

    }

}
