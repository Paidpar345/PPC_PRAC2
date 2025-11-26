package Ejecutables;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;
import Datos.MensajeControl;
import Datos.MensajeDistribucion;
import Interfaces.DataSerializerImpl;
import Interfaces.IDataSerializer;
import Utilidad.XmlParserType;


public class ClienteService {

    private final int PUERTO_DISTRIBUCION = 9876;
    private final int TIMEOUT_MS = 2000;
    private final int MAX_REINTENTOS = 3;

    private final IDataSerializer serializer = new DataSerializerImpl();
    private XmlParserType parserConfig = XmlParserType.SAX;

    private DatagramSocket broadcastSocket;
    private DatagramSocket unicastSocket;
    private static final Logger logger = Logger.getLogger(ClienteService.class.getName());
   
    private final Consumer<String> onTramaRecibida;
    private final AtomicBoolean mostrarTramas = new AtomicBoolean(true);

    public ClienteService(Consumer<String> onTramaRecibida) throws IOException {
        this.onTramaRecibida = onTramaRecibida;
        broadcastSocket = new DatagramSocket(null);
        broadcastSocket.setReuseAddress(true);
        broadcastSocket.bind(new InetSocketAddress(PUERTO_DISTRIBUCION));
        unicastSocket = new DatagramSocket();
    }
    
