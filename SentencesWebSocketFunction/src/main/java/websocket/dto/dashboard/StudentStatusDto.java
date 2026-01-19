package websocket.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * 개별 학생의 실시간 상태
 */
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StudentStatusDto {
    private String email;
    private String name;
    private String room;          // "sentence" | "ai_chat" | null
    private String status;             //active | inactive
    private Integer speakingRatio;
    private Integer duration;
    private String currentSentence;    // 문장 연습 중일 때
    private String currentTopic;       // AI 대화 중일 때
    private Boolean warning;           // 발음 비율 낮음
    private Boolean alert;             // 개입 필요
    private String lastActive;
}
