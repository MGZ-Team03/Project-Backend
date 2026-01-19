package com.speaktracker.auth.service;

import com.speaktracker.auth.dto.UserResponse;
import com.speaktracker.auth.exception.UserNotFoundException;
import com.speaktracker.auth.model.User;
import com.speaktracker.auth.repository.UserRepository;

/**
 * 사용자 정보 조회 서비스
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
        
        UserResponse response = new UserResponse();
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        response.setRole(user.getRole());
        response.setCreatedAt(user.getCreatedAt());
        
        return response;
    }
}
