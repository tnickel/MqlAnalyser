package components;

import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.TableRowSorter;

import data.DataManager;
import data.FavoritesManager;
import data.ProviderStats;
import models.FilterCriteria;
import models.HighlightTableModel;
import renderers.HighlightRenderer;
import renderers.NumberFormatRenderer;
import renderers.RisikoRenderer;
import renderers.RiskScoreRenderer;
import services.ProviderHistoryService;
import utils.ApplicationConstants;
import utils.HtmlDatabase;
import utils.MqlAnalyserConf;

/**
 * Refactored MainTable-Klasse die als Hauptschnittstelle fungiert
 * und an spezialisierte Manager-Klassen delegiert.
 */
public class MainTable extends JTable {
    private static final Logger LOGGER = Logger.getLogger(MainTable.class.getName());
    
    // Model und grundlegende Komponenten
    private final HighlightTableModel model;
    private final DataManager dataManager;
    private final String rootPath;
    private final HtmlDatabase htmlDatabase;
    private final ProviderHistoryService historyService;
    
    // Renderer
    private HighlightRenderer renderer;
    private RiskScoreRenderer riskRenderer;
    
    // Spezialisierte Manager
    private final TableEventHandler eventHandler;
    private final TableTooltipManager tooltipManager;
    private final TableProviderManager providerManager;
    private final TableButtonFactory buttonFactory;
    private final TableRefreshManager refreshManager;
    private final TableStatusManager statusManager;
    private final TableFilterManager filterManager;
    private final TableColumnManager columnManager;
    private final FavoritesFilterManager favoritesManager;

    /**
     * Konstruktor der die verschiedenen Manager-Komponenten initialisiert
     */
    public MainTable(DataManager dataManager, String rootPathStr) {
        // Validiere den Pfad und korrigiere ihn, falls nötig
        rootPathStr = ApplicationConstants.validateRootPath(rootPathStr, "MainTable.constructor");
        
        this.dataManager = dataManager;
        this.rootPath = rootPathStr;
        
        // Konfiguration aus dem Root-Pfad laden
        MqlAnalyserConf config = new MqlAnalyserConf(rootPathStr);
        String downloadPath = config.getDownloadPath();
        
        // Model und grundlegende Komponenten initialisieren
        this.model = new HighlightTableModel(downloadPath);
        this.renderer = new HighlightRenderer();
        this.riskRenderer = new RiskScoreRenderer();
        this.htmlDatabase = new HtmlDatabase(downloadPath);
        
        // Provider History Service initialisieren
        this.historyService = ProviderHistoryService.getInstance();
        this.historyService.initialize(rootPath);
        
        // Manager initialisieren - Reihenfolge ist wichtig wegen Dependencies
        this.filterManager = new TableFilterManager(this, model, dataManager);
        this.columnManager = new TableColumnManager(this);
        this.favoritesManager = new FavoritesFilterManager(this, model, dataManager.getStats(), rootPath);
        
        this.tooltipManager = new TableTooltipManager(this, model, dataManager, htmlDatabase);
        this.providerManager = new TableProviderManager(this, model, dataManager, rootPath, renderer, filterManager);
        this.eventHandler = new TableEventHandler(this, model, dataManager, htmlDatabase, rootPath, providerManager);
        this.buttonFactory = new TableButtonFactory(this, dataManager, htmlDatabase, rootPath, filterManager);
        this.statusManager = new TableStatusManager(this, model, dataManager, filterManager);
        this.refreshManager = new TableRefreshManager(this, model, dataManager, htmlDatabase, historyService, 
                                                    filterManager, favoritesManager, tooltipManager);
        
        // Cross-references setzen
        refreshManager.setRenderers(renderer, riskRenderer);
        
        // Tabelle initialisieren
        initialize();
        setupComponents();
        
        // Spalten-Sichtbarkeit laden NACH der Initialisierung der Tabelle
        columnManager.loadColumnVisibilitySettings();
        
        // Favoriten-Listener einrichten
        setupFavoritesListener();
        
        // Window-Listener für Cleanup
        setupWindowListener();
    }
    
