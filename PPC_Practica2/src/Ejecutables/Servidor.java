package Ejecutables;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import Interfaces.DataSerializerImpl;
import Interfaces.IDataSerializer;
import Datos.MensajeControl;
import Datos.MensajeDistribucion;
import Datos.VariableMeteorologica;
import Utilidad.UnitConverter;
import Utilidad.Utils;

public class Servidor {
	// --- CONSTANTES PARA COMANDOS Y VALORES (Optimización: Evitar "Magic Strings") ---
	private static final String CMD_CAMBIAR_CODIFICACION = "CAMBIAR_CODIFICACION";
	private static final String CMD_CAMBIAR_FRECUENCIA = "CAMBIAR_FRECUENCIA";
	private static final String CMD_CAMBIAR_UNIDADES = "CAMBIAR_UNIDADES";
	private static final String CMD_ACTIVAR = "ACTIVAR";
	private static final String CMD_DESACTIVAR = "DESACTIVAR";
	private static final String VAR_TEMPERATURA = "temperatura";
	private static final String VAR_HUMEDAD = "humedad";

	// --- CONFIGURACIÓN DE RED ---
	private final int PUERTO_DISTRIBUCION = 9876;
	private final int PUERTO_CONTROL;
	private final String ID_SERVIDOR;
	private final String[] VARIABLES_GESTIONADAS;

	// --- ESTADO INTERNO DEL SERVIDOR ---
	private final Map<String, String> unidadesVariables = new ConcurrentHashMap<>();
	private final Map<String, Double> valoresBase = new ConcurrentHashMap<>();
	private final Random random = new Random();
	private volatile boolean activo = true;
	private volatile long frecuenciaMs = 1000;
	private volatile String codificacionActual = "XML";

	// --- COMPONENTES ---
	private DatagramSocket broadcastSocket;
	private DatagramSocket unicastSocket;
	private final IDataSerializer serializer = new DataSerializerImpl();
	private final ExecutorService controlThreadPool = Executors.newFixedThreadPool(10);

	public Servidor(int id, int puertoUnicast) throws SocketException, UnknownHostException {
		// Inicializar propiedades básicas
		this.PUERTO_CONTROL = puertoUnicast;
		this.ID_SERVIDOR = "SERV-" + id;
		this.VARIABLES_GESTIONADAS = Utils.obtenerVariablesServidor(id);

		// Seguir una secuencia de arranque lógica y limpia
		inicializarEstado();
		configurarRed();
		iniciarHilos();

		System.out.println(ID_SERVIDOR + " inicializado en puerto " + PUERTO_CONTROL +
				". Variables: " + Arrays.toString(VARIABLES_GESTIONADAS));

		// Optimización: Registrar un gancho de apagado para cerrar recursos limpiamente
		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
	}

	private void inicializarEstado() {
		for (String varNombre : VARIABLES_GESTIONADAS) {
			unidadesVariables.put(varNombre, Utils.obtenerUnidadInicial(varNombre));
			if (varNombre.contains(VAR_TEMPERATURA)) {
				valoresBase.put(varNombre, 15.0 + (10 * random.nextDouble()));
			} else if (varNombre.contains(VAR_HUMEDAD)) {
				valoresBase.put(varNombre, 50.0 + (20 * random.nextDouble()));
			} else {
				valoresBase.put(varNombre, 20.0 + (30 * random.nextDouble()));
			}
		}
	}

	private void configurarRed() throws SocketException {
		broadcastSocket = new DatagramSocket();
		broadcastSocket.setBroadcast(true);
		unicastSocket = new DatagramSocket(PUERTO_CONTROL);
	}

	private void iniciarHilos() {
		new Thread(this::enviarDistribucion, "Distribucion-" + ID_SERVIDOR).start();
		new Thread(this::recibirControl, "Control-" + ID_SERVIDOR).start();
	}

