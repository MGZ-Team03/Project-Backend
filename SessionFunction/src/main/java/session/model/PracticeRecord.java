package session.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PracticeRecord {

    @JsonProperty("sentence_id")
    private String sentenceId;

    @JsonProperty("pace_ratio")
    private Double paceRatio;

    @JsonProperty("user_time")
    private Long userTime;  // ms

    @JsonProperty("ref_time")
    private Long refTime;   // ms

    private Long timestamp;

    public PracticeRecord() {}

    public PracticeRecord(String sentenceId, Double paceRatio, Long userTime, Long refTime, Long timestamp) {
        this.sentenceId = sentenceId;
        this.paceRatio = paceRatio;
        this.userTime = userTime;
        this.refTime = refTime;
        this.timestamp = timestamp;
    }

    public String getSentenceId() {
        return sentenceId;
    }

    public void setSentenceId(String sentenceId) {
        this.sentenceId = sentenceId;
    }

    public Double getPaceRatio() {
        return paceRatio;
    }

    public void setPaceRatio(Double paceRatio) {
        this.paceRatio = paceRatio;
    }

    public Long getUserTime() {
        return userTime;
    }

    public void setUserTime(Long userTime) {
        this.userTime = userTime;
    }

    public Long getRefTime() {
        return refTime;
    }

    public void setRefTime(Long refTime) {
        this.refTime = refTime;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
