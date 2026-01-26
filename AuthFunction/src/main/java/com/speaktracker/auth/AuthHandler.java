package com.speaktracker.auth;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import com.speaktracker.auth.dto.AuthResponse;
import com.speaktracker.auth.dto.ConfirmRequest;
import com.speaktracker.auth.dto.LoginRequest;
import com.speaktracker.auth.dto.RefreshRequest;
import com.speaktracker.auth.dto.RegisterRequest;
import com.speaktracker.auth.dto.UpdateProfileRequest;
import com.speaktracker.auth.dto.UserResponse;
import com.speaktracker.auth.exception.AuthException;
import com.speaktracker.auth.repository.UserRepository;
import com.speaktracker.auth.service.AuthService;
import com.speaktracker.auth.service.CognitoService;
import com.speaktracker.auth.service.JwtService;
import com.speaktracker.auth.service.UserService;
import com.speaktracker.auth.service.S3Service;

/**
 * Handler for authentication requests using AWS Cognito and DynamoDB.
 * 
 * Endpoints:
 * - POST /api/auth/register : 회원가입 (Cognito + DynamoDB)
 * - POST /api/auth/login : 로그인 (Cognito)
 * - POST /api/auth/confirm : 이메일 인증 확인
 * - POST /api/auth/refresh : 토큰 갱신 (RefreshToken)
 * - GET /api/auth/user : 사용자 정보 조회 (JWT 필요)
 * - PUT /api/auth/profile : 프로필 업데이트 (JWT 필요)
 * - POST /api/auth/profile/image : 프로필 이미지 업로드 URL 발급 (JWT 필요)
 * - GET /api/auth : 테스트 엔드포인트
 */
