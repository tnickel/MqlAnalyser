package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import data.ProviderStats;
import db.HistoryDatabaseManager;
import services.StrategyClassifierService;

public class AnalysisProgressDialog extends JDialog {
    private final List<String> providersToAnalyze;
    private final Map<String, ProviderStats> allStats;
    private final JProgressBar progressBar;
    private final JTextArea logArea;
    private final JLabel statusLabel;
    private JButton startButton;
    private JButton closeButton;
    private final Runnable onComplete;
    private boolean isRunning = false;

    public AnalysisProgressDialog(JFrame parent, List<String> providersToAnalyze, 
                                  Map<String, ProviderStats> allStats, Runnable onComplete) {
        super(parent, "Martingale / Grid Analyse", true);
        this.providersToAnalyze = providersToAnalyze;
        this.allStats = allStats;
        this.onComplete = onComplete;

        this.progressBar = new JProgressBar(0, providersToAnalyze.size());
        this.progressBar.setStringPainted(true);
        this.statusLabel = new JLabel("Bereit zum Starten der Analyse...");
        this.logArea = new JTextArea();
        this.logArea.setEditable(false);

        initUI();
        setSize(550, 400);
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Info Label
        JLabel infoLabel = new JLabel("<html><b>Martingale- & Grid-Erkennung</b><br>" +
                "Analysiert das Orderverhalten der ausgewählten Strategien.<br>" +
                "Auswertung wird in der H2-Datenbank gespeichert und für 7 Tage gecached.</html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        mainPanel.add(infoLabel);

        // Progress panel
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.add(statusLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        mainPanel.add(progressPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Log panel
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Protokoll"));
        mainPanel.add(scrollPane);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        startButton = new JButton("Analyse starten");
        startButton.addActionListener(e -> startAnalysis());
        
        closeButton = new JButton("Schließen");
        closeButton.addActionListener(e -> dispose());
        
        buttonPanel.add(startButton);
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel);

        setContentPane(mainPanel);

        logArea.append("Gefundene Provider zur Analyse: " + providersToAnalyze.size() + "\n");
        if (providersToAnalyze.isEmpty()) {
            startButton.setEnabled(false);
            logArea.append("Alle Provider-Analysen sind aktuell. Keine Aktion erforderlich.\n");
        }
    }

    private void startAnalysis() {
        if (isRunning) return;
        isRunning = true;
        startButton.setEnabled(false);
        closeButton.setEnabled(false);
        progressBar.setValue(0);

        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish("Starte Auswertung für " + providersToAnalyze.size() + " Provider...");
                HistoryDatabaseManager dbManager = HistoryDatabaseManager.getInstance();

                int count = 0;
                for (String providerName : providersToAnalyze) {
                    publish("Analysiere " + providerName + "...");
                    
                    ProviderStats stats = allStats.get(providerName);
                    if (stats == null) {
                        publish("WARNUNG: Keine Trades für " + providerName + " geladen.");
                        count++;
                        updateProgress(count);
                        continue;
                    }

                    // Run algorithms
                    boolean isMartingale = StrategyClassifierService.isMartingale(stats.getTrades());
                    boolean isGrid = StrategyClassifierService.isGrid(stats.getTrades());

                    // Save result in DB
                    dbManager.saveProviderAnalysis(providerName, isMartingale, isGrid);

                    publish(String.format("Provider '%s' fertig: Martingale=%b, Grid=%b", 
                            providerName, isMartingale, isGrid));

                    count++;
                    updateProgress(count);
                    
                    // Short sleep for smooth progress update
                    Thread.sleep(50);
                }

                publish("Analyse erfolgreich abgeschlossen.");
                return null;
            }

            private void updateProgress(int count) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(count);
                    statusLabel.setText("Analysiere Provider " + count + " von " + providersToAnalyze.size());
                });
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    logArea.append(chunk + "\n");
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                isRunning = false;
                closeButton.setEnabled(true);
                progressBar.setValue(providersToAnalyze.size());
                statusLabel.setText("Analyse abgeschlossen.");
                
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        };

        worker.execute();
    }
}
