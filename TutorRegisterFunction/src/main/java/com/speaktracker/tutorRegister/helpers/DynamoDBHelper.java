package com.speaktracker.tutorRegister.helpers;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

import com.speaktracker.tutorRegister.models.*;

/**
 * DynamoDB 작업을 위한 헬퍼 클래스
 */
public class DynamoDBHelper {
    private final DynamoDbClient dynamoDbClient;
    private final String usersTable;
    private final String tutorRequestsTable;
    private final String tutorStudentsTable;
    private final String notificationsTable;

    public DynamoDBHelper() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();
        this.usersTable = System.getenv("USERS_TABLE");
        this.tutorRequestsTable = System.getenv("TUTOR_REQUESTS_TABLE");
        this.tutorStudentsTable = System.getenv("TUTOR_STUDENTS_TABLE");
        this.notificationsTable = System.getenv("NOTIFICATIONS_TABLE");
    }

    // ===== 사용자 관련 =====

    /**
     * 이메일로 사용자 조회
     */
    public User getUserByEmail(String email) {
        try {
            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(usersTable)
                    .key(Map.of("email", AttributeValue.builder().s(email).build()))
                    .build());

            if (!response.hasItem()) {
                return null;
            }

            return mapToUser(response.item());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get user: " + email, e);
        }
    }

    /**
     * 튜터 목록 조회 (role=tutor인 사용자)
     */
    public List<User> getTutors() {
        try {
            ScanResponse response = dynamoDbClient.scan(ScanRequest.builder()
                    .tableName(usersTable)
                    .filterExpression("#role = :tutor")
                    .expressionAttributeNames(Map.of("#role", "role"))
                    .expressionAttributeValues(Map.of(
                            ":tutor", AttributeValue.builder().s("tutor").build()
                    ))
                    .build());

            return response.items().stream()
                    .map(this::mapToUser)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get tutors", e);
        }
    }

    // ===== 튜터 요청 관련 =====

    /**
     * 튜터 요청 생성
     */
    public void createTutorRequest(TutorRequest request) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("request_id", AttributeValue.builder().s(request.getRequestId()).build());
            item.put("created_at", AttributeValue.builder().n(String.valueOf(request.getCreatedAt())).build());
            item.put("student_email", AttributeValue.builder().s(request.getStudentEmail()).build());
            item.put("tutor_email", AttributeValue.builder().s(request.getTutorEmail()).build());
            item.put("tutor_email_status", AttributeValue.builder().s(request.getTutorEmailStatus()).build());
            item.put("status", AttributeValue.builder().s(request.getStatus()).build());
            item.put("updated_at", AttributeValue.builder().n(String.valueOf(request.getUpdatedAt())).build());
            item.put("ttl", AttributeValue.builder().n(String.valueOf(request.getTtl())).build());

            if (request.getMessage() != null) {
                item.put("message", AttributeValue.builder().s(request.getMessage()).build());
            }

            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tutorRequestsTable)
                    .item(item)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create tutor request", e);
        }
    }

    /**
     * 튜터 요청 조회 (PK + SK)
     */
    public TutorRequest getTutorRequest(String requestId, Long createdAt) {
        try {
            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(tutorRequestsTable)
                    .key(Map.of(
                            "request_id", AttributeValue.builder().s(requestId).build(),
                            "created_at", AttributeValue.builder().n(String.valueOf(createdAt)).build()
                    ))
                    .build());

            if (!response.hasItem()) {
                return null;
            }

            return mapToTutorRequest(response.item());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get tutor request", e);
        }
    }

    /**
     * 튜터 요청 조회 (requestId만 사용 - Query로 조회)
     */
    public TutorRequest getTutorRequestByRequestId(String requestId) {
        try {
            QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                    .tableName(tutorRequestsTable)
                    .keyConditionExpression("request_id = :requestId")
                    .expressionAttributeValues(Map.of(
                            ":requestId", AttributeValue.builder().s(requestId).build()
                    ))
                    .limit(1)
                    .build());

            if (response.items().isEmpty()) {
                return null;
            }

            return mapToTutorRequest(response.items().get(0));
        } catch (Exception e) {
            throw new RuntimeException("Failed to get tutor request by requestId", e);
        }
    }

    /**
     * 학생의 pending 요청 확인
     */
    public TutorRequest getPendingRequestByStudent(String studentEmail, String tutorEmail) {
        try {
            QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                    .tableName(tutorRequestsTable)
                    .indexName("student_email-created_at-index")
                    .keyConditionExpression("student_email = :email")
                    .filterExpression("tutor_email = :tutor AND #status = :pending")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(
                            ":email", AttributeValue.builder().s(studentEmail).build(),
                            ":tutor", AttributeValue.builder().s(tutorEmail).build(),
                            ":pending", AttributeValue.builder().s("pending").build()
                    ))
                    .limit(1)
                    .build());

            if (response.items().isEmpty()) {
                return null;
            }

            return mapToTutorRequest(response.items().get(0));
        } catch (Exception e) {
            throw new RuntimeException("Failed to check pending request", e);
        }
    }

    /**
     * 학생의 모든 요청 조회
     */
    public List<TutorRequest> getRequestsByStudent(String studentEmail, String status) {
        try {
            QueryRequest.Builder queryBuilder = QueryRequest.builder()
                    .tableName(tutorRequestsTable)
                    .indexName("student_email-created_at-index")
                    .keyConditionExpression("student_email = :email")
                    .expressionAttributeValues(Map.of(
                            ":email", AttributeValue.builder().s(studentEmail).build()
                    ))
                    .scanIndexForward(false); // 최신순 정렬

            // status 필터 추가
            if (status != null && !status.equals("all")) {
                queryBuilder
                        .filterExpression("#status = :status")
                        .expressionAttributeNames(Map.of("#status", "status"))
                        .expressionAttributeValues(Map.of(
                                ":email", AttributeValue.builder().s(studentEmail).build(),
                                ":status", AttributeValue.builder().s(status).build()
                        ));
            }

            QueryResponse response = dynamoDbClient.query(queryBuilder.build());

            return response.items().stream()
                    .map(this::mapToTutorRequest)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get student requests", e);
        }
    }

    /**
     * 튜터의 요청 조회
     */
    public List<TutorRequest> getRequestsByTutor(String tutorEmail, String status) {
        try {
            String statusFilter = status != null && !status.equals("all") ? status : "pending";
            String partitionKey = tutorEmail + "#" + statusFilter;

            QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                    .tableName(tutorRequestsTable)
                    .indexName("tutor_email_status-created_at-index")
                    .keyConditionExpression("tutor_email_status = :key")
                    .expressionAttributeValues(Map.of(
                            ":key", AttributeValue.builder().s(partitionKey).build()
                    ))
                    .scanIndexForward(false) // 최신순 정렬
                    .build());

            return response.items().stream()
                    .map(this::mapToTutorRequest)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get tutor requests", e);
        }
    }

    /**
     * 튜터 요청 업데이트 (승인/거부)
     */
    public void updateTutorRequestStatus(String requestId, Long createdAt, String status, 
                                          Long processedAt, String rejectionReason) {
        try {
            Map<String, AttributeValue> key = Map.of(
                    "request_id", AttributeValue.builder().s(requestId).build(),
                    "created_at", AttributeValue.builder().n(String.valueOf(createdAt)).build()
            );

            StringBuilder updateExpression = new StringBuilder("SET #status = :status, processed_at = :processedAt, updated_at = :updatedAt");
            Map<String, String> attributeNames = new HashMap<>();
            attributeNames.put("#status", "status");

            Map<String, AttributeValue> attributeValues = new HashMap<>();
            attributeValues.put(":status", AttributeValue.builder().s(status).build());
            attributeValues.put(":processedAt", AttributeValue.builder().n(String.valueOf(processedAt)).build());
            attributeValues.put(":updatedAt", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());

            if (rejectionReason != null) {
                updateExpression.append(", rejection_reason = :reason");
                attributeValues.put(":reason", AttributeValue.builder().s(rejectionReason).build());
            }

            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tutorRequestsTable)
                    .key(key)
                    .updateExpression(updateExpression.toString())
                    .expressionAttributeNames(attributeNames)
                    .expressionAttributeValues(attributeValues)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to update tutor request status", e);
        }
    }

    // ===== 튜터-학생 관계 관련 =====

    /**
     * 튜터-학생 관계 확인
     */
    public TutorStudent getTutorStudentRelation(String tutorEmail, String studentEmail) {
        try {
            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(tutorStudentsTable)
                    .key(Map.of(
                            "tutor_email", AttributeValue.builder().s(tutorEmail).build(),
                            "student_email", AttributeValue.builder().s(studentEmail).build()
                    ))
                    .build());

            if (!response.hasItem()) {
                return null;
            }

            return mapToTutorStudent(response.item());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get tutor-student relation", e);
        }
    }

    /**
     * 튜터의 현재 활성 학생 수 조회
     */
    public int getActiveTutorStudentCount(String tutorEmail) {
        try {
            QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                    .tableName(tutorStudentsTable)
                    .keyConditionExpression("tutor_email = :email")
                    .filterExpression("#status = :active")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(
                            ":email", AttributeValue.builder().s(tutorEmail).build(),
                            ":active", AttributeValue.builder().s("active").build()
                    ))
                    .select(Select.COUNT)
                    .build());

            return response.count();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get active student count", e);
        }
    }

    /**
     * 튜터-학생 관계 생성 (트랜잭션)
     */
    public void createTutorStudentRelation(TutorStudent relation, String requestId, Long requestCreatedAt, String newStatus) {
        try {
            Map<String, AttributeValue> relationItem = new HashMap<>();
            relationItem.put("tutor_email", AttributeValue.builder().s(relation.getTutorEmail()).build());
            relationItem.put("student_email", AttributeValue.builder().s(relation.getStudentEmail()).build());
            relationItem.put("assigned_at", AttributeValue.builder().n(String.valueOf(relation.getAssignedAt())).build());
            relationItem.put("status", AttributeValue.builder().s(relation.getStatus()).build());
            relationItem.put("request_id", AttributeValue.builder().s(relation.getRequestId()).build());

            // 트랜잭션으로 관계 생성 + 요청 상태 업데이트
            List<TransactWriteItem> transactItems = Arrays.asList(
                    TransactWriteItem.builder()
                            .put(Put.builder()
                                    .tableName(tutorStudentsTable)
                                    .item(relationItem)
                                    .build())
                            .build(),
                    TransactWriteItem.builder()
                            .update(Update.builder()
                                    .tableName(tutorRequestsTable)
                                    .key(Map.of(
                                            "request_id", AttributeValue.builder().s(requestId).build(),
                                            "created_at", AttributeValue.builder().n(String.valueOf(requestCreatedAt)).build()
                                    ))
                                    .updateExpression("SET #status = :status, processed_at = :processedAt, updated_at = :updatedAt")
                                    .expressionAttributeNames(Map.of("#status", "status"))
                                    .expressionAttributeValues(Map.of(
                                            ":status", AttributeValue.builder().s(newStatus).build(),
                                            ":processedAt", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build(),
                                            ":updatedAt", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build()
                                    ))
                                    .build())
                            .build()
            );

            dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create tutor-student relation", e);
        }
    }

    // ===== 매핑 메서드 =====

    private User mapToUser(Map<String, AttributeValue> item) {
        User user = new User();
        user.setEmail(item.get("email").s());
        user.setName(item.get("name").s());
        user.setRole(item.get("role").s());
        
        if (item.containsKey("profile_image")) {
            user.setProfileImage(item.get("profile_image").s());
        }
        if (item.containsKey("bio")) {
            user.setBio(item.get("bio").s());
        }
        if (item.containsKey("specialties") && item.get("specialties").hasSs()) {
            user.setSpecialties(item.get("specialties").ss());
        }
        if (item.containsKey("max_students")) {
            AttributeValue maxStudentsAttr = item.get("max_students");
            if (maxStudentsAttr.n() != null) {
                user.setMaxStudents(Integer.parseInt(maxStudentsAttr.n()));
            }
        }
        if (item.containsKey("is_accepting")) {
            user.setIsAccepting(item.get("is_accepting").bool());
        }
        if (item.containsKey("created_at")) {
            AttributeValue createdAtAttr = item.get("created_at");
            try {
                // Number 타입으로 시도
                if (createdAtAttr.n() != null) {
                    user.setCreatedAt(Long.parseLong(createdAtAttr.n()));
                }
                // String 타입으로 시도 (ISO 8601 또는 timestamp)
                else if (createdAtAttr.s() != null) {
                    String createdAtStr = createdAtAttr.s();
                    // ISO 8601 형식이면 파싱, 아니면 숫자로 파싱 시도
                    if (createdAtStr.matches("\\d+")) {
                        user.setCreatedAt(Long.parseLong(createdAtStr));
                    }
                    // ISO 8601은 일단 스킵 (필요시 Instant.parse 사용)
                }
            } catch (NumberFormatException e) {
                // created_at 파싱 실패는 무시 (선택적 필드)
            }
        }

        return user;
    }

    private TutorRequest mapToTutorRequest(Map<String, AttributeValue> item) {
        TutorRequest request = new TutorRequest();
        request.setRequestId(item.get("request_id").s());
        request.setCreatedAt(Long.parseLong(item.get("created_at").n()));
        request.setStudentEmail(item.get("student_email").s());
        request.setTutorEmail(item.get("tutor_email").s());
        request.setStatus(item.get("status").s());
        request.setUpdatedAt(Long.parseLong(item.get("updated_at").n()));

        if (item.containsKey("message")) {
            request.setMessage(item.get("message").s());
        }
        if (item.containsKey("processed_at")) {
            request.setProcessedAt(Long.parseLong(item.get("processed_at").n()));
        }
        if (item.containsKey("rejection_reason")) {
            request.setRejectionReason(item.get("rejection_reason").s());
        }
        if (item.containsKey("ttl")) {
            request.setTtl(Long.parseLong(item.get("ttl").n()));
        }

        return request;
    }

    private TutorStudent mapToTutorStudent(Map<String, AttributeValue> item) {
        TutorStudent relation = new TutorStudent();
        relation.setTutorEmail(item.get("tutor_email").s());
        relation.setStudentEmail(item.get("student_email").s());
        relation.setAssignedAt(Long.parseLong(item.get("assigned_at").n()));
        relation.setStatus(item.get("status").s());
        
        if (item.containsKey("request_id")) {
            relation.setRequestId(item.get("request_id").s());
        }

        return relation;
    }
}
