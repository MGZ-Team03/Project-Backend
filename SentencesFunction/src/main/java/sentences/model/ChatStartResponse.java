package sentences.model;

public class ChatStartResponse {
    private boolean success;
    private String conversationId;
    private String situation;
    private String aiMessage;
    private String role;
    private String goal;
    private String error;

    public ChatStartResponse() {}

    public static ChatStartResponse success(String conversationId, String situation,
                                           String aiMessage, String role, String goal) {
        ChatStartResponse response = new ChatStartResponse();
        response.success = true;
        response.conversationId = conversationId;
        response.situation = situation;
        response.aiMessage = aiMessage;
        response.role = role;
        response.goal = goal;
        return response;
    }

    public static ChatStartResponse error(String errorMessage) {
        ChatStartResponse response = new ChatStartResponse();
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

    public String getSituation() {
        return situation;
    }

    public void setSituation(String situation) {
        this.situation = situation;
    }

    public String getAiMessage() {
        return aiMessage;
    }

    public void setAiMessage(String aiMessage) {
        this.aiMessage = aiMessage;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