public class AuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthService authService;
    private final UserService userService;
    private final S3Service s3Service;
    
    public AuthHandler() {
        // AWS 클라이언트 초기화
        CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.create();
        DynamoDbClient dynamoDbClient = DynamoDbClient.create();
        
        // 환경변수 로드
        String clientId = System.getenv("CLIENT_ID");
        String usersTable = System.getenv("USERS_TABLE");
        String profileImagesBucket = System.getenv("PROFILE_IMAGES_BUCKET");
        
        // 서비스 초기화
        CognitoService cognitoService = new CognitoService(cognitoClient, clientId);
        UserRepository userRepository = new UserRepository(dynamoDbClient, usersTable);
        
        this.authService = new AuthService(cognitoService, userRepository);
        this.userService = new UserService(userRepository);
        this.s3Service = new S3Service(profileImagesBucket);
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
            // POST /api/auth/refresh
            else if ("POST".equals(httpMethod) && path.endsWith("/refresh")) {
                return handleRefresh(input, context);
            }
            // GET /api/auth/user
            else if ("GET".equals(httpMethod) && path.endsWith("/user")) {
                return handleGetUser(input, context);
            }
            // PUT /api/auth/profile
            else if ("PUT".equals(httpMethod) && path.endsWith("/profile")) {
                return handleUpdateProfile(input, context);
            }
            // POST /api/auth/profile/image
            else if ("POST".equals(httpMethod) && path.endsWith("/profile/image")) {
                return handleGetUploadUrl(input, context);
            }
            // GET /api/auth
            else if ("GET".equals(httpMethod) && "/api/auth".equals(path)) {
                return handleGetUserInfo(input, context);
            }
            
            return createResponse(404, Map.of("error", "Not Found"));
            
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, Map.of("error", "Internal Server Error"));
        }
    }
    
    /**
     * 회원가입 처리
     */
    private APIGatewayProxyResponseEvent handleRegister(APIGatewayProxyRequestEvent input, Context context) {
        try {
            RegisterRequest request = objectMapper.readValue(input.getBody(), RegisterRequest.class);
            AuthResponse response = authService.register(request);
            return createResponse(200, response);
        } catch (AuthException e) {
            context.getLogger().log("Register error: " + e.getMessage());
            return createResponse(400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Register error: " + e.getMessage());
            return createResponse(500, Map.of("error", "회원가입 실패"));
        }
    }
    
    /**
     * 로그인 처리
     */
    private APIGatewayProxyResponseEvent handleLogin(APIGatewayProxyRequestEvent input, Context context) {
        try {
            LoginRequest request = objectMapper.readValue(input.getBody(), LoginRequest.class);
            AuthResponse response = authService.login(request);
            return createResponse(200, response);
        } catch (AuthException e) {
            context.getLogger().log("Login error: " + e.getMessage());
            return createResponse(401, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Login error: " + e.getMessage());
            return createResponse(500, Map.of("error", "로그인 실패"));
        }
    }
    
    /**
     * 이메일 인증 확인 처리
     */
    private APIGatewayProxyResponseEvent handleConfirm(APIGatewayProxyRequestEvent input, Context context) {
        try {
            ConfirmRequest request = objectMapper.readValue(input.getBody(), ConfirmRequest.class);
            AuthResponse response = authService.confirm(request);
            return createResponse(200, response);
        } catch (AuthException e) {
            context.getLogger().log("Confirm error: " + e.getMessage());
            return createResponse(400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Confirm error: " + e.getMessage());
            return createResponse(500, Map.of("error", "인증 실패"));
        }
    }
    
    /**
     * 토큰 갱신 처리
     */
    private APIGatewayProxyResponseEvent handleRefresh(APIGatewayProxyRequestEvent input, Context context) {
        try {
            RefreshRequest request = objectMapper.readValue(input.getBody(), RefreshRequest.class);
            AuthResponse response = authService.refreshToken(request.getRefreshToken());
            return createResponse(200, response);
        } catch (AuthException e) {
            context.getLogger().log("Refresh error: " + e.getMessage());
            return createResponse(401, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Refresh error: " + e.getMessage());
            return createResponse(500, Map.of("error", "토큰 갱신 실패"));
        }
    }
    
    /**
     * 사용자 정보 조회 처리
     */
    private APIGatewayProxyResponseEvent handleGetUser(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, String> headers = input.getHeaders();
            String authHeader = headers != null ? headers.get("Authorization") : null;
            
            String email = JwtService.extractEmailFromAuthHeader(authHeader);
            UserResponse response = userService.getUserByEmail(email);
            
            return createResponse(200, response);
        } catch (AuthException e) {
            context.getLogger().log("GetUser error: " + e.getMessage());
            return createResponse(401, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("GetUser error: " + e.getMessage());
            return createResponse(500, Map.of("error", "사용자 정보 조회 실패"));
        }
    }
    
    /**
     * 테스트 엔드포인트
     */
    private APIGatewayProxyResponseEvent handleGetUserInfo(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "AuthHandler is ready");
        return createResponse(200, response);
    }
    
    /**
     * 프로필 업데이트 처리
     */
    private APIGatewayProxyResponseEvent handleUpdateProfile(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, String> headers = input.getHeaders();
            String authHeader = headers != null ? headers.get("Authorization") : null;
            
            String email = JwtService.extractEmailFromAuthHeader(authHeader);
            UpdateProfileRequest request = objectMapper.readValue(input.getBody(), UpdateProfileRequest.class);
            
            UserResponse response = userService.updateProfile(email, request);
            return createResponse(200, response);
        } catch (AuthException e) {
            context.getLogger().log("UpdateProfile error: " + e.getMessage());
            return createResponse(401, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("UpdateProfile error: " + e.getMessage());
            return createResponse(500, Map.of("error", "프로필 업데이트 실패"));
        }
    }
    
    /**
     * 프로필 이미지 업로드 URL 발급
     */
    private APIGatewayProxyResponseEvent handleGetUploadUrl(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, String> headers = input.getHeaders();
            String authHeader = headers != null ? headers.get("Authorization") : null;
            
            String email = JwtService.extractEmailFromAuthHeader(authHeader);
            
            // 요청 바디에서 contentType 추출
            String contentType = "image/jpeg"; // 기본값
            String body = input.getBody();
            if (body != null && !body.isEmpty()) {
                try {
                    Map<String, String> requestBody = objectMapper.readValue(body, Map.class);
                    if (requestBody.containsKey("contentType")) {
                        contentType = requestBody.get("contentType");
                    }
                } catch (Exception e) {
                    context.getLogger().log("Body parsing warning: " + e.getMessage());
                }
            }
            
            // Presigned URL 생성 (contentType 전달)
            Map<String, String> urlInfo = s3Service.generateUploadUrl(email, contentType);
            
            return createResponse(200, urlInfo);
        } catch (AuthException e) {
            context.getLogger().log("GetUploadUrl error: " + e.getMessage());
            return createResponse(401, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("GetUploadUrl error: " + e.getMessage());
            return createResponse(500, Map.of("error", "업로드 URL 생성 실패"));
        }
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
