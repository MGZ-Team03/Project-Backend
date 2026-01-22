package com.speaktracker.tutorRegister.services;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.speaktracker.tutorRegister.helpers.*;
import com.speaktracker.tutorRegister.models.*;
import com.speaktracker.tutorRegister.dto.*;
import static com.speaktracker.tutorRegister.exceptions.TutorRegisterExceptions.*;

/**
 * 튜터 등록 관련 비즈니스 로직
 */
public class TutorRegisterService {
    private final DynamoDBHelper dynamoDBHelper;
    private final WebSocketHelper webSocketHelper;
    private final SQSHelper sqsHelper;
    private final EmailHelper emailHelper;

    public TutorRegisterService() {
        this.dynamoDBHelper = new DynamoDBHelper();
        this.webSocketHelper = new WebSocketHelper();
        this.sqsHelper = new SQSHelper();
        this.emailHelper = new EmailHelper();
    }

    /**
     * DynamoDBHelper getter (role 조회를 위해 App.java에서 사용)
     */
    public DynamoDBHelper getDynamoDBHelper() {
        return dynamoDBHelper;
    }

    /**
     * 튜터 목록 조회
     */
    public TutorListResponseDto getTutors(String studentEmail) {
        List<User> tutors = dynamoDBHelper.getTutors();
        List<TutorInfoDto> result = new ArrayList<>();

        for (User tutor : tutors) {
            TutorInfoDto tutorInfo = new TutorInfoDto();
            tutorInfo.setEmail(tutor.getEmail());
            tutorInfo.setName(tutor.getName());
            tutorInfo.setBio(tutor.getBio());
            tutorInfo.setSpecialties(tutor.getSpecialties());
            tutorInfo.setProfileImage(tutor.getProfileImage());
            tutorInfo.setMaxStudents(tutor.getMaxStudents());
            tutorInfo.setIsAccepting(tutor.getIsAccepting());

            // 현재 학생 수 조회
            int currentStudents = dynamoDBHelper.getActiveTutorStudentCount(tutor.getEmail());
            tutorInfo.setCurrentStudents(currentStudents);

            // 학생의 요청 상태 확인
            String myRequestStatus = null;
            TutorRequest pendingRequest = dynamoDBHelper.getPendingRequestByStudent(studentEmail, tutor.getEmail());
            if (pendingRequest != null) {
                myRequestStatus = pendingRequest.getStatus();
            } else {
                // 이미 등록된 학생인지 확인
                TutorStudent relation = dynamoDBHelper.getTutorStudentRelation(tutor.getEmail(), studentEmail);
                if (relation != null && "active".equals(relation.getStatus())) {
                    myRequestStatus = "registered";
                } else {
                    // 거부된 이력 확인
                    TutorRequest rejectedRequest = dynamoDBHelper.getRejectedRequestByStudent(studentEmail, tutor.getEmail());
                    if (rejectedRequest != null) {
                        myRequestStatus = "rejected";
                    }
                }
            }
            tutorInfo.setMyRequestStatus(myRequestStatus);

            result.add(tutorInfo);
        }

        return new TutorListResponseDto(result);
    }

    /**
     * 튜터 등록 요청 생성
     */
    public RequestResponseDto createTutorRequest(String studentEmail, String tutorEmail, String message) {
        // 1. 유효성 검사
        validateTutorRequest(studentEmail, tutorEmail);

        // 2. 요청 생성
        String requestId = "req_" + UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long ttl = now + TimeUnit.DAYS.toMillis(90); // 90일 후 자동 삭제

        TutorRequest request = new TutorRequest();
        request.setRequestId(requestId);
        request.setStudentEmail(studentEmail);
        request.setTutorEmail(tutorEmail);
        request.setStatus("pending");
        request.setMessage(message);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setTtl(ttl);

        dynamoDBHelper.createTutorRequest(request);

        // 3. 병렬 알림 전송
        User tutor = dynamoDBHelper.getUserByEmail(tutorEmail);
        User student = dynamoDBHelper.getUserByEmail(studentEmail);

        // WebSocket 알림
        webSocketHelper.sendNewTutorRequestNotification(
                tutorEmail, requestId, studentEmail, student.getName(), message, now
        );

        // SQS 큐잉
        sqsHelper.queueNewTutorRequestNotification(
                tutorEmail, tutor.getName(), studentEmail, student.getName(), message, requestId
        );

        // 이메일 알림
        emailHelper.sendNewTutorRequestEmail(
                tutorEmail, tutor.getName(), student.getName(), message
        );

        // 4. 응답 생성
        RequestResponseDto response = new RequestResponseDto();
        response.setRequestId(requestId);
        response.setTutorEmail(tutorEmail);
        response.setStatus("pending");
        response.setCreatedAt(now);
        response.setMessage("등록 요청이 전송되었습니다.");

        return response;
    }

