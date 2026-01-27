package com.speaktracker.auth.repository;

import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import com.speaktracker.auth.model.User;

/**
 * DynamoDB 사용자 저장소
 */
public class UserRepository {
    
    private final DynamoDbClient dynamoDbClient;
    private final String usersTable;
    
    public UserRepository(DynamoDbClient dynamoDbClient, String usersTable) {
        this.dynamoDbClient = dynamoDbClient;
        this.usersTable = usersTable;
    }
    
    /**
     * 사용자 정보 저장
     * @param user 저장할 사용자
     */
    public void save(User user) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("email", AttributeValue.builder().s(user.getEmail()).build());
        item.put("name", AttributeValue.builder().s(user.getName()).build());
        item.put("role", AttributeValue.builder().s(user.getRole()).build());
        item.put("created_at", AttributeValue.builder().s(user.getCreatedAt()).build());
        
        if (user.getUserSub() != null) {
            item.put("user_sub", AttributeValue.builder().s(user.getUserSub()).build());
        }
        
        // 튜터인 경우 기본값 설정
        if ("tutor".equals(user.getRole())) {
            item.put("max_students", AttributeValue.builder().n("10").build());
            item.put("is_accepting", AttributeValue.builder().bool(true).build());
        }
        
        PutItemRequest putItemRequest = PutItemRequest.builder()
            .tableName(usersTable)
            .item(item)
            .build();
        
        dynamoDbClient.putItem(putItemRequest);
    }
    
    /**
     * 이메일로 사용자 조회 (GSI 사용)
     * @param email 사용자 이메일
     * @return 사용자 정보 (없으면 null)
     */
    public User findByEmail(String email) {
        QueryRequest queryRequest = QueryRequest.builder()
            .tableName(usersTable)
            .indexName("email-index")
            .keyConditionExpression("email = :email")
            .expressionAttributeValues(Map.of(
                ":email", AttributeValue.builder().s(email).build()
            ))
            .limit(1)
            .build();
        
        QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
        
        if (!queryResponse.hasItems() || queryResponse.items().isEmpty()) {
            return null;
        }
        
        Map<String, AttributeValue> item = queryResponse.items().get(0);
        
        User user = new User();
        user.setEmail(item.get("email").s());
        user.setName(item.get("name").s());
        user.setRole(item.get("role").s());
        user.setCreatedAt(item.get("created_at").s());
        
        if (item.containsKey("user_sub")) {
            user.setUserSub(item.get("user_sub").s());
        }
        if (item.containsKey("profile_image")) {
            user.setProfileImage(item.get("profile_image").s());
        }
        if (item.containsKey("learning_level")) {
            user.setLearningLevel(item.get("learning_level").s());
        }
        if (item.containsKey("last_level_eval_date")) {
            user.setLastLevelEvalDate(item.get("last_level_eval_date").s());
        }

        return user;
    }
    
    /**
     * 사용자 정보 업데이트
     * @param user 업데이트할 사용자
     */
    public void update(User user) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("email", AttributeValue.builder().s(user.getEmail()).build());
        item.put("name", AttributeValue.builder().s(user.getName()).build());
        item.put("role", AttributeValue.builder().s(user.getRole()).build());
        item.put("created_at", AttributeValue.builder().s(user.getCreatedAt()).build());
        
        if (user.getUserSub() != null) {
            item.put("user_sub", AttributeValue.builder().s(user.getUserSub()).build());
        }
        // profileImage: null이 아니고 비어있지 않으면 저장
        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            item.put("profile_image", AttributeValue.builder().s(user.getProfileImage()).build());
        }
        // profileImage가 null이면 필드 자체를 포함하지 않음 (PutItem은 전체 교체이므로 기존 값 삭제됨)
        if (user.getLearningLevel() != null) {
            item.put("learning_level", AttributeValue.builder().s(user.getLearningLevel()).build());
        }
        
        // 튜터인 경우 기존 값 유지를 위해 조건부 처리
        if ("tutor".equals(user.getRole())) {
            item.put("max_students", AttributeValue.builder().n("10").build());
            item.put("is_accepting", AttributeValue.builder().bool(true).build());
        }
        
        PutItemRequest putItemRequest = PutItemRequest.builder()
            .tableName(usersTable)
            .item(item)
            .build();
        
        dynamoDbClient.putItem(putItemRequest);
    }

    /**
     * 모든 학생 조회 (Scan)
     * @param limit 조회할 최대 개수
     * @param exclusiveStartKey 페이지네이션 시작 키
     * @return ScanResult (학생 목록 + LastEvaluatedKey)
     */
    public ScanResult scanStudents(int limit, Map<String, AttributeValue> exclusiveStartKey) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":roleValue", AttributeValue.builder().s("student").build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#role", "role");

        ScanRequest.Builder scanRequestBuilder = ScanRequest.builder()
            .tableName(usersTable)
            .filterExpression("#role = :roleValue")
            .expressionAttributeValues(expressionAttributeValues)
            .expressionAttributeNames(expressionAttributeNames)
            .projectionExpression("email, learning_level")
            .limit(limit);

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            scanRequestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        ScanResponse response = dynamoDbClient.scan(scanRequestBuilder.build());

        // User 객체 리스트로 변환
        List<User> users = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            User user = new User();
            user.setEmail(item.get("email").s());
            if (item.containsKey("learning_level")) {
                user.setLearningLevel(item.get("learning_level").s());
            }
            users.add(user);
        }

        return new ScanResult(users, response.lastEvaluatedKey());
    }

    /**
     * Scan 결과
     */
    public static class ScanResult {
        private final List<User> users;
        private final Map<String, AttributeValue> lastEvaluatedKey;

        public ScanResult(List<User> users, Map<String, AttributeValue> lastEvaluatedKey) {
            this.users = users;
            this.lastEvaluatedKey = lastEvaluatedKey;
        }

        public List<User> getUsers() {
            return users;
        }

        public Map<String, AttributeValue> getLastEvaluatedKey() {
            return lastEvaluatedKey;
        }
    }
}
