package ui.dialogs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Swing-Dialog zur Anzeige von CSV-Parsing-Fehlern und Warnungen
 */
public class CsvErrorDialog extends JDialog {
    
    public enum ErrorType {
        PARSING_ERROR("CSV-Parsing-Fehler"),
        NO_TRADES_WARNING("Keine Trades gefunden"),
        IO_ERROR("Datei-Fehler");
        
        private final String displayName;
        
        ErrorType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private String fileName;
    private ErrorType errorType;
    private String errorMessage;
    private String details;
    private int totalLines;
    private int skippedLines;
    
    // UI-Komponenten
    private JButton copyButton;
    
    public CsvErrorDialog(Frame parent, String fileName, ErrorType errorType, 
                         String errorMessage, String details, int totalLines, int skippedLines) {
        super(parent, "CSV-Verarbeitungsproblem", true);
        this.fileName = fileName;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.details = details;
        this.totalLines = totalLines;
        this.skippedLines = skippedLines;
        
        initializeDialog();
    }
    
    /**
     * Vereinfachter Konstruktor für reine Fehlermeldungen ohne Line-Count
     */
    public CsvErrorDialog(Frame parent, String fileName, ErrorType errorType, 
                         String errorMessage, String details) {
        this(parent, fileName, errorType, errorMessage, details, 0, 0);
    }
    
    private void initializeDialog() {
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        // Hauptpanel mit Padding
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Header-Bereich
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);
        
        // Details-Bereich (falls vorhanden)
        if (details != null && !details.trim().isEmpty()) {
            mainPanel.add(createDetailsPanel(), BorderLayout.CENTER);
        }
        
        // Button-Bereich
        mainPanel.add(createButtonPanel(), BorderLayout.SOUTH);
        
        add(mainPanel);
        
        // Dialog-Eigenschaften
        setMinimumSize(new Dimension(450, 200));
        setPreferredSize(new Dimension(600, 400));
        pack();
        
        // Zentrieren
        setLocationRelativeTo(getParent());
        
