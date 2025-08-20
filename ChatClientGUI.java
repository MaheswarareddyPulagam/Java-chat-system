import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ChatClientGUI {

    private JFrame frame;
    private JPanel chatPanel;
    private JScrollPane chatScroll;
    private JTextField inputField;
    private JButton sendButton, themeToggle;
    private JLabel typingLabel;
    private DefaultListModel<String> userListModel = new DefaultListModel<>();
    private JList<String> userList;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    private String username;
    private final Map<String, Color> userColors = new HashMap<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");

    private boolean dark = false;
    private Color bgLight = new Color(246, 247, 251);
    private Color bgDark = new Color(26, 27, 30);
    private Color meLight = new Color(208, 241, 255);
    private Color otherLight = new Color(232, 232, 255);
    private Color sysLight = new Color(235, 235, 235);
    private Color meDark = new Color(34, 85, 119);
    private Color otherDark = new Color(54, 54, 79);
    private Color sysDark = new Color(50, 50, 55);
    private Color fgLight = Color.BLACK;
    private Color fgDark = new Color(230, 230, 230);

    private javax.swing.Timer typingTimer;

    private final Set<String> systemMessagesShown = new HashSet<>();

    public ChatClientGUI(String serverIP, int serverPort) {
        askUsername();
        buildUI();
        connect(serverIP, serverPort);
        startReaderThread();
        sendJoinOnce();
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java ChatClientGUI <server-ip> <port>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        SwingUtilities.invokeLater(() -> new ChatClientGUI(host, port));
    }

    private void askUsername() {
        while (true) {
            username = JOptionPane.showInputDialog(null, "Choose a username:", "Login", JOptionPane.PLAIN_MESSAGE);
            if (username == null) System.exit(0);
            username = username.trim();
            if (!username.isEmpty()) break;
        }
        assignColor(username);
    }

    private void buildUI() {
        frame = new JFrame("Chat – " + username);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(new EmptyBorder(8,10,8,10));
        JLabel title = new JLabel("Realtime Chat");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        themeToggle = new JButton("Dark");
        themeToggle.setFocusable(false);
        themeToggle.addActionListener(e -> toggleTheme());
        topBar.add(title, BorderLayout.WEST);
        topBar.add(themeToggle, BorderLayout.EAST);

        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setOpaque(false);

        chatScroll = new JScrollPane(chatPanel);
        chatScroll.setBorder(null);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);

        userList = new JList<>(userListModel);
        userList.setBorder(new EmptyBorder(8,8,8,8));
        userList.setFixedCellHeight(22);
        JScrollPane usersScroll = new JScrollPane(userList);
        usersScroll.setPreferredSize(new Dimension(180, 0));
        usersScroll.setBorder(BorderFactory.createTitledBorder("Online"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, usersScroll);
        split.setResizeWeight(1.0);
        split.setDividerLocation(0.78);

        JPanel inputRow = new JPanel(new BorderLayout(8,0));
        inputRow.setBorder(new EmptyBorder(8,10,10,10));
        inputField = new JTextField();
        sendButton = new JButton("Send");
        sendButton.setFocusable(false);
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendButton, BorderLayout.EAST);

        typingLabel = new JLabel(" ");
        typingLabel.setFont(new Font("Arial", Font.ITALIC, 12));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(typingLabel, BorderLayout.NORTH);
        bottomPanel.add(inputRow, BorderLayout.SOUTH);

        JPanel root = new GradientPanel();
        root.setLayout(new BorderLayout());
        root.add(topBar, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        root.add(bottomPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) { sendTypingSignal(); }
        });

        frame.setContentPane(root);
        applyTheme();
        frame.setSize(900,600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        addSystemLine("Connected UI ready. Type your message and press Enter.");
    }

    private void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            addSystemLine("Connected to " + host + ":" + port);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Cannot connect: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    private void startReaderThread() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleIncoming(line);
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> addSystemLine("Connection closed."));
            }
        }, "reader");
        t.setDaemon(true);
        t.start();
    }

    private void sendJoinOnce() {
        writer.println(username + " has joined the chat.");
        addSystemLine(username + " joined the chat");
        ensureInUserList(username);
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        String full = username + ": " + text;
        writer.println(full);
        inputField.setText("");
        addBubble(username, text, true);
    }

    private void handleIncoming(String raw) {
        if (raw.startsWith("[USERLIST]")) {
            String list = raw.substring("[USERLIST]".length()).trim();
            List<String> users = new ArrayList<>();
            for (String u : list.split(",")) { u = u.trim(); if (!u.isEmpty()) users.add(u);}
            SwingUtilities.invokeLater(() -> {
                userListModel.clear();
                for (String u : users) {
                    userListModel.addElement(u);
                    assignColor(u);
                }
            });
            return;
        }

        // System messages (join/leave)
        if (raw.endsWith("has joined the chat.") || raw.endsWith("has left the chat.")) {
            if (!systemMessagesShown.contains(raw)) {
                systemMessagesShown.add(raw);
                SwingUtilities.invokeLater(() -> addSystemLine(raw));
            }
            return;
        }

        if (raw.startsWith("[SYSTEM]")) {
            String msg = raw.substring("[SYSTEM]".length()).trim();
            if (!systemMessagesShown.contains(msg)) {
                systemMessagesShown.add(msg);
                SwingUtilities.invokeLater(() -> addSystemLine(msg));
            }
            return;
        }

        String sender = raw;
        String msg = "";
        int idx = raw.indexOf(':');
        if (idx > 0) {
            sender = raw.substring(0, idx).trim();
            msg = raw.substring(idx + 1).trim();
        }
        boolean me = sender.equals(username);
        String finalSender = sender;
        String finalMsg = msg.isEmpty() ? raw : msg;
        SwingUtilities.invokeLater(() -> addBubble(finalSender, finalMsg, me));
    }

    private void sendTypingSignal() {
        if (typingTimer != null) typingTimer.stop();
        writer.println("[TYPING]" + username);
        typingLabel.setText(username + " is typing...");
        typingTimer = new javax.swing.Timer(1500, e -> typingLabel.setText(" "));
        typingTimer.setRepeats(false);
        typingTimer.start();
    }

    private void addSystemLine(String text) {
        String time = timeFmt.format(new Date());
        MessageRow row = new MessageRow(text, false, true, bubbleColor(false, true), fg(), time);
        chatPanel.add(row);
        refreshAndAutoScroll();
    }

    private void addBubble(String sender, String text, boolean me) {
        Color bubble = bubbleColor(me, false);
        Color nameColor = userColors.getOrDefault(sender, me ? fg() : fg().darker());
        String time = timeFmt.format(new Date());
        MessageRow row = new MessageRow(sender, text, me, bubble, nameColor, fg(), time);
        chatPanel.add(row);
        refreshAndAutoScroll();
        ensureInUserList(sender);
    }

    private void refreshAndAutoScroll() {
        chatPanel.revalidate();
        chatPanel.repaint();
        JScrollBar v = chatScroll.getVerticalScrollBar();
        v.setValue(v.getMaximum());
    }

    private void ensureInUserList(String u) {
        if (!containsUser(u)) userListModel.addElement(u);
        assignColor(u);
    }

    private void assignColor(String user) {
        userColors.computeIfAbsent(user, k -> {
            int r = (int)(Math.random()*120)+80;
            int g = (int)(Math.random()*120)+80;
            int b = (int)(Math.random()*120)+80;
            return new Color(r,g,b);
        });
    }

    private boolean containsUser(String u) {
        for (int i = 0; i < userListModel.size(); i++) {
            if (userListModel.get(i).equals(u)) return true;
        }
        return false;
    }

    private void toggleTheme() {
        dark = !dark;
        themeToggle.setText(dark ? "Light" : "Dark");
        applyTheme();
    }

    private void applyTheme() {
        Color bg = dark ? bgDark : bgLight;
        frame.getContentPane().setBackground(bg);
        chatPanel.setBackground(new Color(0,0,0,0));
        userList.setBackground(dark ? new Color(36,37,40) : Color.WHITE);
        userList.setForeground(dark ? fgDark : fgLight);
        inputField.setBackground(dark ? new Color(44,45,49) : Color.WHITE);
        inputField.setForeground(dark ? fgDark : fgLight);
        sendButton.setBackground(dark ? new Color(60,63,65) : new Color(235,236,240));
        sendButton.setForeground(dark ? fgDark : fgLight);
        themeToggle.setBackground(sendButton.getBackground());
        themeToggle.setForeground(sendButton.getForeground());
        chatScroll.getViewport().setBackground(new Color(0,0,0,0));
        frame.repaint();
    }

    private Color bubbleColor(boolean me, boolean system) {
        if(system) return dark ? sysDark : sysLight;
        return me ? (dark ? meDark : meLight) : (dark ? otherDark : otherLight);
    }

    private Color fg() { return dark ? fgDark : fgLight; }

    private class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            Color top = dark ? new Color(20,22,25) : new Color(252,253,255);
            Color bottom = dark ? new Color(30,32,36) : new Color(246,247,251);
            g2.setPaint(new GradientPaint(0,0,top,0,h,bottom));
            g2.fillRect(0,0,w,h);
            g2.dispose();
        }
    }

    private class MessageRow extends JPanel {
        // For user messages
        public MessageRow(String sender, String msg, boolean me, Color bubble, Color nameColor, Color fg, String time) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);

            JLabel nameLabel = new JLabel(sender);
            nameLabel.setFont(new Font("Arial", Font.BOLD, 12));
            nameLabel.setForeground(nameColor);

            JLabel msgLabel = new JLabel("<html><body style='width: 250px;'>" + msg + "</body></html>");
            msgLabel.setOpaque(true);
            msgLabel.setBackground(bubble);
            msgLabel.setForeground(fg);
            msgLabel.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(15), new EmptyBorder(6,10,6,10)));

            // Show timestamp on hover only
            msgLabel.setToolTipText(time);

            add(nameLabel);
            add(msgLabel);

            setAlignmentX(me ? RIGHT_ALIGNMENT : LEFT_ALIGNMENT);
            setBorder(new EmptyBorder(2,2,2,2));
        }

        // For system messages
        public MessageRow(String text, boolean me, boolean system, Color bg, Color fg, String time) {
            setLayout(new BorderLayout());
            setOpaque(false);
            JLabel lbl = new JLabel("<html><body style='width: 250px;'>" + text + "  ·  " + time + "</body></html>");
            lbl.setOpaque(!system);
            lbl.setBackground(bg);
            lbl.setForeground(fg);
            lbl.setBorder(new EmptyBorder(8,12,8,12));
            if (!system) lbl.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(15), lbl.getBorder()));
            add(lbl, me ? BorderLayout.EAST : BorderLayout.WEST);
            setBorder(new EmptyBorder(2,2,2,2));
        }
    }

    private class RoundedBorder implements javax.swing.border.Border {
        private int radius;
        public RoundedBorder(int radius) { this.radius = radius; }
        public Insets getBorderInsets(Component c) { return new Insets(radius,radius,radius,radius); }
        public boolean isBorderOpaque() { return false; }
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(Color.GRAY);
            g.drawRoundRect(x, y, width-1, height-1, radius, radius);
        }
    }
}
