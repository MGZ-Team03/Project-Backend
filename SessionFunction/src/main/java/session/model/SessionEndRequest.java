package session.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionEndRequest {

    @JsonProperty("student_email")
    private String studentEmail;

    private String timestamp;  // 시작 시 받은 timestamp

    @JsonProperty("recording_duration")
    private Long recordingDuration;  // ms

    @JsonProperty("speaking_duration")
    private Long speakingDuration;   // ms

    // 문장 연습 전용
    @JsonProperty("practice_records")
    private List<PracticeRecord> practiceRecords;

    // AI 대화 전용
    @JsonProperty("response_latencies")
    private List<Long> responseLatencies;

    @JsonProperty("chat_turns_count")
    private Integer chatTurnsCount;

    public SessionEndRequest() {}

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Long getRecordingDuration() {
        return recordingDuration;
    }

    public void setRecordingDuration(Long recordingDuration) {
        this.recordingDuration = recordingDuration;
    }

    public Long getSpeakingDuration() {
        return speakingDuration;
    }

    public void setSpeakingDuration(Long speakingDuration) {
        this.speakingDuration = speakingDuration;
    }

    public List<PracticeRecord> getPracticeRecords() {
        return practiceRecords;
    }

    public void setPracticeRecords(List<PracticeRecord> practiceRecords) {
        this.practiceRecords = practiceRecords;
    }

    public List<Long> getResponseLatencies() {
        return responseLatencies;
    }

    public void setResponseLatencies(List<Long> responseLatencies) {
        this.responseLatencies = responseLatencies;
    }

    public Integer getChatTurnsCount() {
        return chatTurnsCount;
    }

    public void setChatTurnsCount(Integer chatTurnsCount) {
        this.chatTurnsCount = chatTurnsCount;
    }

    public void validate() {
        if (studentEmail == null || studentEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("student_email is required");
        }
        if (timestamp == null || timestamp.trim().isEmpty()) {
            throw new IllegalArgumentException("timestamp is required");
        }
        if (recordingDuration == null || recordingDuration < 0) {
            throw new IllegalArgumentException("recording_duration must be a positive number");
        }
        if (speakingDuration == null || speakingDuration < 0) {
            throw new IllegalArgumentException("speaking_duration must be a positive number");
        }
    }
}
