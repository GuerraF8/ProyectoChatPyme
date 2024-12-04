import java.net.Socket;

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
            // Intentar conectar al servidor primario primero
            try {
                socket = new Socket("localhost", ServidorChat.PUERTO_PRIMARIO);
            } catch (Exception e) {
                // Si falla, intentar con el servidor secundario
                System.out.println("Conectando al servidor secundario...");
                socket = new Socket("localhost", ServidorChat.PUERTO_SECUNDARIO);
            }
            
            control = new ControlCliente(socket);
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
}
