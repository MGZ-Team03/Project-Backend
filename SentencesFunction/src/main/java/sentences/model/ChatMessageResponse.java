package sentences.model;

public class ChatMessageResponse {
    private boolean success;
    private String conversationId;
    private String aiMessage;
    private int turnCount;
    private String error;

    public ChatMessageResponse() {}

    public static ChatMessageResponse success(String conversationId, String aiMessage, int turnCount) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.success = true;
        response.conversationId = conversationId;
        response.aiMessage = aiMessage;
        response.turnCount = turnCount;
        return response;
    }

    public static ChatMessageResponse error(String errorMessage) {
        ChatMessageResponse response = new ChatMessageResponse();
        response.success = false;
        response.error = errorMessage;
        return response;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getAiMessage() {
        return aiMessage;
    }

    public void setAiMessage(String aiMessage) {
        this.aiMessage = aiMessage;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(int turnCount) {
        this.turnCount = turnCount;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
