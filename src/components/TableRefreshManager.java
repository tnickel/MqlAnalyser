package components;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;

import data.DataManager;
import data.ProviderStats;
import models.HighlightTableModel;
import renderers.HighlightRenderer;
import renderers.NumberFormatRenderer;
import renderers.RiskScoreRenderer;
import services.ProviderHistoryService;
import utils.HtmlDatabase;

/**
 * Klasse für das Refresh-Management der MainTable.
 * Behandelt komplexe Refresh-Operationen und Neuinitialisierungen.
 */
public class TableRefreshManager {
    private static final Logger LOGGER = Logger.getLogger(TableRefreshManager.class.getName());
    
    private final MainTable mainTable;
    private final HighlightTableModel model;
    private final DataManager dataManager;
    private final HtmlDatabase htmlDatabase;
    private final ProviderHistoryService historyService;
    private final TableFilterManager filterManager;
    private final FavoritesFilterManager favoritesManager;
    private final TableTooltipManager tooltipManager;
    
    // Renderer
    private HighlightRenderer renderer;
    private RiskScoreRenderer riskRenderer;
    
    public TableRefreshManager(MainTable mainTable, HighlightTableModel model, DataManager dataManager,
                              HtmlDatabase htmlDatabase, ProviderHistoryService historyService,
                              TableFilterManager filterManager, FavoritesFilterManager favoritesManager,
                              TableTooltipManager tooltipManager) {
        this.mainTable = mainTable;
        this.model = model;
        this.dataManager = dataManager;
        this.htmlDatabase = htmlDatabase;
        this.historyService = historyService;
        this.filterManager = filterManager;
        this.favoritesManager = favoritesManager;
        this.tooltipManager = tooltipManager;
    }
    
    /**
     * Setzt die Renderer-Referenzen
     */
    public void setRenderers(HighlightRenderer renderer, RiskScoreRenderer riskRenderer) {
        this.renderer = renderer;
        this.riskRenderer = riskRenderer;
    }
    
