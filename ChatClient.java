import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Scanner scanner = new Scanner(System.in);

    public ChatClient(String serverAddress, int port) throws IOException {
        socket = new Socket(serverAddress, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void start() {
        // Thread to read messages from server and print them
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException e) {
                System.out.println("Disconnected from server.");
            }
        }).start();

        // Main thread reads user input and sends to server
        while (true) {
            String input = scanner.nextLine();
            out.println(input);
            if (input.equalsIgnoreCase("/quit")) break;
        }
        close();
    }

    private void close() {
        try { socket.close(); } catch (IOException ignored) { }
    }

    public static void main(String[] args) throws IOException {
        String server = "localhost";
        int port = 12345;
        if (args.length >= 1) server = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        ChatClient client = new ChatClient(server, port);
        client.start();
    }
}
