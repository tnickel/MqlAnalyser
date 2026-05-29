package models;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.swing.table.DefaultTableModel;

import data.ProviderStats;
import data.Trade;
import db.HistoryDatabaseManager;
import services.RiskAnalysisServ;
import utils.HtmlDatabase;

public class HighlightTableModel extends DefaultTableModel {
  
	private static final String[] COLUMN_NAMES = {
		    "No.", "Signal Provider", "Balance", "Subscribers", "3MPDD", "6MPDD", "9MPDD", "12MPDD", 
		    "3MProfProz", "Trades", "Trade Days", "Days", "Win Rate %", "Total Profit", 
		    "Avg Profit/Trade", "Max Drawdown %", "Equity Drawdown %", "Profit Factor", 
		    "MaxTrades", "MaxLots", "Max Duration (h)", "Risiko", "Risk Score", "S/L", "T/P", 
		    "Start Date", "End Date", "Stabilitaet", "Steigung", "MaxDDGraphic", "EquityDrawdown3M%"
		};

  
  private final HtmlDatabase htmlDatabase;
  private final HistoryDatabaseManager dbManager;
  
  @Override
  public boolean isCellEditable(int row, int column) {
      return false;  // Verhindert das Editieren aller Zellen
  }

  public HighlightTableModel(String rootPath) {
      super(COLUMN_NAMES, 0);
      this.htmlDatabase = new HtmlDatabase(rootPath);
      this.dbManager = HistoryDatabaseManager.getInstance(rootPath);
  }

  public HtmlDatabase getHtmlDatabase() {
      return this.htmlDatabase;
  }

  

  @Override
  public Class<?> getColumnClass(int columnIndex) {
      switch (columnIndex) {
          case 0:  // No
          case 3:  // Subscribers
          case 9:  // Trades
          case 10: // Trade Days
          case 11: // Days
          case 18: // MaxTrades
          case 20: // Max Duration
          case 22: // Risk Score
          case 23: // S/L
          case 24: // T/P
              return Integer.class;
          case 2:  // Balance
          case 4:  // 3MPDD
          case 5:  // 6MPDD
          case 6:  // 9MPDD
          case 7:  // 12MPDD
          case 8:  // 3MProfProz
          case 12: // Win Rate
          case 13: // Total Profit
          case 14: // Avg Profit/Trade
          case 15: // Max Drawdown
          case 16: // Equity Drawdown
          case 17: // Profit Factor
          case 19: // MaxLots
          case 27: // Stabilität
          case 28: // Steigung
          case 29: // MaxDDGraphic
          case 30: // EquityDrawdown3M%
              return Double.class;
          default:
              return String.class;
      }
  }
  
  public double calculateMPDD(double monthlyProfitPercent, double maxEquityDrawdown) {
      if (maxEquityDrawdown == 0.0) {
          return 0.0;  // Verhindert Division durch Null
      }
      return monthlyProfitPercent / maxEquityDrawdown;
  }

  private long calculateDaysBetween(ProviderStats stats) {
      return Math.abs(ChronoUnit.DAYS.between(stats.getStartDate(), stats.getEndDate())) + 1;
  }
  
