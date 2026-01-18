package websocket.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import websocket.dto.dashboard.DashboardDataResponse;
import websocket.repository.SocketRepository;
import websocket.service.SocketService;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class DashBoardController {
    private final SocketRepository socketRepository;
    private final String wsEndpoint;
    private final Gson gson;

    public Void handleSQSMessages(SQSEvent event, Context context) {
        context.getLogger().log("=== DashBoardController.handleSQSMessage ===");

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
            int messageCount = 0;
            for (SQSEvent.SQSMessage sqsMessage : event.getRecords()) {
                messageCount++;
                context.getLogger().log("\n========================================");
                context.getLogger().log("  메시지 " + messageCount + " 처리 중");
                context.getLogger().log("========================================");

                String messageBody = sqsMessage.getBody();
                context.getLogger().log("📩 수신 원본 JSON:");
                context.getLogger().log(messageBody);

                try {
                    // JSON 파싱
                    DashboardDataResponse data = gson.fromJson(messageBody, DashboardDataResponse.class);
                    context.getLogger().log("\n📊 파싱된 데이터:");
                    context.getLogger().log("   - 활성 사용자: " + data.getActiveUsers());
                    context.getLogger().log("   - 학습 시간: " + data.getSpeakingDuration() + "분");
                    context.getLogger().log("   - 학생 수: " + data.getOrderCount());

                    // WebSocket 메시지 구성
                    Map<String, Object> wsMessage = new HashMap<>();
                    wsMessage.put("type", "dashboard_update");
                    wsMessage.put("data", data);
                    wsMessage.put("timestamp", System.currentTimeMillis());

                    String finalMessage = gson.toJson(wsMessage);
                    context.getLogger().log("\n📤 전송할 WebSocket 메시지:");
                    context.getLogger().log(finalMessage);

                    // 4. 모든 연결에 브로드캐스트
                    context.getLogger().log("\n[3단계] 브로드캐스트 시작");
                    int successCount = 0;
                    int failCount = 0;

                    for (String connectionId : connectionIds) {
                        try {
                            context.getLogger().log("   → 전송 중: " + connectionId);
                            PostToConnectionRequest request = PostToConnectionRequest.builder()
                                    .connectionId(connectionId)
                                    .data(SdkBytes.fromUtf8String(finalMessage))
                                    .build();
                            wsClient.postToConnection(request);

                            context.getLogger().log("   ✅ 성공: " + connectionId);
                            successCount++;
                        } catch (GoneException e) {
                            context.getLogger().log("   ⚠️ 연결 종료됨: " + connectionId);
                            failCount++;
                        } catch (Exception e) {
                            context.getLogger().log("   ❌ 실패: " + connectionId);
                            context.getLogger().log("      에러: " + e.getMessage());
                            failCount++;
                        }
                    }

                    context.getLogger().log("\n========================================");
                    context.getLogger().log("  브로드캐스트 완료");
                    context.getLogger().log("========================================");
                    context.getLogger().log("📈 전송 결과:");
                    context.getLogger().log("   ✅ 성공: " + successCount);
                    context.getLogger().log("   ❌ 실패: " + failCount);
                    context.getLogger().log("   📊 총합: " + (successCount + failCount));

                } catch (Exception e) {
                    context.getLogger().log("❌ JSON 파싱 실패: " + e.getMessage());
                    continue;
                }
            }

        } catch (Exception e) {
            context.getLogger().log("\n========================================");
            context.getLogger().log("  ❌ 치명적 에러 발생!");
            context.getLogger().log("========================================");
            context.getLogger().log("에러 타입: " + e.getClass().getName());
            context.getLogger().log("에러 메시지: " + e.getMessage());
            context.getLogger().log("\n스택 트레이스:");
            e.printStackTrace();
            throw new RuntimeException("WebSocket 브로드캐스트 실패", e);
        }

        return null;
    }
}
