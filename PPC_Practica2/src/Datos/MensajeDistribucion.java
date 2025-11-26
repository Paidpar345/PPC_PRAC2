package Datos;

import java.util.List;

public class MensajeDistribucion {
    private String idServidor;
    private String codificacion;
    private List<VariableMeteorologica> variables;

    public MensajeDistribucion(String idServidor, String codificacion, List<VariableMeteorologica> variables) {
        this.idServidor = idServidor;
        this.codificacion = codificacion;
        this.variables = variables;
    }
    
    public String getIdServidor() { return idServidor; }
    public String getCodificacion() { return codificacion; }
    public List<VariableMeteorologica> getVariables() { return variables; }
   
}
