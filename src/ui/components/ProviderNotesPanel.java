package ui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import db.HistoryDatabaseManager;
import ui.UIConstants;

/**
 * Panel-Komponente zum Anzeigen und Bearbeiten von Notizen für einen Signal Provider
 */
public class ProviderNotesPanel extends JPanel {
    private final String providerName;
    private JTextArea notesTextArea;
    private JButton saveNotesButton;

    /**
     * Konstruktor für das Provider-Notizen-Panel
     * 
     * @param providerName Name des Signal Providers
     */
    public ProviderNotesPanel(String providerName) {
        this.providerName = providerName;
        
        initializeUI();
        loadNotes();
    }

    /**
     * Initialisiert die UI-Komponenten
     */
    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));
        setBackground(UIConstants.BG_COLOR);
        setBorder(BorderFactory.createTitledBorder("Notizen zum Signal Provider"));
        
        // Martingale/Grid-Analyse aus der DB laden
        db.HistoryDatabaseManager.AnalysisResult analysis = db.HistoryDatabaseManager.getInstance().getProviderAnalysis(providerName);
        if (analysis == null && !providerName.endsWith(".csv")) {
            analysis = db.HistoryDatabaseManager.getInstance().getProviderAnalysis(providerName + ".csv");
        }
        if (analysis == null && providerName.endsWith(".csv")) {
            analysis = db.HistoryDatabaseManager.getInstance().getProviderAnalysis(providerName.substring(0, providerName.length() - 4));
        }
        
        String classificationText = "Keine Besonderheiten (-)";
        if (analysis != null) {
            boolean isM = analysis.isMartingale();
            boolean isG = analysis.isGrid();
            if (isM && isG) {
                classificationText = "Martingale & Grid";
            } else if (isM) {
                classificationText = "Martingale";
            } else if (isG) {
                classificationText = "Grid";
            }
        }
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBackground(UIConstants.BG_COLOR);
        JLabel typeLabel = new JLabel("Erkannter Strategietyp: " + classificationText);
        typeLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        if (analysis != null && (analysis.isMartingale() || analysis.isGrid())) {
            typeLabel.setForeground(new Color(180, 0, 0));
        } else {
            typeLabel.setForeground(UIConstants.TEXT_COLOR);
        }
        topPanel.add(typeLabel);
        add(topPanel, BorderLayout.NORTH);
        
        // Textfeld für Notizen erstellen
        notesTextArea = new JTextArea();
        notesTextArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        notesTextArea.setLineWrap(true);  // Automatischer Zeilenumbruch
        notesTextArea.setWrapStyleWord(true);
        notesTextArea.setBackground(Color.WHITE);
        notesTextArea.setForeground(UIConstants.TEXT_COLOR);
        
        // Scrollpane für das Textfeld
        JScrollPane scrollPane = new JScrollPane(notesTextArea);
        scrollPane.setPreferredSize(new Dimension(0, 150));  // Ca. 10 Zeilen hoch
        add(scrollPane, BorderLayout.CENTER);
        
        // Button zum Speichern der Notizen
        saveNotesButton = new JButton("Notizen speichern");
        saveNotesButton.addActionListener(e -> saveNotes());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(UIConstants.BG_COLOR);
        buttonPanel.add(saveNotesButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Speichert die eingegebenen Notizen in der Datenbank
     */
    private void saveNotes() {
        // Notizen aus dem Textfeld holen
        String notes = notesTextArea.getText();
        
        // Datenbankzugriff und Speichern
        try {
            boolean success = HistoryDatabaseManager.getInstance().saveProviderNotes(providerName, notes);
            if (success) {
                JOptionPane.showMessageDialog(this,
                        "Notizen für " + providerName + " wurden gespeichert.",
                        "Notizen gespeichert",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Fehler beim Speichern der Notizen.",
                        "Fehler",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Speichern der Notizen: " + e.getMessage(),
                    "Fehler",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Lädt vorhandene Notizen aus der Datenbank
     */
    private void loadNotes() {
        try {
            String notes = HistoryDatabaseManager.getInstance().getProviderNotes(providerName);
            notesTextArea.setText(notes);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Laden der Notizen: " + e.getMessage(),
                    "Fehler",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Gibt den aktuellen Text im Notizfeld zurück
     * @return Der aktuelle Text
     */
    public String getNotes() {
        return notesTextArea.getText();
    }
    
    /**
     * Setzt den Text im Notizfeld
     * @param notes Der zu setzende Text
     */
    public void setNotes(String notes) {
        notesTextArea.setText(notes);
    }
}