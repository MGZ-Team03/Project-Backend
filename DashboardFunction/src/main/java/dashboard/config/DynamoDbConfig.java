package dashboard.config;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDbConfig {

    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();

    public static DynamoDbClient connectDynamoDb() {
        return dynamoDbClient;
    }
}
