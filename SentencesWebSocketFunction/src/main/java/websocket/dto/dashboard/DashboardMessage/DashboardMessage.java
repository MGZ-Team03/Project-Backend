package websocket.dto.dashboard.DashboardMessage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMessage {
    private String type;
    private Long timestamp;
    private List<StudentInfo> students;
    private Summary summary;
}
