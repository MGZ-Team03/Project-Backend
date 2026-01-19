package sentences.model;

import java.util.List;

public class SentenceFeedbackResponse {
    private boolean success;
    private String correctedUserText;
    private List<String> feedback;
    private List<String> suggestions;
    private String encouragement;
    private String error;

    public SentenceFeedbackResponse() {}

    public static SentenceFeedbackResponse success(
        String correctedUserText,
        List<String> feedback,
        List<String> suggestions,
        String encouragement
    ) {
        SentenceFeedbackResponse response = new SentenceFeedbackResponse();
        response.success = true;
        response.correctedUserText = correctedUserText;
        response.feedback = feedback;
        response.suggestions = suggestions;
        response.encouragement = encouragement;
        return response;
    }

    public static SentenceFeedbackResponse error(String errorMessage) {
        SentenceFeedbackResponse response = new SentenceFeedbackResponse();
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

    public String getCorrectedUserText() {
        return correctedUserText;
    }

    public void setCorrectedUserText(String correctedUserText) {
        this.correctedUserText = correctedUserText;
    }

    public List<String> getFeedback() {
        return feedback;
    }

    public void setFeedback(List<String> feedback) {
        this.feedback = feedback;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public String getEncouragement() {
        return encouragement;
    }

    public void setEncouragement(String encouragement) {
        this.encouragement = encouragement;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

