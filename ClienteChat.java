import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

public class ClienteChat {
    private Socket socket;
    private ControlCliente control;

    public static void main(String[] args) {
        new ClienteChat();
    }

    public ClienteChat() {
        iniciarCliente();
    }

    private void iniciarCliente() {
        try {
            boolean conectado = false;
            
            // Primero intentar conectar al servidor secundario
            if (servidorAceptaClientes(ServidorChat.PUERTO_SECUNDARIO)) {
                try {
                    socket = new Socket("34.176.181.248", ServidorChat.PUERTO_SECUNDARIO);
                    conectado = true;
                    System.out.println("Conectado al servidor secundario");
                } catch (Exception e) {
                    System.out.println("No se pudo conectar al servidor secundario");
                }
            }

            // Si no hay conexión al secundario, intentar con el primario
            if (!conectado && servidorAceptaClientes(ServidorChat.PUERTO_PRIMARIO)) {
                try {
                    socket = new Socket("34.176.181.248", ServidorChat.PUERTO_PRIMARIO);
                    conectado = true;
                    System.out.println("Conectado al servidor primario");
                } catch (Exception e) {
                    System.out.println("No se pudo conectar al servidor primario");
                }
            }

            if (conectado) {
                control = new ControlCliente(socket);
            } else {
                throw new IOException("No se pudo conectar a ningún servidor.");
            }
        } catch (Exception e) {
            System.out.println("No se pudo conectar a ningún servidor. Reintentando en 5 segundos...");
            try {
                Thread.sleep(5000);
                iniciarCliente();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean servidorAceptaClientes(int puerto) {
        int interServerPort = (puerto == ServidorChat.PUERTO_PRIMARIO) ? 6000 : 6001;
        try {
            // Primero verificar si el puerto principal está disponible
            try (Socket testSocket = new Socket("34.176.181.248", puerto)) {
                // El puerto está abierto, podemos intentar conectar
            } catch (IOException e) {
                // Si el puerto no está disponible, retornar false
                return false;
            }
    
            // Verificar el estado del servidor a través del puerto inter-servidor
            Socket interServerSocket = new Socket("34.176.181.248", interServerPort);
            interServerSocket.setSoTimeout(2000);
            ObjectOutputStream salida = new ObjectOutputStream(interServerSocket.getOutputStream());
            ObjectInputStream entrada = new ObjectInputStream(interServerSocket.getInputStream());
    
            // Verificar si el servidor acepta clientes
            salida.writeObject("/aceptaClientes");
            Object respuesta = entrada.readObject();
            
            if (respuesta instanceof Boolean) {
                boolean aceptaClientes = (Boolean) respuesta;
                
                // Si es el servidor primario, aceptar clientes si no hay en el secundario
                if (puerto == ServidorChat.PUERTO_PRIMARIO) {
                    salida.writeObject("/getClientCount");
                    Object clientCountResp = entrada.readObject();
                    if (clientCountResp instanceof Integer) {
                        int clientCount = (Integer) clientCountResp;
                        return aceptaClientes && clientCount == 0;
                    }
                }
                return aceptaClientes;
            }
            return false;
        } catch (Exception e) {
            // Si hay un error al verificar el servidor inter-servidor, 
            // pero el puerto principal está disponible, permitir la conexión
            return true;
        }
    }
}
