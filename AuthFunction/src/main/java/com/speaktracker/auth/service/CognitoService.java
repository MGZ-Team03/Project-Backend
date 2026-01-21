package com.speaktracker.auth.service;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import com.speaktracker.auth.exception.AuthenticationException;
import com.speaktracker.auth.exception.ValidationException;
import com.speaktracker.auth.model.TokenInfo;

/**
 * AWS Cognito와 통신하는 서비스
 */
public class CognitoService {
    
    private final CognitoIdentityProviderClient cognitoClient;
    private final String clientId;
    
    public CognitoService(CognitoIdentityProviderClient cognitoClient, String clientId) {
        this.cognitoClient = cognitoClient;
        this.clientId = clientId;
    }
    
    /**
     * Cognito 회원가입
     * @param email 이메일
     * @param password 비밀번호
     * @param name 이름
     * @return 사용자 ID (userSub)
     * @throws ValidationException 비밀번호 정책 위반
     * @throws AuthenticationException 이미 존재하는 이메일
     */
    public String signUp(String email, String password, String name) {
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
        
        try {
            SignUpResponse signUpResponse = cognitoClient.signUp(signUpRequest);
            return signUpResponse.userSub();
        } catch (UsernameExistsException e) {
            throw new AuthenticationException("이미 존재하는 이메일입니다.");
        } catch (InvalidPasswordException e) {
            throw new ValidationException("비밀번호 정책을 만족하지 않습니다.");
        }
    }
    
    /**
     * Cognito 로그인
     * @param email 이메일
     * @param password 비밀번호
     * @return JWT 토큰 정보
     * @throws AuthenticationException 인증 실패
     */
    public TokenInfo login(String email, String password) {
        Map<String, String> authParameters = new HashMap<>();
        authParameters.put("USERNAME", email);
        authParameters.put("PASSWORD", password);
        
        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
            .clientId(clientId)
            .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
            .authParameters(authParameters)
            .build();
        
        try {
            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);
            
            TokenInfo tokenInfo = new TokenInfo();
            tokenInfo.setIdToken(authResponse.authenticationResult().idToken());
            tokenInfo.setAccessToken(authResponse.authenticationResult().accessToken());
            tokenInfo.setRefreshToken(authResponse.authenticationResult().refreshToken());
            tokenInfo.setExpiresIn(authResponse.authenticationResult().expiresIn());
            
            return tokenInfo;
        } catch (NotAuthorizedException e) {
            throw new AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다.");
        } catch (UserNotConfirmedException e) {
            throw new AuthenticationException("이메일 인증이 필요합니다.");
        }
    }
    
    /**
     * 이메일 인증 확인
     * @param email 이메일
     * @param code 인증 코드
     * @throws AuthenticationException 인증 코드 오류
     */
    public void confirm(String email, String code) {
        ConfirmSignUpRequest confirmRequest = ConfirmSignUpRequest.builder()
            .clientId(clientId)
            .username(email)
            .confirmationCode(code)
            .build();
        
        try {
            cognitoClient.confirmSignUp(confirmRequest);
        } catch (CodeMismatchException e) {
            throw new AuthenticationException("인증 코드가 올바르지 않습니다.");
        } catch (ExpiredCodeException e) {
            throw new AuthenticationException("인증 코드가 만료되었습니다.");
        }
    }
}
