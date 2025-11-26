package Ejecutables;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class ClienteGUI {


    private JFrame frame;
    private JTextArea receivedPacketsArea;
    private JTextField commandInput;
    private JButton sendButton;
    private JButton muteButton;
    private JComboBox<String> parserComboBox;


    private ClienteService clienteService;

    public ClienteGUI() {
       
        try {
            this.clienteService = new ClienteService(this::mostrarTrama);
        } catch (IOException e) {
            e.printStackTrace();
            
            JOptionPane.showMessageDialog(null, "Error al iniciar el socket: " + e.getMessage(), "Error de Red", JOptionPane.ERROR_MESSAGE);
            return;
        }

        
        createUI();


        new Thread(clienteService::recibirDistribucion).start();
    }

    private void createUI() {

        frame = new JFrame("Cliente Swing");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null); // Centrar en pantalla
        frame.setLayout(new BorderLayout(10, 10));


        receivedPacketsArea = new JTextArea();
        receivedPacketsArea.setEditable(false);
        receivedPacketsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(receivedPacketsArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Tramas Recibidas"));

        // --- Panel Inferior para Comandos y Configuración ---
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        JPanel commandPanel = new JPanel(new BorderLayout(10, 0));
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        commandInput = new JTextField();
        sendButton = new JButton("Enviar");

        commandPanel.add(new JLabel("Comando:"), BorderLayout.WEST);
        commandPanel.add(commandInput, BorderLayout.CENTER);
        commandPanel.add(sendButton, BorderLayout.EAST);

        
        muteButton = new JButton("Mute/Unmute");

        
        String[] parserOptions = { "SAX", "DOM" };
        parserComboBox = new JComboBox<>(parserOptions);

        
        configPanel.add(new JLabel("Config:"));
        configPanel.add(muteButton);
        configPanel.add(new JSeparator(SwingConstants.VERTICAL)); // Un separador visual
        configPanel.add(new JLabel("Parser XML:"));
        configPanel.add(parserComboBox);
  

        bottomPanel.add(commandPanel, BorderLayout.NORTH);
        bottomPanel.add(configPanel, BorderLayout.CENTER);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));


       
        sendButton.addActionListener(e -> enviarComando());
        commandInput.addActionListener(e -> enviarComando()); // Para que funcione al pulsar Enter

        muteButton.addActionListener(e -> clienteService.toggleMute());

        
        parserComboBox.addActionListener(e -> {
            // Obtenemos la opción seleccionada
            String selectedParser = (String) parserComboBox.getSelectedItem();

            // Verificamos que no sea nulo antes de enviar
            if (selectedParser != null) {
                clienteService.procesarComando("CONFIG_PARSER " + selectedParser);
            }
        });


        
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    /**
     * Envía el comando escrito en el campo de texto al servicio de red.
     */
    private void enviarComando() {
        String command = commandInput.getText().trim();
        if (!command.isEmpty()) {
            clienteService.procesarComando(command);
            commandInput.setText(""); // Limpiamos el campo después de enviar
        }
    }

    /**
     * Este método es llamado por ClienteService desde un hilo de red.
     * Utiliza SwingUtilities.invokeLater para actualizar la GUI de forma segura
     * en el hilo de eventos de Swing (Event Dispatch Thread).
     */
    private void mostrarTrama(String trama) {
        SwingUtilities.invokeLater(() -> {
            receivedPacketsArea.append(trama + "\n");

            receivedPacketsArea.setCaretPosition(receivedPacketsArea.getDocument().getLength());
        });
    }
}