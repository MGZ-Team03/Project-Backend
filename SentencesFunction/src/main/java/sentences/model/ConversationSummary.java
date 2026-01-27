package sentences.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversationSummary {

    @JsonProperty("conversation_id")
    private String conversationId;

    private String timestamp;

    private String topic;

    private String difficulty;

    @JsonProperty("turn_count")
    private Integer turnCount;

    private String preview;  // 첫 AI 메시지 50자 미리보기

    public ConversationSummary() {}

    public ConversationSummary(String conversationId, String timestamp, String topic,
                               String difficulty, Integer turnCount, String preview) {
        this.conversationId = conversationId;
        this.timestamp = timestamp;
        this.topic = topic;
        this.difficulty = difficulty;
        this.turnCount = turnCount;
        this.preview = preview;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(Integer turnCount) {
        this.turnCount = turnCount;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }
}
