package sentences.model;

public class Sentence {
    private int id;
    private String text;
    private String translation;
    private String situation;

    public Sentence() {}

    public Sentence(int id, String text, String translation, String situation) {
        this.id = id;
        this.text = text;
        this.translation = translation;
        this.situation = situation;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public String getSituation() {
        return situation;
    }

    public void setSituation(String situation) {
        this.situation = situation;
    }
}
