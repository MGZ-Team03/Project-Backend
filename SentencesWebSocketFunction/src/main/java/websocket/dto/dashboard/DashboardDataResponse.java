package websocket.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
// 테스트 목업
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDataResponse {
    private int activeUsers;   // 활성 세션 수
    private int speakingDuration;          // 오늘 학습 시간 (분)
    private int orderCount;     // 전체 학생 수
    private int speakingRation;
    private int totalDuration;
    private String region;
}
