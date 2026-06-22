package ui.components;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import components.MainTable;
import data.DataManager;
import data.ProviderStats;
import services.ProviderHistoryService;
import ui.CompareEquityCurvesDialog;
import ui.CompareOpenTradesDialog;
import ui.PortfolioGeneratorDialog;
import ui.DatabaseViewerDialog;
import ui.EquityDrawdownDialog;
import ui.ForceDbSaveDialog;
import ui.MainFrame;
import ui.RiskScoreExplanationDialog;
import ui.ShowSignalProviderList;
import ui.TableColumnConfigDialog;
import ui.dialogs.CheckSignalProviderDialog;
import ui.dialogs.TextFileViewerDialog;
import utils.HtmlDatabase;
import utils.MqlAnalyserConf;
import utils.UIStyle;

public class MenuManager {
    private final MainFrame parentFrame;
    private final DataManager dataManager;
    private final MainTable mainTable;
    private final MqlAnalyserConf config;
    private final ProviderHistoryService historyService;
    private final String rootPath;
    private final JMenuBar menuBar;

    public MenuManager(MainFrame parentFrame, DataManager dataManager, MainTable mainTable, 
                     MqlAnalyserConf config, ProviderHistoryService historyService, String rootPath) {
        this.parentFrame = parentFrame;
        this.dataManager = dataManager;
        this.mainTable = mainTable;
        this.config = config;
        this.historyService = historyService;
        this.rootPath = rootPath;
        
        this.menuBar = new JMenuBar();
        
        createMenuBar();
    }
    
    public JMenuBar getMenuBar() {
        return menuBar;
    }
    
