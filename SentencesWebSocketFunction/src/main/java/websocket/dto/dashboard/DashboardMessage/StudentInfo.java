package websocket.dto.dashboard.DashboardMessage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentInfo {
    private String email;
    private String tutorEmail;
    private String name;
    private String status;
    private Integer speakingRatio;
    private Integer duration;
    private Boolean warning;
    private Boolean alert;
    private String lastActive;
}
