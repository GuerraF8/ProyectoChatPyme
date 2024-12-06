import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.io.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;

public class ServidorChat {
    public static final int PUERTO_PRIMARIO = 5000;
    public static final int PUERTO_SECUNDARIO = 5001;
    private static final long HEARTBEAT_INTERVAL = 10000; // 10 segundos
    private static final int INTER_SERVER_PORT_PRIMARIO = 6000;
    private static final int INTER_SERVER_PORT_SECUNDARIO = 6001;
    private volatile boolean aceptandoClientes = true;
    
    public static Map<String, Usuario> usuariosRegistrados = new HashMap<>();
    public static Map<String, HiloDeCliente> usuariosConectados = new HashMap<>();
    public static List<HiloDeCliente> hilosClientes = Collections.synchronizedList(new ArrayList<>());
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ServerSocket socketServidor;
    private boolean esPrimario;
    private volatile boolean ejecutando = true;

    public static void main(String[] args) {
        // Intentar iniciar como servidor primario
        try {
            new ServidorChat(PUERTO_PRIMARIO);
        } catch (IOException e) {
            System.out.println("Puerto primario ocupado, iniciando como servidor secundario...");
            try {
                new ServidorChat(PUERTO_SECUNDARIO);
            } catch (IOException ex) {
                System.out.println("No se puede iniciar el servidor: " + ex.getMessage());
            }
        }
    }

    public ServidorChat(int puerto) throws IOException {
        this.esPrimario = (puerto == PUERTO_PRIMARIO);
        this.socketServidor = new ServerSocket(puerto);
        this.aceptandoClientes = true; // Establecer explícitamente como true

        cargarUsuariosDesdeArchivo();
        iniciarRespaldoAutomatico();
        iniciarServidorHeartbeat();
        iniciarServidorInterServidor();
        verificarOtroServidor();
        iniciarVerificacionPeriodica();
        
        System.out.println("Servidor " + (esPrimario ? "primario" : "secundario") + " iniciado en puerto " + puerto);
        System.out.println("Estado de aceptación de clientes: " + aceptandoClientes);
        
        aceptarClientes();
    }