    /**
     * Aktualisiert die Tabellenrenderer und die Ansicht nach einer Favoriten-Änderung
     * Mit verbesserter Tooltip-Unterstützung
     */
    public void refreshTableRendering() {
        LOGGER.info("Beginne radikales TableRendering-Update nach Favoriten-Änderung");
        
        // Die Implementierung dieser Methode erstellt alle Renderer komplett neu
        SwingUtilities.invokeLater(() -> {
            try {
                // Aktuelle Tooltip-Einstellungen sichern
                int initialDelay = ToolTipManager.sharedInstance().getInitialDelay();
                int dismissDelay = ToolTipManager.sharedInstance().getDismissDelay();
                boolean isToolTipEnabled = ToolTipManager.sharedInstance().isEnabled();
                
                // Alte Renderer merken, um ihre Properties zu kopieren
                String oldSearchText = "";
                if (renderer != null) {
                    oldSearchText = renderer.getSearchText();
                }
                
                // Komplett neue Renderer erstellen
                this.renderer = new HighlightRenderer();
                this.renderer.setSearchText(oldSearchText);
                this.riskRenderer = new RiskScoreRenderer(this.renderer);
                
                LOGGER.info("Neue Renderer-Instanzen erstellt");
                
                // Alle Spalten auf den neuen Renderer setzen
                setupColumnRenderers();
                
                // Globale Default-Renderer aktualisieren
                mainTable.setDefaultRenderer(Object.class, renderer);
                mainTable.setDefaultRenderer(Number.class, new NumberFormatRenderer(renderer));
                
                LOGGER.info("Neue Renderer für alle Spalten gesetzt");
                
                // Tooltips wieder aktivieren
                restoreTooltipSettings(isToolTipEnabled, initialDelay, dismissDelay);
                
                // Layout und Darstellung vollständig aktualisieren
                performUIUpdates();
                
                // Verzögertes mehrfaches Repaint, falls das erste nicht ausreicht
                scheduleDelayedRepaints();
                
            } catch (Exception e) {
                LOGGER.severe("Fehler beim Neuinitialisieren der Renderer: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Diese zweite Methode stellt eine noch radikalere Lösung dar, falls die erste nicht funktioniert
     * Erweitert mit verbesserter Tooltip-Unterstützung
     */
    public void forceCompleteReinitialize() {
        LOGGER.info("Beginne komplette Neuinitialisierung der Tabelle");
        
        SwingUtilities.invokeLater(() -> {
            try {
                // Aktuelle Tooltip-Einstellungen sichern
                int initialDelay = ToolTipManager.sharedInstance().getInitialDelay();
                int dismissDelay = ToolTipManager.sharedInstance().getDismissDelay();
                boolean isToolTipEnabled = ToolTipManager.sharedInstance().isEnabled();
                
                // Aktuelle Selektion und andere Zustände sichern
                int[] selectedRows = mainTable.getSelectedRows();
                
                // Alle Provider neu laden
                model.populateData(dataManager.getStats());
                
                // Neue Renderer kreieren
                this.renderer = new HighlightRenderer();
                this.riskRenderer = new RiskScoreRenderer(this.renderer);
                
                // Renderer komplett neu zuweisen
                mainTable.setDefaultRenderer(Object.class, renderer);
                mainTable.setDefaultRenderer(Number.class, new NumberFormatRenderer(renderer));
                
                setupColumnRenderers();
                
                // Hier können wir auch Filtereinstellungen neu anwenden, falls vorhanden
                if (favoritesManager.getCurrentCategory() > 0) {
                    favoritesManager.filterByCategory(favoritesManager.getCurrentCategory());
                }
                
                // Tooltips wieder aktivieren
                restoreTooltipSettings(isToolTipEnabled, initialDelay, dismissDelay);
                
                // Aggressive UI-Updates
                mainTable.updateUI();
                mainTable.revalidate();
                mainTable.repaint();
                
                // Wenn wir Zeilen ausgewählt hatten, versuchen wir die Auswahl wiederherzustellen
                restoreSelection(selectedRows);
                
                // Verzögerte Updates mit zunehmendem Abstand
                scheduleProgressiveRepaints();
                
                LOGGER.info("Komplette Neuinitialisierung abgeschlossen");
                
            } catch (Exception e) {
                LOGGER.severe("Fehler bei kompletter Neuinitialisierung: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Aktualisiert die Tabellendaten und führt notwendige Berechnungen durch
     */
    public void refreshTableData() {
        // Cache im Renderer leeren, damit Favoriten und Bad Provider korrekt angezeigt werden
        if (renderer != null) {
            renderer.clearCache();
        }
        
        filterManager.refreshFilteredData();
        
        // Prüfen, ob wöchentliche Speicherung erforderlich ist
        historyService.checkAndPerformWeeklySave();
        
        // Tabelle neu zeichnen nach Aktualisierung
        mainTable.repaint();
        
        // 3MPDD-Werte aus den .txt-Dateien lesen und in der History speichern
        for (Map.Entry<String, ProviderStats> entry : dataManager.getStats().entrySet()) {
            String providerName = entry.getKey();
            
            // 3MPDD-Wert direkt aus der .txt-Datei lesen
            double mpdd3 = htmlDatabase.getMPDD(providerName, 3);
            
            // Speichere den Wert in der Datenbank
            historyService.store3MpddValue(providerName, mpdd3);
        }
    }
    
    /**
     * Richtet die Spalten-Renderer ein
     */
    private void setupColumnRenderers() {
        NumberFormatRenderer numberRenderer = new NumberFormatRenderer(renderer);
        renderers.RisikoRenderer risikoRenderer = new renderers.RisikoRenderer(renderer);
        
        for (int i = 0; i < mainTable.getColumnCount(); i++) {
            String columnName = mainTable.getColumnName(i);
            
            if ("Risiko".equals(columnName)) {
                mainTable.getColumnModel().getColumn(i).setCellRenderer(risikoRenderer);
            }
            else if ("Risk Score".equals(columnName)) {
                mainTable.getColumnModel().getColumn(i).setCellRenderer(riskRenderer);
            }
            else if ("No.".equals(columnName) || "Signal Provider".equals(columnName)) {
                mainTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
            }
            else {
                mainTable.getColumnModel().getColumn(i).setCellRenderer(numberRenderer);
            }
        }
    }
    
    /**
     * Stellt Tooltip-Einstellungen wieder her
     */
    private void restoreTooltipSettings(boolean isToolTipEnabled, int initialDelay, int dismissDelay) {
        if (isToolTipEnabled) {
            ToolTipManager.sharedInstance().setEnabled(true);
        }
        ToolTipManager.sharedInstance().setInitialDelay(initialDelay);
        ToolTipManager.sharedInstance().setDismissDelay(dismissDelay);
        // Tabelle explizit bei ToolTipManager registrieren
        ToolTipManager.sharedInstance().registerComponent(mainTable);
        
        LOGGER.info("Tooltip-Funktionalität wiederhergestellt");
    }
    
    /**
     * Führt UI-Updates durch
     */
    private void performUIUpdates() {
        mainTable.invalidate();
        mainTable.validate();
        
        // Alle Komponenten aggressiv neu zeichnen
        mainTable.updateUI();
        mainTable.revalidate();
        mainTable.repaint();
        
        LOGGER.info("Erstes repaint() ausgeführt");
    }
    
    /**
     * Plant verzögerte Repaints
     */
    private void scheduleDelayedRepaints() {
        int[] delays = {100, 300, 600, 1000}; // mehrere Verzögerungen
        for (int i = 0; i < delays.length; i++) {
            final int index = i;
            Timer timer = new Timer(delays[i], new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    // Nach jeder Verzögerung sicherstellen, dass Tooltips aktiviert sind
                    if (index == delays.length - 1) {
                        tooltipManager.ensureTooltipsEnabled();
                    }
                    mainTable.repaint();
                    LOGGER.info("Verzögertes repaint() #" + (index+1) + " ausgeführt");
                }
            });
            timer.setRepeats(false);
            timer.start();
        }
    }
    
    /**
     * Stellt die Selektion wieder her
     */
    private void restoreSelection(int[] selectedRows) {
        if (selectedRows.length > 0) {
            mainTable.clearSelection();
            for (int row : selectedRows) {
                if (row < mainTable.getRowCount()) {
                    mainTable.addRowSelectionInterval(row, row);
                }
            }
        }
    }
    
    /**
     * Plant progressive Repaints mit zunehmenden Abständen
     */
    private void scheduleProgressiveRepaints() {
        for(int delay : new int[]{100, 300, 600}) {
            final int finalDelay = delay;
            Timer timer = new Timer(delay, e -> {
                if (finalDelay == 600) {
                    // Nach der letzten Verzögerung sicherstellen, dass Tooltips aktiviert sind
                    tooltipManager.ensureTooltipsEnabled();
                }
                mainTable.repaint();
            });
            timer.setRepeats(false);
            timer.start();
        }
    }
    
    // Getter für die Renderer (falls externe Klassen Zugriff benötigen)
    public HighlightRenderer getRenderer() {
        return renderer;
    }
    
    public RiskScoreRenderer getRiskRenderer() {
        return riskRenderer;
    }
}