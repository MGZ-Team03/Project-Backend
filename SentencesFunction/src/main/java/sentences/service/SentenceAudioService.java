package sentences.service;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SentenceAudioService {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public SentenceAudioService(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public void putPending(
        String sessionId,
        int sentenceIndex,
        String english,
        String korean,
        String voiceId,
        String jobId,
        long ttlEpochSeconds
    ) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("sessionId", AttributeValue.builder().s(sessionId).build());
        item.put("sentenceIndex", AttributeValue.builder().n(String.valueOf(sentenceIndex)).build());
        item.put("status", AttributeValue.builder().s("PENDING").build());
        item.put("requestedAt", AttributeValue.builder().s(Instant.now().toString()).build());
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttlEpochSeconds)).build());

        if (english != null) item.put("english", AttributeValue.builder().s(english).build());
        if (korean != null) item.put("korean", AttributeValue.builder().s(korean).build());
        if (voiceId != null) item.put("voiceId", AttributeValue.builder().s(voiceId).build());
        if (jobId != null) item.put("jobId", AttributeValue.builder().s(jobId).build());

        PutItemRequest req = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build();

        dynamoDbClient.putItem(req);
    }

    public List<Map<String, AttributeValue>> queryBySessionId(String sessionId) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":sid", AttributeValue.builder().s(sessionId).build());

        QueryRequest req = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("sessionId = :sid")
            .expressionAttributeValues(values)
            .scanIndexForward(true)
            .build();

        QueryResponse resp = dynamoDbClient.query(req);
        return resp.items();
    }
}

