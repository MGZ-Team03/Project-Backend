package websocket.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import websocket.dto.EmailRequest;
import websocket.dto.StatusRequest;
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

    public APIGatewayV2WebSocketResponse handleConnect(APIGatewayV2WebSocketEvent event) {
        getLogger().log("------------connect handler-------------");

        String connectionId = event.getRequestContext().getConnectionId();
        EmailRequest request = gson.fromJson(event.getBody(), EmailRequest.class);
        getLogger().log("handleConnect.reqeust: " + request);
        socketRepository.saveConnection(event,request.getTutorEmail(), request.getStudentEmail());

        return createResponse(200, "ok");
    }

    /**
     * Disconnect 시 호출: status를 "inactive"로 업데이트
     */
    public APIGatewayV2WebSocketResponse handleDisconnect(String connectionId) {
        getLogger().log("=== Service: Handle Disconnect === ConnectionID: " + connectionId);


        boolean success = socketRepository.handleDisConnect(connectionId);

        if (success) {
            getLogger().log("handleDisconnect.reqeust: disconnected.success");
            return createResponse(200, "disconnected");
        } else {
            getLogger().log("student not found.handledisconnect");
            return createResponse(404, "student not found");
        }
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

        String currentStatus = socketRepository.getStatus(
                request.getTutorEmail(),
                request.getStudentEmail()
        );

        getLogger().log("Current DB Status: " + currentStatus);
        getLogger().log("Requested Status: " + request.getStatus());

        boolean exists = socketRepository.existsByConnectionId(connectionId);

        if (!exists) {
            socketRepository.saveConnection(event,request.getTutorEmail(), request.getStudentEmail());
        }

        // 아이템이 존재하지 않는 경우 - 새로 생성
        if (currentStatus == null) {
            getLogger().log("✨ Creating new tutor-student relationship");
            socketRepository.saveTutorStudent(request,event);
            getLogger().log("Status created: " + request.getStatus());
        }
        // 아이템은 존재하지만 상태가 다른 경우 - 업데이트
        else if (!request.getStatus().equals(currentStatus)) {
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
