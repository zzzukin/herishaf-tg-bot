package kuzin.r.heryshaf.consts;

public enum Phrase {

    POSITIVE("да, ага, конечно"),
    NEGATIVE("нет, не, не знаю");

    final String phrases;

    Phrase(String phrases) {
        this.phrases = phrases;
    }

    public String get() {
        return phrases;
    }
}