    /**
     * 학생의 요청 목록 조회
     */
    public StudentRequestListResponseDto getMyRequests(String studentEmail, String status) {
        List<TutorRequest> requests = dynamoDBHelper.getRequestsByStudent(studentEmail, status);
        List<StudentRequestDto> result = new ArrayList<>();

        for (TutorRequest request : requests) {
            User tutor = dynamoDBHelper.getUserByEmail(request.getTutorEmail());
            
            StudentRequestDto requestInfo = new StudentRequestDto();
            requestInfo.setRequestId(request.getRequestId());
            requestInfo.setTutorEmail(request.getTutorEmail());
            requestInfo.setTutorName(tutor.getName());
            requestInfo.setStatus(request.getStatus());
            requestInfo.setMessage(request.getMessage());
            requestInfo.setCreatedAt(request.getCreatedAt());
            requestInfo.setProcessedAt(request.getProcessedAt());
            requestInfo.setRejectionReason(request.getRejectionReason());

            result.add(requestInfo);
        }

        return new StudentRequestListResponseDto(result);
    }

    /**
     * 튜터의 요청 목록 조회
     */
    public TutorRequestListResponseDto getTutorRequests(String tutorEmail, String status) {
        List<TutorRequest> requests = dynamoDBHelper.getRequestsByTutor(tutorEmail, status);
        List<TutorRequestDto> result = new ArrayList<>();

        int totalPending = 0;
        long now = System.currentTimeMillis();

        for (TutorRequest request : requests) {
            User student = dynamoDBHelper.getUserByEmail(request.getStudentEmail());
            
            TutorRequestDto requestInfo = new TutorRequestDto();
            requestInfo.setRequestId(request.getRequestId());
            requestInfo.setStudentEmail(request.getStudentEmail());
            requestInfo.setStudentName(student.getName());
            requestInfo.setStatus(request.getStatus());
            requestInfo.setMessage(request.getMessage());
            requestInfo.setCreatedAt(request.getCreatedAt());

            // 대기 일수 계산
            if ("pending".equals(request.getStatus())) {
                long daysWaiting = TimeUnit.MILLISECONDS.toDays(now - request.getCreatedAt());
                requestInfo.setDaysWaiting(daysWaiting);
                totalPending++;
            }

            result.add(requestInfo);
        }

        return new TutorRequestListResponseDto(result, totalPending);
    }

    /**
     * 요청 승인
     */
    public RequestResponseDto approveRequest(String tutorEmail, String requestId) {
        // 1. 요청 조회
        TutorRequest request = dynamoDBHelper.getTutorRequestByRequestId(requestId);
        if (request == null) {
            throw new RequestNotFoundException("요청을 찾을 수 없습니다.");
        }

        // 2. 권한 확인
        if (!request.getTutorEmail().equals(tutorEmail)) {
            throw new UnauthorizedException("해당 요청에 대한 권한이 없습니다.");
        }

        // 3. 이미 처리된 요청인지 확인
        if (!"pending".equals(request.getStatus())) {
            throw new RequestAlreadyProcessedException("이미 처리된 요청입니다.");
        }

        // 4. 정원 확인
        User tutor = dynamoDBHelper.getUserByEmail(tutorEmail);
        int currentStudents = dynamoDBHelper.getActiveTutorStudentCount(tutorEmail);
        if (currentStudents >= tutor.getMaxStudents()) {
            throw new CapacityExceededException("튜터의 정원이 가득 찼습니다.");
        }

        // 5. 트랜잭션으로 관계 생성 + 요청 상태 업데이트
        long now = System.currentTimeMillis();
        String assignedAt = java.time.Instant.ofEpochMilli(now).toString(); // ISO 8601 형식
        
        TutorStudent relation = new TutorStudent();
        relation.setTutorEmail(tutorEmail);
        relation.setStudentEmail(request.getStudentEmail());
        relation.setAssignedAt(assignedAt);
        relation.setStatus("active");
        relation.setRequestId(requestId);

        dynamoDBHelper.createTutorStudentRelation(relation, requestId, request.getCreatedAt(), "approved");

        // 6. 병렬 알림 전송
        User student = dynamoDBHelper.getUserByEmail(request.getStudentEmail());

        // WebSocket 알림
        webSocketHelper.sendRequestApprovedNotification(
                request.getStudentEmail(), requestId, tutorEmail, tutor.getName(), now
        );

        // 알림 DB 저장 (동기적)
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("request_id", requestId);
        notificationData.put("tutor_name", tutor.getName());
        dynamoDBHelper.saveNotification(
                request.getStudentEmail(),
                "TUTOR_REQUEST_APPROVED",
                "튜터 등록 요청이 승인되었습니다",
                tutor.getName() + "님이 등록 요청을 승인했습니다!",
                notificationData,
                Arrays.asList("websocket", "email")
        );

        // 이메일 알림
        emailHelper.sendRequestApprovedEmail(
                request.getStudentEmail(), student.getName(), tutor.getName()
        );

        // 7. 응답 생성
        RequestResponseDto response = new RequestResponseDto();
        response.setRequestId(requestId);
        response.setStatus("approved");
        response.setStudentEmail(request.getStudentEmail());
        response.setProcessedAt(now);
        response.setMessage("학생이 등록되었습니다.");

        return response;
    }

