package dashboard.dto;

import lombok.*;
import org.joda.time.DateTime;

@Builder
@Data
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
