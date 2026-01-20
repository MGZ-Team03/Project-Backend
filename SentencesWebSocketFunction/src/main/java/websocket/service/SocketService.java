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

import java.util.Map;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;
import static websocket.controller.SocketController.createResponse;


@RequiredArgsConstructor
public class SocketService {
    private final SocketRepository socketRepository;
    private final Gson gson = new Gson();


    public APIGatewayV2WebSocketResponse handleConnect(APIGatewayV2WebSocketEvent event) {
        getLogger().log("------------connect handler-------------");

        String connectionId = event.getRequestContext().getConnectionId();
        EmailRequest request = gson.fromJson(event.getBody(), EmailRequest.class);
        getLogger().log("handleConnect.reqeust: " + request);
        socketRepository.saveConnection(event,request.getTutorEmail(), request.getStudentEmail());

        return createResponse(200, "ok");
    }

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


    public APIGatewayV2WebSocketResponse handleStatus(APIGatewayV2WebSocketEvent event, WebSocketRequest<StatusRequest> req) {
        String body = event.getBody();
        getLogger().log("=== Service: Handle Status ===");
        String connectionId = event.getRequestContext().getConnectionId();

        StatusRequest request = req.getData();
        getLogger().log("Request: " + request);

        Map<String,String> currentStatusAndRoom = socketRepository.getStatusAndRoom(
                request.getTutorEmail(),
                request.getStudentEmail()
        );
        String currentStatus =currentStatusAndRoom != null ? currentStatusAndRoom.get("status") : null;
        String currentRoom = currentStatusAndRoom != null ? currentStatusAndRoom.get("room") : null;

        getLogger().log("currentRoom: " + currentRoom);
        getLogger().log("Current DB Status: " + currentStatus);
        getLogger().log("Requested Status: " + request.getStatus());

        boolean exists = socketRepository.existsByConnectionId(connectionId);

        if (!exists) {
            socketRepository.saveConnection(event,request.getTutorEmail(), request.getStudentEmail());
        }

        // 아이템이 존재하지 않는 경우 - 새로 생성
        if (currentStatusAndRoom == null) {
            getLogger().log("✨ Creating new tutor-student relationship");
            socketRepository.saveTutorStudent(request,event);
            getLogger().log("Status created: " + request.getStatus());
        } else if ( !request.getRoom().equals(currentRoom) || !request.getStatus().equals(currentStatus)) {
            getLogger().log("updateStatus");
            socketRepository.updateStatus(
                    connectionId,
                    request.getRoom(),
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
