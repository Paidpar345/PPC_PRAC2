package Utilidad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import Datos.VariableMeteorologica;

public class Utils {
    private static final Random random = new Random();
    
    public static final String[] TODAS_VARIABLES = 
        {"temperatura", "humedad relativa", "partículas en suspensión (PM10)", 
         "dióxido de azufre (SO2)", "dióxido de nitrógeno (NO2)", "ozono (O3)"};
    
    private static final String[] UNIDADES_TEMPERATURA = {"°C", "K", "°F"};
    private static final String[] UNIDADES_CONCENTRACION = {"µg/m³", "ppm"};

    public static List<VariableMeteorologica> generarVariablesAleatorias(String[] variables) {
        List<VariableMeteorologica> vars = new ArrayList<>();
        
        for (String nombre : variables) {
            String unidad = obtenerUnidadInicial(nombre);
            double valor;
            
            if (nombre.contains("temperatura")) {
                valor = 10 + (30 - 10) * random.nextDouble();
            } else if (nombre.contains("humedad")) {
                valor = 40 + (80 - 40) * random.nextDouble();
                unidad = "%";
            } else {
                valor = 5 + (50 - 5) * random.nextDouble();
                unidad = UNIDADES_CONCENTRACION[0];
            }
            
            vars.add(new VariableMeteorologica(nombre, unidad, Math.round(valor * 100.0) / 100.0));
        }
        return vars;
    }
    
    public static String obtenerUnidadInicial(String nombre) {
        if (nombre.contains("temperatura")) return UNIDADES_TEMPERATURA[0];
        if (nombre.contains("humedad")) return "%";
        return UNIDADES_CONCENTRACION[0];
    }
    
    public static String[] obtenerVariablesServidor(int idServidor) {
        switch (idServidor % 3) { 
            case 0: return new String[]{"temperatura", "humedad relativa", "partículas en suspensión (PM10)"};
            case 1: return new String[]{"temperatura", "dióxido de azufre (SO2)", "ozono (O3)"};
            case 2: return new String[]{"humedad relativa", "dióxido de nitrógeno (NO2)", "partículas en suspensión (PM10)", "ozono (O3)"};
            default: return new String[]{};
        }
    }
    
    
    

}