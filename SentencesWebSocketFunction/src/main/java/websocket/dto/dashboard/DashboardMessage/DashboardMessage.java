package websocket.dto.dashboard.DashboardMessage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMessage {
    private String type;
    private Long timestamp;
    private List<StudentInfo> students;
    private Summary summary;
}
