import java.io.*;
import java.net.Socket;
import java.util.*;
import java.awt.Color;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HiloDeCliente implements Runnable {
    private Socket socket;
    private ObjectInputStream entrada;
    private ObjectOutputStream salida;
    private Usuario usuario;
    private final boolean servidorPrimario;
    // Para el historial de mensajes
    private Map<String, List<Mensaje>> historialMensajes = new HashMap<>();
    private Set<String> usuariosChatPrivado = new HashSet<>();

    public HiloDeCliente(Socket socket, boolean esPrimario) {
        this.socket = socket;
        this.servidorPrimario = esPrimario;
        try {
            salida = new ObjectOutputStream(socket.getOutputStream());
            entrada = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void enviarHeartbeat() throws IOException {
        salida.writeObject("/heartbeat");
        salida.flush();
    }
    public Usuario getUsuario() {
        return usuario;
    }

    public void run() {
        try {
            boolean autenticado = false;
            while (!autenticado && socket.isConnected()) {
                Object obj = entrada.readObject();
                if (obj instanceof String) {
                    String mensajeInicial = (String) obj;
                    if (mensajeInicial.startsWith("/login ") || 
                        mensajeInicial.startsWith("/reconectar ")) {
                        String[] partes = mensajeInicial.split(" ", 3);
                        String nombreUsuario = partes[1];
                        String contrasena = partes[2];

                        // Verificar credenciales
                        Usuario usuarioRegistrado = ServidorChat.usuariosRegistrados.get(nombreUsuario);
                        if (usuarioRegistrado != null && usuarioRegistrado.getContrasena().equals(contrasena)) {
                            usuario = usuarioRegistrado;
                            String respuesta = "/login_ok " + usuario.getPerfil() + " " + usuario.isNecesitaCambiarContrasena();
                            if (usuario.getPerfil().equals("Administrativo")) {
                                respuesta += " " + usuario.getArea();
                            }
                            salida.writeObject(respuesta);
                            autenticado = true;

                            // Si no necesita cambiar contraseña, añadimos al cliente a las listas
                            if (!usuario.isNecesitaCambiarContrasena()) {
                                agregarClienteALasListas();
                            }
                        } else {
                            salida.writeObject("/login_fail");
                        }
                    }
                }
            }

            // Si necesita cambiar la contraseña
            if (usuario.isNecesitaCambiarContrasena()) {
                boolean contrasenaCambiada = false;
                while (!contrasenaCambiada) {
                    Object obj = entrada.readObject();
                    if (obj instanceof String) {
                        String mensaje = (String) obj;
                        if (mensaje.startsWith("/cambiar_contrasena ")) {
                            String nuevaContrasena = mensaje.substring(20);
                            usuario.setContrasena(nuevaContrasena);
                            usuario.setNecesitaCambiarContrasena(false);
                            salida.writeObject("/contrasena_cambiada");
                            contrasenaCambiada = true;

                            // Actualizar el archivo de usuarios
                            ServidorChat.actualizarArchivoUsuarios();

                            // Ahora añadimos al cliente a las listas
                            agregarClienteALasListas();
                        }
                    }
                }
            }

            // Preguntar si desea cargar el historial


            while (true) {
                Object obj = entrada.readObject();
                if (obj instanceof Mensaje) {
                    Mensaje mensaje = (Mensaje) obj;
                    usuario.incrementarMensajesEnviados();
                    procesarMensaje(mensaje);
                } else if (obj instanceof String) {
                    String mensaje = (String) obj;
                    procesarComando(mensaje);
                }
            }
        } catch (Exception e) {
            // e.printStackTrace();
        } finally {
            // Cerrar recursos y eliminar cliente de las listas
            try {
                if (socket != null) socket.close();
                if (entrada != null) entrada.close();
                if (salida != null) salida.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (usuario != null) {
                notificarDesconexion();
                ServidorChat.eliminarCliente(this);
            }
        }
    }

    private void agregarClienteALasListas() {
        synchronized (ServidorChat.hilosClientes) {
            usuario.setHoraConexion(LocalDateTime.now());
            ServidorChat.hilosClientes.add(this);
            ServidorChat.usuariosConectados.put(usuario.getNombreUsuario(), this);

            // Imprimir mensaje de conexión
            System.out.println("Usuario conectado: " + usuario.getNombreUsuario() + " (" + usuario.getPerfil() + ")");
            ServidorChat.listarUsuariosConectados();
        }
    }

    private void procesarMensaje(Mensaje mensaje) {
        // Aquí puedes procesar el mensaje según corresponda
        String texto = mensaje.getTexto();
        if (texto.startsWith("/mensaje_sala ")) {
            String[] partes = texto.split(" ", 3);
            String sala = partes[1];
            Mensaje contenidoMensaje = new Mensaje(partes[2], mensaje.getAtributos());
            enviarMensajeASala(sala, contenidoMensaje);
        } else if (texto.startsWith("/mensaje_privado ")) {
            String[] partes = texto.split(" ", 3);
            String destinatario = partes[1];
            Mensaje contenidoMensaje = new Mensaje(partes[2], mensaje.getAtributos());
            enviarMensajePrivado(destinatario, contenidoMensaje);
        } else if (texto.startsWith("/mensaje_urgencia ")) {
            if (usuario.getPerfil().equals("Administrador")) {
                Mensaje contenidoMensaje = new Mensaje(texto.substring(18), mensaje.getAtributos());
                enviarMensajeUrgencia(contenidoMensaje);
            } else {
                try {
                    salida.writeObject("No tiene permisos para enviar mensajes de urgencia.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void procesarComando(String mensaje) {
        try {
            if (mensaje.startsWith("/admin_crear_usuario ")) {
                // Leer los datos del nuevo usuario
                String[] partes = mensaje.split(" ", 8);
                String nombreUsuarioNuevo = partes[1];
                String nombreCompleto = partes[2].replace("_", " ");
                String rut = partes[3];
                String correo = partes[4].replace("_", " ");
                String contrasena = partes[5];
                String perfil = partes[6];
                String area = partes[7].equals("null") ? null : partes[7].replace("_", " ");

                Usuario nuevoUsuario = new Usuario(nombreUsuarioNuevo, nombreCompleto, rut, correo, contrasena, perfil, true, area);
                boolean exito = ServidorChat.agregarUsuario(nuevoUsuario);
                if (exito) {
                    salida.writeObject("/admin_usuario_creado");
                } else {
                    salida.writeObject("/admin_usuario_existente");
                }
            } else if (mensaje.startsWith("/admin_listar_usuarios")) {
                // Enviar la lista de usuarios como cadena de texto
                List<Usuario> listaUsuarios = ServidorChat.obtenerListaUsuarios();
                StringBuilder sb = new StringBuilder();
                for (Usuario usuario : listaUsuarios) {
                    sb.append(usuario.getNombreUsuario()).append(",").append(usuario.getPerfil());
                    if (usuario.getPerfil().equals("Administrativo")) {
                        sb.append("(").append(usuario.getArea()).append(")");
                    }
                    sb.append(";");
                }
                salida.writeObject("/admin_lista_usuarios " + sb.toString());
            } else if (mensaje.startsWith("/admin_reiniciar_contrasena ")) {
                String[] partes = mensaje.split(" ", 3);
                String nombreUsuarioObjetivo = partes[1];
                String nuevaContrasena = partes[2];
                boolean exito = ServidorChat.reiniciarContrasena(nombreUsuarioObjetivo, nuevaContrasena);
                if (exito) {
                    salida.writeObject("/admin_contrasena_reiniciada");
                } else {
                    salida.writeObject("/admin_usuario_no_existente");
                }
            } else if (mensaje.startsWith("/admin_ver_estadisticas ")) {
                String[] partes = mensaje.split(" ", 4);
                String nombreUsuarioObjetivo = partes[1];
                String fechaInicioStr = partes[2];
                String fechaFinStr = partes[3];

                String estadisticas = ServidorChat.obtenerEstadisticasUsuario(nombreUsuarioObjetivo, fechaInicioStr, fechaFinStr);
                salida.writeObject("/admin_estadisticas " + estadisticas);
            } else if (mensaje.startsWith("/obtener_usuarios_conectados")) {
                enviarUsuariosConectados();
            } else if (mensaje.startsWith("/limpiar_chat")) {
                salida.writeObject("/limpiar_chat");
            } else if (mensaje.startsWith("/cargar_historial")) {
                String sala = mensaje.substring(17);
                cargarHistorialSala(sala);  // Llamamos al método aquí               
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarMensajeASala(String sala, Mensaje mensaje) {
        String timestamp = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "] ";
        Mensaje mensajeParaEnviar = new Mensaje(timestamp + "[" + sala + "] " + usuario.getNombreUsuario() + ": " + mensaje.getTexto(), mensaje.getAtributos());
        synchronized (ServidorChat.hilosClientes) {
            for (HiloDeCliente cliente : ServidorChat.hilosClientes) {
                boolean enviar = false;
                if (sala.equals("Medicos") && usuario.getPerfil().equals("Medico")) {
                    if (cliente.usuario.getPerfil().equals("Medico") && cliente != this) {
                        enviar = true;
                    }
                } else if (sala.equals("Auxiliares")) {
                    if ((usuario.getPerfil().equals("Medico") || usuario.getPerfil().equals("Administrativo") || usuario.getPerfil().equals("Auxiliar"))) {
                        if ((cliente.usuario.getPerfil().equals("Auxiliar") || cliente.usuario.getPerfil().equals("Medico") || cliente.usuario.getPerfil().equals("Administrativo")) && cliente != this) {
                            enviar = true;
                        }
                    }
                } else if (usuario.getPerfil().equals("Administrativo")) {
                    if (sala.equals(usuario.getArea())) {
                        if (cliente.usuario.getPerfil().equals("Administrativo") && cliente.usuario.getArea().equals(usuario.getArea()) && cliente != this) {
                            enviar = true;
                        }
                    }
                } else if (sala.equals("Administradores") && usuario.getPerfil().equals("Administrador")) {
                    if (cliente.usuario.getPerfil().equals("Administrador") && cliente != this) {
                        enviar = true;
                    }
                } else if (sala.equals("Pabellon") || sala.equals("Admision") || sala.equals("Examenes")) {
                    if ((usuario.getPerfil().equals("Medico") || usuario.getPerfil().equals("Administrativo") || usuario.getPerfil().equals("Pabellon")) && cliente != this) {
                        if ((cliente.usuario.getPerfil().equals("Medico") || cliente.usuario.getPerfil().equals("Administrativo") || cliente.usuario.getPerfil().equals("Pabellon"))) {
                            enviar = true;
                        }
                    }
                }
    
                if (enviar) {
                    try {
                        cliente.salida.writeObject(mensajeParaEnviar);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            guardarEnHistorial(sala, mensajeParaEnviar);
        }
    
        // Mostrar el mensaje enviado en el propio chat del usuario
        try {
            salida.writeObject(mensajeParaEnviar);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarMensajePrivado(String destinatario, Mensaje mensaje) {
        synchronized (ServidorChat.usuariosConectados) {
            String timestamp = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "] ";
            HiloDeCliente clienteDestino = ServidorChat.usuariosConectados.get(destinatario);
            if (clienteDestino != null) {
                try {
                    // Crear el mensaje para el destinatario
                    Mensaje mensajeParaDestino = new Mensaje(timestamp + "[Privado de " + usuario.getNombreUsuario() + "] " + mensaje.getTexto(), mensaje.getAtributos());
                    usuariosChatPrivado.add(destinatario);
                    clienteDestino.usuariosChatPrivado.add(usuario.getNombreUsuario());
                    clienteDestino.salida.writeObject(mensajeParaDestino);
                    clienteDestino.guardarEnHistorialPrivado(usuario.getNombreUsuario(), mensajeParaDestino);
                    // Crear el mensaje para el remitente
                    Mensaje mensajeParaRemitente = new Mensaje(timestamp + "[Privado a " + destinatario + "] " + mensaje.getTexto(), mensaje.getAtributos());
                    salida.writeObject(mensajeParaRemitente);
                    guardarEnHistorialPrivado(destinatario, mensajeParaRemitente);
    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    salida.writeObject("El usuario " + destinatario + " no está conectado.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void cargarHistorialSala(String sala) throws IOException {
        System.out.println("Cargando historial para sala: " + sala);
        
        if (sala.trim().equals("Enviar a Medico")) {
            // Modificar el filtro para solo cargar archivos que empiezan con historial_privado_[nombreUsuario]_
            File[] archivosPrivados = new File(".").listFiles((dir, name) -> 
                name.startsWith("historial_privado_" + usuario.getNombreUsuario() + "_"));
                
            if (archivosPrivados != null) {
                for (File archivo : archivosPrivados) {
                    System.out.println("Cargando archivo privado: " + archivo.getName());
                    cargarArchivo(archivo);
                }
            }
        } else {
            String nombreArchivo = "historial_" + sala.replaceAll("\\s+", "") + ".txt";
            File archivoSala = new File(nombreArchivo);
            System.out.println("Intentando cargar archivo: " + nombreArchivo);
            if (archivoSala.exists()) {
                System.out.println("Cargando archivo de sala: " + archivoSala.getName());
                cargarArchivo(archivoSala);
            } else {
                System.out.println("El archivo " + nombreArchivo + " no existe");
            }
        }
    }
    
    private void cargarArchivo(File archivo) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (!linea.trim().isEmpty()) {
                    int estilosIndex = linea.lastIndexOf("[styles:");
                    if (estilosIndex != -1) {
                        String mensaje = linea.substring(0, estilosIndex);
                        String estilosStr = linea.substring(estilosIndex + 8, linea.length() - 1);
                        String[] estilos = estilosStr.split(",");
                        
                        SimpleAttributeSet attrs = new SimpleAttributeSet();
                        // Aplicar estilos de formato
                        if (Boolean.parseBoolean(estilos[0])) StyleConstants.setBold(attrs, true);
                        if (Boolean.parseBoolean(estilos[1])) StyleConstants.setItalic(attrs, true);
                        if (Boolean.parseBoolean(estilos[2])) StyleConstants.setUnderline(attrs, true);
                        
                        // Aplicar color
                        if (estilos.length >= 6) { // Asegurarse de que hay suficientes componentes para el color
                            int r = Integer.parseInt(estilos[3]);
                            int g = Integer.parseInt(estilos[4]);
                            int b = Integer.parseInt(estilos[5]);
                            Color color = new Color(r, g, b);
                            StyleConstants.setForeground(attrs, color);
                        }
                        
                        Mensaje mensajeHistorial = new Mensaje(mensaje, attrs);
                        salida.writeObject(mensajeHistorial);
                        salida.flush();
                    }
                }
            }
        }
    }
    private void guardarEnHistorialPrivado(String otroUsuario, Mensaje mensaje) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("historial_privado_" + usuario.getNombreUsuario() + "_" + otroUsuario + ".txt", true))) {
            SimpleAttributeSet attrs = mensaje.getAtributos();
            // Obtener el color y convertirlo a RGB
            Color color = StyleConstants.getForeground(attrs);
            String colorRGB = color != null ? String.format("%d,%d,%d", color.getRed(), color.getGreen(), color.getBlue()) : "0,0,0";
            
            String estilos = String.format("[styles:%b,%b,%b,%s]",
                StyleConstants.isBold(attrs),
                StyleConstants.isItalic(attrs),
                StyleConstants.isUnderline(attrs),
                colorRGB);
            bw.write(mensaje.getTexto() + estilos);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void enviarMensajeUrgencia(Mensaje mensaje) {
        synchronized (ServidorChat.hilosClientes) {
            String timestamp = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + "] ";
            Mensaje mensajeParaEnviar = new Mensaje(timestamp + "[URGENTE] " + usuario.getNombreUsuario() + ": " + mensaje.getTexto(), mensaje.getAtributos());
            for (HiloDeCliente cliente : ServidorChat.hilosClientes) {
                try {
                    cliente.salida.writeObject(mensajeParaEnviar);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            guardarEnHistorial("Urgencia", mensajeParaEnviar);
        }
    }

    private void guardarEnHistorial(String sala, Mensaje mensaje) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("historial_" + sala + ".txt", true))) {
            SimpleAttributeSet attrs = mensaje.getAtributos();
            // Obtener el color y convertirlo a RGB
            Color color = StyleConstants.getForeground(attrs);
            String colorRGB = color != null ? String.format("%d,%d,%d", color.getRed(), color.getGreen(), color.getBlue()) : "0,0,0";
            
            String estilos = String.format("[styles:%b,%b,%b,%s]",
                StyleConstants.isBold(attrs),
                StyleConstants.isItalic(attrs),
                StyleConstants.isUnderline(attrs),
                colorRGB);
            bw.write(mensaje.getTexto() + estilos);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


private void notificarDesconexion() {
    synchronized (ServidorChat.usuariosConectados) {
        for (String destinatario : usuariosChatPrivado) {
            HiloDeCliente clienteDestino = ServidorChat.usuariosConectados.get(destinatario);
            if (clienteDestino != null) {
                try {
                    clienteDestino.salida.writeObject("[Sistema] " + usuario.getNombreUsuario() + " se ha desconectado del chat privado.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

private void enviarUsuariosConectados() {
    synchronized (ServidorChat.usuariosConectados) {
        StringBuilder sb = new StringBuilder();
        sb.append("Usuarios conectados:\n");
        boolean hayUsuariosConectados = false;
        for (String nombreUsuario : ServidorChat.usuariosConectados.keySet()) {
            if (!nombreUsuario.equals(usuario.getNombreUsuario())) {
                sb.append("- ").append(nombreUsuario).append("\n");
                hayUsuariosConectados = true;
            }
        }
        if (!hayUsuariosConectados) {
            sb.append("Ninguno\n");
        }
        try {
            salida.writeObject("/lista_usuarios_conectados " + sb.toString().trim());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
}
