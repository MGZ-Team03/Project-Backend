package com.speaktracker.auth.service;

import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speaktracker.auth.dto.StudentListResponse;
import com.speaktracker.auth.dto.UpdateProfileRequest;
import com.speaktracker.auth.dto.UserResponse;
import com.speaktracker.auth.exception.UserNotFoundException;
import com.speaktracker.auth.model.User;
import com.speaktracker.auth.repository.UserRepository;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

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
     * 학생 목록 조회 (튜터용)
     * @param level 러닝 레벨 필터 (상/중/하)
     * @param sortBy 정렬 기준 (email/level)
     * @param sortOrder 정렬 순서 (asc/desc)
     * @param limit 페이지 크기
     * @param nextToken 페이지네이션 토큰
     * @return 학생 목록 응답
     */
    public StudentListResponse getStudentsList(
            String level,
            String sortBy,
            String sortOrder,
            int limit,
            String nextToken) {

        // 페이지네이션 토큰 디코딩
        Map<String, AttributeValue> exclusiveStartKey = null;
        if (nextToken != null && !nextToken.isEmpty()) {
            exclusiveStartKey = decodeNextToken(nextToken);
        }

        // DynamoDB 스캔
        UserRepository.ScanResult scanResult = userRepository.scanStudents(limit, exclusiveStartKey);
        List<User> students = scanResult.getUsers();

        // 레벨 필터링 (메모리)
        if (level != null && !level.isEmpty()) {
            students = students.stream()
                    .filter(s -> level.equals(s.getLearningLevel()))
                    .collect(Collectors.toList());
        }

        // 정렬 (메모리)
        students = sortStudents(students, sortBy, sortOrder);

        // Response 생성
        StudentListResponse response = new StudentListResponse();
        response.setSuccess(true);
        response.setStudents(students.stream()
                .map(this::toStudentInfo)
                .collect(Collectors.toList()));

        // 페이지네이션 정보
        StudentListResponse.PaginationInfo pagination = new StudentListResponse.PaginationInfo();
        pagination.setCount(students.size());
        pagination.setHasMore(scanResult.getLastEvaluatedKey() != null);
        if (scanResult.getLastEvaluatedKey() != null) {
            pagination.setNextToken(encodeNextToken(scanResult.getLastEvaluatedKey()));
        }
        response.setPagination(pagination);

        return response;
    }

    /**
     * 내부용 사용자 조회 메서드 (예외 발생 안함)
     */
    public User getUserByEmailInternal(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * 학생 목록 정렬
     */
    private List<User> sortStudents(List<User> students, String sortBy, String sortOrder) {
        Comparator<User> comparator = null;

        switch (sortBy != null ? sortBy : "email") {
            case "level":
                comparator = Comparator.comparing(User::getLearningLevel, Comparator.nullsLast(String::compareTo));
                break;
            default: // "email"
                comparator = Comparator.comparing(User::getEmail, Comparator.nullsLast(String::compareTo));
        }

        if ("desc".equals(sortOrder)) {
            comparator = comparator.reversed();
        }

        return students.stream().sorted(comparator).collect(Collectors.toList());
    }

    /**
     * LastEvaluatedKey를 Base64로 인코딩
     */
    private String encodeNextToken(Map<String, AttributeValue> lastEvaluatedKey) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(lastEvaluatedKey);
            return Base64.getEncoder().encodeToString(json.getBytes());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Base64 토큰을 LastEvaluatedKey로 디코딩
     */
    private Map<String, AttributeValue> decodeNextToken(String token) {
        try {
            String json = new String(Base64.getDecoder().decode(token));
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<Map<String, AttributeValue>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * User를 StudentInfo DTO로 변환
     */
    private StudentListResponse.StudentInfo toStudentInfo(User user) {
        StudentListResponse.StudentInfo info = new StudentListResponse.StudentInfo();
        info.setEmail(user.getEmail());
        // learningLevel이 null이면 "-"로 표시
        info.setLearningLevel(user.getLearningLevel() != null ? user.getLearningLevel() : "-");
        return info;
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