    private void createMenuBar() {
        menuBar.setBackground(UIStyle.PRIMARY_COLOR);
        menuBar.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        
        // Datei-Menü
        JMenu fileMenu = new JMenu("Datei");
        fileMenu.setForeground(Color.WHITE);
        
        // Menüpunkt zum Setzen des Download-Pfads
        JMenuItem setPathItem = new JMenuItem("Download-Pfad setzen");
        setPathItem.addActionListener(e -> setDownloadPath());
        
        // Menüpunkt für Spalten-Konfiguration
        JMenuItem configColumnsItem = new JMenuItem("Spalten konfigurieren");
        configColumnsItem.addActionListener(e -> {
            TableColumnConfigDialog dialog = new TableColumnConfigDialog(parentFrame, mainTable);
            dialog.showDialog();
        });
        
        // Menüpunkt für Bewertungs-Konfiguration
        JMenuItem configScoringItem = new JMenuItem("Bewertung konfigurieren");
        configScoringItem.addActionListener(e -> {
            ui.ScoreConfigDialog dialog = new ui.ScoreConfigDialog(parentFrame, mainTable);
            dialog.setVisible(true);
        });
        
        // Menüpunkt zum Beenden
        JMenuItem exitItem = new JMenuItem("Beenden");
        exitItem.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(
                parentFrame,
                "Möchten Sie die Anwendung wirklich beenden?",
                "Beenden",
                JOptionPane.YES_NO_OPTION);
            
            if (result == JOptionPane.YES_OPTION) {
                historyService.shutdown();
                parentFrame.dispose();
                System.exit(0);
            }
        });
        
        fileMenu.add(setPathItem);
        fileMenu.add(configColumnsItem);
        fileMenu.add(configScoringItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        // Datenbank-Menü
        JMenu dbMenu = new JMenu("Datenbank");
        dbMenu.setForeground(Color.WHITE);
        
        // Menüpunkt für Datenbank-Einträge anzeigen
        JMenuItem viewDbItem = new JMenuItem("Datenbank-Einträge anzeigen");
        viewDbItem.addActionListener(e -> {
            DatabaseViewerDialog dialog = new DatabaseViewerDialog(parentFrame, historyService);
            dialog.setVisible(true);
        });
        
        // Menüpunkt für DB-Speicherung erzwingen
        JMenuItem forceDbSaveItem = new JMenuItem("DB-Speicherung erzwingen");
        forceDbSaveItem.addActionListener(e -> {
            if (!historyService.hasDatabaseEntries()) {
                int answer = JOptionPane.showConfirmDialog(
                    parentFrame,
                    "Es wurden keine Einträge in der Datenbank gefunden. Möchten Sie eine initiale Speicherung für alle Provider erzwingen?",
                    "Datenbank leer",
                    JOptionPane.YES_NO_OPTION
                );
                
                if (answer == JOptionPane.YES_OPTION) {
                    // Initiale Speicherung im Hintergrund ausführen
                    new javax.swing.SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            historyService.forceInitialSave();
                            return null;
                        }
                        
                        @Override
                        protected void done() {
                            JOptionPane.showMessageDialog(
                                parentFrame,
                                "Initiale Speicherung abgeschlossen. Die Datenbank enthält nun Einträge für alle Provider.",
                                "Speicherung abgeschlossen",
                                JOptionPane.INFORMATION_MESSAGE
                            );
                        }
                    }.execute();
                }
            } else {
                // Detaillierten Dialog zum Erzwingen der Speicherung öffnen
                ForceDbSaveDialog dialog = new ForceDbSaveDialog(
                    parentFrame,
                    historyService,
                    dataManager.getStats(),
                    config.getDownloadPath()
                );
                dialog.setVisible(true);
            }
        });
        
        // Menüpunkt für DB-Backup erstellen
        JMenuItem backupDbItem = new JMenuItem("Datenbank-Backup erstellen");
        backupDbItem.addActionListener(e -> {
            boolean success = historyService.createBackup();
            if (success) {
                JOptionPane.showMessageDialog(
                    parentFrame,
                    "Datenbank-Backup wurde erfolgreich erstellt.",
                    "Backup erfolgreich",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(
                    parentFrame,
                    "Fehler beim Erstellen des Datenbank-Backups.",
                    "Backup-Fehler",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
        
        JMenuItem runAnalysisItem = new JMenuItem("Martingale/Grid-Analyse durchführen");
        runAnalysisItem.addActionListener(e -> runMartingaleGridAnalysis(true));
        
        dbMenu.add(viewDbItem);
        dbMenu.add(forceDbSaveItem);
        dbMenu.add(runAnalysisItem);
        dbMenu.add(backupDbItem);
        
        // Ansicht-Menü
        JMenu viewMenu = new JMenu("Ansicht");
        viewMenu.setForeground(Color.WHITE);
        
        // Menüpunkt für Equity Curves anzeigen
        JMenuItem showEquityCurvesItem = new JMenuItem("Equity Curves anzeigen");
        showEquityCurvesItem.addActionListener(e -> showCompareDialog());
        
        // Menüpunkt für Equity Drawdown anzeigen (NEUER MENÜPUNKT)
        JMenuItem showEquityDrawdownItem = new JMenuItem("Equity Drawdown anzeigen");
        showEquityDrawdownItem.addActionListener(e -> showEquityDrawdownDialog());
        
        // Menüpunkt für Signal Provider Liste
        JMenuItem showSignalProvidersItem = new JMenuItem("Signal Provider Liste");
        showSignalProvidersItem.addActionListener(e -> {
            HtmlDatabase htmlDb = mainTable.getHtmlDatabase();
            ShowSignalProviderList dialog = new ShowSignalProviderList(
                parentFrame,
                mainTable.getCurrentProviderStats(),
                htmlDb,
                rootPath
            );
            dialog.setVisible(true);
        });
        
        // Menüpunkt für Open Trades vergleichen
        JMenuItem compareOpenTradesItem = new JMenuItem("Open Trades vergleichen");
        compareOpenTradesItem.addActionListener(e -> {
            CompareOpenTradesDialog dialog = new CompareOpenTradesDialog(
                parentFrame,
                mainTable.getCurrentProviderStats()
            );
            dialog.setVisible(true);
        });
        
        viewMenu.add(showEquityCurvesItem);
        viewMenu.add(showEquityDrawdownItem); // Neuer Menüpunkt hinzugefügt
        viewMenu.add(showSignalProvidersItem);
        viewMenu.add(compareOpenTradesItem);
        
        // Menüpunkt für Portfolio Generator (NEUER MENÜPUNKT)
        JMenuItem portfolioGeneratorItem = new JMenuItem("Optimales Portfolio erstellen");
        portfolioGeneratorItem.addActionListener(e -> {
            PortfolioGeneratorDialog dialog = new PortfolioGeneratorDialog(
                parentFrame,
                mainTable.getCurrentProviderStats(),
                mainTable.getHtmlDatabase(),
                mainTable,
                rootPath
            );
            dialog.setVisible(true);
        });
        viewMenu.add(portfolioGeneratorItem);
        
        // Debug-Menü (ERWEITERT)
        JMenu debugMenu = new JMenu("Debug");
        debugMenu.setForeground(Color.WHITE);
        
        // Menüpunkt für SignalProvider.txt anzeigen
        JMenuItem showTextfileItem = new JMenuItem("ShowTextfile");
        showTextfileItem.addActionListener(e -> {
            // Hole den selektierten Provider aus der Tabelle
            Map<String, ProviderStats> selectedProviders = mainTable.getSelectedProvidersMap();
            
            if (selectedProviders.isEmpty()) {
                JOptionPane.showMessageDialog(
                    parentFrame,
                    "Bitte wählen Sie zuerst einen Signal Provider aus der Tabelle aus.",
                    "Keine Auswahl",
                    JOptionPane.WARNING_MESSAGE
                );
                return;
            }
            
            // Nimm den ersten selektierten Provider
            ProviderStats selectedProvider = selectedProviders.values().iterator().next();
            String providerName = selectedProvider.getSignalProvider();
            
            // Verwende den aktuellen Download-Path aus der Konfiguration
            String downloadPath = config.getDownloadPath();
            
            TextFileViewerDialog dialog = new TextFileViewerDialog(parentFrame, downloadPath, providerName);
            dialog.setVisible(true);
        });
        
        // NEUER Menüpunkt für Check Signalprovider
        JMenuItem checkSignalProviderItem = new JMenuItem("Check Signalprovider");
        checkSignalProviderItem.addActionListener(e -> {
            // Verwende rootPath + download anstatt config.getDownloadPath()
            String downloadPath = rootPath + java.io.File.separator + "download";
            CheckSignalProviderDialog dialog = new CheckSignalProviderDialog(parentFrame, downloadPath);
            dialog.setVisible(true);
        });
        
        debugMenu.add(showTextfileItem);
        debugMenu.add(checkSignalProviderItem); // Neuer Menüpunkt hinzugefügt
        
        // Hilfe-Menü
        JMenu helpMenu = new JMenu("Hilfe");
        helpMenu.setForeground(Color.WHITE);
        
        // Menüpunkt für Risk Score Erklärung
        JMenuItem riskScoreHelpItem = new JMenuItem("Risk Score Erklärung");
        riskScoreHelpItem.addActionListener(e -> {
            RiskScoreExplanationDialog dialog = new RiskScoreExplanationDialog(parentFrame);
            dialog.setVisible(true);
        });
        
        // Menüpunkt für Über
        JMenuItem aboutItem = new JMenuItem("Über");
        aboutItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(
                parentFrame,
                "MQL Analyzer\nVersion 1.0\n\nEine Anwendung zur Analyse von MQL5 Signal Providern.\n" +
                "Entwickelt für die Analyse von Trading-Performance und Risikobewertung.",
                "Über MQL Analyzer",
                JOptionPane.INFORMATION_MESSAGE
            );
        });
        
        helpMenu.add(riskScoreHelpItem);
        helpMenu.add(aboutItem);
        
        // Menüs zur Menüleiste hinzufügen
        menuBar.add(fileMenu);
        menuBar.add(dbMenu);
        menuBar.add(viewMenu);
        menuBar.add(debugMenu); // Debug-Menü hinzugefügt
        menuBar.add(helpMenu);
    }
    
 // Neue Methode zur Anzeige des Equity Drawdown Dialogs
    private void showEquityDrawdownDialog() {
        // Hole die aktuellen Provider-Daten direkt von der Tabelle
        Map<String, ProviderStats> currentStats = mainTable.getCurrentProviderStats();
        
        // Debug-Ausgaben
        System.out.println("MenuManager: showEquityDrawdownDialog wird aufgerufen");
        System.out.println("MenuManager: Anzahl der Provider in currentStats: " + 
                           (currentStats != null ? currentStats.size() : "null"));
        
        // Prüfe ob die Provider-Map Daten enthält
        if (currentStats == null || currentStats.isEmpty()) {
            System.out.println("MenuManager: Keine Provider-Daten vorhanden! Hole alle verfügbaren Provider...");
            
            // Als Fallback alle verfügbaren Provider verwenden
            currentStats = dataManager.getStats();
            
            System.out.println("MenuManager: Anzahl aller verfügbaren Provider: " + 
                               (currentStats != null ? currentStats.size() : "null"));
            
            if (currentStats == null || currentStats.isEmpty()) {
                JOptionPane.showMessageDialog(
                    parentFrame,
                    "Es sind keine Provider-Daten zum Anzeigen verfügbar.\n" +
                    "Bitte stellen Sie sicher, dass Provider geladen wurden und keine zu strengen Filter gesetzt sind.",
                    "Keine Daten verfügbar",
                    JOptionPane.WARNING_MESSAGE
                );
                return; // Dialog nicht öffnen, wenn keine Daten verfügbar sind
            }
        }
        
        EquityDrawdownDialog dialog = new EquityDrawdownDialog(
            parentFrame,
            currentStats,
            mainTable.getHtmlDatabase(),
            rootPath
        );
        dialog.setVisible(true);
    }
    
    private void setDownloadPath() {
        String currentPath = config.getDownloadPath();
        JFileChooser chooser = new JFileChooser(currentPath);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        if (chooser.showDialog(parentFrame, "Select Download Directory") == JFileChooser.APPROVE_OPTION) {
            String newPath = chooser.getSelectedFile().getAbsolutePath();
            config.setDownloadPath(newPath);
            
            // History Service mit rootPath aktualisieren, nicht mit dem newPath
            historyService.initialize(rootPath);
            
            // Statusleiste aktualisieren, um den neuen Pfad anzuzeigen
            parentFrame.updateStatusBar();
            
            if (JOptionPane.showConfirmDialog(parentFrame, 
                "Download path updated. Reload data?", 
                "Reload", 
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                reloadData(newPath);
            }
            
            // Nach der Änderung des Pfades erneut überprüfen
            parentFrame.verifyFileSystemAccess();
        }
    }
    
    private void reloadData(String newPath) {
        try {
            dataManager.loadData(newPath);
            mainTable.refreshTableData();
            parentFrame.updateStatusBar();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentFrame, 
                "Error loading data from new path", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void showCompareDialog() {
        CompareEquityCurvesDialog dialog = new CompareEquityCurvesDialog(
            parentFrame, 
            mainTable.getCurrentProviderStats(), 
            rootPath
        );
        dialog.setVisible(true);
    }

    public void runMartingaleGridAnalysis(boolean force) {
        Map<String, ProviderStats> allStats = dataManager.getStats();
        List<String> list;
        if (force) {
            list = new java.util.ArrayList<>(allStats.keySet());
        } else {
            list = historyService.getProvidersNeedingAnalysis(allStats);
        }
        
        if (!list.isEmpty()) {
            ui.AnalysisProgressDialog dialog = new ui.AnalysisProgressDialog(
                parentFrame, 
                list, 
                allStats, 
                () -> mainTable.forceCompleteReinitialize()
            );
            dialog.setVisible(true);
        }
    }
}