    /**
     * Initialisiert die grundlegende Tabellen-Konfiguration
     */
    private void initialize() {
        setModel(model);
        setupTableSorter();
        setupRenderers();
        configureColumns();
        tooltipManager.initializeTooltips();
        
        // Daten laden
        model.populateData(dataManager.getStats());
    }
    
    /**
     * Richtet die zusätzlichen Komponenten ein
     */
    private void setupComponents() {
        eventHandler.setupMouseListener();
        eventHandler.setupKeyBindings();
        model.addTableModelListener(e -> statusManager.updateStatus());
    }
    
    /**
     * Konfiguriert den Tabellen-Sorter
     */
    private void setupTableSorter() {
        TableRowSorter<HighlightTableModel> sorter = new TableRowSorter<>(model);
        
        // Konfiguriere den Sorter
        for (int i = 0; i < model.getColumnCount(); i++) {
            sorter.setComparator(i, (java.util.Comparator<Object>) (o1, o2) -> {
                if (o1 == null && o2 == null) return 0;
                if (o1 == null) return -1;
                if (o2 == null) return 1;
                
                // Behandle numerische Spalten
                if (o1 instanceof Number && o2 instanceof Number) {
                    double d1 = ((Number) o1).doubleValue();
                    double d2 = ((Number) o2).doubleValue();
                    return Double.compare(d1, d2);
                }
                
                // String Vergleich für alle anderen Fälle
                return o1.toString().compareTo(o2.toString());
            });
        }
        setRowSorter(sorter);
    }
    
    /**
     * Richtet die Renderer ein
     */
    /**
     * Richtet die Renderer ein
     */
    private void setupRenderers() {
        // Erstelle einen NumberFormatRenderer, der den HighlightRenderer verwendet
        NumberFormatRenderer numberRenderer = new NumberFormatRenderer(renderer);
        
        // Erstelle RisikoRenderer mit HighlightRenderer als Basis
        RisikoRenderer risikoRenderer = new RisikoRenderer(renderer);
        
        // Setze die Renderer für die Spalten basierend auf dem Spaltennamen
        for (int i = 0; i < getColumnCount(); i++) {
            String columnName = getColumnName(i);
            
            // Nach Spaltenname prüfen, nicht nach Index!
            if ("Risiko".equals(columnName)) {
                getColumnModel().getColumn(i).setCellRenderer(risikoRenderer);
            }
            else if ("Risk Score".equals(columnName)) {
                getColumnModel().getColumn(i).setCellRenderer(riskRenderer);
            }
            // Spalte No und Signal Provider verwenden den Standard-Renderer
            else if ("No.".equals(columnName) || "Signal Provider".equals(columnName) || "M/G".equals(columnName)) {
                getColumnModel().getColumn(i).setCellRenderer(renderer);
            }
            // Alle anderen Spalten sind numerisch und verwenden den NumberFormatRenderer
            else {
                getColumnModel().getColumn(i).setCellRenderer(numberRenderer);
            }
        }
    }
    
    /**
     * Konfiguriert die Spaltenbreiten
     */
    private void configureColumns() {
        getColumnModel().getColumn(1).setPreferredWidth(300);  // Signal Provider ist Spalte 1
        getColumnModel().getColumn(1).setMinWidth(250);
    }
    
    /**
     * Richtet den Favoriten-Listener ein
     */
    private void setupFavoritesListener() {
        FavoritesManager favoritesManager = FavoritesManager.getInstance(rootPath);
        favoritesManager.addFavoritesChangeListener(() -> {
            SwingUtilities.invokeLater(() -> {
                refreshManager.refreshTableRendering();
                LOGGER.info("Tabelle nach Favoriten-Änderung aktualisiert");
            });
        });
    }
    
