package Utilidad;

/**
 * Clase de utilidad responsable de realizar conversiones de unidades.
 * Abstrae la lógica de negocio de la clase Servidor.
 */
public class UnitConverter {

    /**
     * Convierte un valor de temperatura dado en Celsius a la unidad de destino especificada.
     * Si la unidad de destino no es reconocida, devuelve el valor original en Celsius.
     * 
     * @param valorEnCelsius El valor de la temperatura en grados Celsius.
     * @param unidadDestino La unidad a la que se desea convertir (ej. "K", "°F").
     * @return El valor de la temperatura convertido.
     */
    public static double convertirTemperatura(double valorEnCelsius, String unidadDestino) {
        if ("K".equalsIgnoreCase(unidadDestino)) {
            // Fórmula de Celsius a Kelvin
            return valorEnCelsius + 273.15;
        }
        if ("°F".equalsIgnoreCase(unidadDestino)) {
            // Fórmula de Celsius a Fahrenheit
            return (valorEnCelsius * 9.0 / 5.0) + 32;
        }
        
        // Si la unidad es °C o cualquier otra no reconocida, devolvemos el valor base sin cambios.
        return valorEnCelsius; 
    }
}