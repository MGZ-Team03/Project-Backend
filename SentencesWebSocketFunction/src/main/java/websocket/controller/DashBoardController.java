package websocket.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import websocket.repository.SocketRepository;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
public class DashBoardController {
    private final SocketRepository socketRepository;
    private final String wsEndpoint;
    private final Gson gson;

    public Void handleSQSMessages(SQSEvent event, Context context) {
        context.getLogger().log("=== DashBoardController.handleSQSMessage ===");

        context.getLogger().log("========================================");
        context.getLogger().log("  대시보드 업데이트 브로드캐스트");
        context.getLogger().log("========================================");

        try {
            // 1. WebSocket 클라이언트 생성
            context.getLogger().log("[1단계] WebSocket 클라이언트 생성");
            context.getLogger().log("   엔드포인트: " + wsEndpoint);

            ApiGatewayManagementApiClient wsClient = ApiGatewayManagementApiClient.builder()
                    .endpointOverride(URI.create(wsEndpoint))
                    .build();

            // 2. 모든 활성 연결 조회
            context.getLogger().log("\n[2단계] 활성 WebSocket 연결 조회");
            List<String> connectionIds = socketRepository.getAllActiveConnections();
            context.getLogger().log("   📊 활성 연결 수: " + connectionIds.size());

            if (connectionIds.isEmpty()) {
                context.getLogger().log("   ⚠️ 활성 연결이 없습니다. 종료합니다.");
                return null;
            }

            context.getLogger().log("   연결 ID 목록:");
            for (int i = 0; i < connectionIds.size(); i++) {
                context.getLogger().log("      [" + (i + 1) + "] " + connectionIds.get(i));
            }

            // 3. SQS 메시지 처리
            for (SQSEvent.SQSMessage sqsMessage : event.getRecords()) {
                String messageBody = sqsMessage.getBody();
                context.getLogger().log("\n📩 브로드캐스트 데이터 크기: " + messageBody.length() + " bytes");

                int successCount = 0;
                int failCount = 0;

                // 모든 튜터에게 전송
                for (String connectionId : connectionIds) {
                    try {
                        PostToConnectionRequest request = PostToConnectionRequest.builder()
                                .connectionId(connectionId)
                                .data(SdkBytes.fromUtf8String(messageBody))
                                .build();

                        wsClient.postToConnection(request);
                        successCount++;

                    } catch (GoneException e) {
                        context.getLogger().log("   ⚠️ 연결 종료됨: " + connectionId);
                        // TODO: tutor_students 테이블에서 connectionId 업데이트 필요
                        failCount++;
                    } catch (Exception e) {
                        context.getLogger().log("   ❌ 전송 실패 [" + connectionId + "]: " + e.getMessage());
                        failCount++;
                    }
                }

                context.getLogger().log("\n📈 전송 결과:");
                context.getLogger().log("   ✅ 성공: " + successCount);
                context.getLogger().log("   ❌ 실패: " + failCount);
            }

        } catch (Exception e) {
            context.getLogger().log("\n========================================");
            context.getLogger().log("  ❌ 치명적 에러 발생!");
            context.getLogger().log("========================================");
            context.getLogger().log("에러 타입: " + e.getClass().getName());
            context.getLogger().log("에러 메시지: " + e.getMessage());
            context.getLogger().log("\n스택 트레이스:");
            throw new RuntimeException("WebSocket 브로드캐스트 실패", e);
        }

        return null;
    }
}
