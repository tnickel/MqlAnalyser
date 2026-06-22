package services;

import java.io.File;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import data.ProviderStats;
import db.HistoryDatabaseManager;
import db.HistoryDatabaseManager.HistoryEntry;
import utils.ApplicationConstants;
import utils.HtmlDatabase;
import utils.MqlAnalyserConf;

/**
 * Service für die Verwaltung und Speicherung von historischen Statistik-Werten für Signal Provider
 */
public class ProviderHistoryService {
    private static final Logger LOGGER = Logger.getLogger(ProviderHistoryService.class.getName());
    private static ProviderHistoryService instance;
    
    private final Map<String, Map<String, Double>> lastValues;
    private final Preferences prefs;
    private String rootPath;
    private HistoryDatabaseManager dbManager;
    
    // Konstanten für Statistiktypen
    public static final String STAT_TYPE_3MPDD = "3MPDD";
    public static final String STAT_TYPE_SUBSCRIBERS = "SUBSCRIBERS";
    
    // Schlüssel für die Preferences
    private static final String PREF_LAST_WEEKLY_SAVE = "last_weekly_stat_save";
    
    // Neues Feld zur Speicherung des letzten Datenbestands
    private int lastKnownDataCount = 0;
    
    // Konstante für die minimale Wartezeit zwischen automatischen Backups (in Stunden)
    private static final int MIN_HOURS_BETWEEN_BACKUPS = 24;
    
    // Schlüssel für die Preferences
    private static final String PREF_LAST_BACKUP_TIME = "last_database_backup_time";
    private static final String PREF_LAST_DATA_COUNT = "last_known_data_count";
    
    // Singleton-Pattern
    private ProviderHistoryService() {
        this.lastValues = new ConcurrentHashMap<>();
        this.prefs = Preferences.userNodeForPackage(ProviderHistoryService.class);
        this.rootPath = null; // Wird über initialize gesetzt
    }
    
    /**
     * Gibt die Singleton-Instanz zurück
     */
    public static synchronized ProviderHistoryService getInstance() {
        if (instance == null) {
            instance = new ProviderHistoryService();
        }
        return instance;
    }
    
    /**
     * Initialisiert den Service mit dem Root-Pfad und lädt bestehende Werte
     * 
     * @param rootPath Pfad zum Root-Verzeichnis mit den CSV-Dateien
     */
    public void initialize(String rootPath) {
        // Validiere den Pfad und korrigiere ihn, falls nötig
        rootPath = ApplicationConstants.validateRootPath(rootPath, "ProviderHistoryService.initialize");
        
        this.rootPath = rootPath;
        
        // HistoryDatabaseManager mit dem Pfad initialisieren
        this.dbManager = HistoryDatabaseManager.getInstance(rootPath);
        
        // Lade die zuletzt gespeicherten Werte für bestehende Provider
        loadExistingValues();
        
        // Lade den letzten bekannten Datenbestand
        loadLastDataCount();
        
        // Prüfe die Datenintegrität beim Start
        validateDatabaseIntegrity();
        
        LOGGER.info("Provider History Service mit Root-Pfad initialisiert: " + rootPath);
    }

