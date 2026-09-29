package br.com.faitec.falacidade.domain;

/** Valor único já usado por outra conta; "field" diz qual campo, para a tela apontar o certo. */
public class DuplicateFieldException extends IllegalStateException {

    public static final String PHONE_TAKEN = "Este celular já está cadastrado em outra conta.";

    private final String field;

    public DuplicateFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public DuplicateFieldException(String field, String message, Throwable cause) {
        super(message, cause);
        this.field = field;
    }

    public String getField() { return field; }
}
