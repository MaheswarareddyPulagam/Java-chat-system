import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    private ExecutorService pool = Executors.newCachedThreadPool();
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final PrintWriter historyWriter;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ChatServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        historyWriter = new PrintWriter(new BufferedWriter(new FileWriter("chat_history.txt", true)), true);
    }

    public ChatServer() throws IOException { this(PORT); }

    public void start() {
        System.out.println("Server started on port " + serverSocket.getLocalPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("Shutting down server...");
                historyWriter.close();
                pool.shutdownNow();
                serverSocket.close();
            } catch (IOException e) { /* ignore */ }
        }));

        try {
            while (true) {
                Socket socket = serverSocket.accept();
                pool.execute(new ClientHandler(this, socket));
            }
        } catch (IOException e) {
            System.out.println("Server stopped: " + e.getMessage());
        }
    }

    public boolean addClient(String username, ClientHandler handler) {
        if (clients.putIfAbsent(username, handler) == null) {
            broadcast("Server", username + " has joined the chat.");
            return true;
        } else {
            return false;
        }
    }

    public void removeClient(String username) {
        if (username != null && clients.remove(username) != null) {
            broadcast("Server", username + " has left the chat.");
        }
    }

    public void broadcast(String sender, String message) {
        String formatted = formatMsg(sender, message);
        for (ClientHandler h : clients.values()) h.send(formatted);
        writeHistory(formatted);
        System.out.println(formatted);
    }

    public void privateMessage(String from, String to, String message) {
        ClientHandler target = clients.get(to);
        String formatted = formatMsg(from + " -> " + to, message);
        if (target != null) {
            target.send(formatted);
            ClientHandler sender = clients.get(from);
            if (sender != null) sender.send(formatted);
            writeHistory(formatted);
        } else {
            ClientHandler sender = clients.get(from);
            if (sender != null) sender.send("Server: user '" + to + "' not found");
        }
    }

    public String getUserList() {
        return String.join(", ", clients.keySet());
    }

    private String formatMsg(String sender, String message) {
        return LocalDateTime.now().format(fmt) + " [" + sender + "]: " + message;
    }

    private synchronized void writeHistory(String line) {
        historyWriter.println(line);
    }

    public static void main(String[] args) throws IOException {
        int port = PORT;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        ChatServer server = new ChatServer(port);
        server.start();
    }
}
