import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;

// DocumentFilter para permitir solo números
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.AbstractDocument;

public class InterfazAdministracion {
    private JFrame ventana;
    private JPanel panelPrincipal;
    private JButton botonCrearUsuario;
    private JButton botonListarUsuarios;
    private JButton botonReiniciarContrasena;
    private JButton botonVerEstadisticas;
    private JButton botonRespaldosChat;
    private JButton botonCerrar;
    private ObjectInputStream entrada;
    private ObjectOutputStream salida;
    private JFrame ventanaPadre;
    private ScheduledExecutorService scheduler;

    public InterfazAdministracion(ObjectInputStream entrada, ObjectOutputStream salida, JFrame ventanaPadre) {
        this.entrada = entrada;
        this.salida = salida;
        this.ventanaPadre = ventanaPadre;

        ventana = new JFrame("Panel de Administración");
        ventana.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        ventana.setSize(400, 300);
        ventana.setLocationRelativeTo(null);

        panelPrincipal = new JPanel();
        panelPrincipal.setLayout(new GridLayout(6, 1, 10, 10));

        botonCrearUsuario = new JButton("Crear Usuario");
        botonListarUsuarios = new JButton("Listar Usuarios");
        botonReiniciarContrasena = new JButton("Reiniciar Contraseña");
        botonVerEstadisticas = new JButton("Ver Estadísticas de Usuario");
        botonRespaldosChat = new JButton("Respaldos Chat");
        botonCerrar = new JButton("Cerrar");

        panelPrincipal.add(botonCrearUsuario);
        panelPrincipal.add(botonListarUsuarios);
        panelPrincipal.add(botonReiniciarContrasena);
        panelPrincipal.add(botonVerEstadisticas);
        panelPrincipal.add(botonRespaldosChat);
        panelPrincipal.add(botonCerrar);

        ventana.add(panelPrincipal);

        // Agregar listeners
        botonCrearUsuario.addActionListener(e -> crearUsuario());
        botonListarUsuarios.addActionListener(e -> listarUsuarios());
        botonReiniciarContrasena.addActionListener(e -> reiniciarContrasena());
        botonVerEstadisticas.addActionListener(e -> verEstadisticas());
        botonRespaldosChat.addActionListener(e -> mostrarMenuRespaldosChat());
        botonCerrar.addActionListener(e -> ventana.dispose());

        ventana.setVisible(true);

        
    }

