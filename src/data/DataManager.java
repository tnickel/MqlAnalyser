package data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DataManager {
   private static final Logger LOGGER = Logger.getLogger(DataManager.class.getName());
   private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
   private static final String BASE_URL = "https://www.mql5.com/en/signals/";
   
   private final Map<String, ProviderStats> signalProviderStats;
   
   // Fortschritts-Callback
   private Consumer<Integer> progressCallback;
   private Consumer<String> statusCallback;
   
   // Singleton-Instanz
   private static DataManager instance;
   
   // Debug-Modus für zusätzliche Ausgaben
   private boolean debugMode = false;
   
   public DataManager() {
       this.signalProviderStats = new HashMap<>();
       
       // Debug-Ausgabe zur Instanziierung
       if (debugMode) {
           LOGGER.info("DataManager wurde instanziiert");
       }
   }
   
   // Neue Hilfsmethode zum sicheren Parsen von numerischen Werten
   private double parseNumericValue(String value) throws NumberFormatException {
       if (value == null || value.trim().isEmpty()) {
           return 0.0;
       }
       // Entferne alle Leerzeichen und ersetze Kommas durch Punkte
       String cleanValue = value.trim().replace(" ", "").replace(",", ".");
       return Double.parseDouble(cleanValue);
   }
   
   // Setter für Callbacks
   public void setProgressCallback(Consumer<Integer> progressCallback) {
       this.progressCallback = progressCallback;
   }
   
   public void setStatusCallback(Consumer<String> statusCallback) {
       this.statusCallback = statusCallback;
   }
   
   // Singleton-Methode
   public static synchronized DataManager getInstance() {
       if (instance == null) {
           instance = new DataManager();
       }
       return instance;
   }
   
   // Setter für die Instanz (für Testbarkeit)
   public static void setInstance(DataManager manager) {
       instance = manager;
   }
   
   private String extractProviderName(String fileName) {
       // Entferne die .csv Endung
       String name = fileName.toLowerCase().replace(".csv", "");
       // Hier können weitere Bereinigungen des Provider-Namens erfolgen
       return name;
   }
   
   private String constructProviderURL(String providerId) {
       return BASE_URL + providerId;
   }
   
   public void loadData(String path) {
       LOGGER.info("Loading data from: " + path);
       File downloadDirectory = new File(path);
       if (downloadDirectory.exists() && downloadDirectory.isDirectory()) {
           File[] files = downloadDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
           if (files != null) {
               LOGGER.info("Found " + files.length + " CSV files");
               
               // Status melden
               if (statusCallback != null) {
                   statusCallback.accept("Lade Dateien: 0/" + files.length);
               }
               
               // Fortschritt für alle Dateien
               for (int i = 0; i < files.length; i++) {
                   File file = files[i];
                   // Status melden
                   if (statusCallback != null) {
                       statusCallback.accept("Lade Datei: " + file.getName() + " (" + (i+1) + "/" + files.length + ")");
                   }
                   
                   processFile(file);
                   
                   // Fortschritt aktualisieren
                   if (progressCallback != null) {
                       int progress = (int) ((i + 1) / (double) files.length * 100);
                       progressCallback.accept(progress);
                   }
               }
           }
       }
       LOGGER.info("Loaded " + signalProviderStats.size() + " providers");
       
       // Status melden
       if (statusCallback != null) {
           statusCallback.accept("Daten geladen: " + signalProviderStats.size() + " Provider");
       }
       
       // Setze instance beim Laden, falls noch nicht gesetzt
       if (instance == null) {
           instance = this;
       }
   }

   private void processFile(File file) {
       LOGGER.info("Starting to process file: " + file.getName());
       List<String> skippedLines = new ArrayList<>();
       
       try {
           // Dateiformat durch Lesen der ersten Zeile bestimmen
           boolean isMql5Format = false;
           String headerLine = null;
           
           try (BufferedReader headerReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
               headerLine = headerReader.readLine();
               
               if (headerLine != null) {
                   // Bereinigen möglicher BOM-Zeichen oder anderer Präfixe
                   if (headerLine.startsWith("\uFEFF")) {
                       headerLine = headerLine.substring(1);
                   }
                   
                   String[] headerFields = headerLine.split(";");
                   
                   // Debug-Ausgabe für Header-Felder
                   if (debugMode) {
                       LOGGER.info("Header hat " + headerFields.length + " Felder");
                       for (int i = 0; i < headerFields.length; i++) {
                           LOGGER.info("Header-Feld " + i + ": '" + headerFields[i] + "'");
                       }
                   }
                   
                   // MQL5-Format-Erkennung: Hat mindestens 11 Felder, erstes und siebtes sind "Time"
                   // und sechstes Feld ist das duplizierte "Volume"
                   if (headerFields.length >= 11 && 
                       "Time".equals(headerFields[0]) && 
                       "Volume".equals(headerFields[5]) && 
                       "Time".equals(headerFields[6])) {
                       isMql5Format = true;
                       LOGGER.info("Detected MQL5 format with duplicate Volume field");
                   } else {
                       LOGGER.info("Using standard MT4 format");
                   }
               }
           } catch (IOException e) {
               LOGGER.warning("Error reading file header: " + e.getMessage());
           }
           
           // Datei komplett verarbeiten
           try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8), 32768)) {
               
               String line;
               boolean isHeader = true;
               ProviderStats stats = new ProviderStats();
               
               // Setze Provider-Informationen
               String providerName = extractProviderName(file.getName());
               String providerURL = constructProviderURL(providerName);
               stats.setSignalProviderInfo(providerName, providerURL);
               
               int lineCount = 0;
               int tradeCounts = 0;
               double initialBalance = 1000.0; // Default-Wert für MQL5-Format
               boolean foundFirstBalance = false;
               
               while ((line = reader.readLine()) != null) {
                   lineCount++;
                   if (isHeader) {
                       isHeader = false;
                       continue;
                   }
                   
                   // Leere Zeilen überspringen
                   line = line.trim();
                   if (line.isEmpty()) continue;
                   
                   // Debug: Bei Problemen erste paar Zeilen ausgeben
                   if (lineCount <= 5 && debugMode) {
                       LOGGER.info("Line " + lineCount + ": " + line);
                   }
                   
                   String[] data = line.split(";", -1);
                   
                   // Wenn zu wenige Felder, überspringe die Zeile
                   if (data.length < 8) {
                       LOGGER.fine("Line with too few fields: " + line);
                       skippedLines.add(line);
                       continue;
                   }
                           // Balance und Credit Einträge für beide Formate (MT4 und MT5)
                   String rowType = data[1].trim();
                   boolean isBalanceRow = rowType.equalsIgnoreCase("Balance") || rowType.equalsIgnoreCase("Credit");
                   if (isBalanceRow) {
                       double profit = 0.0;
                       try {
                           String balanceStr = null;
                           for (int i = data.length - 1; i >= 2; i--) {
                               if (data[i] != null && !data[i].trim().isEmpty()) {
                                   balanceStr = data[i].trim();
                                   break;
                               }
                           }
                           if (balanceStr != null && !balanceStr.isEmpty()) {
                               if (balanceStr.contains("[")) {
                                   balanceStr = balanceStr.split("\\[")[0].trim();
                               }
                               profit = parseNumericValue(balanceStr);
                           }
                       } catch (NumberFormatException e) {
                           LOGGER.warning("Failed to parse balance: " + e.getMessage());
                       }

                       LocalDateTime openTime;
                       try {
                           openTime = LocalDateTime.parse(data[0].trim(), DATE_TIME_FORMATTER);
                       } catch (DateTimeParseException e) {
                           LOGGER.fine("Failed to parse balance open time: " + data[0]);
                           skippedLines.add(line);
                           continue;
                       }

                       Trade balanceTrans = new Trade(
                           openTime, openTime,
                           rowType, "", 0.0,
                           0.0, 0.0, 0.0, 0.0,
                           providerName, providerURL,
                           0.0, 0.0, profit
                       );
                       stats.addBalanceTransaction(balanceTrans);
                       continue;
                   }
                   
                   // Ignoriere "cancelled" Einträge
                   if (data.length > 1 && data[1].toLowerCase().contains("cancelled")) {
                       continue;
                   }
                   
                   try {
                       // Gemeinsames Feld-Parsing für beide Formate
                       LocalDateTime openTime;
                       try {
                           openTime = LocalDateTime.parse(data[0].trim(), DATE_TIME_FORMATTER);
                       } catch (DateTimeParseException e) {
                           LOGGER.fine("Failed to parse open time: " + data[0]);
                           skippedLines.add(line);
                           continue;
                       }
                       
                       String type = data[1].trim();
                       
                       double lots;
                       try {
                           lots = parseNumericValue(data[2]);
                       } catch (NumberFormatException e) {
                           LOGGER.fine("Failed to parse lots: " + e.getMessage());
                           skippedLines.add(line);
                           continue;
                       }
                       
                       String symbol = data[3].trim();
                       
                       double openPrice;
                       try {
                           openPrice = parseNumericValue(data[4]);
                       } catch (NumberFormatException e) {
                           LOGGER.fine("Failed to parse open price: " + e.getMessage());
                           skippedLines.add(line);
                           continue;
                       }
                       
                       // Initialisiere mit Default-Werten
                       double stopLoss = 0.0;
                       double takeProfit = 0.0;
                       LocalDateTime closeTime;
                       double closePrice;
                       double commission = 0.0;
                       double swap = 0.0;
                       double profit = 0.0;
                       
                       if (isMql5Format) {
                           // MQL5-Format mit festen Positionen
                           // 0: Open Time, 1: Type, 2: Volume, 3: Symbol, 4: Open Price
                           // 5: Volume (wiederholt), 6: Close Time, 7: Close Price
                           // 8: Commission, 9: Swap, 10: Profit
                           
                           try {
                               closeTime = LocalDateTime.parse(data[6].trim(), DATE_TIME_FORMATTER);
                           } catch (DateTimeParseException e) {
                               LOGGER.fine("Failed to parse close time: " + data[6]);
                               skippedLines.add(line);
                               continue;
                           }
                           
                           try {
                               closePrice = parseNumericValue(data[7]);
                           } catch (NumberFormatException e) {
                               LOGGER.fine("Failed to parse close price: " + e.getMessage());
                               skippedLines.add(line);
                               continue;
                           }
                           
                           // Commission und Swap können leer sein
                           if (data.length > 8 && !data[8].trim().isEmpty()) {
                               try {
                                   commission = parseNumericValue(data[8]);
                               } catch (NumberFormatException e) {
                                   LOGGER.fine("Empty or invalid commission: " + data[8]);
                                   // Nicht kritisch, setze auf 0
                               }
                           }
                           
                           if (data.length > 9 && !data[9].trim().isEmpty()) {
                               try {
                                   swap = parseNumericValue(data[9]);
                               } catch (NumberFormatException e) {
                                   LOGGER.fine("Empty or invalid swap: " + data[9]);
                                   // Nicht kritisch, setze auf 0
                               }
                           }
                           
                           // Profit ist wichtig
                           if (data.length > 10) {
                               try {
                                   // MQL5 speichert Profit als ganze Zahl, muss skaliert werden
                                   String profitStr = data[10].trim();
                                   
                                   if (!profitStr.isEmpty()) {
                                       double rawProfit = parseNumericValue(profitStr);
                                       
                                       // Verwende den rohen Profitwert direkt (keine Division durch 100)
                                       profit = rawProfit;
                                       
                                       if (debugMode && lineCount <= 5) {
                                           LOGGER.info(String.format(
                                               "Parsed profit for %s: raw=%f, adjusted=%f", 
                                               symbol, rawProfit, profit));
                                       }
                                   } else {
                                       LOGGER.fine("Empty profit field");
                                       skippedLines.add(line);
                                       continue;
                                   }
                               } catch (NumberFormatException e) {
                                   LOGGER.fine("Failed to parse profit: " + e.getMessage());
                                   skippedLines.add(line);
                                   continue;
                               }
                           } else {
                               LOGGER.fine("No profit field found");
                               skippedLines.add(line);
                               continue;
                           }
                           
                       } else {
                           // Standard-Format mit S/L und T/P
                           boolean hasStopLossTakeProfit = false;
                           
                           // Format-Erkennung anhand des Datums an Position 7
                           if (data.length > 7) {
                               try {
                                   LocalDateTime.parse(data[7].trim(), DATE_TIME_FORMATTER);
                                   hasStopLossTakeProfit = true;
                               } catch (DateTimeParseException e) {
                                   // Kein Datum an Position 7
                                   hasStopLossTakeProfit = false;
                               }
                           }
                           
                           // Überprüfe, ob Felder 5 und 6 numerische Werte enthalten (S/L, T/P)
                           if (!hasStopLossTakeProfit && data.length > 6) {
                               try {
                                   if (data[5] != null && !data[5].trim().isEmpty()) {
                                       parseNumericValue(data[5]);
                                       hasStopLossTakeProfit = true;
                                   }
                               } catch (NumberFormatException e) {
                                   // Ignorieren
                               }
                               
                               try {
                                   if (data[6] != null && !data[6].trim().isEmpty()) {
                                       parseNumericValue(data[6]);
                                       hasStopLossTakeProfit = true;
                                   }
                               } catch (NumberFormatException e) {
                                   // Ignorieren
                               }
                           }
                           
                           // Indizes basierend auf Format bestimmen
                           int closeTimeIndex = hasStopLossTakeProfit ? 7 : 5;
                           int closePriceIndex = hasStopLossTakeProfit ? 8 : 6;
                           int commissionIndex = hasStopLossTakeProfit ? 9 : 7;
                           int swapIndex = hasStopLossTakeProfit ? 10 : 8;
                           int profitIndex = hasStopLossTakeProfit ? 11 : 9;
                           
                           if (debugMode && lineCount <= 5) {
                               LOGGER.info("Format erkannt: hasStopLossTakeProfit=" + hasStopLossTakeProfit);
                               LOGGER.info("Indizes: closeTime=" + closeTimeIndex + ", closePrice=" + closePriceIndex + ", profit=" + profitIndex);
                           }
                           
                           // S/L und T/P auslesen, falls vorhanden
                           if (hasStopLossTakeProfit) {
                               try {
                                   if (data.length > 5 && !data[5].trim().isEmpty()) {
                                       stopLoss = parseNumericValue(data[5]);
                                   }
                                   if (data.length > 6 && !data[6].trim().isEmpty()) {
                                       takeProfit = parseNumericValue(data[6]);
                                   }
                               } catch (NumberFormatException e) {
                                   LOGGER.fine("Failed to parse SL/TP: " + e.getMessage());
                                   // Keine kritische Information, weitermachen
                               }
                           }
                           
                           // Schließzeit auslesen
                           if (data.length <= closeTimeIndex) {
                               LOGGER.fine("Not enough fields for closeTime at index " + closeTimeIndex);
                               skippedLines.add(line);
                               continue;
                           }
                           
                           try {
                               closeTime = LocalDateTime.parse(data[closeTimeIndex].trim(), DATE_TIME_FORMATTER);
                           } catch (DateTimeParseException e) {
                               LOGGER.fine("Failed to parse close time: " + data[closeTimeIndex]);
                               skippedLines.add(line);
                               continue;
                           }
                           
                           // Schließkurs auslesen
                           if (data.length <= closePriceIndex) {
                               LOGGER.fine("Not enough fields for closePrice at index " + closePriceIndex);
                               skippedLines.add(line);
                               continue;
                           }
                           
                           try {
                               closePrice = parseNumericValue(data[closePriceIndex]);
                           } catch (NumberFormatException e) {
                               LOGGER.fine("Failed to parse close price: " + e.getMessage());
                               skippedLines.add(line);
                               continue;
                           }
                           
                           // Kommission auslesen
                           if (data.length > commissionIndex && !data[commissionIndex].trim().isEmpty()) {
                               try {
                                   commission = parseNumericValue(data[commissionIndex]);
                               } catch (NumberFormatException e) {
                                   LOGGER.fine("Failed to parse commission: " + e.getMessage());
                                   // Nicht kritisch, weitermachen
                               }
                           }
                           
                           // Swap auslesen
                           if (data.length > swapIndex && !data[swapIndex].trim().isEmpty()) {
                               try {
                                   swap = parseNumericValue(data[swapIndex]);
                               } catch (NumberFormatException e) {
                                   LOGGER.fine("Failed to parse swap: " + e.getMessage());
                                   // Nicht kritisch, weitermachen
                               }
                           }
                           
                           // Profit auslesen
                           if (data.length <= profitIndex) {
                               LOGGER.fine("Not enough fields for profit at index " + profitIndex);
                               skippedLines.add(line);
                               continue;
                           }
                           
                           try {
                               String profitStr = data[profitIndex].trim();
                               // Bereinigen von Kommentaren wie [sl], [tp]
                               if (profitStr.contains("[")) {
                                   profitStr = profitStr.split("\\[")[0].trim();
                               }
                               profit = parseNumericValue(profitStr);
                           } catch (NumberFormatException e) {
                               LOGGER.fine("Failed to parse profit: " + e.getMessage());
                               skippedLines.add(line);
                               continue;
                           }
                       }
                       
                       // Trade zu den Stats hinzufügen
                       stats.addTrade(
                           openTime, closeTime,
                           type, symbol, lots,
                           openPrice, closePrice,
                           stopLoss, takeProfit,
                           commission, swap, profit
                       );
                       
                       tradeCounts++;
                       
                   } catch (Exception e) {
                       LOGGER.log(Level.WARNING, "General error parsing line: " + line, e);
                       skippedLines.add(line);
                   }
               }
               
               // Bestimme die Initial Balance aus den Balance-Transaktionen
               if (!stats.getBalanceTransactions().isEmpty()) {
                   // Finde die älteste Balance-Transaktion
                   Trade oldestBalance = stats.getBalanceTransactions().stream()
                       .min(java.util.Comparator.comparing(Trade::getOpenTime))
                       .orElse(null);
                   if (oldestBalance != null) {
                       stats.setInitialBalance(oldestBalance.getProfit());
                       LOGGER.info("Determined initial balance from oldest transaction: " + oldestBalance.getProfit());
                   }
               } else {
                   // Fallback auf Default, falls keine Balance-Transaktionen gefunden wurden
                   stats.setInitialBalance(isMql5Format ? 1000.0 : initialBalance);
                   LOGGER.info("No balance transactions found. Using initial balance: " + stats.getInitialBalance());
               }
               
               if (!stats.getProfits().isEmpty()) {
                   stats.sortTradesChronologically();
                   signalProviderStats.put(file.getName(), stats);
                   LOGGER.info(String.format("Successfully processed %s: %d trades loaded, Initial Balance: %.2f, Format: %s",
                           file.getName(), tradeCounts, stats.getInitialBalance(), isMql5Format ? "MQL5" : "Standard"));
               } else {
                   LOGGER.warning("No trades processed for file: " + file.getName() + " (processed lines: " + lineCount + ")");
               }
               
               // Statistik über übersprungene Zeilen
               if (!skippedLines.isEmpty()) {
                   LOGGER.info(String.format("Skipped %d out of %d lines in %s", 
                           skippedLines.size(), lineCount, file.getName()));
               }
           }
           
       } catch (IOException e) {
           LOGGER.severe("Error reading file " + file.getName() + ": " + e.getMessage());
           e.printStackTrace();
       }
   }
   
   public Map<String, ProviderStats> getStats() {
       return signalProviderStats;
   }
}