    /**
     * Richtet den Window-Listener für Cleanup ein
     */
    private void setupWindowListener() {
        try {
            JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
            if (parentFrame != null) {
                parentFrame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        historyService.shutdown();
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.warning("Konnte WindowListener nicht hinzufügen: " + e.getMessage());
        }
    }
    
    // ========== Delegation an Manager-Klassen ==========
    
    @Override
    public String getToolTipText(MouseEvent event) {
        String tooltip = tooltipManager.getToolTipText(event);
        return tooltip != null ? tooltip : super.getToolTipText(event);
    }
    
    // Status-Management
    public void setStatusUpdateCallback(Consumer<String> callback) {
        statusManager.setStatusUpdateCallback(callback);
        providerManager.setStatusUpdateCallback(callback);
        buttonFactory.setStatusUpdateCallback(callback);
    }
    
    public String getStatusText() {
        return statusManager.getStatusText();
    }
    
    public void updateStatus() {
        statusManager.updateStatus();
    }
    
    // Provider-Management
    public List<String> getSelectedProviders() {
        return providerManager.getSelectedProviders();
    }

    public Map<String, ProviderStats> getSelectedProvidersMap() {
        return providerManager.getSelectedProvidersMap();
    }
    
    public void manageFavoriteCategory(String providerId, int currentCategory) {
        providerManager.manageFavoriteCategory(providerId, currentCategory);
        
        // Nach Kategorie-Änderung die Tabelle aktualisieren
        Timer timer = new Timer(500, e -> refreshManager.forceCompleteReinitialize());
        timer.setRepeats(false);
        timer.start();
    }
    
    // Button-Factory
    public JButton createShowSignalProviderButton() {
        return buttonFactory.createShowSignalProviderButton();
    }
    
    public JButton createReportButton() {
        return buttonFactory.createReportButton();
    }
    
    // Refresh-Management
    public void refreshTableRendering() {
        refreshManager.refreshTableRendering();
    }
    
    public void forceCompleteReinitialize() {
        refreshManager.forceCompleteReinitialize();
    }
    
    public void refreshTableData() {
        refreshManager.refreshTableData();
    }
    
    // Event-Handling
    public boolean findAndSelectNext(String searchText, int[] currentIndex) {
        return eventHandler.findAndSelectNext(searchText, currentIndex);
    }
    
    // Filter-Management
    public FilterCriteria getCurrentFilter() {
        return filterManager.getCurrentFilter();
    }
    
    public void applyFilter(FilterCriteria criteria) {
        filterManager.applyFilter(criteria);
    }

    public void resetFilter() {
        filterManager.resetFilter();
    }
    
    public Map<String, ProviderStats> getCurrentProviderStats() {
        // Versuche die gefilterten Daten vom FilterManager zu bekommen
        Map<String, ProviderStats> filteredStats = filterManager.getFilteredProviderStats();
        
        // Überprüfe, ob die gefilterten Daten gültig sind
        if (filteredStats == null || filteredStats.isEmpty()) {
            LOGGER.warning("Gefilterte Provider-Statistiken sind leer oder null");
            return dataManager.getStats();
        }
        
        return filteredStats;
    }
    
    // Highlight-Management
    public void highlightSearchText(String text) {
        renderer.setSearchText(text);
        repaint();
    }
    
    public void clearHighlight() {
        renderer.setSearchText("");
        repaint();
    }
    
    // Favoriten-Management
    public void filterFavorites() {
        favoritesManager.filterByFavorites();
    }
    
    public void filterByFavoriteCategory(int category) {
        favoritesManager.filterByCategory(category);
        statusManager.updateStatus();
    }
    
    public FavoritesFilterManager getFavoritesManager() {
        return favoritesManager;
    }

    public int getFavoriteCategory(String providerName) {
        String providerId = providerManager.extractProviderId(providerName);
        return favoritesManager.getFavoritesManager().getFavoriteCategory(providerId);
    }
    
    // Spalten-Management
    public boolean isColumnVisible(int columnIndex) {
        return columnManager.isColumnVisible(columnIndex);
    }
   
    public void setColumnVisible(int columnIndex, boolean visible) {
        columnManager.setColumnVisible(columnIndex, visible);
    }
    
    // Zugriff auf Kern-Komponenten
    public HtmlDatabase getHtmlDatabase() {
        return htmlDatabase;
    }
    
    // Zugriff auf Manager für erweiterte Verwendung
    public TableStatusManager getStatusManager() {
        return statusManager;
    }
    
    public TableRefreshManager getRefreshManager() {
        return refreshManager;
    }
    
    public TableProviderManager getProviderManager() {
        return providerManager;
    }
}