    private void iniciarServidorInterServidor() {
        int interServerPort = esPrimario ? INTER_SERVER_PORT_PRIMARIO : INTER_SERVER_PORT_SECUNDARIO;
        Thread interServerThread = new Thread(() -> {
            try (ServerSocket interServerSocket = new ServerSocket(interServerPort)) {
                while (ejecutando) {
                    try {
                        Socket socket = interServerSocket.accept();
                        socket.setSoTimeout(2000);
                        ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
                        ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());
                        
                        String request = (String) entrada.readObject();
                        if ("/getClientCount".equals(request)) {
                            salida.writeObject(hilosClientes.size());
                        } else if ("/aceptaClientes".equals(request)) {
                            salida.writeObject(aceptandoClientes);
                        } else if ("/servidorDesconectado".equals(request)) {
                            aceptandoClientes = true;
                        }
                    } catch (Exception e) {
                        // Ignorar errores de timeout
                    }
                }
            } catch (IOException e) {
                System.out.println("Error en servidor inter-servidor: " + e.getMessage());
            }
        });
        interServerThread.setDaemon(true);
        interServerThread.start();
    }

    private void verificarOtroServidor() {
        int otroInterServerPort = esPrimario ? INTER_SERVER_PORT_SECUNDARIO : INTER_SERVER_PORT_PRIMARIO;
        try (Socket interServerSocket = new Socket("localhost", otroInterServerPort);
             ObjectOutputStream salida = new ObjectOutputStream(interServerSocket.getOutputStream());
             ObjectInputStream entrada = new ObjectInputStream(interServerSocket.getInputStream())) {
            
            salida.writeObject("/getClientCount");
            Object respuesta = entrada.readObject();
            if (respuesta instanceof Integer) {
                int clientCount = (Integer) respuesta;
                // Si es servidor secundario y el primario tiene clientes, no aceptar
                if (!esPrimario && clientCount > 0) {
                    aceptandoClientes = false;
                } else {
                    aceptandoClientes = true;
                }
                System.out.println("Otro servidor detectado con " + clientCount + " clientes. Estado de aceptación: " + aceptandoClientes);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("El otro servidor no está disponible. Aceptando conexiones.");
            aceptandoClientes = true;
        }
    }

    private void iniciarVerificacionPeriodica() {
        scheduler.scheduleAtFixedRate(this::verificarOtroServidorPeriodicamente, 0, 10, TimeUnit.SECONDS);
    }

    private void verificarOtroServidorPeriodicamente() {
        try {
            verificarOtroServidor();
        } catch (Exception e) {
            System.out.println("Error en verificación periódica: " + e.getMessage());
            aceptandoClientes = true;
        }
    }

    private void iniciarServidorHeartbeat() {
        Thread heartbeatThread = new Thread(() -> {
            while (ejecutando) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);
                    synchronized(hilosClientes) {
                        enviarHeartbeatAClientes();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void enviarHeartbeatAClientes() {
        synchronized (hilosClientes) {
            for (HiloDeCliente cliente : hilosClientes) {
                try {
                    cliente.enviarHeartbeat();
                } catch (IOException e) {
                    // El cliente probablemente se desconectó
                    eliminarCliente(cliente);
                }
            }
        }
    }

    private void aceptarClientes() {
        Thread acceptThread = new Thread(() -> {
            while (ejecutando) {
                try {
                    if (aceptandoClientes) {
                        Socket cliente = socketServidor.accept();
                        cliente.setSoTimeout(5000); // Timeout de 5 segundos
                        HiloDeCliente nuevoCliente = new HiloDeCliente(cliente, esPrimario);
                        Thread hilo = new Thread(nuevoCliente);
                        hilo.start();
                        System.out.println("Nuevo cliente conectado en puerto " + 
                            (esPrimario ? PUERTO_PRIMARIO : PUERTO_SECUNDARIO));
                    } else {
                        Thread.sleep(1000);
                    }
                } catch (IOException e) {
                    if (ejecutando) {
                        System.err.println("Error aceptando cliente: " + e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public void detener() {
        ejecutando = false;
        aceptandoClientes = false;
        
        // Notificar al otro servidor
        int otroInterServerPort = esPrimario ? INTER_SERVER_PORT_SECUNDARIO : INTER_SERVER_PORT_PRIMARIO;
        try (Socket interServerSocket = new Socket("34.176.181.248", otroInterServerPort);
             ObjectOutputStream salida = new ObjectOutputStream(interServerSocket.getOutputStream())) {
            salida.writeObject("/servidorDesconectado");
        } catch (IOException e) {
            // Ignorar si el otro servidor no está disponible
        }
        
        try {
            socketServidor.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        scheduler.shutdown();
    }

    private void cargarUsuariosDesdeArchivo() {
        File archivoUsuarios = new File("usuarios.txt");
        if (archivoUsuarios.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(archivoUsuarios))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    linea = linea.trim();
                    if (linea.isEmpty()) {
                        continue; 
                    }
                    String[] partes = linea.split(",");
                    if (partes.length < 7) {
                        System.out.println("Línea mal formateada en usuarios.txt: " + linea);
                        continue;
                    }
                    String nombreUsuario = partes[0];
                    String nombreCompleto = partes[1];
                    String rut = partes[2];
                    String correo = partes[3];
                    String contrasena = partes[4];
                    String perfil = partes[5];
                    boolean necesitaCambiarContrasena = Boolean.parseBoolean(partes[6]);
                    String area = partes.length > 7 ? partes[7] : null;

                    Usuario usuario = new Usuario(nombreUsuario, nombreCompleto, rut, correo, contrasena, perfil, necesitaCambiarContrasena, area);
                    usuariosRegistrados.put(nombreUsuario, usuario);
                    System.out.println("Usuario cargado: " + nombreUsuario); // Debug statement
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (usuariosRegistrados.isEmpty()) {
            Usuario admin = new Usuario("admin", "Administrador", "00000000-0", "admin@hospital.cl", "admin", "Administrador", false, null);
            usuariosRegistrados.put("admin", admin);
            guardarUsuarioEnArchivo(admin);
            System.out.println("Usuario admin creado por defecto"); // Debug statement
        }
    }

    public static void guardarUsuarioEnArchivo(Usuario usuario) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("usuarios.txt", true))) {
            bw.write(usuario.getNombreUsuario() + "," + usuario.getNombreCompleto() + "," + usuario.getRut() + ","
                    + usuario.getCorreo() + "," + usuario.getContrasena() + "," + usuario.getPerfil() + ","
                    + usuario.isNecesitaCambiarContrasena()
                    + (usuario.getArea() != null ? "," + usuario.getArea() : ""));
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void actualizarArchivoUsuarios() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("usuarios.txt"))) {
            for (Usuario usuario : usuariosRegistrados.values()) {
                bw.write(usuario.getNombreUsuario() + "," + usuario.getNombreCompleto() + "," + usuario.getRut() + ","
                        + usuario.getCorreo() + "," + usuario.getContrasena() + "," + usuario.getPerfil() + ","
                        + usuario.isNecesitaCambiarContrasena()
                        + (usuario.getArea() != null ? "," + usuario.getArea() : ""));
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    public static void listarUsuariosConectados() {
        synchronized (hilosClientes) {
            StringBuilder sb = new StringBuilder();
            sb.append("\nUsuarios conectados:\n");
            for (HiloDeCliente cliente : hilosClientes) {
                String nombre = cliente.getUsuario().getNombreUsuario();
                String perfil = cliente.getUsuario().getPerfil();
                sb.append(nombre + " (" + perfil + ")\n");
            }
            System.out.print(sb.toString());
        }
    }

    
    public static void eliminarCliente(HiloDeCliente cliente) {
        synchronized (hilosClientes) {
            cliente.getUsuario().setHoraDesconexion(LocalDateTime.now());
            hilosClientes.remove(cliente);
            usuariosConectados.remove(cliente.getUsuario().getNombreUsuario());
            System.out.println("Usuario desconectado: " + cliente.getUsuario().getNombreUsuario());
            listarUsuariosConectados();
        }
    }

    
    public static boolean agregarUsuario(Usuario usuario) {
        if (usuariosRegistrados.containsKey(usuario.getNombreUsuario())) {
            return false; // Usuario ya existe
        } else {
            usuariosRegistrados.put(usuario.getNombreUsuario(), usuario);
            guardarUsuarioEnArchivo(usuario);
            return true;
        }
    }

    
    public static boolean reiniciarContrasena(String nombreUsuario, String nuevaContrasena) {
        Usuario usuario = usuariosRegistrados.get(nombreUsuario);
        if (usuario != null) {
            usuario.setContrasena(nuevaContrasena);
            usuario.setNecesitaCambiarContrasena(true);
            // Actualizar el archivo de usuarios
            actualizarArchivoUsuarios();
            return true;
        } else {
            return false;
        }
    }

    
    public static List<Usuario> obtenerListaUsuarios() {
        return new ArrayList<>(usuariosRegistrados.values());
    }

    private static String formatearDuracion(Duration duracion) {
        long horas = duracion.toHours();
        long minutos = duracion.toMinutes() % 60;
        long segundos = duracion.getSeconds() % 60;
        
        StringBuilder tiempo = new StringBuilder();
        if (horas > 0) {
            tiempo.append(horas).append(horas == 1 ? " hora" : " horas");
            if (minutos > 0 || segundos > 0) tiempo.append(", ");
        }
        if (minutos > 0) {
            tiempo.append(minutos).append(minutos == 1 ? " minuto" : " minutos");
            if (segundos > 0) tiempo.append(", ");
        }
        if (segundos > 0 || (horas == 0 && minutos == 0)) {
            tiempo.append(segundos).append(segundos == 1 ? " segundo" : " segundos");
        }
        
        return tiempo.toString();
    }

    // Método para obtener las estadísticas de un usuario
    public static String obtenerEstadisticasUsuario(String nombreUsuario, String fechaInicioStr, String fechaFinStr) {
        Usuario usuario = usuariosRegistrados.get(nombreUsuario);
        if (usuario != null) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                LocalDate fechaInicio = LocalDate.parse(fechaInicioStr, formatter);
                LocalDate fechaFin = LocalDate.parse(fechaFinStr, formatter);
    
                // Establecer las horas para el inicio y fin del día
                LocalDateTime fechaInicioDateTime = fechaInicio.atStartOfDay();
                LocalDateTime fechaFinDateTime = fechaFin.plusDays(1).atStartOfDay();
    
                // Mapas para contar mensajes por sala y por usuario
                Map<String, Integer> mensajesPorSala = new HashMap<>();
                Map<String, Integer> mensajesPrivados = new HashMap<>();
    
                DateTimeFormatter msgFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                String estadoConexion = "";
                HiloDeCliente clienteActual = usuariosConectados.get(nombreUsuario);
                if (clienteActual != null) {
                    Duration tiempoConectado = Duration.between(usuario.getHoraConexion(), LocalDateTime.now());
                    estadoConexion = String.format("Estado: Conectado, sesión actual (%s)\n\n", 
                        formatearDuracion(tiempoConectado));
                } else {
                    estadoConexion = "Estado: Desconectado\n\n";
                }
    
                // Contar mensajes en salas públicas
                File[] archivosSalas = new File(".").listFiles((dir, name) -> 
                    name.startsWith("historial_") && !name.contains("privado"));
                
                if (archivosSalas != null) {
                    for (File archivo : archivosSalas) {
                        String nombreSala = archivo.getName().substring(10, archivo.getName().length() - 4);
                        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                            String linea;
                            while ((linea = br.readLine()) != null) {
                                if (linea.contains("] " + nombreUsuario + ": ")) {
                                    int finTimestamp = linea.indexOf("]");
                                    String timestampStr = linea.substring(1, finTimestamp);
                                    try {
                                        LocalDateTime timestamp = LocalDateTime.parse(timestampStr, msgFormatter);
                                        if (!timestamp.isBefore(fechaInicioDateTime) && timestamp.isBefore(fechaFinDateTime)) {
                                            mensajesPorSala.merge(nombreSala, 1, Integer::sum);
                                        }
                                    } catch (Exception e) {
                                        System.err.println("Error al parsear fecha: " + timestampStr);
                                    }
                                }
                            }
                        }
                    }
                }
    
                // Contar mensajes privados
                File[] archivosPrivados = new File(".").listFiles((dir, name) -> 
                    name.startsWith("historial_privado_" + nombreUsuario + "_"));
                
                if (archivosPrivados != null) {
                    for (File archivo : archivosPrivados) {
                        String otroUsuario = archivo.getName()
                            .substring(("historial_privado_" + nombreUsuario + "_").length(), 
                                     archivo.getName().length() - 4);
                        
                        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                            String linea;
                            while ((linea = br.readLine()) != null) {
                                if (linea.contains("[Privado a ")) {
                                    int finTimestamp = linea.indexOf("]");
                                    String timestampStr = linea.substring(1, finTimestamp);
                                    try {
                                        LocalDateTime timestamp = LocalDateTime.parse(timestampStr, msgFormatter);
                                        if (!timestamp.isBefore(fechaInicioDateTime) && timestamp.isBefore(fechaFinDateTime)) {
                                            mensajesPrivados.merge(otroUsuario, 1, Integer::sum);
                                        }
                                    } catch (Exception e) {
                                        System.err.println("Error al parsear fecha: " + timestampStr);
                                    }
                                }
                            }
                        }
                    }
                }
    
                // Construir el reporte
                StringBuilder sb = new StringBuilder();
                sb.append("Estadísticas de ").append(nombreUsuario).append(":\n");
                sb.append("Período: ").append(fechaInicioStr).append(" al ").append(fechaFinStr).append("\n");
                sb.append("Perfil: ").append(usuario.getPerfil()).append("\n");
                if (usuario.getPerfil().equals("Administrativo")) {
                    sb.append("Área: ").append(usuario.getArea()).append("\n");
                }
                sb.append(estadoConexion);
    
                int totalMensajes = mensajesPorSala.values().stream().mapToInt(Integer::intValue).sum() +
                                  mensajesPrivados.values().stream().mapToInt(Integer::intValue).sum();
                sb.append("Total de mensajes enviados en el período: ").append(totalMensajes).append("\n\n");
                
                sb.append("Detalle de mensajes:\n");
                mensajesPorSala.forEach((sala, count) -> 
                    sb.append("- En sala ").append(sala.toLowerCase()).append(": ").append(count)
                      .append(count == 1 ? " mensaje\n" : " mensajes\n"));
                
                mensajesPrivados.forEach((user, count) -> 
                    sb.append("- Con ").append(user).append(": ").append(count)
                      .append(count == 1 ? " mensaje\n" : " mensajes\n"));
    
                return sb.toString();
    
            } catch (Exception e) {
                e.printStackTrace();
                return "Error al procesar las fechas. Asegúrese de usar el formato dd/MM/yyyy";
            }
        } else {
            return "El usuario no existe.";
        }
    }

    private void iniciarRespaldoAutomatico() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::crearRespaldoAutoguardado, 0, 1, TimeUnit.HOURS);
    }

    private void crearRespaldoAutoguardado() {
        try {
            // Crear un archivo comprimido con todos los historiales
            File[] archivosHistorial = new File(".").listFiles((dir, name) -> name.startsWith("historial_") && name.endsWith(".txt"));
            if (archivosHistorial == null || archivosHistorial.length == 0) {
                return;
            }

            String nombreRespaldo = "Respaldo_Autoguardado.zip";

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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
