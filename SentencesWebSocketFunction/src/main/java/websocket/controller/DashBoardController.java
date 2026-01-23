package websocket.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import websocket.dto.dashboard.DashboardMessage.DashboardMessage;
import websocket.repository.SocketRepository;

import java.net.URI;

@RequiredArgsConstructor
public class DashBoardController {
    private final SocketRepository socketRepository;
    private final String wsEndpoint;
    private final Gson gson;

    public Void handleSQSMessages(SQSEvent event, Context context) {
        context.getLogger().log(
                "=== DashBoardController.handleSQSMessage | 대시보드 업데이트 브로드캐스트 ==="
        );

        try {
            // 1. WebSocket 클라이언트 생성
            context.getLogger().log(
                    "[1단계] WebSocket 클라이언트 생성 | 엔드포인트: " + wsEndpoint
            );

            ApiGatewayManagementApiClient wsClient = ApiGatewayManagementApiClient.builder()
                    .endpointOverride(URI.create(wsEndpoint))
                    .build();


            // 3. SQS 메시지 처리
            for (SQSEvent.SQSMessage sqsMessage : event.getRecords()) {
                String messageBody = sqsMessage.getBody();
                context.getLogger().log(
                        "📩 브로드캐스트 데이터 크기: " + messageBody.length() + " bytes | SQS 메시지: " + messageBody
                );


                DashboardMessage msg = gson.fromJson(messageBody, DashboardMessage.class);
                context.getLogger().log("DashboardMessage : "+ gson.toJson(msg));

                // tutorEmail 추출 (첫 번째 학생의 tutorEmail)
                if (msg.getStudents() == null || msg.getStudents().isEmpty()) {
                    context.getLogger().log("⚠️ 학생 정보 없음");
                    continue;
                }
                String tutorEmail = msg.getStudents().getFirst().getTutorEmail();
                context.getLogger().log("🎯 타겟 튜터: " + tutorEmail);

                // ✅ String으로 받기
                String connectionId = socketRepository.getTutorConnectionIds(tutorEmail);

                if (connectionId == null) {
                    context.getLogger().log("⚠️ 튜터 연결 없음");
                    continue;
                }

                context.getLogger().log("✅ ConnectionId: " + connectionId);

                // 전송
                try {
                    PostToConnectionRequest request = PostToConnectionRequest.builder()
                            .connectionId(connectionId)
                            .data(SdkBytes.fromUtf8String(messageBody))
                            .build();

                    wsClient.postToConnection(request);
                    context.getLogger().log("✅ 전송 성공!");

                } catch (GoneException e) {
                    context.getLogger().log("handelSQSMessage: ⚠️ 연결 종료됨");
                } catch (Exception e) {
                    context.getLogger().log("❌ 전송 실패: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            context.getLogger().log(
                    "❌ 치명적 에러 발생 | 에러 타입: " + e.getClass().getName()
                            + " | 에러 메시지: " + e.getMessage()
                            + " | 스택 트레이스 아래 확인"
            );
            throw new RuntimeException("WebSocket 브로드캐스트 실패", e);
        }

        return null;
    }
}
