package statistics.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WeeklySummary {

    @JsonProperty("total_recording_time")
    private Long totalRecordingTime;  // ms

    @JsonProperty("total_speaking_time")
    private Long totalSpeakingTime;   // ms

    @JsonProperty("total_sessions")
    private Integer totalSessions;

    @JsonProperty("total_practice_count")
    private Integer totalPracticeCount;

    @JsonProperty("total_chat_turns")
    private Integer totalChatTurns;

    @JsonProperty("avg_pace_ratio")
    private Double avgPaceRatio;

    @JsonProperty("avg_response_latency")
    private Double avgResponseLatency;

    @JsonProperty("avg_net_speaking_density")
    private Double avgNetSpeakingDensity;

    @JsonProperty("avg_response_quality")
    private Double avgResponseQuality;

    @JsonProperty("active_days")
    private Integer activeDays;

    public WeeklySummary() {}

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

    public Integer getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(Integer totalSessions) {
        this.totalSessions = totalSessions;
    }

    public Integer getTotalPracticeCount() {
        return totalPracticeCount;
    }

    public void setTotalPracticeCount(Integer totalPracticeCount) {
        this.totalPracticeCount = totalPracticeCount;
    }

    public Integer getTotalChatTurns() {
        return totalChatTurns;
    }

    public void setTotalChatTurns(Integer totalChatTurns) {
        this.totalChatTurns = totalChatTurns;
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

    public Double getAvgResponseQuality() {
        return avgResponseQuality;
    }

    public void setAvgResponseQuality(Double avgResponseQuality) {
        this.avgResponseQuality = avgResponseQuality;
    }

    public Integer getActiveDays() {
        return activeDays;
    }

    public void setActiveDays(Integer activeDays) {
        this.activeDays = activeDays;
    }
}
