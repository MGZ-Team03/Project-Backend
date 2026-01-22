package com.speaktracker.stt.service;

import com.speaktracker.stt.model.PronunciationResult;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class PronunciationResultRepository {
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    public PronunciationResultRepository(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    public void save(
            String studentEmail,
            String originalText,
            String transcribedText,
            String sentenceId,
            String sessionId,
            Long audioDurationMs,
            PronunciationResult result) {

        String timestamp = ISO_FORMATTER.format(Instant.now());
        long ttl = Instant.now().plusSeconds(90 * 24 * 60 * 60).getEpochSecond(); // 90일

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("student_email", AttributeValue.builder().s(studentEmail).build());
        item.put("timestamp", AttributeValue.builder().s(timestamp).build());
        item.put("original_text", AttributeValue.builder().s(originalText).build());
        item.put("transcribed_text", AttributeValue.builder().s(transcribedText).build());
        item.put("overall_score", AttributeValue.builder().n(String.valueOf(result.getOverallScore())).build());
        item.put("word_accuracy", AttributeValue.builder().n(String.valueOf(result.getWordAccuracy())).build());
        item.put("sequence_score", AttributeValue.builder().n(String.valueOf(result.getSequenceScore())).build());
        item.put("completeness_score", AttributeValue.builder().n(String.valueOf(result.getCompletenessScore())).build());
        item.put("feedback", AttributeValue.builder().s(result.getFeedback()).build());
        item.put("grade", AttributeValue.builder().s(result.getGrade()).build());
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());

        if (sentenceId != null && !sentenceId.isEmpty()) {
            item.put("sentence_id", AttributeValue.builder().s(sentenceId).build());
        }

        if (sessionId != null && !sessionId.isEmpty()) {
            item.put("session_id", AttributeValue.builder().s(sessionId).build());
        }

        if (audioDurationMs != null) {
            item.put("audio_duration_ms", AttributeValue.builder().n(String.valueOf(audioDurationMs)).build());
        }

        if (result.getMissedWords() != null && !result.getMissedWords().isEmpty()) {
            item.put("missed_words", AttributeValue.builder()
                    .l(result.getMissedWords().stream()
                            .map(w -> AttributeValue.builder().s(w).build())
                            .toArray(AttributeValue[]::new))
                    .build());
        }

        if (result.getExtraWords() != null && !result.getExtraWords().isEmpty()) {
            item.put("extra_words", AttributeValue.builder()
                    .l(result.getExtraWords().stream()
                            .map(w -> AttributeValue.builder().s(w).build())
                            .toArray(AttributeValue[]::new))
                    .build());
        }

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
    }
}
