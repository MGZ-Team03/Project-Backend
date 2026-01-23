package session.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionStartRequest {

    @JsonProperty("student_email")
    private String studentEmail;

    @JsonProperty("session_type")
    private String sessionType;  // "sentence" | "ai_chat"

    @JsonProperty("tutor_email")
    private String tutorEmail;   // optional

    public SessionStartRequest() {}

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getTutorEmail() {
        return tutorEmail;
    }

    public void setTutorEmail(String tutorEmail) {
        this.tutorEmail = tutorEmail;
    }

    public void validate() {
        if (studentEmail == null || studentEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("student_email is required");
        }
        if (sessionType == null || sessionType.trim().isEmpty()) {
            throw new IllegalArgumentException("session_type is required");
        }
        if (!sessionType.equals("sentence") && !sessionType.equals("ai_chat")) {
            throw new IllegalArgumentException("session_type must be 'sentence' or 'ai_chat'");
        }
    }
}
