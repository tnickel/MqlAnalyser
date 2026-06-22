package components;

import java.awt.Frame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import data.DataManager;
import data.ProviderStats;
import models.FilterCriteria;
import models.HighlightTableModel;
import ui.LoadingDialog;

public class TableFilterManager {
    private final MainTable mainTable;
    private final HighlightTableModel tableModel;
    private final DataManager dataManager;
    private FilterCriteria currentFilter;

    public TableFilterManager(MainTable mainTable, HighlightTableModel tableModel, DataManager dataManager) {
        this.mainTable = mainTable;
        this.tableModel = tableModel;
        this.dataManager = dataManager;
        this.currentFilter = new FilterCriteria();
        loadSavedFilter();
    }

    public FilterCriteria getCurrentFilter() {
        return currentFilter != null ? currentFilter : new FilterCriteria();
    }

    public void applyFilter(FilterCriteria criteria) {
        this.currentFilter = criteria;
        currentFilter.saveFilters(); // Speichert die Filterwerte nach Anwendung
        refreshFilteredDataWithProgress();
    }

    public void resetFilter() {
        this.currentFilter = new FilterCriteria();
        currentFilter.saveFilters(); // Speichert den leeren Filter
        refreshFilteredData();
    }

    public void loadSavedFilter() {
        if (currentFilter == null) {
            currentFilter = new FilterCriteria();
        }
        currentFilter.loadFilters();
    }

    /**
     * Diese neue Methode führt die Filterung mit einer Fortschrittsanzeige durch
     */
    public void refreshFilteredDataWithProgress() {
        if (currentFilter == null) {
            tableModel.populateData(dataManager.getStats());
            mainTable.updateStatus();
            mainTable.repaint(); // Wichtig: Tabelle neu zeichnen
            return;
        }
        
        LoadingDialog progressDialog = new LoadingDialog(
        	    (Frame)SwingUtilities.getWindowAncestor(mainTable),
        	    "Filter anwenden",
        	    "Filtere Daten..."
        	);
        
        // Starte die Filterung in einem Hintergrund-Thread
        SwingWorker<Map<String, ProviderStats>, Integer> worker = 
            new SwingWorker<Map<String, ProviderStats>, Integer>() {
                
            @Override
            protected Map<String, ProviderStats> doInBackground() throws Exception {
                Map<String, ProviderStats> stats = dataManager.getStats();
                Map<String, ProviderStats> result = new java.util.HashMap<>();
                
                final List<Object[]> allRows = new ArrayList<>();
                final List<String> providerNames = new ArrayList<>();
                
                // Befülle das TableModel einmalig auf dem EDT, um korrekte Normalisierung zu erhalten
                SwingUtilities.invokeAndWait(() -> {
                    tableModel.setRowCount(0);
                    tableModel.populateData(stats);
                    
                    // Extrahiere alle Zeilendaten
                    for (int row = 0; row < tableModel.getRowCount(); row++) {
                        String providerName = (String) tableModel.getValueAt(row, 1);
                        providerNames.add(providerName);
                        
                        Object[] rowData = new Object[tableModel.getColumnCount()];
                        for (int col = 0; col < tableModel.getColumnCount(); col++) {
                            rowData[col] = tableModel.getValueAt(row, col);
                        }
                        allRows.add(rowData);
                    }
                });
                
                int total = allRows.size();
                for (int i = 0; i < total; i++) {
                    int progress = (i * 100) / total;
                    publish(progress);
                    
                    String providerName = providerNames.get(i);
                    Object[] rowData = allRows.get(i);
                    ProviderStats pStats = stats.get(providerName);
                    
                    boolean matches = currentFilter.matches(pStats, rowData);
                    if (matches) {
                        result.put(providerName, pStats);
                    }
                    
                    final int currentIndex = i;
                    final String currentName = providerName;
                    SwingUtilities.invokeLater(() -> {
                        progressDialog.setStatus("Verarbeite Provider: " + currentName + 
                                               " (" + (currentIndex+1) + "/" + total + ")");
                    });
                }
                
                return result;
            }
            
            @Override
            protected void process(List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    int latestProgress = chunks.get(chunks.size() - 1);
                    progressDialog.setProgress(latestProgress);
                }
            }
            
            @Override
            protected void done() {
                try {
                    Map<String, ProviderStats> filteredStats = get();
                    tableModel.populateData(filteredStats);
                    mainTable.updateStatus();
                    mainTable.repaint();
                    progressDialog.complete();
                } catch (Exception e) {
                    e.printStackTrace();
                    progressDialog.dispose();
                }
            }
        };
        
