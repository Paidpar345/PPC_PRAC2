package Datos;

public class MensajeControl {
    private String codificacion;
    private String comando; 
    private String valor;

    public MensajeControl(String codificacion, String comando, String valor) {
        this.codificacion = codificacion;
        this.comando = comando;
        this.valor = valor;
    }

    public String getCodificacion() { return codificacion; }
    public String getComando() { return comando; }
    public String getValor() { return valor; }
    
}
