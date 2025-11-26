package Datos;

public class VariableMeteorologica extends Variable {
    private double valor;

    public VariableMeteorologica(String nombre, String unidad, double valor) {
        super(nombre, unidad);
        this.valor = valor;
    }

    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }

	

	
}