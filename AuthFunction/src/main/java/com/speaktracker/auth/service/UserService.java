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
        // profileImage: null이 아니면 업데이트 (빈 문자열은 null로 변환하여 이미지 제거)
        if (request.getProfileImage() != null) {
            // 빈 문자열이면 null로 설정 (이미지 제거)
            String newImage = request.getProfileImage().isEmpty() ? null : request.getProfileImage();
            user.setProfileImage(newImage);
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
