package utils;

import java.time.LocalDate;
import java.util.logging.Logger;

/**
 * Klasse für spezielle Drawdown-Analysen und -Berechnungen.
 * Enthält komplexere Drawdown-Berechnungen wie 3-Monats-Maxima.
 */
public class DrawdownAnalyzer {
    private static final Logger LOGGER = Logger.getLogger(DrawdownAnalyzer.class.getName());
    
    private final FileDataReader fileDataReader;
    
    public DrawdownAnalyzer(FileDataReader fileDataReader) {
        this.fileDataReader = fileDataReader;
    }
    
    /**
     * Ermittelt den maximalen Drawdown der letzten 3 Monate für einen Provider
     * 
     * @param fileName Dateiname des Providers
     * @return Maximaler Drawdown in Prozent oder 0.0 wenn keine Daten gefunden wurden
     */
    public double getMaxDrawdown3M(String fileName) {
        String drawdownData = fileDataReader.readDrawdownChartDataFromFile(fileName);
        if (drawdownData == null || drawdownData.isEmpty()) {
            LOGGER.warning("Keine Drawdown-Daten für " + fileName + " gefunden");
            return 0.0;
        }
        
        // Aktuelles Datum für die 3-Monats-Berechnung
        LocalDate currentDate = LocalDate.now();
        LocalDate threeMonthsAgo = currentDate.minusMonths(3);
        
        double maxDrawdown = 0.0;
        LocalDate maxDrawdownDate = null;
        String[] lines = drawdownData.split("\n");
        
        if (lines.length <= 1) {
            LOGGER.warning("Nicht genügend Drawdown-Daten für " + fileName);
            return 0.0;
        }
        
        // Debug-Ausgabe
        // System.out.println("===== Analysiere Drawdown-Daten für " + fileName + " =====");
        // System.out.println("Zeitraum: von " + threeMonthsAgo + " bis " + currentDate);
        // System.out.println("Gefundene Zeilen: " + lines.length);
        
        // Für Debugging: Alle Drawdown-Werte ausgeben, die größer als 10% sind
        // System.out.println("Signifikante Drawdown-Werte (>10%):");
        
        int relevantDataCount = 0;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            String[] parts = line.split(":");
            if (parts.length != 2) {
                continue;
            }
            
            try {
                // Parsen des Datums aus dem Format YYYY-MM-DD
                String dateStr = parts[0].trim();
                LocalDate lineDate = LocalDate.parse(dateStr);
                
                // Nur Daten der letzten 3 Monate berücksichtigen
                if (lineDate.isAfter(threeMonthsAgo) || lineDate.isEqual(threeMonthsAgo)) {
                    relevantDataCount++;
                    
                    // Drawdown-Wert parsen (Prozentangabe mit Komma)
                    String valueStr = parts[1].trim().replace("%", "").replace(",", ".");
                    double drawdown = Double.parseDouble(valueStr);
                    
                    // Für Debug: Signifikante Werte ausgeben
                    // if (drawdown > 10.0) {
                    //     System.out.println(dateStr + ": " + drawdown + "%");
                    // }
                    
                    // Maximum aktualisieren
                    if (drawdown > maxDrawdown) {
                        maxDrawdown = drawdown;
                        maxDrawdownDate = lineDate;
                    }
                }
            } catch (Exception e) {
                System.err.println("Fehler beim Parsen der Zeile: " + line + " - " + e.getMessage());
            }
        }
        
        // System.out.println("Relevante Datenpunkte in den letzten 3 Monaten: " + relevantDataCount);
        // System.out.println("Maximaler 3-Monats-Drawdown: " + maxDrawdown + "%" + 
        //                   (maxDrawdownDate != null ? " (am " + maxDrawdownDate + ")" : ""));
        // System.out.println("============================================");
        
        // Speichere den maximalen 3-Monats-Drawdown in der Datenbank
        fileDataReader.updateCacheData(fileName, "MaxDrawdown3M", String.format("%.2f", maxDrawdown).replace(',', '.'));
        
        return maxDrawdown;
    }
    
    /**
     * Wrapper-Methode für das Lesen von Drawdown-Chart-Daten
     * 
     * @param fileName Name der Datei
     * @return String mit Drawdown-Chart-Daten oder null wenn nicht gefunden
     */
    public String getDrawdownChartData(String fileName) {
        return fileDataReader.readDrawdownChartDataFromFile(fileName);
    }
}