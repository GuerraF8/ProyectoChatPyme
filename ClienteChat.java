import java.net.Socket;

public class ClienteChat {
    private Socket socket;
    private ControlCliente control;

    public static void main(String[] args) {
        new ClienteChat();
    }

    public ClienteChat() {
        try {
            socket = new Socket("localhost", 5000);
            control = new ControlCliente(socket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