        // ESC-Key zum Schließen
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }
    
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new EmptyBorder(0, 0, 15, 0));
        
        // Icon
        JLabel iconLabel = new JLabel();
        if (errorType == ErrorType.NO_TRADES_WARNING) {
            iconLabel.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
        } else {
            iconLabel.setIcon(UIManager.getIcon("OptionPane.errorIcon"));
        }
        iconLabel.setBorder(new EmptyBorder(0, 0, 0, 15));
        headerPanel.add(iconLabel, BorderLayout.WEST);
        
        // Nachrichten-Panel
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        
        // Titel (fett)
        JLabel titleLabel = new JLabel(errorType.getDisplayName());
        Font titleFont = titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize() + 2f);
        titleLabel.setFont(titleFont);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagePanel.add(titleLabel);
        
        messagePanel.add(Box.createVerticalStrut(5));
        
        // Dateiname
        JLabel fileLabel = new JLabel("Datei: " + fileName);
        fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagePanel.add(fileLabel);
        
        // Fehlermeldung (falls vorhanden)
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            messagePanel.add(Box.createVerticalStrut(5));
            JLabel messageLabel = new JLabel("<html><body style='width: 300px'>" + errorMessage + "</body></html>");
            messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messagePanel.add(messageLabel);
        }
        
        // Statistik-Informationen
        if (totalLines > 0) {
            messagePanel.add(Box.createVerticalStrut(5));
            String statsText = String.format("Verarbeitet: %d Zeilen, Übersprungen: %d Zeilen", 
                                            totalLines, skippedLines);
            JLabel statsLabel = new JLabel(statsText);
            statsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            messagePanel.add(statsLabel);
        }
        
        headerPanel.add(messagePanel, BorderLayout.CENTER);
        
        return headerPanel;
    }
    
    private JPanel createDetailsPanel() {
        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.setBorder(new EmptyBorder(15, 0, 15, 0));
        
        // Separator-Linie
        detailsPanel.add(new JSeparator(SwingConstants.HORIZONTAL), BorderLayout.NORTH);
        
        // Details-Label
        JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 10));
        JLabel detailsLabel = new JLabel("Details:");
        Font labelFont = detailsLabel.getFont().deriveFont(Font.BOLD);
        detailsLabel.setFont(labelFont);
        labelPanel.add(detailsLabel);
        detailsPanel.add(labelPanel, BorderLayout.NORTH);
        
        // Details-TextArea mit ScrollPane
        JTextArea detailsTextArea = new JTextArea(details);
        detailsTextArea.setEditable(false);
        detailsTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsTextArea.setBackground(getContentPane().getBackground());
        detailsTextArea.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        JScrollPane scrollPane = new JScrollPane(detailsTextArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(500, 200));
        scrollPane.setBorder(BorderFactory.createLoweredBevelBorder());
        
        detailsPanel.add(scrollPane, BorderLayout.CENTER);
        
        return detailsPanel;
    }
    
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(new EmptyBorder(15, 0, 0, 0));
        
        // Button: Details kopieren (nur wenn Details vorhanden)
        if (details != null && !details.trim().isEmpty()) {
            copyButton = new JButton("Details kopieren");
            copyButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    copyDetailsToClipboard();
                }
            });
            buttonPanel.add(copyButton);
        }
        
        // Button: OK
        JButton okButton = new JButton("OK");
        okButton.setPreferredSize(new Dimension(80, okButton.getPreferredSize().height));
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        buttonPanel.add(okButton);
        
        // Enter-Key für OK-Button
        getRootPane().setDefaultButton(okButton);
        
        return buttonPanel;
    }
    
    private void copyDetailsToClipboard() {
        if (details == null || details.trim().isEmpty()) {
            return;
        }
        
        // Erstelle vollständige Informationen
        StringBuilder fullDetails = new StringBuilder();
        fullDetails.append("CSV-Verarbeitungsproblem\n");
        fullDetails.append("=======================\n\n");
        fullDetails.append("Fehlertyp: ").append(errorType.getDisplayName()).append("\n");
        fullDetails.append("Datei: ").append(fileName).append("\n");
        
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            fullDetails.append("Nachricht: ").append(errorMessage).append("\n");
        }
        
        if (totalLines > 0) {
            fullDetails.append("Statistik: ").append(totalLines).append(" Zeilen verarbeitet, ")
                      .append(skippedLines).append(" Zeilen übersprungen\n");
        }
        
        fullDetails.append("\nDetails:\n").append(details);
        
        StringSelection stringSelection = new StringSelection(fullDetails.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
        
        // Kurzes visuelles Feedback
        if (copyButton != null) {
            String originalText = copyButton.getText();
            copyButton.setText("Kopiert!");
            copyButton.setEnabled(false);
            
            Timer timer = new Timer(1500, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    copyButton.setText(originalText);
                    copyButton.setEnabled(true);
                }
            });
            timer.setRepeats(false);
            timer.start();
        }
    }
    
    /**
     * Statische Hilfsmethode für Thread-sichere Anzeige aus DataManager
     */
    public static void showError(String fileName, ErrorType errorType, 
                               String errorMessage, String details, int totalLines, int skippedLines) {
        SwingUtilities.invokeLater(() -> {
            Frame parentFrame = null;
            
            // Versuche das Hauptfenster zu finden
            for (Frame frame : Frame.getFrames()) {
                if (frame.isDisplayable() && frame.isVisible()) {
                    parentFrame = frame;
                    break;
                }
            }
            
            CsvErrorDialog dialog = new CsvErrorDialog(parentFrame, fileName, errorType, 
                                                      errorMessage, details, totalLines, skippedLines);
            dialog.setVisible(true);
        });
    }
    
    /**
     * Vereinfachte statische Hilfsmethode ohne Line-Count
     */
    public static void showError(String fileName, ErrorType errorType, 
                               String errorMessage, String details) {
        showError(fileName, errorType, errorMessage, details, 0, 0);
    }
}