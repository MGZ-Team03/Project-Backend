package sentences.model;

import java.util.List;

public class SentenceRecommendResponse {
    private boolean success;
    private List<RecommendedSentence> sentences;
    private String topic;
    private String difficulty;
    private int count;
    private int remainingTurns = -1;  // -1 = 대화 없음, 0 이상 = 남은 턴 수
    private String error;

    public SentenceRecommendResponse() {}

    public static SentenceRecommendResponse success(List<RecommendedSentence> sentences, String topic, String difficulty) {
        SentenceRecommendResponse response = new SentenceRecommendResponse();
        response.success = true;
        response.sentences = sentences;
        response.topic = topic;
        response.difficulty = difficulty;
        response.count = sentences == null ? 0 : sentences.size();
        return response;
    }

    public static SentenceRecommendResponse error(String errorMessage) {
        SentenceRecommendResponse response = new SentenceRecommendResponse();
        response.success = false;
        response.error = errorMessage;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<RecommendedSentence> getSentences() {
        return sentences;
    }

    public void setSentences(List<RecommendedSentence> sentences) {
        this.sentences = sentences;
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

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getRemainingTurns() {
        return remainingTurns;
    }

    public void setRemainingTurns(int remainingTurns) {
        this.remainingTurns = remainingTurns;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

