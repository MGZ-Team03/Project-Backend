package com.speaktracker.tts.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.speaktracker.tts.model.JobStatus;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class JobStatusService {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ObjectMapper objectMapper;

    public JobStatusService(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
        this.objectMapper = new ObjectMapper();
    }

    public void createJob(String jobId, String status) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("job_id", AttributeValue.builder().s(jobId).build());
        item.put("status", AttributeValue.builder().s(status).build());
        item.put("ttl", AttributeValue.builder().n(String.valueOf(Instant.now().getEpochSecond() + 3600)).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
    }

    public void updateJobCompleted(String jobId, String audioUrl) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("job_id", AttributeValue.builder().s(jobId).build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":status", AttributeValue.builder().s("COMPLETED").build());
        expressionAttributeValues.put(":audioUrl", AttributeValue.builder().s(audioUrl).build());

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET #status = :status, audio_url = :audioUrl")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        dynamoDbClient.updateItem(request);
    }

    public void updateJobFailed(String jobId, String error) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("job_id", AttributeValue.builder().s(jobId).build());

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

    public JobStatus getJobStatus(String jobId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("job_id", AttributeValue.builder().s(jobId).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();

        GetItemResponse response = dynamoDbClient.getItem(request);

        if (!response.hasItem()) {
            return null;
        }

        Map<String, AttributeValue> item = response.item();
        JobStatus jobStatus = new JobStatus();
        jobStatus.setJobId(jobId);
        jobStatus.setStatus(item.get("status").s());

        if (item.containsKey("audio_url")) {
            jobStatus.setAudioUrl(item.get("audio_url").s());
        }

        if (item.containsKey("error")) {
            jobStatus.setError(item.get("error").s());
        }

        return jobStatus;
    }
}
