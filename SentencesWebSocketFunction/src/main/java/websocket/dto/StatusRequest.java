package websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.joda.time.DateTime;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StatusRequest {
    private String tutorEmail;
    private String studentEmail;
    private String status;
    private String room;
    private DateTime assigned_at;
}
