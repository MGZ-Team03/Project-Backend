package com.speaktracker.auth.service;

import com.speaktracker.auth.dto.UpdateProfileRequest;
import com.speaktracker.auth.dto.UserResponse;
import com.speaktracker.auth.exception.UserNotFoundException;
import com.speaktracker.auth.model.User;
import com.speaktracker.auth.repository.UserRepository;

/**
 * 사용자 정보 조회 및 프로필 관리 서비스
 */
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    /**
     * 이메일로 사용자 정보 조회
     * @param email 사용자 이메일
     * @return 사용자 정보
     * @throws UserNotFoundException 사용자를 찾을 수 없는 경우
     */
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email);
        
        if (user == null) {
            throw new UserNotFoundException("사용자를 찾을 수 없습니다.");
        }
        
        return toUserResponse(user);
    }
    
    /**
     * 프로필 업데이트
     * @param email 사용자 이메일
     * @param request 업데이트 요청
     * @return 업데이트된 사용자 정보
     */
    public UserResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email);
        
        if (user == null) {
            throw new UserNotFoundException("사용자를 찾을 수 없습니다.");
        }
        
        // 변경 가능한 필드만 업데이트
        if (request.getName() != null && !request.getName().isEmpty()) {
            user.setName(request.getName());
        }
        if (request.getProfileImage() != null && !request.getProfileImage().isEmpty()) {
            user.setProfileImage(request.getProfileImage());
        }
        
        userRepository.update(user);
        
        return toUserResponse(user);
    }
    
    /**
     * User를 UserResponse로 변환
     */
    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        response.setRole(user.getRole());
        response.setCreatedAt(user.getCreatedAt());
        response.setProfileImage(user.getProfileImage());
        response.setLearningLevel(user.getLearningLevel());
        return response;
    }
}