        worker.execute();
        progressDialog.setVisible(true);
    }

    public void refreshFilteredData() {
        Map<String, ProviderStats> stats = dataManager.getStats();
        
        tableModel.setRowCount(0);
        tableModel.populateData(stats);
        
        if (currentFilter == null) {
            mainTable.updateStatus();
            mainTable.repaint();
            return;
        }
        
        Map<String, ProviderStats> result = new java.util.HashMap<>();
        List<Object[]> matchedRows = new ArrayList<>();
        
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            String providerName = (String) tableModel.getValueAt(row, 1);
            Object[] rowData = new Object[tableModel.getColumnCount()];
            for (int col = 0; col < tableModel.getColumnCount(); col++) {
                rowData[col] = tableModel.getValueAt(row, col);
            }
            
            ProviderStats pStats = stats.get(providerName);
            if (pStats != null && currentFilter.matches(pStats, rowData)) {
                result.put(providerName, pStats);
                matchedRows.add(rowData);
            }
        }
        
        tableModel.setRowCount(0);
        int num = 1;
        for (Object[] row : matchedRows) {
            row[0] = num++;
            tableModel.addRow(row);
        }
        
        mainTable.updateStatus();
        mainTable.repaint();
    }

    /**
     * Gibt die aktuell gefilterten Provider-Statistiken zurück.
     * 
     * @return Map mit Providername als Schlüssel und ProviderStats als Wert
     */
    public Map<String, ProviderStats> getFilteredProviderStats() {
        // Logger für Debugging
        Logger logger = Logger.getLogger(TableFilterManager.class.getName());
        logger.info("getFilteredProviderStats wird aufgerufen");
        
        // Ergebnismap erstellen
        Map<String, ProviderStats> filteredStats = new HashMap<>();
        
        try {
            // Alle Statistiken vom DataManager holen
            Map<String, ProviderStats> allStats = dataManager.getStats();
            
            if (allStats == null || allStats.isEmpty()) {
                logger.warning("Keine Provider-Statistiken im DataManager gefunden");
                return filteredStats; // Leere Map zurückgeben
            }
            
            // Prüfe ob ein Filter angewendet wurde - ohne isActive() Methode
            if (currentFilter != null && tableModel.getRowCount() < allStats.size()) {
                logger.info("Filter scheint aktiv zu sein (weniger Zeilen als Provider)");
                
                // Durchlaufe das aktuelle TableModel, um gefilterte Providernamen zu erhalten
                for (int row = 0; row < tableModel.getRowCount(); row++) {
                    String providerName = (String) tableModel.getValueAt(row, 1); // Spalte 1 enthält Provider-Namen
                    
                    if (allStats.containsKey(providerName)) {
                        filteredStats.put(providerName, allStats.get(providerName));
                    }
                }
            } else {
                // Wenn kein Filter aktiv ist, alle Provider zurückgeben
                logger.info("Kein Filter aktiv oder keine Filterung angewendet, verwende alle Provider");
                filteredStats.putAll(allStats);
            }
            
            logger.info("Filterergebnis: " + filteredStats.size() + " Provider");
            
        } catch (Exception e) {
            logger.severe("Fehler in getFilteredProviderStats: " + e.getMessage());
            e.printStackTrace();
        }
        
        return filteredStats;
    }
}