package websocket.dto.dashboard;

import lombok.*;


/**
 * 개별 학생의 실시간 상태
 */
/**
 * 개별 학생의 실시간 상태
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentStatusDto {
    private String email;
    private String tutorEmail;
    private String name;
    private String room;          // "sentence" | "ai" | null
    private String status;             // "speaking" | "listening" | "inactive"
    private Integer speakingRatio;
    private Integer duration;
    private String currentSentence;    // 문장 연습 중일 때
    private String currentTopic;       // AI 대화 중일 때
    private Boolean warning;           // 발음 비율 낮음
    private Boolean alert;             // 개입 필요
    private String lastActive;         // 마지막 활동 시간
}