package sentences.model;

public class SentenceFeedbackRequest {
    private String originalText;
    private String userText;
    private String difficulty;

    public SentenceFeedbackRequest() {}

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getUserText() {
        return userText;
    }

    public void setUserText(String userText) {
        this.userText = userText;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getDifficultyOrDefault() {
        return (difficulty == null || difficulty.trim().isEmpty()) ? "medium" : difficulty.trim();
    }

    public void validate() {
        if (originalText == null || originalText.trim().isEmpty()) {
            throw new IllegalArgumentException("originalText is required");
        }
        if (userText == null || userText.trim().isEmpty()) {
            throw new IllegalArgumentException("userText is required");
        }
        String d = getDifficultyOrDefault();
        if (!d.equals("easy") && !d.equals("medium") && !d.equals("hard")) {
            throw new IllegalArgumentException("Invalid difficulty. Must be one of: easy, medium, hard");
        }
    }
}

