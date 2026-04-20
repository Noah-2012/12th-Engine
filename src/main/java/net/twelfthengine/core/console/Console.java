package net.twelfthengine.core.console;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.text.*;

public class Console extends JFrame {

  // ─── Log Level ────────────────────────────────────────────────────────────────

  public enum LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    SYSTEM
  }

  // ─── Log Entry ────────────────────────────────────────────────────────────────

  public record LogEntry(LocalTime time, LogLevel level, String message, Color color) {}

  // ─── Colors & Fonts ───────────────────────────────────────────────────────────

  // Background layers
  private static final Color BG_DEEP = new Color(13, 15, 20); // deepest bg
  private static final Color BG_PANEL = new Color(18, 21, 28); // main panel
  private static final Color BG_INPUT = new Color(22, 26, 35); // input area
  private static final Color BG_TOOLBAR = new Color(16, 19, 25); // toolbar
  private static final Color BG_HOVER = new Color(35, 42, 58); // button hover
  private static final Color BG_SELECTED = new Color(42, 52, 72); // selected/active

  // Accent
  private static final Color ACCENT = new Color(82, 170, 255); // electric blue
  private static final Color ACCENT_DIM = new Color(45, 100, 160);

  // Text
  private static final Color TEXT_PRIMARY = new Color(220, 225, 235);
  private static final Color TEXT_SECONDARY = new Color(100, 110, 130);
  private static final Color TEXT_MUTED = new Color(60, 70, 90);

  // Log level colors
  private static final Color C_DEBUG = new Color(110, 160, 110); // muted sage
  private static final Color C_INFO = new Color(82, 170, 255); // electric blue
  private static final Color C_WARN = new Color(240, 180, 60); // amber
  private static final Color C_ERROR = new Color(255, 80, 80); // vivid red
  private static final Color C_SYSTEM = new Color(180, 130, 255); // soft violet
  private static final Color C_INPUT = new Color(160, 170, 190); // cool gray
  private static final Color C_DEFAULT = TEXT_PRIMARY;

  // Border accent colors
  private static final Color BORDER_SUBTLE = new Color(30, 36, 50);
  private static final Color BORDER_ACCENT = new Color(50, 70, 100);

  // Fonts
  private static final Font FONT_MONO = loadFont("JetBrains Mono", "Monospaced", Font.PLAIN, 13);
  private static final Font FONT_MONO_B = loadFont("JetBrains Mono", "Monospaced", Font.PLAIN, 13);
  private static final Font FONT_UI = loadFont("Inter", "Segoe UI", Font.PLAIN, 12);
  private static final Font FONT_UI_SM = loadFont("Inter", "Segoe UI", Font.PLAIN, 11);
  private static final Font FONT_LABEL = loadFont("Inter", "Segoe UI", Font.BOLD, 11);

  private static Font loadFont(String preferred, String fallback, int style, int size) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    for (String name : ge.getAvailableFontFamilyNames()) {
      if (name.equalsIgnoreCase(preferred)) return new Font(preferred, style, size);
    }
    return new Font(fallback, style, size);
  }

  // ─── State ────────────────────────────────────────────────────────────────────

  // ─── Runtime Variables (CVars) ────────────────────────────────────────────────

  private static final Map<String, CVar<?>> CVARS = new ConcurrentHashMap<>();

  private abstract static class CVar<T> {
    final String name;
    final Class<T> type;

    private final java.util.function.Supplier<T> getter;
    private final java.util.function.Consumer<T> setter;

    CVar(
        String name,
        Class<T> type,
        java.util.function.Supplier<T> getter,
        java.util.function.Consumer<T> setter) {

      this.name = name.toLowerCase();
      this.type = type;
      this.getter = getter;
      this.setter = setter;
    }

    abstract T parse(String input) throws Exception;

    void setFromString(String input) throws Exception {
      setter.accept(parse(input));
    }

    T get() {
      return getter.get();
    }

    String typeName() {
      return type.getSimpleName().toLowerCase();
    }
  }

  private static final Map<String, Consumer<String[]>> COMMANDS = new ConcurrentHashMap<>();
  private final List<LogEntry> logHistory = new ArrayList<>();
  private final List<String> cmdHistory = new ArrayList<>();
  private int cmdHistoryIndex = -1;

  // Autocomplete
  private String ghostSuggestion = "";
  private final Color GHOST_COLOR = new Color(120, 130, 150, 120);

  private final java.util.concurrent.ConcurrentLinkedQueue<LogEntry> pendingQueue =
      new java.util.concurrent.ConcurrentLinkedQueue<>();
  private final javax.swing.Timer flushTimer = new javax.swing.Timer(50, e -> flushLogs());

  private LogLevel filterLevel = null; // null = show all
  private String searchQuery = "";

  private static final String LOG_PATH = "logs/engine.log";
  private static final long MAX_SIZE = 50 * 1024 * 1024; // 50 MB limit

  // ─── Components ───────────────────────────────────────────────────────────────

  private JTextPane outputPane;
  private JTextField inputField;
  private JTextField searchField;
  private JScrollPane scrollPane;
  private StyledDocument document;

  // Filter buttons
  private final Map<LogLevel, JToggleButton> filterButtons = new LinkedHashMap<>();

  // Status bar
  private JLabel statusLabel;
  private JLabel logCountLabel;

  private static Console instance;

  // ─── Autocomplete popup ───────────────────────────────
  private JPopupMenu autocompletePopup;
  private JList<String> suggestionList;
  private DefaultListModel<String> suggestionModel;
  private int selectedSuggestionIndex = -1;

  // ─── Static Init ──────────────────────────────────────────────────────────────

  static {
    registerCommand("help", args -> getInstance().showHelp());
    registerCommand("clear", args -> getInstance().clear());
    registerCommand("exit", args -> System.exit(0));
    registerCommand(
        "filter",
        args -> {
          Console c = getInstance();
          if (args.length == 0) {
            c.setFilter(null);
            return;
          }
          try {
            c.setFilter(LogLevel.valueOf(args[0].toUpperCase()));
          } catch (IllegalArgumentException e) {
            c.printError("Unknown level: " + args[0]);
          }
        });
    registerCommand(
        "search",
        args -> {
          Console c = getInstance();
          c.setSearch(args.length == 0 ? "" : String.join(" ", args));
        });
    registerCommand("copy", args -> getInstance().copyAllToClipboard());
    registerCommand("echo", args -> getInstance().print(String.join(" ", args)));
    registerCommand(
        "cvars",
        args -> {
          Console c = getInstance();

          if (CVARS.isEmpty()) {
            c.printSystem("No CVars registered.");
            return;
          }

          c.printSystem("─── CVars ─────────────────────────────────────────");

          CVARS.values().stream()
              .sorted(Comparator.comparing(v -> v.name))
              .forEach(
                  v -> {
                    c.printInfo(
                        String.format("  %-24s = %-10s (%s)", v.name, v.get(), v.typeName()));
                  });

          c.printSystem("────────────────────────────────────────────────────");
        });
  }

  // ─── Constructor ──────────────────────────────────────────────────────────────

  private Console() {
    super("12th Engine — Console");
    wipeLogIfOvergrown();
    applyDarkTitleBar();
    initComponents();
    buildLayout();
    attachHandlers();

    setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            hideConsole();
          }
        });

    setSize(1060, 680);
    setMinimumSize(new Dimension(700, 400));
    setLocationRelativeTo(null);

    printSystem("12th Engine v1.0 — Console ready");
    printSystem("Type 'help' for a list of commands.");
  }

  public static Console getInstance() {
    if (instance == null) instance = new Console();
    return instance;
  }

  public List<LogEntry> getLogHistory() {
    return logHistory;
  }

  public List<String> getCmdHistory() {
    return cmdHistory;
  }

  public Map<String, Consumer<String[]>> getRegisteredCommands() {
    return COMMANDS;
  }

  // ─── UI Construction ──────────────────────────────────────────────────────────

  private void initComponents() {
    // Output pane
    outputPane =
        new JTextPane() {
          @Override
          public boolean getScrollableTracksViewportWidth() {
            return true;
          }
        };
    outputPane.setEditable(false);
    outputPane.setFont(FONT_MONO);
    outputPane.setBackground(BG_PANEL);
    outputPane.setForeground(TEXT_PRIMARY);
    outputPane.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
    document = outputPane.getStyledDocument();

    scrollPane = new JScrollPane(outputPane);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, BORDER_SUBTLE));
    styleScrollBar(scrollPane.getVerticalScrollBar());

    // Input field
    inputField =
        new JTextField() {
          @Override
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (ghostSuggestion.isEmpty()) return;

            String typed = getText();
            if (typed.isEmpty()) return;
            if (!ghostSuggestion.startsWith(typed.toLowerCase())) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setFont(getFont());
            g2.setColor(GHOST_COLOR);

            FontMetrics fm = g2.getFontMetrics();
            int x = fm.stringWidth(typed) + 8;
            int y = getHeight() / 2 + fm.getAscent() / 2 - 2;

            String remainder = ghostSuggestion.substring(typed.length());
            g2.drawString(remainder, x, y);
            g2.dispose();
          }
        };
    inputField.setFont(FONT_MONO);
    inputField.setBackground(BG_INPUT);
    inputField.setForeground(TEXT_PRIMARY);
    inputField.setCaretColor(ACCENT);
    inputField.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
    inputField.setToolTipText("Enter a command…");
    inputField.setFocusTraversalKeysEnabled(false);

    // Search field
    searchField = new JTextField(18);
    searchField.setFont(FONT_UI_SM);
    searchField.setBackground(new Color(25, 30, 42));
    searchField.setForeground(TEXT_PRIMARY);
    searchField.setCaretColor(ACCENT);
    searchField.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_ACCENT, 1, true),
            BorderFactory.createEmptyBorder(3, 8, 3, 8)));
    searchField.putClientProperty("placeholder", "Search logs…");

    // Status bar
    statusLabel = makeLabel("Ready", TEXT_SECONDARY, FONT_UI_SM);
    logCountLabel = makeLabel("0 entries", TEXT_MUTED, FONT_UI_SM);

    initAutocompletePopup();
  }

  private void buildLayout() {
    setBackground(BG_DEEP);
    getRootPane().setBorder(BorderFactory.createLineBorder(BORDER_ACCENT, 1));

    JPanel root = new JPanel(new BorderLayout(0, 0));
    root.setBackground(BG_DEEP);

    root.add(buildToolbar(), BorderLayout.NORTH);
    root.add(scrollPane, BorderLayout.CENTER);
    root.add(buildInputBar(), BorderLayout.SOUTH);

    setContentPane(root);
  }

  private JPanel buildToolbar() {
    JPanel bar = new JPanel(new BorderLayout(10, 0));
    bar.setBackground(BG_TOOLBAR);
    bar.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_SUBTLE),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));

    // Left — title + level filters
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    left.setOpaque(false);

    JLabel titleLabel = makeLabel("@ CONSOLE", ACCENT, FONT_MONO_B);
    left.add(titleLabel);
    left.add(makeSeparator());

    left.add(makeLabel("FILTER:", TEXT_SECONDARY, FONT_LABEL));
    left.add(makeAllFilterButton());
    for (LogLevel lvl : LogLevel.values()) {
      JToggleButton btn = makeFilterButton(lvl);
      filterButtons.put(lvl, btn);
      left.add(btn);
    }

    // Right — search + actions
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    right.setOpaque(false);

    right.add(searchField);
    right.add(makeToolButton("⧉ Copy All", e -> copyAllToClipboard()));
    right.add(makeToolButton("✕ Clear", e -> clear()));

    bar.add(left, BorderLayout.WEST);
    bar.add(right, BorderLayout.EAST);
    return bar;
  }

  private JPanel buildInputBar() {
    JPanel panel = new JPanel(new BorderLayout(0, 0));
    panel.setBackground(BG_INPUT);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_ACCENT),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)));

    // Prompt
    JLabel prompt = makeLabel("  ❯ ", ACCENT, FONT_MONO_B);
    prompt.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

    panel.add(prompt, BorderLayout.WEST);
    panel.add(inputField, BorderLayout.CENTER);
    panel.add(buildStatusBar(), BorderLayout.SOUTH);

    return panel;
  }

  private JPanel buildStatusBar() {
    JPanel bar = new JPanel(new BorderLayout());
    bar.setBackground(BG_DEEP);
    bar.setBorder(BorderFactory.createEmptyBorder(3, 14, 3, 14));

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    left.setOpaque(false);
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    right.setOpaque(false);

    left.add(statusLabel);
    right.add(logCountLabel);
    right.add(makeLabel("12th Engine", TEXT_MUTED, FONT_UI_SM));

    bar.add(left, BorderLayout.WEST);
    bar.add(right, BorderLayout.EAST);
    return bar;
  }

  // ─── Event Handlers ───────────────────────────────────────────────────────────

  private void attachHandlers() {
    // Enter to submit command
    inputField.addActionListener(
        e -> {
          String text = inputField.getText().trim();
          if (!text.isEmpty()) {
            cmdHistory.add(0, text);
            if (cmdHistory.size() > 200) cmdHistory.remove(cmdHistory.size() - 1);
            cmdHistoryIndex = -1;
          }
          processCommand(text);
          inputField.setText("");
        });

    // Up/Down for command history
    inputField.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (autocompletePopup.isVisible()) {

              if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                int i = Math.min(selectedSuggestionIndex + 1, suggestionModel.size() - 1);
                suggestionList.setSelectedIndex(i);
                selectedSuggestionIndex = i;
                e.consume();
                return;
              }

              if (e.getKeyCode() == KeyEvent.VK_UP) {
                int i = Math.max(selectedSuggestionIndex - 1, 0);
                suggestionList.setSelectedIndex(i);
                selectedSuggestionIndex = i;
                e.consume();
                return;
              }

              if (e.getKeyCode() == KeyEvent.VK_TAB || e.getKeyCode() == KeyEvent.VK_ENTER) {
                e.consume();
                acceptSuggestion();
                return;
              }

              if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                autocompletePopup.setVisible(false);
              }
            }

            if (e.getKeyCode() == KeyEvent.VK_UP) {
              if (cmdHistoryIndex < cmdHistory.size() - 1) {
                cmdHistoryIndex++;
                inputField.setText(cmdHistory.get(cmdHistoryIndex));
              }
            } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
              if (cmdHistoryIndex > 0) {
                cmdHistoryIndex--;
                inputField.setText(cmdHistory.get(cmdHistoryIndex));
              } else {
                cmdHistoryIndex = -1;
                inputField.setText("");
              }
            } else if (e.getKeyCode() == KeyEvent.VK_TAB) {
              e.consume();

              if (!ghostSuggestion.isEmpty()) {
                inputField.setText(ghostSuggestion + " ");
                ghostSuggestion = "";
                inputField.repaint();
              }
            }
          }
        });

    inputField
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateSuggestion();
              }

              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateSuggestion();
              }

              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateSuggestion();
              }
            });

    // Live search
    searchField
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applySearchFilter();
              }

              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applySearchFilter();
              }

              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applySearchFilter();
              }
            });

    // Context menu on output pane
    outputPane.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            if (e.isPopupTrigger()) showContextMenu(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) showContextMenu(e);
          }
        });
  }

  private void updateSuggestion() {
    String text = inputField.getText().trim();
    ghostSuggestion = findSuggestion(text);
    inputField.repaint();

    updateAutocompletePopup();
  }

  private void acceptSuggestion() {
    String value = suggestionList.getSelectedValue();
    if (value == null) return;

    inputField.setText(value + " ");
    autocompletePopup.setVisible(false);
  }

  // ─── Command Processing ───────────────────────────────────────────────────────

  public void processCommand(String commandLine) {
    if (commandLine.trim().isEmpty()) return;

    appendToDocument("❯ " + commandLine + "\n", C_INPUT, null);

    String[] parts = commandLine.trim().split("\\s+");
    String commandName = parts[0].toLowerCase();
    String[] args = Arrays.copyOfRange(parts, 1, parts.length);

    Consumer<String[]> handler = COMMANDS.get(commandName);
    if (handler != null) {
      try {
        handler.accept(args);
        setStatus("Command executed: " + commandName);
      } catch (Exception e) {
        printError("Error in '" + commandName + "': " + e.getMessage());
      }
    } else {
      printError("Unknown command: '" + commandName + "'. Type 'help' for help.");
    }
  }

  private static class IntCVar extends CVar<Integer> {
    IntCVar(String name, Supplier<Integer> g, Consumer<Integer> s) {
      super(name, Integer.class, g, s);
    }

    Integer parse(String s) {
      return Integer.parseInt(s);
    }
  }

  private static class FloatCVar extends CVar<Float> {
    FloatCVar(String name, Supplier<Float> g, Consumer<Float> s) {
      super(name, Float.class, g, s);
    }

    Float parse(String s) {
      return Float.parseFloat(s);
    }
  }

  private static class BoolCVar extends CVar<Boolean> {
    BoolCVar(String name, Supplier<Boolean> g, Consumer<Boolean> s) {
      super(name, Boolean.class, g, s);
    }

    Boolean parse(String s) {
      return Boolean.parseBoolean(s);
    }
  }

  private static class StringCVar extends CVar<String> {
    StringCVar(String name, Supplier<String> g, Consumer<String> s) {
      super(name, String.class, g, s);
    }

    String parse(String s) {
      return s;
    }
  }

  private List<String> getAllCommandNames() {
    Set<String> names = new LinkedHashSet<>();

    names.addAll(COMMANDS.keySet());
    names.addAll(CVARS.keySet());

    return new ArrayList<>(names);
  }

  public String findSuggestion(String input) {
    if (input.isEmpty()) return "";

    for (String name : getAllCommandNames()) {
      if (name.startsWith(input.toLowerCase())) {
        return name;
      }
    }
    return "";
  }

  // ─── Logging ──────────────────────────────────────────────────────────────────

  private void logMessage(LogLevel level, String levelTag, String message, Color color) {
    String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
    LogEntry entry = new LogEntry(LocalTime.now(), level, message, color);

    synchronized (logHistory) {
      logHistory.add(entry);
    }

    boolean visible = matchesFilter(entry) && matchesSearch(entry);
    if (visible) {
      appendToDocument(formatLine(ts, levelTag, message), color, level);
    }

    updateLogCount();
  }

  private String formatLine(String ts, String tag, String msg) {
    return String.format("[%s] %-7s %s%n", ts, tag, msg);
  }

  // ─── Public API ───────────────────────────────────────────────────────────────

  public void print(String message) {
    logMessage(LogLevel.INFO, "[INFO]", message, C_DEFAULT);
  }

  public void printColored(String message, Color color) {
    String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
    LogEntry entry = new LogEntry(LocalTime.now(), LogLevel.INFO, message, color);
    synchronized (logHistory) {
      logHistory.add(entry);
    }
    appendToDocument(formatLine(ts, "[INFO]", message), color, LogLevel.INFO);
    updateLogCount();
  }

  public void printInfo(String message) {
    logMessage(LogLevel.INFO, "[INFO]", message, C_INFO);
  }

  public void printWarning(String message) {
    logMessage(LogLevel.WARNING, "[WARN]", message, C_WARN);
  }

  public void printError(String message) {
    logMessage(LogLevel.ERROR, "[ERROR]", message, C_ERROR);
  }

  public void printDebug(String message) {
    logMessage(LogLevel.DEBUG, "[DEBUG]", message, C_DEBUG);
  }

  public void printSystem(String message) {
    logMessage(LogLevel.SYSTEM, "[SYS]", message, C_SYSTEM);
  }

  // ─── Filter & Search ──────────────────────────────────────────────────────────

  public void setFilter(LogLevel level) {
    this.filterLevel = level;
    setStatus(level == null ? "Filter: All" : "Filter: " + level.name());
    refreshDisplay();
  }

  public void setSearch(String query) {
    this.searchQuery = query.toLowerCase();
    searchField.setText(query);
    setStatus(query.isEmpty() ? "Search cleared" : "Search: \"" + query + "\"");
    refreshDisplay();
  }

  private void applySearchFilter() {
    searchQuery = searchField.getText().toLowerCase();
    refreshDisplay();
  }

  private boolean matchesFilter(LogEntry entry) {
    return filterLevel == null || entry.level() == filterLevel;
  }

  private boolean matchesSearch(LogEntry entry) {
    return searchQuery.isEmpty() || entry.message().toLowerCase().contains(searchQuery);
  }

  private void refreshDisplay() {
    SwingUtilities.invokeLater(
        () -> {
          try {
            document.remove(0, document.getLength());
          } catch (BadLocationException ignored) {
          }

          synchronized (logHistory) {
            for (LogEntry entry : logHistory) {
              if (matchesFilter(entry) && matchesSearch(entry)) {
                String ts = entry.time().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
                String tag = tagForLevel(entry.level());
                appendToDocument(
                    formatLine(ts, tag, entry.message()), entry.color(), entry.level());
              }
            }
          }

          updateLogCount();
        });
  }

  private String tagForLevel(LogLevel level) {
    return switch (level) {
      case DEBUG -> "[DEBUG]";
      case INFO -> "[INFO]";
      case WARNING -> "[WARN]";
      case ERROR -> "[ERROR]";
      case SYSTEM -> "[SYS]";
    };
  }

  public void wipeLogIfOvergrown() {
    java.io.File file = new java.io.File(LOG_PATH);

    if (file.exists() && file.length() > MAX_SIZE) {
      try (java.nio.channels.FileChannel outChan =
          new java.io.FileOutputStream(file, false).getChannel()) {
        outChan.truncate(0);
        printSystem("Log file reached limit: Content wiped successfully.");
      } catch (java.io.IOException e) {
        printError("Critical failure clearing log file: " + e.getMessage());
      }
    }
  }

  // ─── Actions ──────────────────────────────────────────────────────────────────

  public void clear() {
    synchronized (logHistory) {
      logHistory.clear();
    }
    SwingUtilities.invokeLater(
        () -> {
          try {
            document.remove(0, document.getLength());
          } catch (BadLocationException ignored) {
          }
        });
    updateLogCount();
    setStatus("Console cleared");
  }

  public void copyAllToClipboard() {
    StringBuilder sb = new StringBuilder();
    synchronized (logHistory) {
      for (LogEntry e : logHistory) {
        sb.append(
            formatLine(
                e.time().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                tagForLevel(e.level()),
                e.message()));
      }
    }
    Toolkit.getDefaultToolkit()
        .getSystemClipboard()
        .setContents(new StringSelection(sb.toString()), null);
    setStatus("Copied " + logHistory.size() + " entries to clipboard");
  }

  private void showHelp() {
    printSystem("─── Commands ──────────────────────────────────────");
    printSystem("  help                — Show this help");
    printSystem("  clear               — Clear console output");
    printSystem("  filter [LEVEL]      — Filter by level (DEBUG/INFO/WARNING/ERROR/SYSTEM)");
    printSystem("  filter              — Reset filter (show all)");
    printSystem("  search [query]      — Search log messages");
    printSystem("  search              — Clear search");
    printSystem("  copy                — Copy all logs to clipboard");
    printSystem("  echo [text]         — Print a message");
    printSystem("  exit                — Quit application");
    printSystem("───────────────────────────────────────────────────");
    printSystem("  ↑ / ↓ arrows        — Navigate command history");
    printSystem("  Right-click         — Context menu");
  }

  private void showContextMenu(MouseEvent e) {
    JPopupMenu menu = new JPopupMenu();
    menu.setBackground(BG_INPUT);
    menu.setBorder(BorderFactory.createLineBorder(BORDER_ACCENT, 1));

    menu.add(styledMenuItem("Copy Selected", () -> outputPane.copy()));
    menu.add(styledMenuItem("Copy All Logs", this::copyAllToClipboard));
    menu.addSeparator();
    menu.add(styledMenuItem("Clear Console", this::clear));
    menu.show(outputPane, e.getX(), e.getY());
  }

  private JMenuItem styledMenuItem(String text, Runnable action) {
    JMenuItem item = new JMenuItem(text);
    item.setFont(FONT_UI_SM);
    item.setBackground(BG_INPUT);
    item.setForeground(TEXT_PRIMARY);
    item.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
    item.addActionListener(e -> action.run());
    item.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent e) {
            item.setBackground(BG_HOVER);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            item.setBackground(BG_INPUT);
          }
        });
    return item;
  }

  // ─── UI Utilities ─────────────────────────────────────────────────────────────

  private JToggleButton makeAllFilterButton() {
    JToggleButton btn = new JToggleButton("ALL");
    btn.setSelected(true);
    btn.setFont(FONT_LABEL);
    btn.setForeground(ACCENT);
    btn.setBackground(BG_SELECTED);
    btn.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_DIM, 1, true),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)));
    btn.setFocusPainted(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.addActionListener(
        e -> {
          setFilter(null);
          btn.setSelected(true);
          filterButtons.values().forEach(b -> b.setSelected(false));
        });
    return btn;
  }

  private JToggleButton makeFilterButton(LogLevel level) {
    Color c = colorForLevel(level);
    JToggleButton btn = new JToggleButton(level.name());
    btn.setFont(FONT_LABEL);
    btn.setForeground(c);
    btn.setBackground(BG_TOOLBAR);
    btn.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(c.darker(), 1, true),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)));
    btn.setFocusPainted(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.addActionListener(
        e -> {
          if (btn.isSelected()) {
            setFilter(level);
            filterButtons.forEach(
                (l, b) -> {
                  if (l != level) b.setSelected(false);
                });
          } else {
            setFilter(null);
          }
        });
    btn.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent e) {
            if (!btn.isSelected()) btn.setBackground(BG_HOVER);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            if (!btn.isSelected()) btn.setBackground(BG_TOOLBAR);
          }
        });
    return btn;
  }

  private Color colorForLevel(LogLevel level) {
    return switch (level) {
      case DEBUG -> C_DEBUG;
      case INFO -> C_INFO;
      case WARNING -> C_WARN;
      case ERROR -> C_ERROR;
      case SYSTEM -> C_SYSTEM;
    };
  }

  private JButton makeToolButton(String text, ActionListener action) {
    JButton btn = new JButton(text);
    btn.setFont(FONT_UI_SM);
    btn.setForeground(TEXT_SECONDARY);
    btn.setBackground(BG_TOOLBAR);
    btn.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
    btn.setFocusPainted(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.addActionListener(action);
    btn.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent e) {
            btn.setBackground(BG_HOVER);
            btn.setForeground(TEXT_PRIMARY);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            btn.setBackground(BG_TOOLBAR);
            btn.setForeground(TEXT_SECONDARY);
          }
        });
    return btn;
  }

  private JSeparator makeSeparator() {
    JSeparator sep = new JSeparator(JSeparator.VERTICAL);
    sep.setPreferredSize(new Dimension(1, 20));
    sep.setForeground(BORDER_SUBTLE);
    return sep;
  }

  private JLabel makeLabel(String text, Color color, Font font) {
    JLabel lbl = new JLabel(text);
    lbl.setFont(font);
    lbl.setForeground(color);
    return lbl;
  }

  private void styleScrollBar(JScrollBar bar) {
    bar.setBackground(BG_PANEL);
    bar.setUI(
        new javax.swing.plaf.basic.BasicScrollBarUI() {
          @Override
          protected void configureScrollBarColors() {
            thumbColor = new Color(50, 62, 85);
            thumbDarkShadowColor = BG_PANEL;
            thumbLightShadowColor = BG_PANEL;
            thumbHighlightColor = BG_PANEL;
            trackColor = BG_PANEL;
            trackHighlightColor = BG_PANEL;
          }

          @Override
          protected JButton createDecreaseButton(int o) {
            return zeroButton();
          }

          @Override
          protected JButton createIncreaseButton(int o) {
            return zeroButton();
          }

          private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            return b;
          }

          @Override
          protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isDragging ? ACCENT_DIM : thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
            g2.dispose();
          }
        });
  }

  private void setStatus(String msg) {
    SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
  }

  private void updateLogCount() {
    long visible, total;
    synchronized (logHistory) {
      total = logHistory.size();
      visible = logHistory.stream().filter(e -> matchesFilter(e) && matchesSearch(e)).count();
    }
    final long v = visible, t = total;
    SwingUtilities.invokeLater(
        () -> logCountLabel.setText(v == t ? t + " entries" : v + " / " + t + " entries"));
  }

  private void appendToDocument(String text, Color color, LogLevel level) {
    // We reuse the LogEntry record or create a simple one for the UI
    pendingQueue.add(new LogEntry(LocalTime.now(), level, text, color));

    if (!flushTimer.isRunning()) {
      flushTimer.start();
    }
  }

  private void flushLogs() {
    if (pendingQueue.isEmpty()) {
      flushTimer.stop();
      return;
    }

    // Capture the current batch to process on the UI thread
    List<LogEntry> batch = new ArrayList<>();
    LogEntry entry;
    while ((entry = pendingQueue.poll()) != null) {
      batch.add(entry);
    }

    SwingUtilities.invokeLater(
        () -> {
          try {
            // Memory management: Trim document if it gets too huge
            if (document.getLength() > 100_000) {
              document.remove(0, 50_000);
            }

            for (LogEntry e : batch) {
              SimpleAttributeSet style = new SimpleAttributeSet();
              StyleConstants.setForeground(style, e.color() != null ? e.color() : C_DEFAULT);
              StyleConstants.setFontFamily(style, FONT_MONO.getFamily());
              StyleConstants.setFontSize(style, FONT_MONO.getSize());

              document.insertString(document.getLength(), e.message(), style);
            }

            outputPane.setCaretPosition(document.getLength());
          } catch (BadLocationException ex) {
            ex.printStackTrace();
          }
        });
  }

  @SuppressWarnings("removal")
  private void applyDarkTitleBar() {
    // On Windows 11: attempt dark title bar via undocumented property
    try {
      Class<?> c = Class.forName("com.sun.awt.AWTUtilities");
      // no-op; just a best-effort attempt
    } catch (ClassNotFoundException ignored) {
    }
  }

  private void initAutocompletePopup() {
    suggestionModel = new DefaultListModel<>();
    suggestionList = new JList<>(suggestionModel);

    suggestionList.setFont(FONT_MONO);
    suggestionList.setBackground(BG_INPUT);
    suggestionList.setForeground(TEXT_PRIMARY);
    suggestionList.setSelectionBackground(BG_SELECTED);
    suggestionList.setSelectionForeground(TEXT_PRIMARY);
    suggestionList.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    suggestionList.setVisibleRowCount(6);

    autocompletePopup = new JPopupMenu();
    autocompletePopup.setBorder(BorderFactory.createLineBorder(BORDER_ACCENT));
    autocompletePopup.setFocusable(false);

    JScrollPane scroll = new JScrollPane(suggestionList);
    scroll.setBorder(null);
    scroll.setPreferredSize(new Dimension(260, 120));

    autocompletePopup.add(scroll);
  }

  List<String> findSuggestions(String input) {
    if (input.isEmpty()) return List.of();

    String lower = input.toLowerCase();
    List<String> matches = new ArrayList<>();

    for (String name : getAllCommandNames()) {
      if (name.startsWith(lower)) {
        matches.add(name);
      }
    }

    return matches.stream().limit(6).toList();
  }

  private void updateAutocompletePopup() {
    String text = inputField.getText().trim();
    List<String> suggestions = findSuggestions(text);

    if (suggestions.isEmpty()) {
      autocompletePopup.setVisible(false);
      return;
    }

    suggestionModel.clear();
    suggestions.forEach(suggestionModel::addElement);
    suggestionList.setSelectedIndex(0);
    selectedSuggestionIndex = 0;

    try {
      Rectangle r = inputField.getBounds();
      Point p =
          SwingUtilities.convertPoint(inputField.getParent(), r.getLocation(), getLayeredPane());

      autocompletePopup.show(
          getLayeredPane(), p.x + 8, p.y - autocompletePopup.getPreferredSize().height);
    } catch (Exception ignored) {
    }

    autocompletePopup.setVisible(true);
  }

  // ─── Public Window Control ────────────────────────────────────────────────────

  public static void registerCommand(String name, Consumer<String[]> handler) {
    COMMANDS.put(name.toLowerCase(), handler);
  }

  public static <T> void registerCVar(CVar<T> var) {
    CVARS.put(var.name, var);

    registerCommand(
        var.name,
        args -> {
          Console c = getInstance();

          try {
            // print value
            if (args.length == 0) {
              c.printInfo(var.name + " = " + var.get() + " (" + var.typeName() + ")");
              return;
            }

            // set value
            String newValue = String.join(" ", args);
            var.setFromString(newValue);
            c.printSystem(var.name + " set to " + var.get());

          } catch (Exception e) {
            c.printError("Invalid value for " + var.name + " (" + var.typeName() + ")");
          }
        });
  }

  public static void bindInt(String name, Supplier<Integer> g, Consumer<Integer> s) {
    registerCVar(new IntCVar(name, g, s));
  }

  public static void bindFloat(String name, Supplier<Float> g, Consumer<Float> s) {
    registerCVar(new FloatCVar(name, g, s));
  }

  public static void bindBool(String name, Supplier<Boolean> g, Consumer<Boolean> s) {
    registerCVar(new BoolCVar(name, g, s));
  }

  public static void bindString(String name, Supplier<String> g, Consumer<String> s) {
    registerCVar(new StringCVar(name, g, s));
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
    SwingUtilities.invokeLater(() -> setVisible(false));
  }

  public boolean isConsoleVisible() {
    return isVisible();
  }
}
