import java.io.Serializable;
import javax.swing.text.SimpleAttributeSet;

public class Mensaje implements Serializable {
    private String texto;
    private SimpleAttributeSet atributos;

    public Mensaje(String texto, SimpleAttributeSet atributos) {
        this.texto = texto;
        this.atributos = atributos;
    }

    public String getTexto() {
        return texto;
    }

    public SimpleAttributeSet getAtributos() {
        return atributos;
    }
}
