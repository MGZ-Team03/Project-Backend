package websocket.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import websocket.dto.EmailRequest;
import websocket.dto.StatusRequest;
import websocket.dto.TutorStudentDto;
import websocket.dto.WebSocketRequest;
import websocket.repository.SocketRepository;
import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;
import static websocket.controller.SocketController.createResponse;

/**
 * Connect 시 호출: 존재 여부 확인 후 분기 처리
 * - 존재하면: status를 "idle"로 업데이트
 * - 존재하지 않으면: 새로 등록
 */
@RequiredArgsConstructor
public class SocketService {
    private final SocketRepository socketRepository;
    private final Gson gson = new Gson();

    /**
     * Connect 시 connectionId 저장
     */
    public void saveConnection(String connectionId, String userEmail) {
        getLogger().log("=== Service: Save Connection ===");
        socketRepository.saveConnection(connectionId, userEmail);
    }

    /**
     * Disconnect 시 호출: status를 "inactive"로 업데이트 + connectionId 삭제
     */
    /**
     * Disconnect 시 호출: status를 "inactive"로 업데이트 + connectionId 삭제
     */
    public APIGatewayV2WebSocketResponse handleDisconnect(APIGatewayV2WebSocketEvent event, String connectionId){
        getLogger().log("=== Service: Handle Disconnect ===");
        
        // connectionId 삭제
        socketRepository.deleteConnection(connectionId);
        
        // 학생 상태 업데이트 (body가 있는 경우만)
        if (event.getBody() != null && !event.getBody().isEmpty()) {
            EmailRequest request = gson.fromJson(event.getBody(), EmailRequest.class);
            boolean exists = socketRepository.existsTutorStudent(request.getTutorEmail(), request.getStudentEmail());
            if (exists) {
                getLogger().log("📌 Updating status to 'inactive'");
                socketRepository.updateStatus(request.getTutorEmail(), request.getStudentEmail(), "inactive");
            } else {
                getLogger().log("⚠️ Tutor-Student not found, skipping disconnect");
            }
        }

        return createResponse(200,"disconnected");
    }
    /**
     * status 업데이트
     */
    public void updateStatus(TutorStudentDto tutorStudentDto) {
        socketRepository.updateStatus(
                tutorStudentDto.getTutorEmail(),
                tutorStudentDto.getStudentEmail(),
                tutorStudentDto.getStatus());
    }

    public APIGatewayV2WebSocketResponse handleStatus(APIGatewayV2WebSocketEvent event, WebSocketRequest<StatusRequest> req) {
        String body = event.getBody();
        getLogger().log("=== Service: Handle Status ===");

        StatusRequest request = req.getData();
        getLogger().log("Request: " + request);

        String currentStatus = socketRepository.getStatus(
                request.getTutorEmail(),
                request.getStudentEmail()
        );

        getLogger().log("Current DB Status: " + currentStatus);
        getLogger().log("Requested Status: " + request.getStatus());

        // 아이템이 존재하지 않는 경우 - 무시 (튜터 등록 승인 후에만 관계가 생성되어야 함)
        if (currentStatus == null) {
            getLogger().log("⚠️ Tutor-Student relationship not found, ignoring status update");
            getLogger().log("Tutor: " + request.getTutorEmail() + ", Student: " + request.getStudentEmail());
            return createResponse(404, "Tutor-Student relationship not found");
        }
        // 아이템은 존재하지만 상태가 다른 경우 - 업데이트
        else if (!request.getStatus().equals(currentStatus)) {
            socketRepository.updateStatus(
                    request.getTutorEmail(),
                    request.getStudentEmail(),
                    request.getStatus()
            );
            getLogger().log("Status updated: " + currentStatus + " -> " + request.getStatus());
        }
        // 상태가 같은 경우 - 아무것도 안 함
        else {
            getLogger().log("Status unchanged: " + currentStatus);
        }

        return createResponse(200, request.getStatus());
    }
}
