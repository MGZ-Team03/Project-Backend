package sentences.model;

/**
 * SentencesFunction → TTSQueue 로 전송되는 메시지 페이로드.
 * TTSFunction의 com.speaktracker.tts.model.TTSJobMessage 와 JSON 필드명이 일치해야 함.
 */
public class TTSJobMessagePayload {
    private String jobId;
    private String text;
    private String voiceId;
    private String s3Key;

    private String sessionId;
    private Integer sentenceIndex;
    private boolean trackDuration;

    public TTSJobMessagePayload() {}

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getVoiceId() {
        return voiceId;
    }

    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getSentenceIndex() {
        return sentenceIndex;
    }

    public void setSentenceIndex(Integer sentenceIndex) {
        this.sentenceIndex = sentenceIndex;
    }

    public boolean isTrackDuration() {
        return trackDuration;
    }

    public boolean getTrackDuration() {
        return trackDuration;
    }

    public void setTrackDuration(boolean trackDuration) {
        this.trackDuration = trackDuration;
    }
}