    public void recibirDistribucion() {
        try {
            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (true) {
                broadcastSocket.receive(packet);
                String mensajeCompleto = new String(packet.getData(), 0, packet.getLength()).trim();

                int indiceSaltoDeLinea = mensajeCompleto.indexOf('\n');

                if (indiceSaltoDeLinea == -1) {
                    onTramaRecibida.accept("\n--- TRAMA MAL FORMADA RECIBIDA ---\n" +
                                        "No se encontró cabecera de protocolo. Trama descartada.");
                    almacenarTrama("MALFORMADO", "txt", mensajeCompleto);
                    continue;
                }

                String codificacion = mensajeCompleto.substring(0, indiceSaltoDeLinea).trim();
                String payload = mensajeCompleto.substring(indiceSaltoDeLinea + 1);

                XmlParserType parserUsar = codificacion.equals("XML") ? this.parserConfig : null;

                try {
                    MensajeDistribucion msg = serializer.deserializeDistribution(payload, codificacion, parserUsar);
                    if (mostrarTramas.get()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("\n--- TRAMA RECIBIDA ---\n");
                        sb.append("Servidor: ").append(msg.getIdServidor());
                        sb.append(" | Codificación: ").append(codificacion);
                        sb.append(" | Parser: ").append(parserUsar != null ? parserUsar : "GSON").append("\n");
                        msg.getVariables().forEach(v ->
                            sb.append("  - ").append(v.getNombre()).append(": ").append(v.getValor()).append(" ").append(v.getUnidad()).append("\n"));
                        onTramaRecibida.accept(sb.toString());
                    }
                    almacenarTrama(msg.getIdServidor(), codificacion, payload);
                } catch (Exception e) {
                    logger.severe("Error deserializando trama: " + e.getMessage());
                    onTramaRecibida.accept("\n--- TRAMA RECIBIDA FALLIDA (" + codificacion + ") ---\n" +
                                        "La trama no pudo ser deserializada: " + e.getMessage());
                    almacenarTrama("FALLO_DESERIALIZACION", codificacion, payload);
                }
            }
        } catch (Exception e) {
            logger.severe("Error en recibirDistribucion: " + e.getMessage());
            onTramaRecibida.accept("Cliente Error Rx: " + e.getMessage());
        }
    }

    
    private void almacenarTrama(String idServidor, String codificacion, String data) {
        String filename = "rx/" + idServidor + "_" + codificacion + "_" + new Date().getTime() + "." + codificacion.toLowerCase();
        try {
            File dir = new File("rx");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("No se pudo crear el directorio: " + dir.getAbsolutePath());
            }
            try (FileWriter writer = new FileWriter(filename)) {
                writer.write(data);
            }
        } catch (IOException e) {
            logger.severe("Error al guardar archivo: " + e.getMessage());
            onTramaRecibida.accept("Error al guardar archivo: " + e.getMessage());
        }
    }

     public void procesarComando(String line) {
    	    if (line.isEmpty()) return;
    	    String[] parts = line.split(" ", 4);

    	    // Maneja el comando "MUTE" para activar/desactivar la visualización de tramas
    	    if (parts.length > 0 && "MUTE".equalsIgnoreCase(parts[0])) {
    	        toggleMute();
    	        return;
    	    }

    	    // Maneja el comando "CONFIG_PARSER" para cambiar el analizador XML
    	    if (parts.length == 2 && "CONFIG_PARSER".equalsIgnoreCase(parts[0])) {
    	        try {
    	            this.parserConfig = XmlParserType.valueOf(parts[1].toUpperCase());
    	            onTramaRecibida.accept("Parser XML configurado a: " + this.parserConfig);
    	        } catch (IllegalArgumentException e) {
    	            onTramaRecibida.accept("Error: Parser no válido. Use SAX o DOM.");
    	        }
    	        return;
    	    }

    	    // Valida que el comando de control tenga el número mínimo de partes (3)
    	    if (parts.length < 3) {
    	        onTramaRecibida.accept("Comando incompleto. Formato: PUERTO COMANDO [VALOR] CODIFICACION.");
    	        return;
    	    }

    	    try {
    	        int puertoServidor = Integer.parseInt(parts[0]);
    	        String comando = parts[1];
    	        String valor;
    	        String codificacion;

    	        // Determina si el comando incluye un valor (4 partes) o no (3 partes)
    	        if (parts.length == 3) {
    	            // Caso para comandos como: 9001 ACTIVAR XML
    	            valor = ""; // El valor está vacío porque no es necesario
    	            codificacion = parts[2];
    	        } else {
    	            // Caso para comandos como: 9001 CAMBIAR_FRECUENCIA 500 XML
    	            valor = parts[2];
    	            codificacion = parts[3];
    	        }
    	        
    	        // Llama al método que envía el comando al servidor
    	        enviarControlConReenvio(puertoServidor, comando, valor, codificacion);
    	        
    	    } catch (NumberFormatException e) {
    	        onTramaRecibida.accept("Error: El puerto debe ser numérico.");
    	    } catch (Exception e) {
    	        onTramaRecibida.accept("Error en el envío de control: " + e.getMessage());
    	    }
    	}

		
		public void toggleMute() {
		    boolean nuevoEstado = !mostrarTramas.get();
		    mostrarTramas.set(nuevoEstado);
		    onTramaRecibida.accept("Visualización de tramas: " + (nuevoEstado ? "ACTIVADA (ON)" : "DESACTIVADA (OFF)"));
		}
		    
    public void close() {
        if (broadcastSocket != null && !broadcastSocket.isClosed()) {
            broadcastSocket.close();
        }
        if (unicastSocket != null && !unicastSocket.isClosed()) {
            unicastSocket.close();
        }
    }

    private void enviarControlConReenvio(int puertoServidor, String comando, String valor, String codificacion) throws Exception {
        MensajeControl controlMsg = new MensajeControl(codificacion, comando, valor);
        String payload = serializer.serializeControl(controlMsg);
        
        String mensajeCompleto = codificacion + "\n" + payload;
        byte[] data = mensajeCompleto.getBytes();

        InetAddress servidorAddress = InetAddress.getByName("localhost");
        DatagramPacket packet = new DatagramPacket(data, data.length, servidorAddress, puertoServidor);

        int reintentos = 0;
        boolean ackRecibido = false;
        
        while (reintentos < MAX_REINTENTOS && !ackRecibido) {
            onTramaRecibida.accept("Enviando control a " + puertoServidor + " (Intento " + (reintentos + 1) + ")");
            unicastSocket.send(packet);
            unicastSocket.setSoTimeout(TIMEOUT_MS);
            
            try {
                byte[] bufferRespuesta = new byte[1024];
                DatagramPacket respuestaPacket = new DatagramPacket(bufferRespuesta, bufferRespuesta.length);
                unicastSocket.receive(respuestaPacket);
                
                String response = new String(respuestaPacket.getData(), 0, respuestaPacket.getLength()).trim();
                onTramaRecibida.accept(">>> RESPUESTA CONTROL: " + response);

                if (response.contains("ACK")) { 
                    ackRecibido = true;
                }
                
            } catch (SocketTimeoutException e) {
                onTramaRecibida.accept("Timeout: No se recibió ACK del servidor " + puertoServidor);
                reintentos++;
            }
        }
        
        if (!ackRecibido) {
        	if (!ackRecibido) {
        	    logger.warning("Falla de comunicación con el servidor " + puertoServidor);
        	    onTramaRecibida.accept("!! Falla de comunicación !!: No se pudo confirmar el comando con el servidor " + puertoServidor);
        	}

        }
    }
}