    /**
     * 요청 거부
     */
    public RequestResponseDto rejectRequest(String tutorEmail, String requestId, String reason) {
        // 1. 요청 조회
        TutorRequest request = dynamoDBHelper.getTutorRequestByRequestId(requestId);
        if (request == null) {
            throw new RequestNotFoundException("요청을 찾을 수 없습니다.");
        }

        // 2. 권한 확인
        if (!request.getTutorEmail().equals(tutorEmail)) {
            throw new UnauthorizedException("해당 요청에 대한 권한이 없습니다.");
        }

        // 3. 이미 처리된 요청인지 확인
        if (!"pending".equals(request.getStatus())) {
            throw new RequestAlreadyProcessedException("이미 처리된 요청입니다.");
        }

        // 4. 요청 상태 업데이트
        long now = System.currentTimeMillis();
        dynamoDBHelper.updateTutorRequestStatus(requestId, request.getCreatedAt(), "rejected", now, reason);

        // 5. 병렬 알림 전송
        User tutor = dynamoDBHelper.getUserByEmail(tutorEmail);
        User student = dynamoDBHelper.getUserByEmail(request.getStudentEmail());

        // WebSocket 알림
        webSocketHelper.sendRequestRejectedNotification(
                request.getStudentEmail(), requestId, tutorEmail, tutor.getName(), reason, now
        );

        // 알림 DB 저장 (동기적)
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("request_id", requestId);
        notificationData.put("tutor_name", tutor.getName());
        notificationData.put("rejection_reason", reason);
        dynamoDBHelper.saveNotification(
                request.getStudentEmail(),
                "TUTOR_REQUEST_REJECTED",
                "튜터 등록 요청이 거부되었습니다",
                tutor.getName() + "님이 등록 요청을 거부했습니다.",
                notificationData,
                Arrays.asList("websocket", "email")
        );

        // 이메일 알림
        emailHelper.sendRequestRejectedEmail(
                request.getStudentEmail(), student.getName(), tutor.getName(), reason
        );

        // 6. 응답 생성
        RequestResponseDto response = new RequestResponseDto();
        response.setRequestId(requestId);
        response.setStatus("rejected");
        response.setStudentEmail(request.getStudentEmail());
        response.setProcessedAt(now);
        response.setRejectionReason(reason);
        response.setMessage("요청이 거부되었습니다.");

        return response;
    }

    /**
     * 요청 취소 (학생용)
     */
    public RequestResponseDto cancelRequest(String studentEmail, String requestId) {
        // 1. 요청 조회
        TutorRequest request = dynamoDBHelper.getTutorRequestByRequestId(requestId);
        if (request == null) {
            throw new RequestNotFoundException("요청을 찾을 수 없습니다.");
        }

        // 2. 권한 확인
        if (!request.getStudentEmail().equals(studentEmail)) {
            throw new UnauthorizedException("해당 요청에 대한 권한이 없습니다.");
        }

        // 3. pending 상태인지 확인
        if (!"pending".equals(request.getStatus())) {
            throw new CannotCancelException("대기 중인 요청만 취소할 수 있습니다.");
        }

        // 4. 요청 상태 업데이트
        long now = System.currentTimeMillis();
        dynamoDBHelper.updateTutorRequestStatus(requestId, request.getCreatedAt(), "cancelled", now, null);

        // 5. 응답 생성
        RequestResponseDto response = new RequestResponseDto();
        response.setRequestId(requestId);
        response.setStatus("cancelled");
        response.setMessage("요청이 취소되었습니다.");

        return response;
    }

