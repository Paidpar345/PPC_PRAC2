package Interfaces;

import Datos.MensajeControl;
import Datos.MensajeDistribucion;
import Utilidad.XmlParserType;

public interface IDataSerializer {
    String serializeDistribution(MensajeDistribucion msg);
    
   
    MensajeDistribucion deserializeDistribution(String data, String codificacion, XmlParserType parserType) throws Exception;
    
    String serializeControl(MensajeControl msg);
    
  
    MensajeControl deserializeControl(String data, String codificacion) throws Exception;
}
