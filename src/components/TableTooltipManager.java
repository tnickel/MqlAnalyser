package components;

import java.awt.event.MouseEvent;
import java.util.logging.Logger;

import javax.swing.ToolTipManager;

import data.DataManager;
import data.ProviderStats;
import models.HighlightTableModel;
import utils.HtmlDatabase;

/**
 * Klasse für das Tooltip-Management der MainTable.
 * Behandelt die Erstellung und Anzeige von Tooltips für verschiedene Spalten.
 */
public class TableTooltipManager {
    private static final Logger LOGGER = Logger.getLogger(TableTooltipManager.class.getName());
    
    private final MainTable mainTable;
    private final HighlightTableModel model;
    private final DataManager dataManager;
    private final HtmlDatabase htmlDatabase;
    
    public TableTooltipManager(MainTable mainTable, HighlightTableModel model, 
                              DataManager dataManager, HtmlDatabase htmlDatabase) {
        this.mainTable = mainTable;
        this.model = model;
        this.dataManager = dataManager;
        this.htmlDatabase = htmlDatabase;
    }
    
    /**
     * Stellt sicher, dass die Tooltip-Funktionalität aktiviert ist
     */
    public void ensureTooltipsEnabled() {
        // Tooltip-Setup
        ToolTipManager.sharedInstance().setInitialDelay(200);
        ToolTipManager.sharedInstance().setDismissDelay(8000);
        ToolTipManager.sharedInstance().registerComponent(mainTable);
        ToolTipManager.sharedInstance().setEnabled(true);
        
        LOGGER.info("Tooltip-Funktionalität explizit aktiviert");
    }
    
    /**
     * Initialisiert die Tooltip-Funktionalität
     */
    public void initializeTooltips() {
        // Tooltip-Setup
        ToolTipManager.sharedInstance().setInitialDelay(200);
        ToolTipManager.sharedInstance().setDismissDelay(8000);
        ToolTipManager.sharedInstance().registerComponent(mainTable);
    }
    
    /**
     * Erstellt Tooltips für Tabellen-Zellen basierend auf der Spalte und dem Inhalt
     * 
     * @param event MouseEvent für die Position
     * @return Tooltip-Text oder null
     */
    public String getToolTipText(MouseEvent event) {
        int row = mainTable.rowAtPoint(event.getPoint());
        int column = mainTable.columnAtPoint(event.getPoint());
        
        if (row < 0 || column < 0) {
            return null;
        }
        
        // Umwandlung in Modell-Indizes
        int modelRow = mainTable.convertRowIndexToModel(row);
        int modelColumn = mainTable.convertColumnIndexToModel(column);
        
        if (modelRow < 0 || modelColumn < 0 || modelRow >= model.getRowCount() || modelColumn >= model.getColumnCount()) {
            return null;
        }
        
        Object value = model.getValueAt(modelRow, modelColumn);
        if (value == null) {
            return null;
        }
        
        // Spezielle Behandlung für bestimmte Spalten
        String columnName = model.getColumnName(modelColumn);
        
        // Für Signal Provider-Spalte (normalerweise Spalte 1)
        if (modelColumn == 1) {
            return createProviderTooltip(value.toString());
        }
        
        // Für MPDD-Spalten (5, 6, 7, 8) spezielle Tooltips verwenden
        if (modelColumn >= 5 && modelColumn <= 8) {
            return createMPDDTooltip(modelRow, modelColumn);
        }
        
        // Für alle anderen numerischen Werte eine formatierte Anzeige
        if (value instanceof Number) {
            return createNumericTooltip(mainTable.getColumnName(column), (Number) value);
        }
        
        // Standardrückgabe für alle anderen Fälle
        return mainTable.getColumnName(column) + ": " + value.toString();
    }
    
    /**
     * Erstellt Tooltip für Provider-Spalte
     */
    private String createProviderTooltip(String providerName) {
        ProviderStats stats = dataManager.getStats().get(providerName);
        if (stats != null) {
            return model.buildCurrencyPairsTooltip(stats);
        }
        return null;
    }
    
    /**
     * Erstellt Tooltip für MPDD-Spalten
     */
    private String createMPDDTooltip(int modelRow, int modelColumn) {
        String providerName = model.getValueAt(modelRow, 1).toString();
        
        // Monatsanzahl aus der Spalte ableiten (3, 6, 9, 12 Monate)
        int months = 3;
        if (modelColumn == 6) months = 6;
        else if (modelColumn == 7) months = 9;
        else if (modelColumn == 8) months = 12;
        
        // Den HTML-Tooltip von der HtmlDatabase abrufen
        String mpddTooltip = htmlDatabase.getMPDDTooltip(providerName, months);
        if (mpddTooltip != null && !mpddTooltip.isEmpty()) {
            return mpddTooltip;
        }
        
        return null;
    }
    
    /**
     * Erstellt Tooltip für numerische Werte
     */
    private String createNumericTooltip(String columnName, Number value) {
        double numValue = value.doubleValue();
        if (Math.abs(numValue) < 0.01 && numValue != 0) {
            // Für sehr kleine Werte wissenschaftliche Notation
            return String.format("%s: %.6e", columnName, numValue);
        } else {
            // Für normale Werte 2 Nachkommastellen
            return String.format("%s: %.2f", columnName, numValue);
        }
    }
}