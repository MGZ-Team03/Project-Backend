package com.speaktracker.tts.service;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class SentenceAudioService {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public SentenceAudioService(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public void updateCompleted(
        String sessionId,
        int sentenceIndex,
        String s3Key,
        Long durationMs,
        String voiceId,
        String jobId
    ) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("sessionId", AttributeValue.builder().s(sessionId).build());
        key.put("sentenceIndex", AttributeValue.builder().n(String.valueOf(sentenceIndex)).build());

        Map<String, String> names = new HashMap<>();
        names.put("#status", "status");

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":status", AttributeValue.builder().s("COMPLETED").build());
        values.put(":completedAt", AttributeValue.builder().s(Instant.now().toString()).build());
        values.put(":s3Key", AttributeValue.builder().s(s3Key).build());

        String updateExpression = "SET #status = :status, completedAt = :completedAt, s3Key = :s3Key";

        if (durationMs != null) {
            values.put(":durationMs", AttributeValue.builder().n(String.valueOf(durationMs)).build());
            updateExpression += ", durationMs = :durationMs";
        }
        if (voiceId != null) {
            values.put(":voiceId", AttributeValue.builder().s(voiceId).build());
            updateExpression += ", voiceId = :voiceId";
        }
        if (jobId != null) {
            values.put(":jobId", AttributeValue.builder().s(jobId).build());
            updateExpression += ", jobId = :jobId";
        }

        UpdateItemRequest req = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression(updateExpression)
            .expressionAttributeNames(names)
            .expressionAttributeValues(values)
            .build();

        dynamoDbClient.updateItem(req);
    }

    public void updateFailed(
        String sessionId,
        int sentenceIndex,
        String errorMessage,
        String errorCode
    ) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("sessionId", AttributeValue.builder().s(sessionId).build());
        key.put("sentenceIndex", AttributeValue.builder().n(String.valueOf(sentenceIndex)).build());

        Map<String, String> names = new HashMap<>();
        names.put("#status", "status");

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":status", AttributeValue.builder().s("FAILED").build());
        values.put(":completedAt", AttributeValue.builder().s(Instant.now().toString()).build());
        if (errorMessage != null) values.put(":errorMessage", AttributeValue.builder().s(errorMessage).build());
        if (errorCode != null) values.put(":errorCode", AttributeValue.builder().s(errorCode).build());

        String updateExpression = "SET #status = :status, completedAt = :completedAt";
        if (errorMessage != null) updateExpression += ", errorMessage = :errorMessage";
        if (errorCode != null) updateExpression += ", errorCode = :errorCode";

        UpdateItemRequest req = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression(updateExpression)
            .expressionAttributeNames(names)
            .expressionAttributeValues(values)
            .build();

        dynamoDbClient.updateItem(req);
    }
}

