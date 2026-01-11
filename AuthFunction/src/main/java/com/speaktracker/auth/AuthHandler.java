package com.speaktracker.auth;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * Handler for authentication requests using AWS Cognito and DynamoDB.
 * 
 * Endpoints:
 * - POST /api/auth/register : 회원가입 (Cognito + DynamoDB)
 * - POST /api/auth/login : 로그인 (Cognito)
 * - GET /api/auth : 사용자 정보 조회
 */
public class AuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CognitoIdentityProviderClient cognitoClient;
    private final DynamoDbClient dynamoDbClient;
    
    private final String userPoolId;
    private final String clientId;
    private final String usersTable;
    private final String tutorStudentsTable;
    
    public AuthHandler() {
        this.cognitoClient = CognitoIdentityProviderClient.create();
        this.dynamoDbClient = DynamoDbClient.create();
        
        this.userPoolId = System.getenv("USER_POOL_ID");
        this.clientId = System.getenv("CLIENT_ID");
        this.usersTable = System.getenv("USERS_TABLE");
        this.tutorStudentsTable = System.getenv("TUTOR_STUDENTS_TABLE");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String httpMethod = input.getHttpMethod();
        String path = input.getPath();
        
        context.getLogger().log("Method: " + httpMethod + ", Path: " + path);
        
        try {
            // POST /api/auth/register
            if ("POST".equals(httpMethod) && path.endsWith("/register")) {
                return handleRegister(input, context);
            }
            // POST /api/auth/login
            else if ("POST".equals(httpMethod) && path.endsWith("/login")) {
                return handleLogin(input, context);
            }
            // POST /api/auth/confirm
            else if ("POST".equals(httpMethod) && path.endsWith("/confirm")) {
                return handleConfirm(input, context);
            }
            // GET /api/auth/user - 사용자 정보 조회 (JWT 필요)
            else if ("GET".equals(httpMethod) && path.endsWith("/user")) {
                return handleGetUser(input, context);
            }
            // GET /api/auth
            else if ("GET".equals(httpMethod) && "/api/auth".equals(path)) {
                return handleGetUserInfo(input, context);
            }
            
            return createResponse(404, Map.of("error", "Not Found"));
            
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 회원가입: Cognito + DynamoDB
     * Request Body: { "email": "user@example.com", "password": "password123", "name": "홍길동", "role": "student" }
     */
    private APIGatewayProxyResponseEvent handleRegister(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, Object> body = objectMapper.readValue(input.getBody(), Map.class);
            String email = (String) body.get("email");
            String password = (String) body.get("password");
            String name = (String) body.get("name");
            String role = (String) body.getOrDefault("role", "student"); // 기본값: student
            
            // 1. Cognito 회원가입
            AttributeType emailAttr = AttributeType.builder()
                .name("email")
                .value(email)
                .build();
            
            AttributeType nameAttr = AttributeType.builder()
                .name("name")
                .value(name)
                .build();
            
            SignUpRequest signUpRequest = SignUpRequest.builder()
                .clientId(clientId)
                .username(email)
                .password(password)
                .userAttributes(emailAttr, nameAttr)
                .build();
            
            SignUpResponse signUpResponse = cognitoClient.signUp(signUpRequest);
            context.getLogger().log("Cognito SignUp successful: " + signUpResponse.userSub());
            
            // 2. DynamoDB에 사용자 정보 저장
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("email", AttributeValue.builder().s(email).build());
            item.put("name", AttributeValue.builder().s(name).build());
            item.put("role", AttributeValue.builder().s(role).build());
            item.put("created_at", AttributeValue.builder().s(Instant.now().toString()).build());
            
            PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(usersTable)
                .item(item)
                .build();
            
            dynamoDbClient.putItem(putItemRequest);
            context.getLogger().log("DynamoDB PutItem successful");
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "회원가입 성공");
            response.put("email", email);
            response.put("userSub", signUpResponse.userSub());
            response.put("confirmationRequired", !signUpResponse.userConfirmed());
            
            return createResponse(200, response);
            
        } catch (UsernameExistsException e) {
            return createResponse(400, Map.of("error", "이미 존재하는 이메일입니다."));
        } catch (InvalidPasswordException e) {
            return createResponse(400, Map.of("error", "비밀번호 정책을 만족하지 않습니다."));
        } catch (Exception e) {
            context.getLogger().log("Register error: " + e.getMessage());
            return createResponse(500, Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 로그인: Cognito InitiateAuth
     * Request Body: { "email": "user@example.com", "password": "password123" }
     */
    private APIGatewayProxyResponseEvent handleLogin(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, Object> body = objectMapper.readValue(input.getBody(), Map.class);
            String email = (String) body.get("email");
            String password = (String) body.get("password");
            
            Map<String, String> authParameters = new HashMap<>();
            authParameters.put("USERNAME", email);
            authParameters.put("PASSWORD", password);
            
            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .clientId(clientId)
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .authParameters(authParameters)
                .build();
            
            // JWT 토큰 발급
            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "로그인 성공");
            response.put("idToken", authResponse.authenticationResult().idToken());
            response.put("accessToken", authResponse.authenticationResult().accessToken());
            response.put("refreshToken", authResponse.authenticationResult().refreshToken());
            response.put("expiresIn", authResponse.authenticationResult().expiresIn());
            
            return createResponse(200, response);
            
        } catch (NotAuthorizedException e) {
            return createResponse(401, Map.of("error", "이메일 또는 비밀번호가 올바르지 않습니다."));
        } catch (UserNotConfirmedException e) {
            return createResponse(400, Map.of("error", "이메일 인증이 필요합니다."));
        } catch (Exception e) {
            context.getLogger().log("Login error: " + e.getMessage());
            return createResponse(500, Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 이메일 인증 확인
     * Request Body: { "email": "user@example.com", "code": "123456" }
     */
    private APIGatewayProxyResponseEvent handleConfirm(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, Object> body = objectMapper.readValue(input.getBody(), Map.class);
            String email = (String) body.get("email");
            String code = (String) body.get("code");
            
            ConfirmSignUpRequest confirmRequest = ConfirmSignUpRequest.builder()
                .clientId(clientId)
                .username(email)
                .confirmationCode(code)
                .build();
            
            cognitoClient.confirmSignUp(confirmRequest);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "이메일 인증 완료");
            response.put("email", email);
            
            return createResponse(200, response);
            
        } catch (CodeMismatchException e) {
            return createResponse(400, Map.of("error", "인증 코드가 올바르지 않습니다."));
        } catch (ExpiredCodeException e) {
            return createResponse(400, Map.of("error", "인증 코드가 만료되었습니다."));
        } catch (Exception e) {
            context.getLogger().log("Confirm error: " + e.getMessage());
            return createResponse(500, Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 사용자 정보 조회 (DynamoDB에서)
     * Authorization 헤더에서 JWT 파싱하여 email 추출 후 DynamoDB 조회
     */
    private APIGatewayProxyResponseEvent handleGetUser(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Authorization 헤더에서 JWT 토큰 추출
            Map<String, String> headers = input.getHeaders();
            String authHeader = headers != null ? headers.get("Authorization") : null;
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return createResponse(401, Map.of("error", "인증 토큰이 필요합니다."));
            }
            
            String token = authHeader.substring(7); // "Bearer " 제거
            
            // JWT 토큰에서 email 추출 (간단한 Base64 디코딩)
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return createResponse(401, Map.of("error", "유효하지 않은 토큰입니다."));
            }
            
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
            String email = (String) claims.get("email");
            
            if (email == null) {
                return createResponse(401, Map.of("error", "토큰에서 이메일을 찾을 수 없습니다."));
            }
            
            // DynamoDB에서 사용자 정보 조회
            software.amazon.awssdk.services.dynamodb.model.GetItemRequest getItemRequest = 
                software.amazon.awssdk.services.dynamodb.model.GetItemRequest.builder()
                    .tableName(usersTable)
                    .key(Map.of("email", AttributeValue.builder().s(email).build()))
                    .build();
            
            software.amazon.awssdk.services.dynamodb.model.GetItemResponse getItemResponse = 
                dynamoDbClient.getItem(getItemRequest);
            
            if (!getItemResponse.hasItem()) {
                return createResponse(404, Map.of("error", "사용자를 찾을 수 없습니다."));
            }
            
            Map<String, AttributeValue> item = getItemResponse.item();
            
            // 응답 데이터 구성
            Map<String, Object> response = new HashMap<>();
            response.put("email", item.get("email").s());
            response.put("name", item.get("name").s());
            response.put("role", item.get("role").s());
            response.put("created_at", item.get("created_at").s());
            
            return createResponse(200, response);
            
        } catch (Exception e) {
            context.getLogger().log("GetUser error: " + e.getMessage());
            return createResponse(500, Map.of("error", "사용자 정보 조회 실패"));
        }
    }
    
    /**
     * 사용자 정보 조회 (테스트용)
     */
    private APIGatewayProxyResponseEvent handleGetUserInfo(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "AuthHandler is ready");
        response.put("userPoolId", userPoolId);
        response.put("usersTable", usersTable);
        
        return createResponse(200, response);
    }
    
    /**
     * API Gateway 응답 생성
     */
    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        
        try {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withHeaders(headers)
                .withBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
