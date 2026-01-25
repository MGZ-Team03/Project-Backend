package dashboard.service;

import dashboard.dto.StatusRequest;
import dashboard.dto.dashboard.DashboardUpdateDto;
import dashboard.repository.DashboardRepository;
import dashboard.utils.StudentStatusCollector;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class DashboardService {
    private final DashboardRepository studentRepository;
    private final StudentStatusCollector collector;

    /**
     * 학생 상태 업데이트
     */
    public Map<String, Object> updateStatus(StatusRequest request) {
        getLogger().log("=== Service: Update Status ===");
        getLogger().log("Request: " + request);

        validateStatusRequest(request);

        // 현재 상태 조회
        Map<String, String> currentStatusAndRoom = studentRepository.getStatusAndRoom(
                request.getTutorEmail(),
                request.getStudentEmail()
        );

        String currentStatus = currentStatusAndRoom != null ? currentStatusAndRoom.get("status") : null;
        String currentRoom = currentStatusAndRoom != null ? currentStatusAndRoom.get("room") : null;

        getLogger().log("Current Status: " + currentStatus);
        getLogger().log("Current Room: " + currentRoom);
        getLogger().log("Requested Status: " + request.getStatus());
        getLogger().log("Requested Room: " + request.getRoom());

        Map<String, Object> result = new HashMap<>();

        // 신규 생성
        if (currentStatusAndRoom == null) {
            getLogger().log("✨ Creating new tutor-student relationship");
            studentRepository.saveTutorStudent(request);
            result.put("action", "created");
            result.put("status", request.getStatus());
        }
        // 업데이트
        else if (!request.getRoom().equals(currentRoom) || !request.getStatus().equals(currentStatus)) {
            getLogger().log("🔄 Updating status");
            studentRepository.updateStatus(
                    request.getTutorEmail(),
                    request.getStudentEmail(),
                    request.getStatus(),
                    request.getRoom()
            );
            result.put("action", "updated");
            result.put("previousStatus", currentStatus);
            result.put("newStatus", request.getStatus());
        }
        // 변경 없음
        else {
            getLogger().log("✅ Status unchanged");
            result.put("action", "unchanged");
            result.put("status", currentStatus);
        }

        result.put("tutorEmail", request.getTutorEmail());
        result.put("studentEmail", request.getStudentEmail());

        return result;
    }

    /**
     * 학생 상태 조회
     */
    public Map<String, String> getStatus(String tutorEmail, String studentEmail) {
        getLogger().log("=== Service: Get Status ===");
        getLogger().log("Tutor: " + tutorEmail + ", Student: " + studentEmail);

        return studentRepository.getStatusAndRoom(tutorEmail, studentEmail);
    }

    /**
     * 대시보드 데이터 조회
     */
    public DashboardUpdateDto getDashboard(String tutorEmail) {
        getLogger().log("=== Service: Get Dashboard ===");
        getLogger().log("Tutor Email: " + tutorEmail);

        try {
            DashboardUpdateDto dashboardData = collector.collectByTutor(tutorEmail);
            getLogger().log("✅ Dashboard data collected: " + dashboardData.getStudents().size() + " students");
            return dashboardData;
        } catch (Exception e) {
            getLogger().log("❌ Dashboard collection failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to collect dashboard data", e);
        }
    }

    /**
     * 전체 학생 목록 조회
     */
    public DashboardUpdateDto getAllStudents(String tutorEmail) {
        getLogger().log("=== Service: Get All Students ===");
        getLogger().log("Tutor Email: " + tutorEmail);

        return collector.collectAllStudents(tutorEmail);
    }

    /**
     * 요청 검증
     */
    private void validateStatusRequest(StatusRequest request) {
        if (request.getTutorEmail() == null || request.getTutorEmail().isEmpty()) {
            throw new IllegalArgumentException("tutorEmail is required");
        }

        if (request.getStudentEmail() == null || request.getStudentEmail().isEmpty()) {
            throw new IllegalArgumentException("studentEmail is required");
        }

        if (request.getStatus() == null || request.getStatus().isEmpty()) {
            throw new IllegalArgumentException("status is required");
        }

        // 유효하지 않은 tutorEmail 체크
        if ("undefined".equalsIgnoreCase(request.getTutorEmail())
                || "unknown@example.com".equalsIgnoreCase(request.getTutorEmail())) {
            String errorMsg = "❌ CRITICAL: 유효하지 않은 tutorEmail = " + request.getTutorEmail();
            getLogger().log(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
    }
}
