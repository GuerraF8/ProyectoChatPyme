import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import javax.swing.*;

public class ControlCliente implements Runnable, ActionListener {
    private static final int MAX_REINTENTOS = 3;
    private static final long TIEMPO_RECONEXION = 5000;
    private static final long HEARTBEAT_TIMEOUT = 15000; // Aumentar a 15 segundos
    private long ultimoHeartbeat;
    
    private Socket socket;
    private ObjectInputStream entrada;
    private ObjectOutputStream salida;
    private String nombreUsuario;
    private String contrasena;
    private String perfil;
    private boolean necesitaCambiarContrasena;
    private String area;
    private PanelCliente panel;
    private volatile boolean conectado = false;
    private volatile boolean intentandoReconectar = false;
    private int puertoActual = ServidorChat.PUERTO_PRIMARIO;

    // Constructor que acepta un Socket como parámetro
    public void conectarAlServidor() {
        try {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                   
                }
            }
            
            socket = new Socket("34.176.181.248", puertoActual);
            socket.setKeepAlive(true);
            socket.setSoTimeout(0);
            
            
            salida = new ObjectOutputStream(socket.getOutputStream());
            salida.flush();
            
            entrada = new ObjectInputStream(socket.getInputStream());
            
            conectado = true;
            ultimoHeartbeat = System.currentTimeMillis();
            
            if (panel != null) {
                SwingUtilities.invokeLater(() -> 
                    panel.actualizarEstadoConexion(true));
            }
            