  private double calculateTrend(Map<String, Double> monthlyProfits, String currentMonth) {
	    // Wenn monthlyProfits leer ist oder kein currentMonth vorhanden ist, gib 0.0 zurück
	    if (monthlyProfits.isEmpty() || currentMonth == null) {
	        return 0.0;
	    }
	    
	    // Hole die vorherigen Monate (ohne den aktuellen)
	    TreeMap<String, Double> sortedProfits = new TreeMap<>(monthlyProfits);
	    
	    // Sicherheitsprüfung für den currentMonth
	    if (!sortedProfits.containsKey(currentMonth)) {
	        return 0.0;
	    }
	    
	    String[] months = sortedProfits.headMap(currentMonth, false).keySet().toArray(new String[0]);
	    int monthsAvailable = months.length;
	    
	    // Wenn keine vorherigen Monate verfügbar sind
	    if (monthsAvailable == 0) {
	        return 0.0;
	    }
	    
	    // Fall 1: Mindestens 3 Monate verfügbar - ursprüngliche Berechnung
	    if (monthsAvailable >= 3) {
	        double profit1 = sortedProfits.get(months[monthsAvailable - 3]);  // Ältester Monat
	        double profit2 = sortedProfits.get(months[monthsAvailable - 2]);  // Mittlerer Monat
	        double profit3 = sortedProfits.get(months[monthsAvailable - 1]);  // Neuester Monat
	        
	        // Berechne die Steigungen zwischen den Punkten
	        double slope1 = profit2 - profit1;  // Steigung zwischen Monat 1 und 2
	        double slope2 = profit3 - profit2;  // Steigung zwischen Monat 2 und 3
	        
	        // Wenn beide Steigungen positiv sind (durchgehend steigend)
	        if (slope1 > 0 && slope2 > 0) {
	            // Berechne Durchschnittssteigung und verstärke den Effekt
	            return (slope1 + slope2) / 2.0;
	        } else if (slope1 > 0 || slope2 > 0) {
	            // Wenn nur eine Steigung positiv ist, gib einen kleineren Wert zurück
	            return Math.max(slope1, slope2) / 4.0;
	        } else {
	            // Wenn beide Steigungen negativ sind, gib einen negativen Wert zurück
	            return (slope1 + slope2) / 2.0;
	        }
	    }
	    // Fall 2: Nur 2 Monate verfügbar - neue Berechnung
	    else if (monthsAvailable == 2) {
	        double profit1 = sortedProfits.get(months[monthsAvailable - 2]);  // Älterer Monat
	        double profit2 = sortedProfits.get(months[monthsAvailable - 1]);  // Neuerer Monat
	        
	        // Berechne die Steigung zwischen den beiden Monaten
	        double slope = profit2 - profit1;
	        
	        // Wenn die Steigung positiv ist (zunehmender Trend)
	        if (slope > 0) {
	            // Gib die Steigung zurück, aber etwas reduziert, da wir weniger Datenpunkte haben
	            return slope * 0.8; // 80% der Steigung als konservativere Schätzung
	        } else {
	            // Bei negativer Steigung gib einen negativen Wert zurück
	            return slope * 0.8;
	        }
	    }
	    // Fall 3: Nur 1 Monat verfügbar
	    else if (monthsAvailable == 1) {
	        double profit = sortedProfits.get(months[0]);
	        
	        // Wenn der Profit positiv ist, gib einen kleinen positiven Wert zurück
	        if (profit > 0) {
	            return profit * 0.2; // 20% des Profits als vorsichtige Schätzung
	        } else {
	            return profit * 0.2; // Gleichermaßen für negative Werte
	        }
	    }
	    
	    // Fallback (sollte nie erreicht werden)
	    return 0.0;
	}

