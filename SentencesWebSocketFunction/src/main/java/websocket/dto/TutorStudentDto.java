package websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