            if (nombreUsuario != null) {
                salida.writeObject("/login " + nombreUsuario + " " + contrasena);
                salida.flush();
            }
        } catch (IOException e) {
            System.out.println("Error al conectar con el servidor en puerto " + puertoActual + ": " + e.getMessage());
            conectado = false;
            throw new RuntimeException(e);
        }
    }

    private void manejarErrorConexion() {
        conectado = false;
        if (!intentandoReconectar) {
            intentandoReconectar = true;
            if (panel != null) {
                SwingUtilities.invokeLater(() -> 
                    panel.actualizarEstadoConexion(false));
            }
            
            new Thread(() -> {
                while (!conectado && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(TIEMPO_RECONEXION);
                        
                        // Intentar ambos puertos alternadamente
                        for (int puerto : new int[]{ServidorChat.PUERTO_SECUNDARIO, ServidorChat.PUERTO_PRIMARIO}) {
                            try {
                                System.out.println("Intentando conectar al puerto " + puerto);
                                puertoActual = puerto;
                                conectarAlServidor();
                                if (conectado) {
                                    System.out.println("Conexión establecida con el puerto " + puerto);
                                    break;
                                }
                            } catch (Exception e) {
                                System.out.println("Error al conectar con el puerto " + puerto + ": " + e.getMessage());
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                intentandoReconectar = false;
            }).start();
        }
    }

    public ControlCliente(Socket socket) {
        try {
            this.socket = socket;
            salida = new ObjectOutputStream(socket.getOutputStream());
            entrada = new ObjectInputStream(socket.getInputStream());
            conectado = true; // Establecer conectado = true en el constructor

            realizarLogin();

            if (necesitaCambiarContrasena) {
                cambiarContrasena();
            }

            creaYVisualizaVentana();
            panel.addActionListener(this);

            Thread hilo = new Thread(this);
            hilo.start();

        } catch (Exception e) {
            e.printStackTrace();
            conectado = false;
        }
    }

    private void realizarLogin() throws IOException, ClassNotFoundException {
        boolean autenticado = false;
        while (!autenticado) {
            nombreUsuario = JOptionPane.showInputDialog("Ingrese su nombre de usuario:");
            contrasena = JOptionPane.showInputDialog("Ingrese su contraseña:");

            if (nombreUsuario == null || contrasena == null) {
                System.exit(0); // Si el usuario cancela el login
            }

            // Enviar credenciales al servidor
            salida.writeObject("/login " + nombreUsuario + " " + contrasena);
            System.out.println("Enviando credenciales: " + nombreUsuario + " " + contrasena); // Debug statement

            // Esperar respuesta del servidor
            Object respuestaObj = entrada.readObject();
            if (respuestaObj instanceof String) {
                String respuesta = (String) respuestaObj;
                System.out.println("Respuesta del servidor: " + respuesta); // Debug statement
                if (respuesta.startsWith("/login_ok")) {
                    autenticado = true;
                    String[] partes = respuesta.split(" ");
                    perfil = partes[1];
                    necesitaCambiarContrasena = Boolean.parseBoolean(partes[2]);
                    if (perfil.equals("Administrativo") && partes.length > 3) {
                        area = partes[3];
                    }
                    System.out.println("Autenticación exitosa: " + nombreUsuario); // Debug statement
                } else {
                    JOptionPane.showMessageDialog(null, "Credenciales incorrectas. Intente nuevamente.");
                    System.out.println("Credenciales incorrectas: " + nombreUsuario); // Debug statement
                }
            }
        }
    }

    private void cambiarContrasena() throws IOException, ClassNotFoundException {
        String nuevaContrasena = JOptionPane
                .showInputDialog("Debe cambiar su contraseña. Ingrese la nueva contraseña:");
        if (nuevaContrasena == null) {
            System.exit(0); // Si el usuario cancela el cambio de contraseña
        }
        salida.writeObject("/cambiar_contrasena " + nuevaContrasena);

        Object respuestaObj = entrada.readObject();
        if (respuestaObj instanceof String) {
            String respuesta = (String) respuestaObj;
            if (respuesta.equals("/contrasena_cambiada")) {
                this.contrasena = nuevaContrasena; // Actualizar la contraseña almacenada
                necesitaCambiarContrasena = false;
                JOptionPane.showMessageDialog(null, "Contraseña cambiada exitosamente.");
            } else {
                JOptionPane.showMessageDialog(null, "Error al cambiar la contraseña.");
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent evento) {
        if (!conectado) {
            JOptionPane.showMessageDialog(panel.getVentana(), 
                "No hay conexión con el servidor. El mensaje se enviará cuando se restablezca la conexión.",
                "Sin conexión",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String sala = panel.getSalaActual();
            Mensaje mensaje = panel.getMensaje();

            if (mensaje.getTexto() != null && !mensaje.getTexto().isEmpty()) {
                synchronized(salida) {
                    try {
                        if (sala.equals("Urgencia")) {
                            mensaje = new Mensaje("/mensaje_urgencia " + mensaje.getTexto(), mensaje.getAtributos());
                        } else if (sala.equals("Medicos")) {
                            mensaje = new Mensaje("/mensaje_sala Medicos " + mensaje.getTexto(), mensaje.getAtributos());
                        } else if (sala.equals("Enviar a Medico")) {
                            String destinatario = JOptionPane.showInputDialog("Ingrese el nombre de usuario del Medico:");
                            if (destinatario != null && !destinatario.isEmpty()) {
                                mensaje = new Mensaje("/mensaje_privado " + destinatario + " " + mensaje.getTexto(), 
                                    mensaje.getAtributos());
                            } else {
                                return;
                            }
                        } else {
                            mensaje = new Mensaje("/mensaje_sala " + sala + " " + mensaje.getTexto(), 
                                mensaje.getAtributos());
                        }
                        salida.writeObject(mensaje);
                        salida.flush();
                    } catch (IOException e) {
                        manejarErrorConexion();
                        throw e; // Propagar la excepción para reintentar el envío después
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            ultimoHeartbeat = System.currentTimeMillis();
            while (true) {
                try {
                    if (System.currentTimeMillis() - ultimoHeartbeat > HEARTBEAT_TIMEOUT && conectado) {
                        // No se ha recibido heartbeat en el tiempo establecido
                        manejarErrorConexion();
                        continue;
                    }

                    Object obj = entrada.readObject();
                    if (obj instanceof String) {
                        String mensaje = (String) obj;
                        if (mensaje.equals("/heartbeat")) {
                            ultimoHeartbeat = System.currentTimeMillis();
                            conectado = true;
                            continue;
                        }
                        if (mensaje.startsWith("/admin_usuario_creado")) {
                            JOptionPane.showMessageDialog(panel.getVentana(), "Usuario creado exitosamente.");
                        } else if (mensaje.startsWith("/admin_usuario_existente")) {
                            JOptionPane.showMessageDialog(panel.getVentana(), "El nombre de usuario ya existe.", "Error",
                                    JOptionPane.ERROR_MESSAGE);
                        } else if (mensaje.startsWith("/admin_contrasena_reiniciada")) {
                            JOptionPane.showMessageDialog(panel.getVentana(), "Contraseña reiniciada exitosamente.");
                        } else if (mensaje.startsWith("/admin_usuario_no_existente")) {
                            JOptionPane.showMessageDialog(panel.getVentana(), "El usuario no existe.", "Error",
                                    JOptionPane.ERROR_MESSAGE);
                        } else if (mensaje.startsWith("/admin_lista_usuarios ")) {
                            String usuariosStr = mensaje.substring(21);
                            String[] usuariosArray = usuariosStr.split(";");
                            StringBuilder sb = new StringBuilder();
                            sb.append("Usuarios Registrados:\n");
                            for (String usuarioInfo : usuariosArray) {
                                if (!usuarioInfo.isEmpty()) {
                                    String[] info = usuarioInfo.split(",");
                                    sb.append("- ").append(info[0]).append(" (").append(info[1]).append(")\n");
                                }
                            }
                            JTextArea areaTexto = new JTextArea(sb.toString());
                            areaTexto.setEditable(false);
                            JScrollPane scrollPane = new JScrollPane(areaTexto);
        
                            JOptionPane.showMessageDialog(panel.getVentana(), scrollPane, "Lista de Usuarios",
                                    JOptionPane.INFORMATION_MESSAGE);
                        } else if (mensaje.startsWith("/admin_estadisticas ")) {
                            String estadisticas = mensaje.substring(19);
                            JTextArea areaTexto = new JTextArea(estadisticas);
                            areaTexto.setEditable(false);
                            JScrollPane scrollPane = new JScrollPane(areaTexto);
        
                            JOptionPane.showMessageDialog(panel.getVentana(), scrollPane, "Estadísticas de Usuario",
                                    JOptionPane.INFORMATION_MESSAGE);
                        } else if (mensaje.equals("/preguntar_historial")) {
                            int opcion = JOptionPane.showConfirmDialog(panel.getVentana(),
                                    "¿Desea cargar su historial de conversaciones?", "Historial",
                                    JOptionPane.YES_NO_OPTION);
                            if (opcion == JOptionPane.YES_OPTION) {
                                salida.writeObject("/cargar_historial");
                            } else {
                                salida.writeObject("/no_cargar_historial");
                            }
                        } else if (mensaje.startsWith("/lista_usuarios_conectados")) {
                            String listaUsuarios = mensaje.substring(25).trim();
                            if (listaUsuarios.startsWith("s ")) {
                                listaUsuarios = listaUsuarios.substring(2);
                            }
                            if (listaUsuarios.isEmpty()) {
                                listaUsuarios = "Ninguno";
                            }
                            JTextArea areaTexto = new JTextArea(listaUsuarios);
                            areaTexto.setEditable(false);
                            JScrollPane scrollPane = new JScrollPane(areaTexto);
                        
                            JOptionPane.showMessageDialog(panel.getVentana(), scrollPane, "Usuarios Conectados",
                                    JOptionPane.INFORMATION_MESSAGE);
                        }else if (mensaje.equals("/limpiar_chat")) {
                            panel.limpiarChat();
                        } else if (mensaje.startsWith("[Sistema] ")) {
                            // Mostrar notificación del sistema
                            JOptionPane.showMessageDialog(panel.getVentana(), mensaje.substring(10), "Notificación",
                                    JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            panel.addTexto(mensaje, null);
                        }
                    } else if (obj instanceof Mensaje) {
                        Mensaje mensaje = (Mensaje) obj;
                        System.out.println("Mensaje objeto recibido: " + mensaje.getTexto()); // Debug
                        panel.addTexto(mensaje.getTexto(), mensaje.getAtributos());
                    }
                    
                } catch (IOException e) {
                    if (conectado) {
                        manejarErrorConexion();
                    }
                    if (!conectado) {
                        Thread.sleep(1000); 
                    }
                }
        }
    } catch (Exception e) {
            e.printStackTrace();
        }
    }


    
    private void creaYVisualizaVentana() {
        JFrame ventana = new JFrame("Usuario: " + nombreUsuario + " (" + perfil + ")");
        ventana.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        panel = new PanelCliente(ventana.getContentPane(), nombreUsuario, perfil, this, ventana);
        ventana.setSize(800, 600);
        ventana.setVisible(true);
    }

    // Método para abrir la interfaz de administración
    public void abrirInterfazAdministracion() {
        new InterfazAdministracion(entrada, salida, panel.getVentana());
    }

    // Método para enviar mensajes al servidor
    public void enviarMensajeAlServidor(String mensaje) {
        try {
            salida.writeObject(mensaje);
            salida.flush(); // Aseguramos que los datos se envíen inmediatamente
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getArea() {
        return area;
    }

    public boolean estaConectado() {
        return conectado;
    }

}
