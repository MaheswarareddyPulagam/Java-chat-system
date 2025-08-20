import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private ChatServer server;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    public ClientHandler(ChatServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("Enter username:");
            String name = in.readLine();
            while (name != null && (name.trim().isEmpty() || !server.addClient(name.trim(), this))) {
                out.println("Invalid or taken. Enter username:");
                name = in.readLine();
            }
            if (name == null) return;
            username = name.trim();

            out.println("Welcome " + username + "! Commands: /quit, /list, /pm <user> <message>");
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equalsIgnoreCase("/quit")) break;
                else if (line.equalsIgnoreCase("/list")) out.println("Users: " + server.getUserList());
                else if (line.startsWith("/pm ")) {
                    String[] parts = line.split(" ", 3);
                    if (parts.length >= 3) server.privateMessage(username, parts[1], parts[2]);
                    else out.println("Invalid /pm usage. Use: /pm username message");
                } else {
                    server.broadcast(username, line);
                }
            }
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            try {
                if (username != null) server.removeClient(username);
                socket.close();
            } catch (IOException ignored) { }
        }
    }

    public void send(String msg) {
        out.println(msg);
    }
}