	private void shutdown() {
		System.out.println("Cerrando servidor " + ID_SERVIDOR + "...");
		this.activo = false;
		controlThreadPool.shutdownNow();
		try {
			unicastSocket.close();
			broadcastSocket.close();
		} catch (Exception e) {
			System.err.println(ID_SERVIDOR + " error al cerrar sockets: " + e.getMessage());
		}
		System.out.println(ID_SERVIDOR + " cerrado.");
	}

	// En el archivo: Servidor.java

	private void enviarDistribucion() {
	    try {
	        InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");

	        // --- INICIO DE LA CORRECCIÓN ---

	        // El bucle ahora es infinito para que el hilo nunca muera.
	        // La interrupción del hilo es la forma correcta de detenerlo desde fuera (ej. en shutdown).
	        while (!Thread.currentThread().isInterrupted()) { 
	            
	            // La variable 'activo' ahora solo decide SI se envía el paquete,
	            // pero no detiene el bucle ni mata el hilo.
	            if (this.activo) { 
	                simularFluctuacionValores();
	                List<VariableMeteorologica> varsParaMensaje = new ArrayList<>();

	                for (String varNombre : VARIABLES_GESTIONADAS) {
	                    double valorBase = valoresBase.get(varNombre);
	                    String unidadDestino = unidadesVariables.get(varNombre);
	                    double valorConvertido = convertirValor(valorBase, varNombre, unidadDestino);
	                    valorConvertido = Math.round(valorConvertido * 100.0) / 100.0;
	                    varsParaMensaje.add(new VariableMeteorologica(varNombre, unidadDestino, valorConvertido));
	                }

	                MensajeDistribucion msg = new MensajeDistribucion(ID_SERVIDOR, codificacionActual, varsParaMensaje);
	                String payload = serializer.serializeDistribution(msg);
	                String mensajeCompleto = codificacionActual + "\n" + payload;
	                byte[] data = mensajeCompleto.getBytes();
	                DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddress, PUERTO_DISTRIBUCION);
	                broadcastSocket.send(packet);

	                System.out.printf("[%s] BROADCAST: %d bytes en %s. Frec: %dms%n", ID_SERVIDOR, data.length, codificacionActual, frecuenciaMs);
	            }
	            
	            // La pausa siempre se ejecuta, esté activo o no.
	            Thread.sleep(frecuenciaMs);
	        }
	        


	    } catch (InterruptedException e) {
	        System.out.println(ID_SERVIDOR + " hilo de distribución interrumpido. Saliendo...");
	        Thread.currentThread().interrupt(); // Restaurar el estado de interrupción
	    } catch (Exception e) {
	       
	        if (!broadcastSocket.isClosed()) {
	            System.err.println(ID_SERVIDOR + " error en el hilo de distribución: " + e.getMessage());
	        }
	    }
	}

	private void recibirControl() {
		try {
			byte[] buffer = new byte[1024];
			while (!Thread.currentThread().isInterrupted()) {
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				unicastSocket.receive(packet);
				final byte[] receivedDataCopy = Arrays.copyOf(packet.getData(), packet.getLength());
				final InetAddress clienteAddress = packet.getAddress();
				final int clientePort = packet.getPort();
				if (!controlThreadPool.isShutdown()) {
					controlThreadPool.submit(() -> procesarPaqueteControl(receivedDataCopy, clienteAddress, clientePort));
				}
			}
		} catch (SocketException e) {
			System.out.println(ID_SERVIDOR + " socket de control cerrado. Saliendo...");
		} catch (Exception e) {
			if (!controlThreadPool.isShutdown()) {
				System.err.println(ID_SERVIDOR + " Error fatal en control: " + e.getMessage());
			}
		}
	}

	private void procesarPaqueteControl(byte[] data, InetAddress clienteAddress, int clientePort) {
		String response;
		try {
			String mensajeCompleto = new String(data).trim();
			int indiceSaltoDeLinea = mensajeCompleto.indexOf('\n');
			if (mensajeCompleto.isEmpty() || indiceSaltoDeLinea == -1) {
				throw new IllegalArgumentException("Paquete de control mal formado (sin cabecera).");
			}
			String codificacion = mensajeCompleto.substring(0, indiceSaltoDeLinea).trim();
			String payload = mensajeCompleto.substring(indiceSaltoDeLinea + 1);
			MensajeControl controlMsg = serializer.deserializeControl(payload, codificacion);
			aplicarComando(controlMsg);
			response = ID_SERVIDOR + " ACK: " + controlMsg.getComando() + " aplicado correctamente.";
		} catch (Exception e) {
			response = ID_SERVIDOR + " NACK: " + e.getMessage();
		}

		try {
			byte[] responseData = response.getBytes();
			DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clienteAddress, clientePort);
			unicastSocket.send(responsePacket);
		} catch (IOException e) {
			// Ignorar el error si el socket ya está cerrado durante el apagado
			if (!unicastSocket.isClosed()) {
				System.err.println(ID_SERVIDOR + " Error al enviar respuesta: " + e.getMessage());
			}
		}
	}

	private void aplicarComando(MensajeControl msg) throws IllegalArgumentException {
		String comando = msg.getComando().toUpperCase();
		String valor = msg.getValor();

		switch (comando) {
		case CMD_CAMBIAR_CODIFICACION:
			if ("XML".equalsIgnoreCase(valor) || "JSON".equalsIgnoreCase(valor)) {
				this.codificacionActual = valor.toUpperCase();
				System.out.println(ID_SERVIDOR + ": Codificación cambiada a " + valor);
			} else {
				throw new IllegalArgumentException("Codificación '" + valor + "' no soportada.");
			}
			break;
		case CMD_CAMBIAR_FRECUENCIA:
			try {
				long nuevaFrecuencia = Long.parseLong(valor);
				if (nuevaFrecuencia <= 0) throw new IllegalArgumentException("La frecuencia debe ser positiva.");
				this.frecuenciaMs = nuevaFrecuencia;
				System.out.println(ID_SERVIDOR + ": Frecuencia cambiada a " + valor + "ms");
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Valor '" + valor + "' no es numérico para frecuencia.");
			}
			break;
		case CMD_ACTIVAR:
			this.activo = true;
			System.out.println(ID_SERVIDOR + ": Envío de datos ACTIVADO");
			break;
		case CMD_DESACTIVAR:
			this.activo = false;
			System.out.println(ID_SERVIDOR + ": Envío de datos DESACTIVADO");
			break;
		case CMD_CAMBIAR_UNIDADES:
			String[] partes = valor.split(":", 2);
			if (partes.length != 2 || partes[0].trim().isEmpty() || partes[1].trim().isEmpty()) {
				throw new IllegalArgumentException("Formato inválido. Use 'variable:unidad'.");
			}
			String nombreVar = partes[0].trim();
			String nuevaUnidad = partes[1].trim();
			if (!unidadesVariables.containsKey(nombreVar)) {
				throw new IllegalArgumentException("Servidor no gestiona la variable '" + nombreVar + "'.");
			}
			unidadesVariables.put(nombreVar, nuevaUnidad);
			System.out.println(ID_SERVIDOR + ": Unidad de '" + nombreVar + "' cambiada a '" + nuevaUnidad + "'.");
			break;
		default:
			throw new IllegalArgumentException("Comando '" + comando + "' desconocido.");
		}
	}

	private synchronized void simularFluctuacionValores() {
	    for (String varNombre : VARIABLES_GESTIONADAS) {
	        double valorActual = valoresBase.get(varNombre);
	        double cambio = (random.nextDouble() - 0.5) * 0.5;
	        valoresBase.put(varNombre, valorActual + cambio);
	    }
	}

	private double convertirValor(double valorBase, String nombreVar, String unidadDestino) {
		if (nombreVar.contains(VAR_TEMPERATURA)) {
			return UnitConverter.convertirTemperatura(valorBase, unidadDestino);
		}
		return valorBase;
	}

	public static void main(String[] args) throws SocketException, UnknownHostException {
		new Servidor(1, 9001);
		new Servidor(2, 9002);
		new Servidor(3, 9003);
	}
}