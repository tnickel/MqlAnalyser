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
		    "No.", "Signal Provider", "Score", "Balance", "Subscribers", "3MPDD", "6MPDD", "9MPDD", "12MPDD", 
		    "3MProfProz", "Trades", "Trade Days", "Pairs", "Days", "Win Rate %", "Total Profit", 
		    "Avg Profit/Trade", "Max Drawdown %", "Equity Drawdown %", "Profit Factor", 
		    "MaxTrades", "MaxLots", "Max Duration (h)", "Avg Duration (h)", "Risiko", "Risk Score", "S/L", "T/P", 
		    "Start Date", "End Date", "Stabilitaet", "Steigung", "MaxDDGraphic", "EquityDrawdown3M%", "M/G"
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

  

  private double cachedMinTrades = 0.0;
  private double cachedMaxTrades = 1.0;
  private double cachedMinTradeDays = 0.0;
  private double cachedMaxTradeDays = 1.0;
  private double cachedMinMpdd3 = 0.0;
  private double cachedMaxMpdd3 = 1.0;
  private double cachedMinEd = 0.0;
  private double cachedMaxEd = 1.0;
  private double cachedMinSubscribers = 0.0;
  private double cachedMaxSubscribers = 1.0;
  private double cachedMinMonthlyProfit = 0.0;
  private double cachedMaxMonthlyProfit = 1.0;

  private double normalize(double val, double min, double max, boolean higherIsBetter) {
      if (max <= min) {
          return 50.0;
      }
      double norm;
      if (higherIsBetter) {
          norm = (val - min) / (max - min) * 100.0;
      } else {
          norm = (max - val) / (max - min) * 100.0;
      }
      return Math.max(0.0, Math.min(100.0, norm));
  }

  private static class ProviderMetrics {
      final double trades;
      final double tradeDays;
      final double mpdd3;
      final double equityDrawdown;
      final double subscribers;
      final double avgMonthlyProfit;

      ProviderMetrics(double trades, double tradeDays, double mpdd3, double equityDrawdown, double subscribers, double avgMonthlyProfit) {
          this.trades = trades;
          this.tradeDays = tradeDays;
          this.mpdd3 = mpdd3;
          this.equityDrawdown = equityDrawdown;
          this.subscribers = subscribers;
          this.avgMonthlyProfit = avgMonthlyProfit;
      }
  }

  @Override
  public Class<?> getColumnClass(int columnIndex) {
      switch (columnIndex) {
          case 0:  // No
          case 4:  // Subscribers
          case 10: // Trades
          case 11: // Trade Days
          case 12: // Pairs
          case 13: // Days
          case 20: // MaxTrades
          case 22: // Max Duration
          case 25: // Risk Score
          case 26: // S/L
          case 27: // T/P
              return Integer.class;
          case 2:  // Score
          case 3:  // Balance
          case 5:  // 3MPDD
          case 6:  // 6MPDD
          case 7:  // 9MPDD
          case 8:  // 12MPDD
          case 9:  // 3MProfProz
          case 14: // Win Rate
          case 15: // Total Profit
          case 16: // Avg Profit/Trade
          case 17: // Max Drawdown
          case 18: // Equity Drawdown
          case 19: // Profit Factor
          case 21: // MaxLots
          case 23: // Avg Duration
          case 30: // Stabilität
          case 31: // Steigung
          case 32: // MaxDDGraphic
          case 33: // EquityDrawdown3M%
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

	    // Pass 1: Gather metrics to calculate min/max for normalization
	    cachedMinTrades = Double.MAX_VALUE; cachedMaxTrades = -Double.MAX_VALUE;
	    cachedMinTradeDays = Double.MAX_VALUE; cachedMaxTradeDays = -Double.MAX_VALUE;
	    cachedMinMpdd3 = Double.MAX_VALUE; cachedMaxMpdd3 = -Double.MAX_VALUE;
	    cachedMinEd = Double.MAX_VALUE; cachedMaxEd = -Double.MAX_VALUE;
	    cachedMinSubscribers = Double.MAX_VALUE; cachedMaxSubscribers = -Double.MAX_VALUE;
	    cachedMinMonthlyProfit = Double.MAX_VALUE; cachedMaxMonthlyProfit = -Double.MAX_VALUE;

	    java.util.Map<String, ProviderMetrics> metricsCache = new java.util.HashMap<>();

	    for (Map.Entry<String, ProviderStats> entry : statsMap.entrySet()) {
	        String providerName = entry.getKey();
	        ProviderStats stats = entry.getValue();

	        double equityDrawdown = htmlDatabase.getEquityDrawdown(providerName);
	        int subscribers = htmlDatabase.getSubscribers(providerName);
	        double threeMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 3);
	        double mpdd3 = calculateMPDD(threeMonthProfit, equityDrawdown);

	        double trades = stats.getTrades().size();
	        double tradeDays = stats.getTradeDays();

	        // Calculate average monthly profit % over entire lifetime
	        Map<String, Double> monthlyProfits = htmlDatabase.getMonthlyProfitPercentages(providerName);
	        double avgMonthlyProfit = 0.0;
	        if (!monthlyProfits.isEmpty()) {
	            double sum = 0.0;
	            for (double val : monthlyProfits.values()) {
	                sum += val;
	            }
	            avgMonthlyProfit = sum / monthlyProfits.size();
	        }

	        ProviderMetrics metrics = new ProviderMetrics(trades, tradeDays, mpdd3, equityDrawdown, subscribers, avgMonthlyProfit);
	        metricsCache.put(providerName, metrics);

	        cachedMinTrades = Math.min(cachedMinTrades, trades);
	        cachedMaxTrades = Math.max(cachedMaxTrades, trades);

	        cachedMinTradeDays = Math.min(cachedMinTradeDays, tradeDays);
	        cachedMaxTradeDays = Math.max(cachedMaxTradeDays, tradeDays);

	        cachedMinMpdd3 = Math.min(cachedMinMpdd3, mpdd3);
	        cachedMaxMpdd3 = Math.max(cachedMaxMpdd3, mpdd3);

	        cachedMinEd = Math.min(cachedMinEd, equityDrawdown);
	        cachedMaxEd = Math.max(cachedMaxEd, equityDrawdown);

	        cachedMinSubscribers = Math.min(cachedMinSubscribers, subscribers);
	        cachedMaxSubscribers = Math.max(cachedMaxSubscribers, subscribers);

	        cachedMinMonthlyProfit = Math.min(cachedMinMonthlyProfit, avgMonthlyProfit);
	        cachedMaxMonthlyProfit = Math.max(cachedMaxMonthlyProfit, avgMonthlyProfit);
	    }

	    // Get score configuration weights
	    utils.ScoreConfig scoreConfig = utils.ScoreConfig.getInstance();
	    double wProfit = scoreConfig.getWeightProfit();
	    double w3Mpdd = scoreConfig.getWeight3Mpdd();
	    double wDrawdown = scoreConfig.getWeightDrawdown();
	    double wTrades = scoreConfig.getWeightTrades();
	    double wTradeDays = scoreConfig.getWeightTradeDays();
	    double wSubscribers = scoreConfig.getWeightSubscribers();
	    double totalWeight = wProfit + w3Mpdd + wDrawdown + wTrades + wTradeDays + wSubscribers;

	    // Pass 2: Calculate scores and populate rows
	    for (Map.Entry<String, ProviderStats> entry : statsMap.entrySet()) {
	        String providerName = entry.getKey();
	        ProviderStats stats = entry.getValue();
	        
	        ProviderMetrics metrics = metricsCache.get(providerName);
	        
	        double score = 0.0;
	        if (totalWeight > 0 && metrics != null) {
	            double scoreT = normalize(metrics.trades, cachedMinTrades, cachedMaxTrades, true);
	            double scoreD = normalize(metrics.tradeDays, cachedMinTradeDays, cachedMaxTradeDays, true);
	            double scoreM = normalize(metrics.mpdd3, cachedMinMpdd3, cachedMaxMpdd3, true);
	            double scoreED = normalize(metrics.equityDrawdown, cachedMinEd, cachedMaxEd, false); // Lower is better
	            
	            double scoreS = 0.0;
	            if (metrics.subscribers > 0) {
	                if (metrics.subscribers == 1) {
	                    scoreS = 20.0;
	                } else {
	                    scoreS = 50.0 + normalize(metrics.subscribers, 2, cachedMaxSubscribers, true) * 0.5;
	                }
	            }
	            
	            double scoreP = 0.0;
	            if (metrics.avgMonthlyProfit > 0.0) {
	                scoreP = normalize(metrics.avgMonthlyProfit, 0.0, cachedMaxMonthlyProfit, true);
	            }

	            score = (wProfit * scoreP + w3Mpdd * scoreM + wDrawdown * scoreED + wTrades * scoreT + wTradeDays * scoreD + wSubscribers * scoreS) / totalWeight;
	        }
	        
	        double equityDrawdown = htmlDatabase.getEquityDrawdown(providerName);
	        double balance = htmlDatabase.getBalance(providerName);
	        int subscribers = htmlDatabase.getSubscribers(providerName);
	        double maxDDGraphic = htmlDatabase.getEquityDrawdownGraphic(providerName);
	        double equityDrawdown3M = htmlDatabase.getMaxDrawdown3M(providerName);
	        
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
	        
	        // Martingale/Grid-Analyse laden
	        HistoryDatabaseManager.AnalysisResult analysis = dbManager.getProviderAnalysis(providerName);
	        String mgType = "-";
	        if (analysis != null) {
	            boolean isM = analysis.isMartingale();
	            boolean isG = analysis.isGrid();
	            if (isM && isG) {
	                mgType = "MG";
	            } else if (isM) {
	                mgType = "M";
	            } else if (isG) {
	                mgType = "G";
	            }
	        }
	        
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
	            score,
	            balance,
	            subscribers,
	            mpdd3,
	            mpdd6,
	            mpdd9,
	            mpdd12,
	            threeMonthProfit,
	            stats.getTrades().size(),
	            stats.getTradeDays(),
	            stats.getPairsCount(),
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
	            Math.round(stats.getAverageDuration() * 100.0) / 100.0,
	            riskCategory == 0 ? "-" : String.valueOf(riskCategory), // Risiko als String mit "-" für 0
	            riskScore,
	            stats.hasStopLoss() ? 1 : 0,
	            stats.hasTakeProfit() ? 1 : 0,
	            stats.getStartDate(),
	            stats.getEndDate(),
	            stabilitaet,
	            steigung,
	            maxDDGraphic,
	            equityDrawdown3M,
	            mgType
	        });
	    }
	    fireTableDataChanged();
	}
  
  public Object[] createRowDataForProvider(String providerName, ProviderStats stats) {
	    double equityDrawdown = htmlDatabase.getEquityDrawdown(providerName);
	    double balance = htmlDatabase.getBalance(providerName);
	    int subscribers = htmlDatabase.getSubscribers(providerName);
	    double maxDDGraphic = htmlDatabase.getEquityDrawdownGraphic(providerName);
	    double equityDrawdown3M = htmlDatabase.getMaxDrawdown3M(providerName);
	    
	    // Berechne MPDD für verschiedene Zeiträume
	    double threeMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 3);
	    double sixMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 6);
	    double nineMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 9);
	    double twelveMonthProfit = htmlDatabase.getAverageMonthlyProfit(providerName, 12);
	    
	    double mpdd3 = calculateMPDD(threeMonthProfit, equityDrawdown);
	    double mpdd6 = calculateMPDD(sixMonthProfit, equityDrawdown);
	    double mpdd9 = calculateMPDD(nineMonthProfit, equityDrawdown);
	    double mpdd12 = calculateMPDD(twelveMonthProfit, equityDrawdown);

	    double trades = stats.getTrades().size();
	    double tradeDays = stats.getTradeDays();

	    // Calculate average monthly profit % over entire lifetime
	    Map<String, Double> monthlyProfits = htmlDatabase.getMonthlyProfitPercentages(providerName);
	    double avgMonthlyProfit = 0.0;
	    if (!monthlyProfits.isEmpty()) {
	        double sum = 0.0;
	        for (double val : monthlyProfits.values()) {
	            sum += val;
	        }
	        avgMonthlyProfit = sum / monthlyProfits.size();
	    }

	    // Get score configuration weights
	    utils.ScoreConfig scoreConfig = utils.ScoreConfig.getInstance();
	    double wProfit = scoreConfig.getWeightProfit();
	    double w3Mpdd = scoreConfig.getWeight3Mpdd();
	    double wDrawdown = scoreConfig.getWeightDrawdown();
	    double wTrades = scoreConfig.getWeightTrades();
	    double wTradeDays = scoreConfig.getWeightTradeDays();
	    double wSubscribers = scoreConfig.getWeightSubscribers();
	    double totalWeight = wProfit + w3Mpdd + wDrawdown + wTrades + wTradeDays + wSubscribers;

	    double score = 0.0;
	    if (totalWeight > 0) {
	        double scoreT = normalize(trades, cachedMinTrades, cachedMaxTrades, true);
	        double scoreD = normalize(tradeDays, cachedMinTradeDays, cachedMaxTradeDays, true);
	        double scoreM = normalize(mpdd3, cachedMinMpdd3, cachedMaxMpdd3, true);
	        double scoreED = normalize(equityDrawdown, cachedMinEd, cachedMaxEd, false);
	        
	        double scoreS = 0.0;
	        if (subscribers > 0) {
	            if (subscribers == 1) {
	                scoreS = 20.0;
	            } else {
	                scoreS = 50.0 + normalize(subscribers, 2, cachedMaxSubscribers, true) * 0.5;
	            }
	        }
	        
	        double scoreP = 0.0;
	        if (avgMonthlyProfit > 0.0) {
	            scoreP = normalize(avgMonthlyProfit, 0.0, cachedMaxMonthlyProfit, true);
	        }

	        score = (wProfit * scoreP + w3Mpdd * scoreM + wDrawdown * scoreED + wTrades * scoreT + wTradeDays * scoreD + wSubscribers * scoreS) / totalWeight;
	    }
	    
	    // Risiko-Kategorie aus der Datenbank laden
	    int riskCategory = dbManager.getProviderRiskCategory(providerName);
	    stats.setRiskCategory(riskCategory);
	    
	    // Martingale/Grid-Analyse laden
	    HistoryDatabaseManager.AnalysisResult analysis = dbManager.getProviderAnalysis(providerName);
	    String mgType = "-";
	    if (analysis != null) {
	        boolean isM = analysis.isMartingale();
	        boolean isG = analysis.isGrid();
	        if (isM && isG) {
	            mgType = "MG";
	        } else if (isM) {
	            mgType = "M";
	        } else if (isG) {
	            mgType = "G";
	        }
	    }
	    
	    int riskScore = RiskAnalysisServ.calculateRiskScore(stats);
	    double stabilitaet = htmlDatabase.getStabilitaetswert(providerName);
	    
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
	        score,
	        balance,
	        subscribers,
	        mpdd3,
	        mpdd6,
	        mpdd9,
	        mpdd12,
	        threeMonthProfit,
	        stats.getTrades().size(),
	        stats.getTradeDays(),
	        stats.getPairsCount(),
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
	        Math.round(stats.getAverageDuration() * 100.0) / 100.0,
	        riskCategory == 0 ? "-" : String.valueOf(riskCategory), // Risiko als String mit "-" für 0
	        riskScore,
	        stats.hasStopLoss() ? 1 : 0,
	        stats.hasTakeProfit() ? 1 : 0,
	        stats.getStartDate(),
	        stats.getEndDate(),
	        stabilitaet,
	        steigung,
	        maxDDGraphic,
	        equityDrawdown3M,
	        mgType
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