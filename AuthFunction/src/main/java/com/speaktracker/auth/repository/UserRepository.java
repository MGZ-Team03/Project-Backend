package com.speaktracker.auth.repository;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

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
     * 이메일로 사용자 조회
     * @param email 사용자 이메일
     * @return 사용자 정보 (없으면 null)
     */
    public User findByEmail(String email) {
        GetItemRequest getItemRequest = GetItemRequest.builder()
            .tableName(usersTable)
            .key(Map.of("email", AttributeValue.builder().s(email).build()))
            .build();
        
        GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
        
        if (!getItemResponse.hasItem()) {
            return null;
        }
        
        Map<String, AttributeValue> item = getItemResponse.item();
        
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
        if (user.getProfileImage() != null) {
            item.put("profile_image", AttributeValue.builder().s(user.getProfileImage()).build());
        }
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
}
