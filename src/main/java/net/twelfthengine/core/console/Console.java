// net/twelfthengine/console/Console.java
package net.twelfthengine.core.console;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.text.*;

public class Console extends JFrame {
  private static final Map<String, Consumer<String[]>> COMMANDS = new ConcurrentHashMap<>();
  private JTextPane outputPane;
  private JTextField inputField;
  private JScrollPane scrollPane;
  private StyledDocument document;

  // Style constants for different log levels
  private static final Color INFO_COLOR = new Color(100, 200, 255); // Light Blue
  private static final Color WARNING_COLOR = new Color(255, 200, 100); // Orange
  private static final Color ERROR_COLOR = new Color(255, 100, 100); // Light Red
  private static final Color DEBUG_COLOR = new Color(180, 255, 180); // Light Green
  private static final Color DEFAULT_COLOR = Color.WHITE;
  private static final Color INPUT_COLOR = new Color(220, 220, 220); // Light Gray

  private static final Font MODERN_FONT = new Font("Consolas", Font.PLAIN, 13);
  private static final Font INPUT_FONT = new Font("Segoe UI", Font.PLAIN, 13);

  private static Console instance;

  static {
    // Register default commands
    registerCommand(
        "help",
        args -> getInstance().printColored("Available commands: help, clear, exit", DEFAULT_COLOR));
    registerCommand("clear", args -> getInstance().clear());
    registerCommand("exit", args -> System.exit(0));
  }

  private Console() {
    super("12th Engine Console");
    initializeComponents();
    setupLayout();
    setupStyles();
    setupEventHandlers();

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    setSize(900, 700);
    setLocationRelativeTo(null);

    printColored("12th Engine Console Initialized", DEFAULT_COLOR);
    printColored("Type 'help' for available commands", DEFAULT_COLOR);
  }

  public static Console getInstance() {
    if (instance == null) {
      instance = new Console();
    }
    return instance;
  }

  private void initializeComponents() {
    outputPane = new JTextPane();
    outputPane.setEditable(false);
    outputPane.setFont(MODERN_FONT);
    outputPane.setBackground(new Color(30, 30, 30)); // Dark background
    document = outputPane.getStyledDocument();

    scrollPane = new JScrollPane(outputPane);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());

    inputField = new JTextField();
    inputField.setFont(INPUT_FONT);
    inputField.setBackground(new Color(50, 50, 50));
    inputField.setForeground(DEFAULT_COLOR);
    inputField.setCaretColor(Color.WHITE);
  }

  private void setupLayout() {
    setLayout(new BorderLayout());
    add(scrollPane, BorderLayout.CENTER);

    JPanel inputPanel = new JPanel(new BorderLayout());
    inputPanel.setBackground(new Color(40, 40, 40));
    inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    JLabel promptLabel = new JLabel(" > ");
    promptLabel.setFont(INPUT_FONT);
    promptLabel.setForeground(new Color(150, 150, 150));

    inputPanel.add(promptLabel, BorderLayout.WEST);
    inputPanel.add(inputField, BorderLayout.CENTER);
    add(inputPanel, BorderLayout.SOUTH);
  }

  private void setupStyles() {
    // Setup look and feel
    try {
      UIManager.setLookAndFeel(UIManager.getLookAndFeel());
    } catch (Exception e) {
      // Fallback to default
    }

    // Set window icon if you have one
    // setIconImage(Toolkit.getDefaultToolkit().getImage("path/to/icon.png"));
  }

  private void setupEventHandlers() {
    inputField.addActionListener(
        e -> {
          processCommand(inputField.getText());
          inputField.setText("");
        });
  }

  private void processCommand(String commandLine) {
    if (commandLine.trim().isEmpty()) return;

    appendToDocument("> " + commandLine + "\n", INPUT_COLOR);

    String[] parts = commandLine.trim().split("\\s+");
    String commandName = parts[0].toLowerCase();
    String[] args = new String[parts.length - 1];
    System.arraycopy(parts, 1, args, 0, args.length);

    Consumer<String[]> handler = COMMANDS.get(commandName);
    if (handler != null) {
      try {
        handler.accept(args);
      } catch (Exception e) {
        printError("Error executing command '" + commandName + "': " + e.getMessage());
        e.printStackTrace();
      }
    } else {
      printError("Unknown command: " + commandName);
    }
  }

  private void appendToDocument(String text, Color color) {
    SwingUtilities.invokeLater(
        () -> {
          try {
            SimpleAttributeSet style = new SimpleAttributeSet();
            StyleConstants.setForeground(style, color);
            StyleConstants.setFontFamily(style, MODERN_FONT.getFamily());
            StyleConstants.setFontSize(style, MODERN_FONT.getSize());

            document.insertString(document.getLength(), text, style);
            outputPane.setCaretPosition(document.getLength());
          } catch (BadLocationException e) {
            e.printStackTrace();
          }
        });
  }

  public void print(String message) {
    String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    appendToDocument("[" + timestamp + "] " + message + "\n", DEFAULT_COLOR);
  }

  public void printColored(String message, Color color) {
    String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    appendToDocument("[" + timestamp + "] " + message + "\n", color);
  }

  public void printInfo(String message) {
    String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    appendToDocument("[" + timestamp + "] [INFO] " + message + "\n", INFO_COLOR);
  }

  public void printWarning(String message) {
    String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    appendToDocument("[" + timestamp + "] [WARN] " + message + "\n", WARNING_COLOR);
  }

  public void printError(String message) {
    String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    appendToDocument("[" + timestamp + "] [ERROR] " + message + "\n", ERROR_COLOR);
  }

  public void printDebug(String message) {
    String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    appendToDocument("[" + timestamp + "] [DEBUG] " + message + "\n", DEBUG_COLOR);
  }

  public void clear() {
    SwingUtilities.invokeLater(
        () -> {
          try {
            document.remove(0, document.getLength());
          } catch (BadLocationException e) {
            e.printStackTrace();
          }
        });
  }

  public static void registerCommand(String name, Consumer<String[]> handler) {
    COMMANDS.put(name.toLowerCase(), handler);
  }

  public static void unregisterCommand(String name) {
    COMMANDS.remove(name.toLowerCase());
  }

  public void showConsole() {
    SwingUtilities.invokeLater(
        () -> {
          setVisible(true);
          toFront();
          requestFocus();
          inputField.requestFocusInWindow();
        });
  }

  public void hideConsole() {
    SwingUtilities.invokeLater(
        () -> {
          setVisible(false);
        });
  }

  public boolean isConsoleVisible() {
    return isVisible();
  }
}
