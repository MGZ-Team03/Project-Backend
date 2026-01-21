package sentences.service;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class JobStatusService {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public JobStatusService(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public void createJob(String requestId, String status) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("job_id", AttributeValue.builder().s(requestId).build());
        item.put("status", AttributeValue.builder().s(status).build());
        item.put("ttl", AttributeValue.builder().n(String.valueOf(Instant.now().getEpochSecond() + 3600)).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
    }

    public void updateJobCompleted(String requestId, String conversationId, String aiResponse, int turnCount) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("job_id", AttributeValue.builder().s(requestId).build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":status", AttributeValue.builder().s("COMPLETED").build());
        expressionAttributeValues.put(":conversationId", AttributeValue.builder().s(conversationId).build());
        expressionAttributeValues.put(":aiResponse", AttributeValue.builder().s(aiResponse).build());
        expressionAttributeValues.put(":turnCount", AttributeValue.builder().n(String.valueOf(turnCount)).build());

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET #status = :status, conversation_id = :conversationId, ai_response = :aiResponse, turn_count = :turnCount")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        dynamoDbClient.updateItem(request);
    }

    public void updateJobFailed(String requestId, String error) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("job_id", AttributeValue.builder().s(requestId).build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":status", AttributeValue.builder().s("FAILED").build());
        expressionAttributeValues.put(":error", AttributeValue.builder().s(error).build());

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET #status = :status, #error = :error")
                .expressionAttributeNames(Map.of("#status", "status", "#error", "error"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        dynamoDbClient.updateItem(request);
    }

    public Map<String, AttributeValue> getJobStatus(String requestId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("job_id", AttributeValue.builder().s(requestId).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();

        GetItemResponse response = dynamoDbClient.getItem(request);

        if (!response.hasItem()) {
            return null;
        }

        return response.item();
    }
}
