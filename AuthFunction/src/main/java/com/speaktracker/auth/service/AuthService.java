package com.speaktracker.auth.service;

import java.time.Instant;

import com.speaktracker.auth.dto.AuthResponse;
import com.speaktracker.auth.dto.ConfirmRequest;
import com.speaktracker.auth.dto.LoginRequest;
import com.speaktracker.auth.dto.RegisterRequest;
import com.speaktracker.auth.model.TokenInfo;
import com.speaktracker.auth.model.User;
import com.speaktracker.auth.repository.UserRepository;

/**
 * 인증 관련 비즈니스 로직 서비스
 */
public class AuthService {
    
    private final CognitoService cognitoService;
    private final UserRepository userRepository;
    
    public AuthService(CognitoService cognitoService, UserRepository userRepository) {
        this.cognitoService = cognitoService;
        this.userRepository = userRepository;
    }
    
    /**
     * 회원가입
     * @param request 회원가입 요청
     * @return 회원가입 응답
     */
    public AuthResponse register(RegisterRequest request) {
        // 1. Cognito 회원가입
        String userSub = cognitoService.signUp(request.getEmail(), request.getPassword(), request.getName());
        
        // 2. DynamoDB에 사용자 정보 저장
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setRole(request.getRole());
        user.setCreatedAt(Instant.now().toString());
        user.setUserSub(userSub);
        
        userRepository.save(user);
        
        // 3. 응답 생성
        AuthResponse response = new AuthResponse();
        response.setMessage("회원가입 성공");
        
        return response;
    }
    
    /**
     * 로그인
     * @param request 로그인 요청
     * @return 로그인 응답 (JWT 토큰 포함)
     */
    public AuthResponse login(LoginRequest request) {
        TokenInfo tokenInfo = cognitoService.login(request.getEmail(), request.getPassword());
        
        AuthResponse response = new AuthResponse();
        response.setMessage("로그인 성공");
        response.setIdToken(tokenInfo.getIdToken());
        response.setAccessToken(tokenInfo.getAccessToken());
        response.setRefreshToken(tokenInfo.getRefreshToken());
        response.setExpiresIn(tokenInfo.getExpiresIn());
        
        return response;
    }
    
    /**
     * 이메일 인증 확인
     * @param request 인증 확인 요청
     * @return 인증 확인 응답
     */
    public AuthResponse confirm(ConfirmRequest request) {
        cognitoService.confirm(request.getEmail(), request.getCode());
        
        AuthResponse response = new AuthResponse();
        response.setMessage("이메일 인증 완료");
        
        return response;
    }
}
