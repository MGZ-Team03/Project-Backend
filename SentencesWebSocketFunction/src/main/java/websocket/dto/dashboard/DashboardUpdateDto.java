package websocket.dto.dashboard;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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