package statistics.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyStatistics {

    @JsonProperty("student_email")
    private String studentEmail;

    private String date;  // YYYY-MM-DD

    // 기본 통계
    @JsonProperty("total_recording_time")
    private Long totalRecordingTime;  // ms

    @JsonProperty("total_speaking_time")
    private Long totalSpeakingTime;   // ms

    @JsonProperty("sessions_count")
    private Integer sessionsCount;

    @JsonProperty("practice_count")
    private Integer practiceCount;

    @JsonProperty("chat_turns_count")
    private Integer chatTurnsCount;

    // 3대 지표
    @JsonProperty("avg_pace_ratio")
    private Double avgPaceRatio;

    @JsonProperty("avg_response_latency")
    private Double avgResponseLatency;

    @JsonProperty("avg_net_speaking_density")
    private Double avgNetSpeakingDensity;

    // 상세 기록
    @JsonProperty("pace_ratios")
    private List<Double> paceRatios;

    @JsonProperty("response_latencies")
    private List<Long> responseLatencies;

    // 새로 추가된 필드
    @JsonProperty("avg_response_quality")
    private Double avgResponseQuality;

    @JsonProperty("response_qualities")
    private List<ResponseQuality> responseQualities;

    public DailyStatistics() {}

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Long getTotalRecordingTime() {
        return totalRecordingTime;
    }

    public void setTotalRecordingTime(Long totalRecordingTime) {
        this.totalRecordingTime = totalRecordingTime;
    }

    public Long getTotalSpeakingTime() {
        return totalSpeakingTime;
    }

    public void setTotalSpeakingTime(Long totalSpeakingTime) {
        this.totalSpeakingTime = totalSpeakingTime;
    }

    public Integer getSessionsCount() {
        return sessionsCount;
    }

    public void setSessionsCount(Integer sessionsCount) {
        this.sessionsCount = sessionsCount;
    }

    public Integer getPracticeCount() {
        return practiceCount;
    }

    public void setPracticeCount(Integer practiceCount) {
        this.practiceCount = practiceCount;
    }

    public Integer getChatTurnsCount() {
        return chatTurnsCount;
    }

    public void setChatTurnsCount(Integer chatTurnsCount) {
        this.chatTurnsCount = chatTurnsCount;
    }

    public Double getAvgPaceRatio() {
        return avgPaceRatio;
    }

    public void setAvgPaceRatio(Double avgPaceRatio) {
        this.avgPaceRatio = avgPaceRatio;
    }

    public Double getAvgResponseLatency() {
        return avgResponseLatency;
    }

    public void setAvgResponseLatency(Double avgResponseLatency) {
        this.avgResponseLatency = avgResponseLatency;
    }

    public Double getAvgNetSpeakingDensity() {
        return avgNetSpeakingDensity;
    }

    public void setAvgNetSpeakingDensity(Double avgNetSpeakingDensity) {
        this.avgNetSpeakingDensity = avgNetSpeakingDensity;
    }

    public List<Double> getPaceRatios() {
        return paceRatios;
    }

    public void setPaceRatios(List<Double> paceRatios) {
        this.paceRatios = paceRatios;
    }

    public List<Long> getResponseLatencies() {
        return responseLatencies;
    }

    public void setResponseLatencies(List<Long> responseLatencies) {
        this.responseLatencies = responseLatencies;
    }

    public Double getAvgResponseQuality() {
        return avgResponseQuality;
    }

    public void setAvgResponseQuality(Double avgResponseQuality) {
        this.avgResponseQuality = avgResponseQuality;
    }

    public List<ResponseQuality> getResponseQualities() {
        return responseQualities;
    }

    public void setResponseQualities(List<ResponseQuality> responseQualities) {
        this.responseQualities = responseQualities;
    }

    /**
     * 빈 통계 객체 생성 (데이터 없는 날짜용)
     */
    public static DailyStatistics empty(String studentEmail, String date) {
        DailyStatistics stats = new DailyStatistics();
        stats.setStudentEmail(studentEmail);
        stats.setDate(date);
        stats.setTotalRecordingTime(0L);
        stats.setTotalSpeakingTime(0L);
        stats.setSessionsCount(0);
        stats.setPracticeCount(0);
        stats.setChatTurnsCount(0);
        stats.setAvgPaceRatio(null);
        stats.setAvgResponseLatency(null);
        stats.setAvgNetSpeakingDensity(null);
        stats.setAvgResponseQuality(null);
        stats.setPaceRatios(new ArrayList<>());
        stats.setResponseLatencies(new ArrayList<>());
        stats.setResponseQualities(new ArrayList<>());
        return stats;
    }
}