    /**
     * Lädt die zuletzt gespeicherten Werte aller Provider aus der Datenbank
     * und speichert sie im Cache, um unnötige Speicheroperationen zu vermeiden
     */
    private void loadExistingValues() {
        if (rootPath == null) {
            LOGGER.warning("Root-Pfad nicht gesetzt. Konnte existierende Werte nicht laden.");
            return;
        }
        
        LOGGER.info("Lade bestehende Statistik-Werte...");
        
        try {
            // Hole alle CSV-Dateien im Root-Verzeichnis
            File downloadDirectory = new File(rootPath);
            if (downloadDirectory.exists() && downloadDirectory.isDirectory()) {
                File[] files = downloadDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
                
                if (files != null) {
                    for (File file : files) {
                        String providerName = file.getName();
                        
                        // Hole den letzten 3MPDD-Wert für diesen Provider aus der Datenbank
                        Double mpddValue = dbManager.getLatestStatValue(providerName, STAT_TYPE_3MPDD);
                        
                        if (mpddValue != null) {
                            // Speichere den Wert im Cache
                            getProviderCache(providerName).put(STAT_TYPE_3MPDD, mpddValue);
                            LOGGER.fine("Letzter 3MPDD-Wert für " + providerName + " geladen: " + mpddValue);
                        }
                        
                        // Hole den letzten SUBSCRIBERS-Wert für diesen Provider aus der Datenbank
                        Double subscribersValue = dbManager.getLatestStatValue(providerName, STAT_TYPE_SUBSCRIBERS);
                        
                        if (subscribersValue != null) {
                            // Speichere den Wert im Cache
                            getProviderCache(providerName).put(STAT_TYPE_SUBSCRIBERS, subscribersValue);
                            LOGGER.fine("Letzter SUBSCRIBERS-Wert für " + providerName + " geladen: " + subscribersValue);
                        }
                        
                        // Hier können später weitere Statistik-Typen hinzugefügt werden
                    }
                }
            }
            
            LOGGER.info("Bestehende Statistik-Werte für " + lastValues.size() + " Provider geladen");
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Laden bestehender Statistik-Werte: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Gibt die Cache-Map für einen Provider zurück oder erstellt sie, falls noch nicht vorhanden
     */
    private Map<String, Double> getProviderCache(String providerName) {
        return lastValues.computeIfAbsent(providerName, k -> new ConcurrentHashMap<>());
    }
    
    /**
     * Lädt den letzten bekannten Datenbestand aus den Preferences
     */
    private void loadLastDataCount() {
        lastKnownDataCount = prefs.getInt(PREF_LAST_DATA_COUNT, 0);
        LOGGER.info("Letzter bekannter Datenbestand: " + lastKnownDataCount + " Einträge");
    }
    
    /**
     * Speichert den aktuellen Datenbestand in den Preferences
     */
    private void saveCurrentDataCount() {
        int currentCount = dbManager.countAllEntries();
        prefs.putInt(PREF_LAST_DATA_COUNT, currentCount);
        lastKnownDataCount = currentCount;
        LOGGER.info("Aktueller Datenbestand gespeichert: " + currentCount + " Einträge");
    }
    
    /**
     * Validiert die Integrität der Datenbank
     */
    private void validateDatabaseIntegrity() {
        int currentCount = dbManager.countAllEntries();
        
        // Wenn der aktuelle Datenbestand kleiner ist als der letzte bekannte,
        // könnte ein Datenverlust vorliegen
        if (currentCount < lastKnownDataCount && lastKnownDataCount > 0) {
            LOGGER.severe("MÖGLICHER DATENVERLUST ERKANNT! Aktuell: " + currentCount + 
                    " Einträge, Zuvor bekannt: " + lastKnownDataCount + " Einträge");
            
            // Erstelle automatisch ein Backup
            createBackupIfNeeded(true);
            
            // Eine Warnung ausgeben (könnte durch ein Dialogfenster ersetzt werden)
            System.err.println("\n*** WARNUNG: MÖGLICHER DATENVERLUST ERKANNT! ***\n" +
                    "Aktueller Datenbestand: " + currentCount + " Einträge\n" +
                    "Letzter bekannter Bestand: " + lastKnownDataCount + " Einträge\n" +
                    "Ein automatisches Backup wurde erstellt.\n");
        } else {
            LOGGER.info("Datenintegrität OK: " + currentCount + " Einträge");
            
            // Aktualisiere den gespeicherten Datenbestand
            saveCurrentDataCount();
            
            // Erstelle ein reguläres Backup, falls erforderlich
            createBackupIfNeeded(false);
        }
    }
    
    /**
     * Erstellt ein Backup der Datenbank, wenn genügend Zeit seit dem letzten Backup vergangen ist
     * oder wenn force=true
     * 
     * @param force Wenn true, wird ein Backup unabhängig von der vergangenen Zeit erstellt
     */
    private void createBackupIfNeeded(boolean force) {
        long lastBackupTime = prefs.getLong(PREF_LAST_BACKUP_TIME, 0);
        long currentTime = System.currentTimeMillis();
        
        // Berechne die verstrichene Zeit seit dem letzten Backup in Stunden
        long hoursSinceLastBackup = (currentTime - lastBackupTime) / (60 * 60 * 1000);
        
        if (force || hoursSinceLastBackup >= MIN_HOURS_BETWEEN_BACKUPS) {
            LOGGER.info("Erstelle Datenbank-Backup" + (force ? " (erzwungen)" : ""));
            
            boolean success = dbManager.createBackup();
            if (success) {
                // Speichere den Zeitpunkt des Backups
                prefs.putLong(PREF_LAST_BACKUP_TIME, currentTime);
                LOGGER.info("Datenbank-Backup erfolgreich erstellt");
            }
        }
    }
    
    /**
     * Prüft, ob seit der letzten wöchentlichen Speicherung
     * genügend Zeit vergangen ist und führt die Speicherung durch, wenn nötig
     */
    public void checkAndPerformWeeklySave() {
        LocalDate today = LocalDate.now();
        LocalDate lastSaveDate = getLastWeeklySaveDate();
        
        // Prüfe, ob seit dem letzten Speichern mindestens 7 Tage vergangen sind
        if (lastSaveDate == null || ChronoUnit.DAYS.between(lastSaveDate, today) >= 7) {
            LOGGER.info("Es sind mindestens 7 Tage seit der letzten wöchentlichen Speicherung vergangen. Führe Speicherung durch...");
            
            // Validiere die Datenintegrität vor der Speicherung
            validateDatabaseIntegrity();
            
            // Speichere alle Werte
            storeAllStatValues(true);
            
            // Aktualisiere das Datum der letzten Speicherung
            saveLastWeeklySaveDate(today);
            
            // Aktualisiere den Datenbestand nach der Speicherung
            saveCurrentDataCount();
            
            LOGGER.info("Wöchentliche Statistik-Speicherung abgeschlossen. Nächste Speicherung ab: " + today.plusDays(7));
        } else {
            LOGGER.fine("Letzte wöchentliche Speicherung war am " + lastSaveDate + 
                    ". Nächste Speicherung ab: " + lastSaveDate.plusDays(7));
        }
    }
    
    /**
     * Speichert das Datum der letzten wöchentlichen Speicherung
     */
    private void saveLastWeeklySaveDate(LocalDate date) {
        prefs.put(PREF_LAST_WEEKLY_SAVE, date.toString());
    }
    
    /**
     * Holt das Datum der letzten wöchentlichen Speicherung
     */
    private LocalDate getLastWeeklySaveDate() {
        String dateStr = prefs.get(PREF_LAST_WEEKLY_SAVE, null);
        if (dateStr != null) {
            try {
                return LocalDate.parse(dateStr);
            } catch (Exception e) {
                LOGGER.warning("Fehler beim Parsen des letzten Speicherdatums: " + e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * Speichert einen statistischen Wert für einen Signal Provider und
     * prüft auf Änderungen im Vergleich zum letzten gespeicherten Wert
     * 
     * @param providerName Signal Provider Name
     * @param statType Art des statistischen Werts
     * @param value Der zu speichernde Wert
     * @return true bei erfolgreicher Speicherung, false bei Fehler
     */
    public boolean storeStatValue(String providerName, String statType, double value) {
        return storeStatValue(providerName, statType, value, false);
    }
    
    /**
     * Speichert einen statistischen Wert für einen Signal Provider und
     * prüft auf Änderungen im Vergleich zum letzten gespeicherten Wert
     * 
     * @param providerName Signal Provider Name
     * @param statType Art des statistischen Werts
     * @param value Der zu speichernde Wert
     * @param force Wenn true, wird die Speicherung erzwungen, auch wenn der Wert unverändert ist
     * @return true bei erfolgreicher Speicherung, false bei Fehler
     */
    public boolean storeStatValue(String providerName, String statType, double value, boolean force) {
        // Prüfen, ob sich der Wert geändert hat
        Map<String, Double> providerCache = getProviderCache(providerName);
        Double lastValue = providerCache.get(statType);
        boolean hasChanged = lastValue == null || Math.abs(lastValue - value) > 0.001;
        
        if (hasChanged || force) {
            LOGGER.info(String.format("%s-Wert für %s hat sich geändert: %.4f → %.4f", 
                    statType, 
                    providerName, 
                    (lastValue != null ? lastValue : 0.0), 
                    value));
                    
            // Speichern (mit Sicherheitsmechanismen in HistoryDatabaseManager)
            boolean success = dbManager.storeStatValue(providerName, statType, value, force);
            if (success) {
                providerCache.put(statType, value);
                
                // Aktualisiere den gespeicherten Datenbestand nach erfolgreicher Speicherung
                saveCurrentDataCount();
            }
            return success;
        } else {
            LOGGER.fine(String.format("%s-Wert für %s unverändert (%.4f)", 
                    statType, providerName, value));
        }
        
        return true; // Keine Änderung, kein Fehler
    }
    
    /**
     * Speichert den 3MPDD-Wert für einen Signal Provider
     * 
     * @param providerName Signal Provider Name
     * @param mpddValue 3MPDD-Wert
     * @return true bei erfolgreicher Speicherung, false bei Fehler
     */
    public boolean store3MpddValue(String providerName, double mpddValue) {
        return storeStatValue(providerName, STAT_TYPE_3MPDD, mpddValue);
    }
    
    /**
     * Speichert den 3MPDD-Wert für einen Signal Provider mit Option zum Erzwingen der Speicherung
     * 
     * @param providerName Signal Provider Name
     * @param mpddValue 3MPDD-Wert
     * @param force Wenn true, wird die Speicherung erzwungen, auch wenn der Wert sich nicht geändert hat
     * @return true bei erfolgreicher Speicherung, false bei Fehler
     */
    public boolean store3MpddValue(String providerName, double mpddValue, boolean force) {
        return storeStatValue(providerName, STAT_TYPE_3MPDD, mpddValue, force);
    }
    
    /**
     * Speichert statistische Werte für alle Signal Provider
     * 
     * @param forceUpdate Erzwingt Speicherung auch wenn keine Änderung
     */
    public void storeAllStatValues(boolean forceUpdate) {
        if (rootPath == null) {
            LOGGER.warning("Root-Pfad nicht gesetzt. Konnte Statistik-Werte nicht speichern.");
            return;
        }
        
        HtmlDatabase htmlDb = new HtmlDatabase(rootPath);
        
        // Für jeden Provider in der HTML-Datenbank
        Map<String, Map<String, Double>> currentValues = collectAllStatValues(htmlDb);
        
        for (Map.Entry<String, Map<String, Double>> entry : currentValues.entrySet()) {
            String providerName = entry.getKey();
            Map<String, Double> statValues = entry.getValue();
            
            // Alle Statistiktypen für diesen Provider speichern
            for (Map.Entry<String, Double> statEntry : statValues.entrySet()) {
                String statType = statEntry.getKey();
                double value = statEntry.getValue();
                
                // Wert speichern (mit forceUpdate)
                dbManager.storeStatValue(providerName, statType, value, forceUpdate);
                
                // Cache aktualisieren
                getProviderCache(providerName).put(statType, value);
            }
        }
        
        LOGGER.info("Statistik-Werte für alle Provider aktualisiert" + (forceUpdate ? " (erzwungen)" : ""));
    }
    
    /**
     * Erzwingt die sofortige Speicherung aller vorhandenen Statistikwerte für alle Provider
     * und erstellt einen Eintrag auch für Provider, die bisher keine Einträge haben
     */
    public void forceInitialSave() {
        if (rootPath == null) {
            LOGGER.warning("Root-Pfad nicht gesetzt. Konnte Statistik-Werte nicht speichern.");
            return;
        }
        
        try {
            // Erstelle ein Backup vor der initialen Speicherung
            createBackupIfNeeded(true);
            
            // Hole alle CSV-Dateien im Root-Verzeichnis
            File downloadDirectory = new File(rootPath);
            if (downloadDirectory.exists() && downloadDirectory.isDirectory()) {
                File[] files = downloadDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
                
                if (files != null) {
                    LOGGER.info("Erzwinge Speicherung für " + files.length + " Provider");
                    
                    // Verwende die gleiche Berechnung wie in der Haupttabelle
                    HtmlDatabase htmlDb = new HtmlDatabase(rootPath);
                    
                    for (File file : files) {
                        String providerName = file.getName();
                        
                        // Berechne 3MPDD-Wert
                        double threeMonthProfit = htmlDb.getAverageMonthlyProfit(providerName, 3);
                        double equityDrawdown = htmlDb.getEquityDrawdown(providerName);
                        double mpdd3 = calculateMPDD(threeMonthProfit, equityDrawdown);
                        
                        // In DB speichern
                        dbManager.storeStatValue(providerName, STAT_TYPE_3MPDD, mpdd3, true);
                        
                        // Subscribers speichern
                        double subscribers = htmlDb.getSubscribers(providerName);
                        dbManager.storeStatValue(providerName, STAT_TYPE_SUBSCRIBERS, subscribers, true);
                        
                        LOGGER.info(String.format("3MPDD-Wert %.4f und Subscribers %.0f für Provider %s gespeichert", 
                                mpdd3, subscribers, providerName));
                    }
                }
            }
            
            // Aktualisiere den Datenbestand nach der Speicherung
            saveCurrentDataCount();
            
            LOGGER.info("Initiale Speicherung abgeschlossen");
        } catch (Exception e) {
            LOGGER.severe("Fehler bei der initialen Speicherung: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Sammelt alle aktuellen Statistik-Werte aus der vorhandenen Datenquelle
     *
     * @param htmlDb Die HTML-Datenbank
     * @return Map mit Provider-Namen als Schlüssel und innerer Map mit Statistiktypen und Werten
     */
    private Map<String, Map<String, Double>> collectAllStatValues(HtmlDatabase htmlDb) {
        Map<String, Map<String, Double>> allValues = new HashMap<>();
        
        // Hole den Root-Pfad aus der HtmlDatabase
        MqlAnalyserConf config = new MqlAnalyserConf(ApplicationConstants.ROOT_PATH);
        String downloadpfad = config.getDownloadPath();
        
        // Hole alle CSV-Dateien im Root-Verzeichnis
        File downloadDirectory = new File(downloadpfad);
        if (downloadDirectory.exists() && downloadDirectory.isDirectory()) {
            File[] files = downloadDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
            
            if (files != null) {
                for (File file : files) {
                    String providerName = file.getName();
                    Map<String, Double> providerValues = new HashMap<>();
                    
                    // Berechne 3MPDD für jeden Provider
                    double threeMonthProfit = htmlDb.getAverageMonthlyProfit(providerName, 3);
                    double equityDrawdown = htmlDb.getEquityDrawdown(providerName);
                    double mpdd3 = calculateMPDD(threeMonthProfit, equityDrawdown);
                    
                    // Speichere den 3MPDD-Wert
                    providerValues.put(STAT_TYPE_3MPDD, mpdd3);
                    
                    // Speichere den Abonnenten-Wert
                    double subscribers = htmlDb.getSubscribers(providerName);
                    providerValues.put(STAT_TYPE_SUBSCRIBERS, subscribers);
                    
                    allValues.put(providerName, providerValues);
                }
            }
        }
        
        return allValues;
    }

    /**
     * Hilfsmethode für die MPDD-Berechnung
     */
    private double calculateMPDD(double monthlyProfitPercent, double maxEquityDrawdown) {
        if (maxEquityDrawdown == 0.0) {
            return 0.0;  // Verhindert Division durch Null
        }
        return monthlyProfitPercent / maxEquityDrawdown;
    }

    /**
     * Holt die Historie der 3MPDD-Werte für einen Signal Provider
     * 
     * @param providerName Signal Provider Name
     * @return Liste von 3MPDD-Werten mit Zeitstempeln
     */
    public List<HistoryEntry> get3MpddHistory(String providerName) {
        return dbManager.getStatHistory(providerName, STAT_TYPE_3MPDD);
    }

    /**
     * Holt die Historie eines statistischen Werts für einen Signal Provider
     * 
     * @param providerName Signal Provider Name
     * @param statType Art des statistischen Werts
     * @return Liste von Werten mit Zeitstempeln
     */
    public List<HistoryEntry> getStatHistory(String providerName, String statType) {
        return dbManager.getStatHistory(providerName, statType);
    }

    /**
     * Holt alle Historieneinträge für alle Provider und Statistiktypen
     * 
     * @return Map mit Providername als Schlüssel und einer Map von Statistiktypen zu HistoryEntry-Listen als Wert
     */
    public Map<String, Map<String, List<HistoryEntry>>> getAllHistoryEntries() {
        if (dbManager == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return new HashMap<>();
        }
        
        Map<String, Map<String, List<HistoryEntry>>> result = new HashMap<>();
        
        try {
            // Alle Provider abrufen
            List<String> allProviders = dbManager.getAllProviders();
            
            // Für jeden Provider alle Stats abrufen
            for (String providerName : allProviders) {
                Map<String, List<HistoryEntry>> providerStats = new HashMap<>();
                
                // 3MPDD-Historie abrufen
                List<HistoryEntry> mpddHistory = dbManager.getStatHistory(providerName, STAT_TYPE_3MPDD);
                if (!mpddHistory.isEmpty()) {
                    providerStats.put(STAT_TYPE_3MPDD, mpddHistory);
                }
                
                // Subscribers-Historie abrufen
                List<HistoryEntry> subsHistory = dbManager.getStatHistory(providerName, STAT_TYPE_SUBSCRIBERS);
                if (!subsHistory.isEmpty()) {
                    providerStats.put(STAT_TYPE_SUBSCRIBERS, subsHistory);
                }
                
                if (!providerStats.isEmpty()) {
                    result.put(providerName, providerStats);
                }
            }
            
            return result;
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Abrufen aller Historieneinträge: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /**
     * Prüft, ob die Datenbank Einträge enthält
     * 
     * @return true wenn Einträge vorhanden sind, false sonst
     */
    public boolean hasDatabaseEntries() {
        if (dbManager == null) {
            return false;
        }
        
        try {
            // Prüfe, ob es Provider gibt
            List<String> providers = dbManager.getAllProviders();
            return !providers.isEmpty();
        } catch (Exception e) {
            LOGGER.severe("Fehler beim Prüfen auf Datenbankeinträge: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Erstellt ein Backup der Datenbank
     * 
     * @return true wenn erfolgreich, false wenn fehlgeschlagen
     */
    public boolean createBackup() {
        if (dbManager == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return false;
        }
        
        boolean success = dbManager.createBackup();
        if (success) {
            prefs.putLong(PREF_LAST_BACKUP_TIME, System.currentTimeMillis());
            LOGGER.info("Manuelles Datenbank-Backup erfolgreich erstellt");
        } else {
            LOGGER.severe("Fehler beim Erstellen des manuellen Backups");
        }
        
        return success;
    }

    /**
     * Findet alle geladenen Provider, die eine neue Analyse benötigen (nie analysiert oder > 7 Tage her).
     */
    public List<String> getProvidersNeedingAnalysis(Map<String, ProviderStats> allStats) {
        List<String> needingAnalysis = new java.util.ArrayList<>();
        if (dbManager == null || allStats == null) {
            return needingAnalysis;
        }
        
        for (String providerName : allStats.keySet()) {
            HistoryDatabaseManager.AnalysisResult analysis = dbManager.getProviderAnalysis(providerName);
            if (analysis == null) {
                needingAnalysis.add(providerName);
            } else {
                java.time.LocalDateTime lastAnalyzed = analysis.getLastAnalyzed();
                if (lastAnalyzed == null || java.time.temporal.ChronoUnit.DAYS.between(lastAnalyzed, java.time.LocalDateTime.now()) >= 7) {
                    needingAnalysis.add(providerName);
                }
            }
        }
        return needingAnalysis;
    }

    /**
     * Beendet den Service und gibt Ressourcen frei
     */
    public void shutdown() {
        // Erstelle ein letztes Backup
        createBackupIfNeeded(true);
        
        // Aktualisiere den gespeicherten Datenbestand
        saveCurrentDataCount();
        
        // Datenintegrität prüfen
        validateDatabaseIntegrity();
        
        if (dbManager != null) {
            dbManager.closeConnection();
        }
        LOGGER.info("Provider History Service beendet");
    }
}