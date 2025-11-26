package Datos;

public class Variable {
    private String nombre;
    private String unidad;

    public Variable(String nombre, String unidad) {
        this.nombre = nombre;
        this.unidad = unidad;
    }

    public String getNombre() { return nombre; }
    public String getUnidad() { return unidad; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
}