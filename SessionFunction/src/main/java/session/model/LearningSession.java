package session.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LearningSession {

    @JsonProperty("student_email")
    private String studentEmail;

    private String timestamp;

    @JsonProperty("session_type")
    private String sessionType;  // "sentence" | "ai_chat"

    @JsonProperty("recording_duration")
    private Long recordingDuration;  // ms

    @JsonProperty("speaking_duration")
    private Long speakingDuration;   // ms

    @JsonProperty("net_speaking_density")
    private Double netSpeakingDensity;  // %

    // 문장 연습 전용
    @JsonProperty("practice_records")
    private List<PracticeRecord> practiceRecords;

    @JsonProperty("avg_pace_ratio")
    private Double avgPaceRatio;

    // AI 대화 전용
    @JsonProperty("response_latencies")
    private List<Long> responseLatencies;

    @JsonProperty("avg_response_latency")
    private Double avgResponseLatency;

    @JsonProperty("chat_turns_count")
    private Integer chatTurnsCount;

    @JsonProperty("tutor_email")
    private String tutorEmail;

    private Long ttl;

    public LearningSession() {}

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

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
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

    public Double getNetSpeakingDensity() {
        return netSpeakingDensity;
    }

    public void setNetSpeakingDensity(Double netSpeakingDensity) {
        this.netSpeakingDensity = netSpeakingDensity;
    }

    public List<PracticeRecord> getPracticeRecords() {
        return practiceRecords;
    }

    public void setPracticeRecords(List<PracticeRecord> practiceRecords) {
        this.practiceRecords = practiceRecords;
    }

    public Double getAvgPaceRatio() {
        return avgPaceRatio;
    }

    public void setAvgPaceRatio(Double avgPaceRatio) {
        this.avgPaceRatio = avgPaceRatio;
    }

    public List<Long> getResponseLatencies() {
        return responseLatencies;
    }

    public void setResponseLatencies(List<Long> responseLatencies) {
        this.responseLatencies = responseLatencies;
    }

    public Double getAvgResponseLatency() {
        return avgResponseLatency;
    }

    public void setAvgResponseLatency(Double avgResponseLatency) {
        this.avgResponseLatency = avgResponseLatency;
    }

    public Integer getChatTurnsCount() {
        return chatTurnsCount;
    }

    public void setChatTurnsCount(Integer chatTurnsCount) {
        this.chatTurnsCount = chatTurnsCount;
    }

    public String getTutorEmail() {
        return tutorEmail;
    }

    public void setTutorEmail(String tutorEmail) {
        this.tutorEmail = tutorEmail;
    }

    public Long getTtl() {
        return ttl;
    }

    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }
}
