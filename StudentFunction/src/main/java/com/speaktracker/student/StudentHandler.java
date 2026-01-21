package com.speaktracker.student;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 학생 관련 API Lambda 핸들러
 * 
 * 주요 기능:
 * 1. 학생의 튜터 정보 조회
 * 2. 학생 프로필 관리
 * 3. 학생 통계 조회
 */
public class StudentHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private static final String TUTOR_STUDENTS_TABLE = System.getenv("TUTOR_STUDENTS_TABLE");
    private static final Gson gson = new Gson();
    private static final Logger logger = Logger.getLogger(StudentHandler.class.getName());

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
        headers.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");

        String path = input.getPath();
        String method = input.getHttpMethod();
        logger.info("Path: " + path + ", Method: " + method);

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        try {
            // GET /api/student/tutor - 학생의 튜터 정보 조회
            if ("GET".equals(method) && "/api/student/tutor".equals(path)) {
                return getStudentTutor(input, response);
            }

            // 404 - Endpoint not found
            return response
                    .withStatusCode(404)
                    .withBody("{\"error\": \"Endpoint not found\"}");

        } catch (Exception e) {
            logger.severe("Error: " + e.getMessage());
            return response
                    .withStatusCode(500)
                    .withBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * GET /api/student/tutor?student_email={email}
     * 학생의 튜터 정보 조회
     */
    private APIGatewayProxyResponseEvent getStudentTutor(APIGatewayProxyRequestEvent input, APIGatewayProxyResponseEvent response) {
        Map<String, String> queryParams = input.getQueryStringParameters();
        
        if (queryParams == null || !queryParams.containsKey("student_email")) {
            return response
                    .withStatusCode(400)
                    .withBody("{\"error\": \"student_email parameter is required\"}");
        }

        String studentEmail = queryParams.get("student_email");
        logger.info("Querying tutor for student: " + studentEmail);

        try {
            // tutor_students 테이블에서 student_email-index GSI로 조회
            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(TUTOR_STUDENTS_TABLE)
                    .indexName("student_email-index")
                    .keyConditionExpression("student_email = :email")
                    .expressionAttributeValues(Map.of(
                            ":email", AttributeValue.builder().s(studentEmail).build()
                    ))
                    .limit(1)
                    .build();

            QueryResponse queryResponse = dynamoDbClient.query(queryRequest);

            if (queryResponse.items().isEmpty()) {
                return response
                        .withStatusCode(404)
                        .withBody("{\"error\": \"No tutor assigned to this student\"}");
            }

            Map<String, AttributeValue> item = queryResponse.items().get(0);
            
            Map<String, Object> result = new HashMap<>();
            result.put("tutor_email", item.get("tutor_email").s());
            result.put("student_email", item.get("student_email").s());
            result.put("assigned_at", item.containsKey("assigned_at") ? item.get("assigned_at").s() : null);
            result.put("status", item.containsKey("status") ? item.get("status").s() : "active");

            return response
                    .withStatusCode(200)
                    .withBody(gson.toJson(result));

        } catch (Exception e) {
            logger.severe("Error querying tutor: " + e.getMessage());
            return response
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Failed to query tutor: " + e.getMessage() + "\"}");
        }
    }
}
