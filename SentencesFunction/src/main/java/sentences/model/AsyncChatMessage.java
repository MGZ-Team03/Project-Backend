package sentences.model;

import java.util.List;

public class AsyncChatMessage {
    private String requestId;
    private String conversationId;
    private String studentEmail;
    private String systemPrompt;
    private List<ConversationMessage> messages;

    public AsyncChatMessage() {}

    public AsyncChatMessage(String requestId, String conversationId, String studentEmail,
                           String systemPrompt, List<ConversationMessage> messages) {
        this.requestId = requestId;
        this.conversationId = conversationId;
        this.studentEmail = studentEmail;
        this.systemPrompt = systemPrompt;
        this.messages = messages;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public List<ConversationMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ConversationMessage> messages) {
        this.messages = messages;
    }
}
