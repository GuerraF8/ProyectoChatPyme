import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class PanelCliente {
    private JScrollPane scroll;
    private JTextPane textPane;
    private JTextField textField;
    private JButton boton;
    private JLabel labelNombre;
    private JList<String> listaSalas;
    private DefaultListModel<String> modeloListaSalas;
    private String salaActual;
    private ControlCliente controlCliente;
    private JFrame ventana;

    
    private Map<String, StyledDocument> documentosSalas = new HashMap<>();

    
    private SimpleAttributeSet atributos;

   
    private JToggleButton btnNegrita;
    private JToggleButton btnItalica;
    private JToggleButton btnSubrayado;

    
    private JButton btnColores;
    private Color colorSeleccionado;

    public PanelCliente(Container contenedor, String nombreUsuario, String perfil, ControlCliente controlCliente,
            JFrame ventana) {
        this.controlCliente = controlCliente;
        this.ventana = ventana;
        atributos = new SimpleAttributeSet();
        colorSeleccionado = Color.BLACK; // Color por defecto

        contenedor.setLayout(new BorderLayout());

       
        JMenuBar menuBar = new JMenuBar();
        ventana.setJMenuBar(menuBar);

        if (perfil.equals("Administrador")) {
            JMenu menuAdmin = new JMenu("Administracion");
            JMenuItem itemAdministracion = new JMenuItem("Abrir Panel de Administracion");
            itemAdministracion.addActionListener(e -> controlCliente.abrirInterfazAdministracion());
            menuAdmin.add(itemAdministracion);
            menuBar.add(menuAdmin);
        }

        JMenu menuOpciones = new JMenu("Opciones");
        JMenuItem itemUsuariosConectados = new JMenuItem("Ver Usuarios Conectados");
        itemUsuariosConectados
                .addActionListener(e -> controlCliente.enviarMensajeAlServidor("/obtener_usuarios_conectados"));
        JMenuItem itemLimpiarChat = new JMenuItem("Limpiar Chat");
        itemLimpiarChat.addActionListener(e -> controlCliente.enviarMensajeAlServidor("/limpiar_chat"));

        menuOpciones.add(itemUsuariosConectados);
        menuOpciones.add(itemLimpiarChat);
        menuBar.add(menuOpciones);

        
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false); 

        btnNegrita = new JToggleButton("N");
        btnNegrita.setFont(new Font("Serif", Font.BOLD, 12));
        btnNegrita.addActionListener(e -> toggleBold());

        btnItalica = new JToggleButton("I");
        btnItalica.setFont(new Font("Serif", Font.ITALIC, 12));
        btnItalica.addActionListener(e -> toggleItalic());

        btnSubrayado = new JToggleButton("S");
        btnSubrayado.setFont(new Font("Serif", Font.PLAIN, 12));
        btnSubrayado.addActionListener(e -> toggleUnderline());

        
        btnColores = new JButton("Colores");
        btnColores.addActionListener(e -> mostrarMenuColores());

        toolBar.add(btnNegrita);
        toolBar.add(btnItalica);
        toolBar.add(btnSubrayado);
        toolBar.addSeparator(new Dimension(20, 0)); // Espacio entre botones
        toolBar.add(btnColores);

        // Mostrar el nombre del usuario y su perfil
        labelNombre = new JLabel("Usuario conectado: " + nombreUsuario + " (" + perfil + ")", SwingConstants.CENTER);
        labelNombre.setFont(new Font("Verdana", Font.BOLD, 16));

        JPanel panelSuperior = new JPanel(new BorderLayout());
        panelSuperior.add(labelNombre, BorderLayout.NORTH);
        panelSuperior.add(toolBar, BorderLayout.SOUTH);

        contenedor.add(panelSuperior, BorderLayout.NORTH);

        // Área de texto para mostrar mensajes (con texto enriquecido)
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font("Verdana", Font.PLAIN, 14));
        scroll = new JScrollPane(textPane);

        // Panel inferior para escribir mensajes
        JPanel panelInferior = new JPanel(new BorderLayout());
        textField = new JTextField(50);
        textField.setFont(new Font("Verdana", Font.PLAIN, 14));
        boton = new JButton("Enviar");
        boton.setFont(new Font("Verdana", Font.PLAIN, 14));
        panelInferior.add(textField, BorderLayout.CENTER);
        panelInferior.add(boton, BorderLayout.EAST);

        // Lista de salas
        modeloListaSalas = new DefaultListModel<>();
        listaSalas = new JList<>(modeloListaSalas);
        listaSalas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaSalas.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                salaActual = listaSalas.getSelectedValue();
                cargarHistorialSala(salaActual);
            }
        });

        // Añadir las salas disponibles según el perfil
        agregarSalasSegunPerfil(perfil);

        // Panel izquierdo con la lista de salas
        JScrollPane scrollSalas = new JScrollPane(listaSalas);
        scrollSalas.setPreferredSize(new Dimension(200, 0));

        
        contenedor.add(scrollSalas, BorderLayout.WEST);
        contenedor.add(scroll, BorderLayout.CENTER);
        contenedor.add(panelInferior, BorderLayout.SOUTH);

        // Seleccionar la primera sala por defecto
        if (!modeloListaSalas.isEmpty()) {
            listaSalas.setSelectedIndex(0);
            salaActual = listaSalas.getSelectedValue();
            cargarHistorialSala(salaActual);
        }
    }

    private void mostrarMenuColores() {
        JPopupMenu menuColores = new JPopupMenu();

        JMenuItem itemRojo = new JMenuItem("Rojo");
        itemRojo.addActionListener(e -> seleccionarColor(Color.RED));
        JMenuItem itemVerde = new JMenuItem("Verde");
        itemVerde.addActionListener(e -> seleccionarColor(Color.GREEN));
        JMenuItem itemAzul = new JMenuItem("Azul");
        itemAzul.addActionListener(e -> seleccionarColor(Color.BLUE));
        JMenuItem itemNegro = new JMenuItem("Negro");
        itemNegro.addActionListener(e -> seleccionarColor(Color.BLACK));

        menuColores.add(itemRojo);
        menuColores.add(itemVerde);
        menuColores.add(itemAzul);
        menuColores.add(itemNegro);

        menuColores.show(btnColores, btnColores.getWidth() / 2, btnColores.getHeight() / 2);
    }

    private void seleccionarColor(Color color) {
        colorSeleccionado = color;
        StyleConstants.setForeground(atributos, colorSeleccionado);
    }

    private void agregarSalasSegunPerfil(String perfil) {
        if (perfil.equals("Medico")) {
            modeloListaSalas.addElement("Medicos");
            modeloListaSalas.addElement("Enviar a Medico"); 
            modeloListaSalas.addElement("Auxiliares"); 
            modeloListaSalas.addElement("Pabellon"); 
            modeloListaSalas.addElement("Admision"); 
            modeloListaSalas.addElement("Examenes"); 
        } else if (perfil.equals("Administrativo")) {
            modeloListaSalas.addElement(controlCliente.getArea()); // Su área
            modeloListaSalas.addElement("Enviar a Medico");
            if (controlCliente.getArea().equals("Auxiliar")) {
                modeloListaSalas.removeElement(controlCliente.getArea());
                modeloListaSalas.removeElement("Enviar a Medico");
            }             
            modeloListaSalas.addElement("Auxiliares"); 
        } else if (perfil.equals("Auxiliar")) {
            modeloListaSalas.addElement("Auxiliares");
        } else if (perfil.equals("Administrador")) {
            modeloListaSalas.addElement("Urgencia");
            modeloListaSalas.addElement("Administradores");
        }
    }

    public void addActionListener(ActionListener accion) {
        textField.addActionListener(accion);
        boton.addActionListener(accion);
    }

    public String getSalaActual() {
        return salaActual;
    }

    public Mensaje getMensaje() {
        String texto = textField.getText();
        textField.setText("");
        Mensaje mensaje = new Mensaje(texto, atributos);

        // Reiniciar atributos
        atributos = new SimpleAttributeSet();
        StyleConstants.setForeground(atributos, colorSeleccionado); // Mantener el color seleccionado

        // Mantener el estado de los botones de formato
        if (btnNegrita.isSelected()) {
            StyleConstants.setBold(atributos, true);
        }
        if (btnItalica.isSelected()) {
            StyleConstants.setItalic(atributos, true);
        }
        if (btnSubrayado.isSelected()) {
            StyleConstants.setUnderline(atributos, true);
        }

        return mensaje;
    }

    public void addTexto(String texto, AttributeSet atributos) {
        try {
            StyledDocument doc = documentosSalas.get(salaActual);
            if (doc == null) {
                doc = new DefaultStyledDocument();
                documentosSalas.put(salaActual, doc);
            }
            doc.insertString(doc.getLength(), texto + "\n", atributos);
            textPane.setDocument(doc);
            textPane.setCaretPosition(doc.getLength());
    
            SwingUtilities.invokeLater(() -> {
                scroll.getVerticalScrollBar().setValue(
                    scroll.getVerticalScrollBar().getMaximum());
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void addTextoASala(String sala, String texto, AttributeSet atributos) {
        try {
            StyledDocument doc = documentosSalas.get(sala);
            if (doc == null) {
                doc = new DefaultStyledDocument();
                documentosSalas.put(sala, doc);
            }
            
            doc.insertString(doc.getLength(), texto + "\n", atributos);
            
            // Si la sala actual es la misma que la sala a la que se añadió el texto
            if (sala.equals(salaActual)) {
                textPane.setDocument(doc);
                textPane.setCaretPosition(doc.getLength());
                
                SwingUtilities.invokeLater(() -> {
                    scroll.getVerticalScrollBar().setValue(
                        scroll.getVerticalScrollBar().getMaximum());
                });
            }
        } catch (Exception e) {
            System.err.println("Error añadiendo texto a sala " + sala + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    public JFrame getVentana() {
        return ventana;
    }

    public void limpiarChat() {
        StyledDocument doc = new DefaultStyledDocument();
        documentosSalas.put(salaActual, doc);
        textPane.setDocument(doc);
    }

    private void cargarHistorialSala(String sala) {
        StyledDocument doc = documentosSalas.get(sala);
        if (doc == null) {
            doc = new DefaultStyledDocument();
            documentosSalas.put(sala, doc);
            
            int opcion = JOptionPane.showConfirmDialog(ventana,
                "¿Desea cargar el historial de la sala " + sala + "?",
                "Cargar Historial",
                JOptionPane.YES_NO_OPTION);
                
            if (opcion == JOptionPane.YES_OPTION) {
                textPane.setDocument(doc);
                String comando = "/cargar_historial " + sala;
                System.out.println("Enviando comando: " + comando); // Para debug
                controlCliente.enviarMensajeAlServidor(comando);
            }
        }
        textPane.setDocument(doc);
    }

    private void toggleBold() {
        boolean isBold = StyleConstants.isBold(atributos);
        StyleConstants.setBold(atributos, !isBold);
    }

    private void toggleItalic() {
        boolean isItalic = StyleConstants.isItalic(atributos);
        StyleConstants.setItalic(atributos, !isItalic);
    }

    private void toggleUnderline() {
        boolean isUnderline = StyleConstants.isUnderline(atributos);
        StyleConstants.setUnderline(atributos, !isUnderline);
    }
}
