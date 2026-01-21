package sentences.model;

public class SentenceRecommendRequest {
    private String topic;
    private String difficulty;
    private Integer count;
    private String conversationId;  // 선택: 대화 중 추천 시 턴 차감용

    public SentenceRecommendRequest() {}

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

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public int getCountOrDefault() {
        return (count == null) ? 5 : count;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public void validate() {
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("topic is required");
        }
        if (difficulty == null || difficulty.trim().isEmpty()) {
            throw new IllegalArgumentException("difficulty is required");
        }

        // 유효한 주제인지 확인
        String[] validTopics = {"restaurant", "airport", "shopping", "hotel", "hospital", "interview", "daily", "directions"};
        boolean validTopic = false;
        for (String t : validTopics) {
            if (t.equals(topic)) {
                validTopic = true;
                break;
            }
        }
        if (!validTopic) {
            throw new IllegalArgumentException("Invalid topic. Must be one of: restaurant, airport, shopping, hotel, hospital, interview, daily, directions");
        }

        // 유효한 난이도인지 확인
        if (!difficulty.equals("easy") && !difficulty.equals("medium") && !difficulty.equals("hard")) {
            throw new IllegalArgumentException("Invalid difficulty. Must be one of: easy, medium, hard");
        }

        int c = getCountOrDefault();
        if (c < 1 || c > 10) {
            throw new IllegalArgumentException("Invalid count. Must be between 1 and 10");
        }
    }
}

