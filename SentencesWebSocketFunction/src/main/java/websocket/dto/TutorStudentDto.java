package websocket.dto;

import lombok.*;

@Data
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TutorStudentDto {
    private String tutorEmail;
    private String studentEmail;
    private String status;
    private String ratio;
}
