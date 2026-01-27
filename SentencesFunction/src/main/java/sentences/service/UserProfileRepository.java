package sentences.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * UsersTable 프로필(learning_level 등) 조회/갱신용 Repository
 *
 * UsersTable 스키마:
 * - PK: role
 * - SK: email
 * - GSI: email-index (PK: email)
 */
public class UserProfileRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String usersTableName;

    public UserProfileRepository(String usersTableName) {
        if (usersTableName == null || usersTableName.isBlank()) {
            throw new IllegalArgumentException("USERS_TABLE is not configured");
        }
        this.dynamoDbClient = DynamoDbClient.create();
        this.usersTableName = usersTableName;
    }

    /**
     * email-index로 email에 해당하는 role(=PK)을 찾습니다.
     */
    public Optional<String> findRoleByEmail(String email) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":email", AttributeValue.builder().s(email).build());

        QueryRequest request = QueryRequest.builder()
            .tableName(usersTableName)
            .indexName("email-index")
            .keyConditionExpression("email = :email")
            .expressionAttributeValues(values)
            .limit(1)
            .build();

        QueryResponse response = dynamoDbClient.query(request);
        if (!response.hasItems() || response.items().isEmpty()) {
            return Optional.empty();
        }

        Map<String, AttributeValue> item = response.items().get(0);
        if (item == null || !item.containsKey("role") || item.get("role").s() == null) {
            return Optional.empty();
        }
        return Optional.of(item.get("role").s());
    }

    /**
     * 마지막 레벨 평가 날짜를 조회합니다.
     */
    public Optional<String> getLastLevelEvalDate(String role, String email) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("role", AttributeValue.builder().s(role).build());
        key.put("email", AttributeValue.builder().s(email).build());

        GetItemRequest request = GetItemRequest.builder()
            .tableName(usersTableName)
            .key(key)
            .projectionExpression("last_level_eval_date")
            .build();

        GetItemResponse response = dynamoDbClient.getItem(request);
        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }

        Map<String, AttributeValue> item = response.item();
        if (!item.containsKey("last_level_eval_date") || item.get("last_level_eval_date").s() == null) {
            return Optional.empty();
        }
        return Optional.of(item.get("last_level_eval_date").s());
    }

    /**
     * UsersTable의 learning_level과 last_level_eval_date를 UpdateItem으로 갱신합니다.
     */
    public void updateLearningLevel(String role, String email, String learningLevel, String evalDate) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("role", AttributeValue.builder().s(role).build());
        key.put("email", AttributeValue.builder().s(email).build());

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":lvl", AttributeValue.builder().s(learningLevel).build());
        values.put(":evalDate", AttributeValue.builder().s(evalDate).build());

        UpdateItemRequest request = UpdateItemRequest.builder()
            .tableName(usersTableName)
            .key(key)
            .updateExpression("SET learning_level = :lvl, last_level_eval_date = :evalDate")
            .expressionAttributeValues(values)
            .build();

        dynamoDbClient.updateItem(request);
    }
}