    private void crearUsuario() {
        JTextField nombreUsuarioField = new JTextField();
        JTextField nombreCompletoField = new JTextField();
        JTextField rutField = new JTextField();
        JTextField correoField = new JTextField();
        JTextField contrasenaField = new JTextField();

        // Actualizar perfilComboBox con solo "Médico" y "Administrativo"
        JComboBox<String> perfilComboBox = new JComboBox<>(new String[] { "Medico", "Administrativo" });

        // Inicialmente, áreaField es un JComboBox
        String[] areasAdministrativas = { "Pabellon", "Admision", "Examenes", "Auxiliar" };
        JComboBox<String> areaComboBox = new JComboBox<>(areasAdministrativas);
        areaComboBox.setEnabled(false); // Deshabilitado por defecto

        JPanel panel = new JPanel(new GridLayout(7, 2));
        panel.add(new JLabel("Nombre de Usuario:"));
        panel.add(nombreUsuarioField);
        panel.add(new JLabel("Nombre Completo:"));
        panel.add(nombreCompletoField);
        panel.add(new JLabel("RUT:"));
        panel.add(rutField);
        panel.add(new JLabel("Correo:"));
        panel.add(correoField);
        panel.add(new JLabel("Contraseña:"));
        panel.add(contrasenaField);
        panel.add(new JLabel("Perfil:"));
        panel.add(perfilComboBox);
        panel.add(new JLabel("Área (solo para Administrativos):"));
        panel.add(areaComboBox);

        // Añadir ItemListener para el perfilComboBox
        perfilComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String perfilSeleccionado = (String) perfilComboBox.getSelectedItem();
                if (perfilSeleccionado.equals("Administrativo")) {
                    areaComboBox.setEnabled(true);
                } else {
                    areaComboBox.setEnabled(false);
                }
            }
        });

        int result = JOptionPane.showConfirmDialog(ventana, panel, "Crear Usuario", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String nombreUsuario = nombreUsuarioField.getText().trim();
            String nombreCompleto = nombreCompletoField.getText().trim();
            String rut = rutField.getText().trim();
            String correo = correoField.getText().trim();
            String contrasena = contrasenaField.getText().trim();
            String perfil = (String) perfilComboBox.getSelectedItem();
            String area = areaComboBox.isEnabled() ? (String) areaComboBox.getSelectedItem() : "null";

            if (nombreUsuario.isEmpty() || nombreCompleto.isEmpty() || rut.isEmpty() || correo.isEmpty()
                    || contrasena.isEmpty() || perfil.isEmpty()) {
                JOptionPane.showMessageDialog(ventana, "Todos los campos son obligatorios.", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (perfil.equals("Administrativo") && (area.equals("null") || area.isEmpty())) {
                JOptionPane.showMessageDialog(ventana, "Debe seleccionar un área para usuarios administrativos.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Reemplazar espacios en blanco en los campos
            nombreCompleto = nombreCompleto.replace(" ", "_");
            correo = correo.replace(" ", "_");
            area = area.replace(" ", "_");

            try {
                salida.writeObject("/admin_crear_usuario " + nombreUsuario + " " + nombreCompleto + " " + rut + " "
                        + correo + " " + contrasena + " " + perfil + " " + area);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void listarUsuarios() {
        try {
            salida.writeObject("/admin_listar_usuarios");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void reiniciarContrasena() {
        String nombreUsuario = JOptionPane.showInputDialog(ventana, "Ingrese el nombre de usuario:");
        if (nombreUsuario != null && !nombreUsuario.isEmpty()) {
            String nuevaContrasena = JOptionPane.showInputDialog(ventana, "Ingrese la nueva contraseña:");
            if (nuevaContrasena != null && !nuevaContrasena.isEmpty()) {
                try {
                    salida.writeObject("/admin_reiniciar_contrasena " + nombreUsuario + " " + nuevaContrasena);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                JOptionPane.showMessageDialog(ventana, "Contraseña no válida.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(ventana, "Nombre de usuario no válido.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void verEstadisticas() {
        String nombreUsuario = JOptionPane.showInputDialog(ventana, "Ingrese el nombre de usuario:");
        if (nombreUsuario != null && !nombreUsuario.isEmpty()) {
            try {
                // Crear campos de texto para día, mes y año
                JTextField diaInicioField = new JTextField(2);
                JTextField mesInicioField = new JTextField(2);
                JTextField anioInicioField = new JTextField(4);
    
                JTextField diaFinField = new JTextField(2);
                JTextField mesFinField = new JTextField(2);
                JTextField anioFinField = new JTextField(4);
    
                // Configurar DocumentFilter para permitir solo números
                ((AbstractDocument) diaInicioField.getDocument()).setDocumentFilter(new NumericDocumentFilter());
                ((AbstractDocument) mesInicioField.getDocument()).setDocumentFilter(new NumericDocumentFilter());
                ((AbstractDocument) anioInicioField.getDocument()).setDocumentFilter(new NumericDocumentFilter());
    
                ((AbstractDocument) diaFinField.getDocument()).setDocumentFilter(new NumericDocumentFilter());
                ((AbstractDocument) mesFinField.getDocument()).setDocumentFilter(new NumericDocumentFilter());
                ((AbstractDocument) anioFinField.getDocument()).setDocumentFilter(new NumericDocumentFilter());
    
                // Crear panel para los campos de fecha de inicio
                JPanel panelFechaInicio = new JPanel();
                panelFechaInicio.add(new JLabel("Día:"));
                panelFechaInicio.add(diaInicioField);
                panelFechaInicio.add(Box.createHorizontalStrut(15)); // Espacio entre campos
                panelFechaInicio.add(new JLabel("Mes:"));
                panelFechaInicio.add(mesInicioField);
                panelFechaInicio.add(Box.createHorizontalStrut(15)); // Espacio entre campos
                panelFechaInicio.add(new JLabel("Año:"));
                panelFechaInicio.add(anioInicioField);
    
                // Mostrar diálogo para ingresar la fecha de inicio
                int result = JOptionPane.showConfirmDialog(ventana, panelFechaInicio, "Ingrese la fecha de inicio", JOptionPane.OK_CANCEL_OPTION);
                if (result == JOptionPane.OK_OPTION) {
                    String diaInicio = diaInicioField.getText().length() == 1 ? "0" + diaInicioField.getText() : diaInicioField.getText();
                    String mesInicio = mesInicioField.getText().length() == 1 ? "0" + mesInicioField.getText() : mesInicioField.getText();
                    String fechaInicioStr = diaInicio + "/" + mesInicio + "/" + anioInicioField.getText();
    
                    // Crear panel para los campos de fecha de fin
                    JPanel panelFechaFin = new JPanel();
                    panelFechaFin.add(new JLabel("Día:"));
                    panelFechaFin.add(diaFinField);
                    panelFechaFin.add(Box.createHorizontalStrut(15)); // Espacio entre campos
                    panelFechaFin.add(new JLabel("Mes:"));
                    panelFechaFin.add(mesFinField);
                    panelFechaFin.add(Box.createHorizontalStrut(15)); // Espacio entre campos
                    panelFechaFin.add(new JLabel("Año:"));
                    panelFechaFin.add(anioFinField);
    
                    // Mostrar diálogo para ingresar la fecha de fin
                    result = JOptionPane.showConfirmDialog(ventana, panelFechaFin, "Ingrese la fecha de fin", JOptionPane.OK_CANCEL_OPTION);
                    if (result == JOptionPane.OK_OPTION) {
                        String diaFin = diaFinField.getText().length() == 1 ? "0" + diaFinField.getText() : diaFinField.getText();
                        String mesFin = mesFinField.getText().length() == 1 ? "0" + mesFinField.getText() : mesFinField.getText();
                        String fechaFinStr = diaFin + "/" + mesFin + "/" + anioFinField.getText();
    
                        // Enviar comando al servidor con el intervalo de tiempo
                        salida.writeObject("/admin_ver_estadisticas " + nombreUsuario + " " + fechaInicioStr + " " + fechaFinStr);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } else {
            JOptionPane.showMessageDialog(ventana, "Nombre de usuario no válido.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    // DocumentFilter para permitir solo números
    class NumericDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string.matches("\\d+")) {
                super.insertString(fb, offset, string, attr);
            }
        }
    
        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text.matches("\\d+")) {
                super.replace(fb, offset, length, text, attrs);
            }
        }
    }

    private void mostrarMenuRespaldosChat() {
        JPopupMenu menuRespaldos = new JPopupMenu();

        JMenuItem itemCrearRespaldo = new JMenuItem("Crear respaldo");
        itemCrearRespaldo.addActionListener(e -> crearRespaldo());

        JMenuItem itemCargarRespaldo = new JMenuItem("Cargar respaldo");
        itemCargarRespaldo.addActionListener(e -> cargarRespaldo());

        menuRespaldos.add(itemCrearRespaldo);
        menuRespaldos.add(itemCargarRespaldo);

        menuRespaldos.show(botonRespaldosChat, botonRespaldosChat.getWidth() / 2, botonRespaldosChat.getHeight() / 2);
    }

    private void crearRespaldo() {
        try {
            // Crear un archivo comprimido con todos los historiales
            File[] archivosHistorial = new File(".").listFiles((dir, name) -> name.startsWith("historial_") && name.endsWith(".txt"));
            if (archivosHistorial == null || archivosHistorial.length == 0) {
                JOptionPane.showMessageDialog(ventana, "No hay archivos de historial para respaldar.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int numeroRespaldo = new File(".").listFiles((dir, name) -> name.startsWith("Respaldo_") && name.endsWith(".zip")).length + 1;
            String nombreRespaldo = "Respaldo_" + numeroRespaldo + ".zip";

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(nombreRespaldo))) {
                for (File archivo : archivosHistorial) {
                    try (FileInputStream fis = new FileInputStream(archivo)) {
                        ZipEntry zipEntry = new ZipEntry(archivo.getName());
                        zos.putNextEntry(zipEntry);

                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }
                        zos.closeEntry();
                    }
                }
            }

            JOptionPane.showMessageDialog(ventana, "Respaldo creado exitosamente: " + nombreRespaldo, "Éxito", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(ventana, "Error al crear el respaldo.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }



    private void cargarRespaldo() {
        try {
            // Listar todos los archivos de respaldo
            File[] archivosRespaldo = new File(".").listFiles((dir, name) -> name.startsWith("Respaldo_") && name.endsWith(".zip"));
            if (archivosRespaldo == null || archivosRespaldo.length == 0) {
                JOptionPane.showMessageDialog(ventana, "No hay archivos de respaldo disponibles.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String[] nombresRespaldo = new String[archivosRespaldo.length];
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            for (int i = 0; i < archivosRespaldo.length; i++) {
                nombresRespaldo[i] = archivosRespaldo[i].getName() + " (creado el " + sdf.format(new Date(archivosRespaldo[i].lastModified())) + ")";
            }

            String respaldoSeleccionado = (String) JOptionPane.showInputDialog(ventana, "Seleccione un respaldo para cargar:",
                    "Cargar Respaldo", JOptionPane.QUESTION_MESSAGE, null, nombresRespaldo, nombresRespaldo[0]);
            if (respaldoSeleccionado == null) {
                return;
            }

            String nombreRespaldo = respaldoSeleccionado.split(" ")[0];

            // Cargar el archivo comprimido y reemplazar los historiales existentes
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(nombreRespaldo))) {
                ZipEntry zipEntry;
                while ((zipEntry = zis.getNextEntry()) != null) {
                    File nuevoArchivo = new File(zipEntry.getName());
                    try (FileOutputStream fos = new FileOutputStream(nuevoArchivo)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                    zis.closeEntry();
                }
            }

            JOptionPane.showMessageDialog(ventana, "Respaldo cargado exitosamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(ventana, "Error al cargar el respaldo.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
