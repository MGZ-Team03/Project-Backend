package sentences.model;

import java.util.List;

public class SentenceGenerateResponse {
    private boolean success;
    private List<Sentence> sentences;
    private String topic;
    private String difficulty;
    private int count;
    private String error;

    public SentenceGenerateResponse() {}

    public static SentenceGenerateResponse success(List<Sentence> sentences, String topic, String difficulty) {
        SentenceGenerateResponse response = new SentenceGenerateResponse();
        response.success = true;
        response.sentences = sentences;
        response.topic = topic;
        response.difficulty = difficulty;
        response.count = sentences.size();
        return response;
    }

    public static SentenceGenerateResponse error(String errorMessage) {
        SentenceGenerateResponse response = new SentenceGenerateResponse();
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

    public List<Sentence> getSentences() {
        return sentences;
    }

    public void setSentences(List<Sentence> sentences) {
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

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
