package utils;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Refactored HtmlDatabase-Klasse die als Hauptschnittstelle fungiert
 * und an spezialisierte Analyzer-Klassen delegiert.
 */
public class HtmlDatabase {
    private static final Logger LOGGER = Logger.getLogger(HtmlDatabase.class.getName());
    
    // Spezialisierte Komponenten
    private final FileDataReader fileDataReader;
    private final BasicDataProvider basicDataProvider;
    private final DrawdownAnalyzer drawdownAnalyzer;
    private final ProfitAnalyzer profitAnalyzer;
    private final StabilityAnalyzer stabilityAnalyzer;
    
    /**
     * Konstruktor der die verschiedenen Analyzer-Komponenten initialisiert
     * 
     * @param downloadpath Pfad zu den Datendateien
     */
    public HtmlDatabase(String downloadpath) {
        // Initialisiere die Komponenten in der richtigen Reihenfolge
        this.fileDataReader = new FileDataReader(downloadpath);
        this.basicDataProvider = new BasicDataProvider(fileDataReader);
        this.drawdownAnalyzer = new DrawdownAnalyzer(fileDataReader);
        this.profitAnalyzer = new ProfitAnalyzer(fileDataReader, basicDataProvider);
        this.stabilityAnalyzer = new StabilityAnalyzer(fileDataReader);
        
        // Protokolliere den tatsächlich verwendeten Pfad
        LOGGER.info("HtmlDatabase refactored initialisiert mit Pfad: " + downloadpath);
    }
    
    // ========== Delegation an BasicDataProvider ==========
    
    public Map<String, Double> getMonthlyProfitPercentages(String fileName) {
        return basicDataProvider.getMonthlyProfitPercentages(fileName);
    }
    
    public double getEquityDrawdown(String fileName) {
        return basicDataProvider.getEquityDrawdown(fileName);
    }
    
    public double getBalance(String fileName) {
        return basicDataProvider.getBalance(fileName);
    }
    
    public double getEquityDrawdownGraphic(String fileName) {
        return basicDataProvider.getEquityDrawdownGraphic(fileName);
    }
    
    public int getSubscribers(String fileName) {
        return basicDataProvider.getSubscribers(fileName);
    }
    
    // ========== Delegation an DrawdownAnalyzer ==========
    
    public double getMaxDrawdown3M(String fileName) {
        return drawdownAnalyzer.getMaxDrawdown3M(fileName);
    }
    
    public String getDrawdownChartData(String fileName) {
        return drawdownAnalyzer.getDrawdownChartData(fileName);
    }
    
    // ========== Delegation an ProfitAnalyzer ==========
    
    public double getAvr3MonthProfit(String fileName) {
        return profitAnalyzer.getAvr3MonthProfit(fileName);
    }
    
    public String get3MonthProfitTooltip(String fileName) {
        return profitAnalyzer.get3MonthProfitTooltip(fileName);
    }
    
    public double getAverageMonthlyProfit(String fileName, int n) {
        return profitAnalyzer.getAverageMonthlyProfit(fileName, n);
    }
    
    public List<String> getLastThreeMonthsDetails(String fileName) {
        return profitAnalyzer.getLastThreeMonthsDetails(fileName);
    }
    
    // ========== Delegation an StabilityAnalyzer ==========
    
    public double getStabilitaetswert(String fileName) {
        return stabilityAnalyzer.getStabilitaetswert(fileName);
    }
    
    public String getStabilitaetswertDetails(String fileName) {
        return stabilityAnalyzer.getStabilitaetswertDetails(fileName);
    }
    
    public void saveSteigungswert(String fileName, double steigung) {
        stabilityAnalyzer.saveSteigungswert(fileName, steigung);
    }

    public double getSteigungswert(String fileName) {
        return stabilityAnalyzer.getSteigungswert(fileName);
    }
    
    // ========== Delegation an FileDataReader ==========
    
    public boolean checkFileAccess() {
        return fileDataReader.checkFileAccess();
    }
    
    public String getRootPath() {
        return fileDataReader.getRootPath();
    }
    
    // ========== MPDD Management (jetzt über BasicDataProvider) ==========
    
    public double getMPDD(String fileName, int months) {
        if (months == 3) {
            return basicDataProvider.get3MPDD(fileName);
        } else {
            return basicDataProvider.getMPDD(fileName, months);
        }
    }
    
    public String getMPDDTooltip(String fileName, int months) {
        return basicDataProvider.getMPDDTooltip(fileName, months);
    }
    
    // ========== Zugriff auf Komponenten für erweiterte Verwendung ==========
    
    /**
     * Gibt den FileDataReader zurück für direkten Dateizugriff
     * @return Die FileDataReader-Instanz
     */
    public FileDataReader getFileDataReader() {
        return fileDataReader;
    }
    
    /**
     * Gibt den BasicDataProvider zurück für Grunddaten
     * @return Die BasicDataProvider-Instanz
     */
    public BasicDataProvider getBasicDataProvider() {
        return basicDataProvider;
    }
    
    /**
     * Gibt den DrawdownAnalyzer zurück für Drawdown-Analysen
     * @return Die DrawdownAnalyzer-Instanz
     */
    public DrawdownAnalyzer getDrawdownAnalyzer() {
        return drawdownAnalyzer;
    }
    
    /**
     * Gibt den ProfitAnalyzer zurück für Profit-Analysen
     * @return Die ProfitAnalyzer-Instanz
     */
    public ProfitAnalyzer getProfitAnalyzer() {
        return profitAnalyzer;
    }
    
    /**
     * Gibt den StabilityAnalyzer zurück für Stabilitäts-Analysen
     * @return Die StabilityAnalyzer-Instanz
     */
    public StabilityAnalyzer getStabilityAnalyzer() {
        return stabilityAnalyzer;
    }
}