  public void populateData(Map<String, ProviderStats> statsMap) {
	    setRowCount(0);
	    int rowNum = 1;

	    for (Map.Entry<String, ProviderStats> entry : statsMap.entrySet()) {
	        String providerName = entry.getKey();
	        ProviderStats stats = entry.getValue();
	        
		        double equityDrawdown = htmlDatabase.getEquityDrawdown(providerName);
	        double balance = htmlDatabase.getBalance(providerName);
	        int subscribers = htmlDatabase.getSubscribers(providerName);
	        double maxDDGraphic = htmlDatabase.getEquityDrawdownGraphic(providerName);
            double equityDrawdown3M = htmlDatabase.getMaxDrawdown3M(providerName); // Neue Spalte
	        
	        // Berechne MPDD für verschiedene Zeiträume und aktualisiere die Tooltips
	        htmlDatabase.getMPDD(providerName, 3);  
	        htmlDatabase.getMPDD(providerName, 6);
	        htmlDatabase.getMPDD(providerName, 9);
	        htmlDatabase.getMPDD(providerName, 12);
	        
	        // Berechne die MPDD-Werte für die Anzeige
	        double threeMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 3);
	        double sixMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 6);
	        double nineMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 9);
	        double twelveMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 12);
	        
	        double mpdd3 = calculateMPDD(threeMonthProfit, equityDrawdown);
	        double mpdd6 = calculateMPDD(sixMonthProfit, equityDrawdown);
	        double mpdd9 = calculateMPDD(nineMonthProfit, equityDrawdown);
	        double mpdd12 = calculateMPDD(twelveMonthProfit, equityDrawdown);
	        
	        // Risiko-Kategorie aus der Datenbank laden
	        int riskCategory = dbManager.getProviderRiskCategory(providerName);
	        stats.setRiskCategory(riskCategory);
	        
	        int riskScore = RiskAnalysisServ.calculateRiskScore(stats);
	        double stabilitaet = htmlDatabase.getStabilitaetswert(providerName);
	        
	        Map<String, Double> monthlyProfits = htmlDatabase.getMonthlyProfitPercentages(providerName);
	        double steigung = 0.0;
	        
	        // Sichere Behandlung der Steigungsberechnung
	        if (!monthlyProfits.isEmpty()) {
	            TreeMap<String, Double> sortedMonthProfits = new TreeMap<>(monthlyProfits);
	            if (!sortedMonthProfits.isEmpty()) {
	                try {
	                    String currentMonth = sortedMonthProfits.lastKey();
	                    steigung = calculateTrend(monthlyProfits, currentMonth);
	                    
	                    // Speichere den Steigungswert
	                    htmlDatabase.saveSteigungswert(providerName, steigung);
	                } catch (Exception e) {
	                    // Ignoriere Fehler bei der Steigungsberechnung
	                    System.err.println("Fehler bei der Steigungsberechnung für " + providerName + ": " + e.getMessage());
	                }
	            }
	        }
	        
	        long daysBetween = calculateDaysBetween(stats);
	        
	        addRow(new Object[]{
	            rowNum++, 
	            providerName, 
	            balance,
	            subscribers,
	            mpdd3,
	            mpdd6,
	            mpdd9,
	            mpdd12,
	            threeMonthProfit,
	            stats.getTrades().size(),
	            stats.getTradeDays(),
	            daysBetween,
	            stats.getWinRate(),
	            stats.getTotalProfit(),
	            stats.getAverageProfit(),
	            stats.getMaxDrawdown(),
	            equityDrawdown,
	            stats.getProfitFactor(),
	            stats.getMaxConcurrentTrades(),
	            stats.getMaxConcurrentLots(),
	            stats.getMaxDuration(),
	            riskCategory == 0 ? "-" : String.valueOf(riskCategory), // Risiko als String mit "-" für 0
	            riskScore,
	            stats.hasStopLoss() ? 1 : 0,
	            stats.hasTakeProfit() ? 1 : 0,
	            stats.getStartDate(),
	            stats.getEndDate(),
	            stabilitaet,
	            steigung,
	            maxDDGraphic,
                equityDrawdown3M // Neue Spalte
	        });
	    }
	    fireTableDataChanged();
	}
  
  public Object[] createRowDataForProvider(String providerName, ProviderStats stats) {
	    double equityDrawdown = htmlDatabase.getEquityDrawdown(providerName);
	    double balance = htmlDatabase.getBalance(providerName);
	    int subscribers = htmlDatabase.getSubscribers(providerName);
	    double maxDDGraphic = htmlDatabase.getEquityDrawdownGraphic(providerName);
        double equityDrawdown3M = htmlDatabase.getMaxDrawdown3M(providerName); // Neue Spalte
	    
	    // Berechne MPDD für verschiedene Zeiträume
	    double threeMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 3);
	    double sixMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 6);
	    double nineMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 9);
	    double twelveMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 12);
	    
	    double mpdd3 = calculateMPDD(threeMonthProfit, equityDrawdown);
	    double mpdd6 = calculateMPDD(sixMonthProfit, equityDrawdown);
	    double mpdd9 = calculateMPDD(nineMonthProfit, equityDrawdown);
	    double mpdd12 = calculateMPDD(twelveMonthProfit, equityDrawdown);
	    
	    // Risiko-Kategorie aus der Datenbank laden
	    int riskCategory = dbManager.getProviderRiskCategory(providerName);
	    stats.setRiskCategory(riskCategory);
	    
	    int riskScore = RiskAnalysisServ.calculateRiskScore(stats);
	    double stabilitaet = htmlDatabase.getStabilitaetswert(providerName);
	    
	    Map<String, Double> monthlyProfits = htmlDatabase.getMonthlyProfitPercentages(providerName);
	    double steigung = 0.0;
	    
	    // Währungspaare für Tooltips sammeln
	    String currencyPairsTooltip = buildCurrencyPairsTooltip(stats);
	    
	    // Sichere Behandlung der Steigungsberechnung
	    if (!monthlyProfits.isEmpty()) {
	        TreeMap<String, Double> sortedMonthProfits = new TreeMap<>(monthlyProfits);
	        if (!sortedMonthProfits.isEmpty()) {
	            try {
	                String currentMonth = sortedMonthProfits.lastKey();
	                steigung = calculateTrend(monthlyProfits, currentMonth);
	                
	                // Speichere den Steigungswert
	                htmlDatabase.saveSteigungswert(providerName, steigung);
	            } catch (Exception e) {
	                // Ignoriere Fehler bei der Steigungsberechnung
	                System.err.println("Fehler bei der Steigungsberechnung für " + providerName + ": " + e.getMessage());
	            }
	        }
	    }
	    
	    long daysBetween = calculateDaysBetween(stats);
	    
	    return new Object[]{
	        0, // Platzhalter für die Nummer
	        providerName,
	        balance,
	        subscribers,
	        mpdd3,
	        mpdd6,
	        mpdd9,
	        mpdd12,
	        threeMonthProfit,
	        stats.getTrades().size(),
	        stats.getTradeDays(),
	        daysBetween,
	        stats.getWinRate(),
	        stats.getTotalProfit(),
	        stats.getAverageProfit(),
	        stats.getMaxDrawdown(),
	        equityDrawdown,
	        stats.getProfitFactor(),
	        stats.getMaxConcurrentTrades(),
	        stats.getMaxConcurrentLots(),
	        stats.getMaxDuration(),
	        riskCategory == 0 ? "-" : String.valueOf(riskCategory), // Risiko als String mit "-" für 0
	        riskScore,
	        stats.hasStopLoss() ? 1 : 0,
	        stats.hasTakeProfit() ? 1 : 0,
	        stats.getStartDate(),
	        stats.getEndDate(),
	        stabilitaet,
	        steigung,
	        maxDDGraphic,
            equityDrawdown3M // Neue Spalte
	    };
	}
	
	// Methode zum Erstellen eines Tooltips für Währungspaare
    public String buildCurrencyPairsTooltip(ProviderStats stats) {
        Map<String, Long> currencyPairCounts = stats.getTrades().stream()
                .collect(Collectors.groupingBy(
                    Trade::getSymbol,
                    Collectors.counting()
                ));
        
        // Sortiere nach Anzahl der Trades (absteigend)
        List<Map.Entry<String, Long>> sortedPairs = currencyPairCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        StringBuilder tooltip = new StringBuilder("<html><b>Währungspaare:</b><br>");
        for (Map.Entry<String, Long> entry : sortedPairs) {
            tooltip.append(entry.getKey())
                   .append(": ")
                   .append(entry.getValue())
                   .append(" Trades<br>");
        }
        tooltip.append("</html>");
        
        return tooltip.toString();
    }
    
    /**
     * Gibt alle verwendeten Währungspaare für einen Provider zurück
     * 
     * @param stats ProviderStats Objekt
     * @return Set mit allen verwendeten Währungspaaren
     */
    public Set<String> getUsedCurrencyPairs(ProviderStats stats) {
        return stats.getTrades().stream()
                .map(Trade::getSymbol)
                .collect(Collectors.toSet());
    }
    public void clearData() {
        setRowCount(0);
        fireTableDataChanged();
    }

}