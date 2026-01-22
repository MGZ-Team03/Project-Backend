package websocket.dto.dashboard.DashboardMessage;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Summary {
    private Integer active;
    private Integer warning;
    private Integer total;
    private Integer speaking;
}