package statistics.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseQuality {

    @JsonProperty("durationMs")
    private Long durationMs;

    @JsonProperty("wordCount")
    private Integer wordCount;

    @JsonProperty("wordsPerMinute")
    private Double wordsPerMinute;

    @JsonProperty("fluencyScore")
    private Double fluencyScore;

    @JsonProperty("overallScore")
    private Double overallScore;

    @JsonProperty("timestamp")
    private Long timestamp;

    public ResponseQuality() {}

    // Getters
    public Long getDurationMs() {
        return durationMs;
    }

    public Integer getWordCount() {
        return wordCount;
    }

    public Double getWordsPerMinute() {
        return wordsPerMinute;
    }

    public Double getFluencyScore() {
        return fluencyScore;
    }

    public Double getOverallScore() {
        return overallScore;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    // Setters
    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public void setWordCount(Integer wordCount) {
        this.wordCount = wordCount;
    }

    public void setWordsPerMinute(Double wordsPerMinute) {
        this.wordsPerMinute = wordsPerMinute;
    }

    public void setFluencyScore(Double fluencyScore) {
        this.fluencyScore = fluencyScore;
    }

    public void setOverallScore(Double overallScore) {
        this.overallScore = overallScore;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
