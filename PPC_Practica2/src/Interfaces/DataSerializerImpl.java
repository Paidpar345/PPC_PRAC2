package Interfaces;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import Datos.MensajeControl;
import Datos.MensajeDistribucion;
import Datos.VariableMeteorologica;
import Utilidad.CustomContentHandler;
import Utilidad.MyErrorHandler;
import Utilidad.XmlParserType;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.xml.sax.InputSource;
import org.w3c.dom.*;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;

public class DataSerializerImpl implements IDataSerializer {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();


    @Override
    public String serializeDistribution(MensajeDistribucion msg) {
        if (msg.getCodificacion().equals("JSON")) {
            return gson.toJson(msg);
        }
        return serializeDistributionToXml(msg);
    }
    
    @Override
    public String serializeControl(MensajeControl msg) {
        if (msg.getCodificacion().equals("JSON")) {
            return gson.toJson(msg);
        }
        return serializeControlToXml(msg);
    }



    @Override
    public MensajeDistribucion deserializeDistribution(String data, String codificacion, XmlParserType parserType) throws Exception {
        if (codificacion.equals("JSON")) {
            return gson.fromJson(data, MensajeDistribucion.class);
        }
        if (codificacion.equals("XML")) {
            if (parserType == XmlParserType.SAX) {
                return deserializeDistributionFromXmlSAX(data);
            } else if (parserType == XmlParserType.DOM) {
                return deserializeDistributionFromXmlDOM(data);
            }
        }
        throw new IllegalArgumentException("Codificación o tipo de parser XML no soportado.");
    }
    
    @Override
    public MensajeControl deserializeControl(String data, String codificacion) throws Exception {

        if ("JSON".equalsIgnoreCase(codificacion)) {
            return gson.fromJson(data, MensajeControl.class);
        } else if ("XML".equalsIgnoreCase(codificacion)) {
            return deserializeControlFromXml(data);
        }
        throw new IllegalArgumentException("Codificación de control no soportada: " + codificacion);
    }

   
    private String serializeDistributionToXml(MensajeDistribucion msg) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement("MensajeDistribucion");
            doc.appendChild(rootElement);

            rootElement.setAttribute("idServidor", msg.getIdServidor());
            rootElement.setAttribute("codificacion", msg.getCodificacion());
            rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");

            rootElement.setAttribute("xsi:noNamespaceSchemaLocation", "recursos/distribucion.xsd"); 

            for (VariableMeteorologica var : msg.getVariables()) {
                Element varElement = doc.createElement("Variable");
                rootElement.appendChild(varElement);

                Element nombre = doc.createElement("nombre");
                nombre.appendChild(doc.createTextNode(var.getNombre()));
                varElement.appendChild(nombre);

                Element unidad = doc.createElement("unidad");
                unidad.appendChild(doc.createTextNode(var.getUnidad()));
                varElement.appendChild(unidad);

                Element valor = doc.createElement("valor");
                valor.appendChild(doc.createTextNode(String.valueOf(var.getValor())));
                varElement.appendChild(valor);
            }

            StringWriter sw = new StringWriter();
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();

        } catch (Exception e) { e.printStackTrace(); return ""; }
    }
    
    private String serializeControlToXml(MensajeControl msg) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element rootElement = doc.createElement("MensajeControl");
            doc.appendChild(rootElement);

            rootElement.setAttribute("codificacion", msg.getCodificacion());
            rootElement.setAttribute("comando", msg.getComando());
            rootElement.setAttribute("valor", msg.getValor());
            
            StringWriter sw = new StringWriter();
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();

        } catch (Exception e) { e.printStackTrace(); return ""; }
    }



    private MensajeDistribucion deserializeDistributionFromXmlSAX(String xmlData) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(true);
        factory.setFeature("http://xml.org/sax/features/validation", true);
        factory.setFeature("http://apache.org/xml/features/validation/schema", true);

        SAXParser saxParser = factory.newSAXParser();
        CustomContentHandler handler = new CustomContentHandler(); 
        
        saxParser.getXMLReader().setErrorHandler(new MyErrorHandler());
        
        saxParser.parse(new InputSource(new StringReader(xmlData)), handler); 
        
        System.out.println("Mensaje XML validado correctamente (Parser SAX)");
        
        return handler.getMensajeDistribucion();
    }
    

    
    private MensajeDistribucion deserializeDistributionFromXmlDOM(String xmlData) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(true);
        factory.setFeature("http://xml.org/sax/features/validation", true);
        factory.setFeature("http://apache.org/xml/features/validation/schema", true);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new MyErrorHandler());
        
        Document doc = builder.parse(new InputSource(new StringReader(xmlData)));
        Element root = doc.getDocumentElement();
        
        String idServidor = root.getAttribute("idServidor");
        String codificacion = root.getAttribute("codificacion");
        
        MensajeDistribucion msg = new MensajeDistribucion(idServidor, codificacion, new ArrayList<>());
        
        NodeList varNodes = root.getElementsByTagName("Variable");
        for (int i = 0; i < varNodes.getLength(); i++) {
            Node varNode = varNodes.item(i);
            if (varNode.getNodeType() == Node.ELEMENT_NODE) {
                Element varElement = (Element) varNode;
                
                String nombre = varElement.getElementsByTagName("nombre").item(0).getTextContent();
                String unidad = varElement.getElementsByTagName("unidad").item(0).getTextContent();
                String valorStr = varElement.getElementsByTagName("valor").item(0).getTextContent();
                double valor = Double.parseDouble(valorStr);
                
                msg.getVariables().add(new VariableMeteorologica(nombre, unidad, valor));
            }
        }
        
        System.out.println("Mensaje XML validado correctamente (Parser DOM)");
        
        return msg;
    }
    
    

    private MensajeControl deserializeControlFromXml(String xmlData) throws Exception {
        
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setValidating(true); 
        docFactory.setFeature("http://xml.org/sax/features/validation", true);
        docFactory.setFeature("http://apache.org/xml/features/validation/schema", true);
        
        
        docFactory.setAttribute(
            "http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation", 
            "recursos/control.xsd" 
        );
        
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        docBuilder.setErrorHandler(new MyErrorHandler());
        
        Document doc = docBuilder.parse(new InputSource(new StringReader(xmlData)));
        
        System.out.println("Mensaje XML de Control validado correctamente.");
        
        Element root = doc.getDocumentElement();
        
        String codificacion = root.getAttribute("codificacion");
        String comando = root.getAttribute("comando");
        String valor = root.getAttribute("valor");
        
        
        return new MensajeControl(codificacion, comando, valor);
    }
}