    // ===== 유효성 검사 =====

    private void validateTutorRequest(String studentEmail, String tutorEmail) {
        // 1. 튜터 존재 확인
        User tutor = dynamoDBHelper.getUserByEmail(tutorEmail);
        if (tutor == null || !"tutor".equals(tutor.getRole())) {
            throw new TutorNotFoundException("튜터를 찾을 수 없습니다.");
        }

        // 2. 튜터가 학생을 받는지 확인
        if (tutor.getIsAccepting() != null && !tutor.getIsAccepting()) {
            throw new TutorNotAcceptingException("튜터가 현재 학생을 받지 않습니다.");
        }

        // 3. 중복 요청 확인
        TutorRequest existingRequest = dynamoDBHelper.getPendingRequestByStudent(studentEmail, tutorEmail);
        if (existingRequest != null) {
            throw new DuplicateRequestException("이미 해당 튜터에게 pending 요청이 있습니다.");
        }

        // 3-1. 거부된 이력 확인 (영구 차단)
        TutorRequest rejectedRequest = dynamoDBHelper.getRejectedRequestByStudent(studentEmail, tutorEmail);
        if (rejectedRequest != null) {
            throw new RequestPreviouslyRejectedException("해당 튜터에게 거부된 이력이 있어 요청할 수 없습니다.");
        }

        // 4. 이미 등록된 학생인지 확인
        TutorStudent relation = dynamoDBHelper.getTutorStudentRelation(tutorEmail, studentEmail);
        if (relation != null && "active".equals(relation.getStatus())) {
            throw new AlreadyRegisteredException("이미 해당 튜터의 학생으로 등록되어 있습니다.");
        }

        // 5. 정원 확인
        int currentStudents = dynamoDBHelper.getActiveTutorStudentCount(tutorEmail);
        if (currentStudents >= tutor.getMaxStudents()) {
            throw new CapacityExceededException("튜터의 정원이 가득 찼습니다.");
        }
    }

    // ===== 알림 관련 =====

    /**
     * 알림 목록 조회
     */
    public NotificationListResponseDto getNotifications(String userEmail, Boolean isReadFilter) {
        List<Notification> notifications = dynamoDBHelper.getNotificationsByUser(userEmail, isReadFilter);
        int unreadCount = dynamoDBHelper.getUnreadNotificationCount(userEmail);

        List<NotificationDto> result = new ArrayList<>();
        for (Notification notification : notifications) {
            // NEW_TUTOR_REQUEST 타입 알림의 경우, 요청 상태가 pending인 경우만 포함
            if ("NEW_TUTOR_REQUEST".equals(notification.getType())) {
                Map<String, Object> data = notification.getData();
                
                if (data != null && data.containsKey("request_id")) {
                    String requestId = (String) data.get("request_id");
                    
                    try {
                        TutorRequest request = dynamoDBHelper.getTutorRequestByRequestId(requestId);
                        
                        if (request != null) {
                            System.out.println("DEBUG: Request status: " + request.getStatus());
                        }
                        
                        // 요청이 없거나 pending 상태가 아니면 스킵
                        if (request == null || !"pending".equals(request.getStatus())) {
                            continue;
                        }
                    } catch (Exception e) {
                        // 요청 조회 실패 시 스킵
                        System.err.println("❌ Failed to check request status for notification: " + notification.getNotificationId());
                        e.printStackTrace();
                        continue;
                    }
                }
            }
            
            NotificationDto dto = new NotificationDto();
            dto.setNotificationId(notification.getNotificationId());
            dto.setNotificationIdTimestamp(notification.getNotificationIdTimestamp());
            dto.setType(notification.getType());
            dto.setTitle(notification.getTitle());
            dto.setMessage(notification.getMessage());
            dto.setData(notification.getData());
            dto.setIsRead(notification.getIsRead());
            dto.setSentVia(notification.getSentVia());
            dto.setCreatedAt(notification.getCreatedAt());
            result.add(dto);
        }

        return new NotificationListResponseDto(result, unreadCount);
    }

    /**
     * 알림 읽음 처리
     */
    public void markNotificationAsRead(String userEmail, String notificationIdTimestamp) {
        dynamoDBHelper.markNotificationAsRead(userEmail, notificationIdTimestamp);
    }
}
