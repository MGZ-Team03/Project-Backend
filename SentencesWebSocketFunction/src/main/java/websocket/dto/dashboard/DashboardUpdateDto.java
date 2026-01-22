package websocket.dto.dashboard;


import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * 대시보드 전체 업데이트 데이터
 */
@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardUpdateDto {
    private String type;                           // "dashboard_update"
    private Long timestamp;
    private List<StudentStatusDto> students;       // 학생 리스트
    private Map<String, Integer> summary;          // 통계 요약
}