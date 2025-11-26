package Utilidad;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import Datos.MensajeDistribucion;
import Datos.VariableMeteorologica;

import java.util.ArrayList;

public class CustomContentHandler extends DefaultHandler {
    private MensajeDistribucion mensaje;
    private VariableMeteorologica currentVar;
    private StringBuilder characters;

    @Override
    public void startDocument() {
        mensaje = new MensajeDistribucion("", "", new ArrayList<>());
        characters = new StringBuilder();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        characters.setLength(0);
        
        if (qName.equals("MensajeDistribucion")) {
            String idServidor = attributes.getValue("idServidor");
            String codificacion = attributes.getValue("codificacion");
            mensaje = new MensajeDistribucion(idServidor, codificacion, new ArrayList<>());
        } else if (qName.equals("Variable")) {
            currentVar = new VariableMeteorologica("", "", 0.0);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        characters.append(new String(ch, start, length));
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String data = characters.toString().trim();

        if (qName.equals("Variable")) {
            if (currentVar != null) {
                mensaje.getVariables().add(currentVar);
            }
            currentVar = null;
        } else if (currentVar != null) {
            switch (qName) {
                case "nombre":
                    currentVar.setNombre(data);
                    break;
                case "unidad":
                    currentVar.setUnidad(data);
                    break;
                case "valor":
                    try {
                        currentVar.setValor(Double.parseDouble(data));
                    } catch (NumberFormatException e) {
                        throw new SAXException("Valor numérico inválido en tag <valor>: " + data); 
                    }
                    break;
            }
        }
    }

    public MensajeDistribucion getMensajeDistribucion() {
        return mensaje;
    }
}