package db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import utils.ApplicationConstants;

/**
 * Verwaltet die H2-Datenbankverbindung und Operationen für die Speicherung
 * von historischen Werten für Signal Provider
 */
public class HistoryDatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(HistoryDatabaseManager.class.getName());
    private static HistoryDatabaseManager instance;
    private Connection connection;
    private String rootPath;
    
    // SQL-Statements für Datenbankoperationen
    private static final String CREATE_PROVIDERS_TABLE = 
            "CREATE TABLE IF NOT EXISTS signal_providers (" +
            "provider_id INT AUTO_INCREMENT PRIMARY KEY, " +
            "provider_name VARCHAR(255) UNIQUE NOT NULL)";
    
    private static final String CREATE_STAT_VALUES_TABLE = 
    	    "CREATE TABLE IF NOT EXISTS stat_values (" +
    	    "id INT AUTO_INCREMENT PRIMARY KEY, " +
    	    "provider_id INT NOT NULL, " +
    	    "stat_type VARCHAR(50) NOT NULL, " +
    	    "recorded_date TIMESTAMP NOT NULL, " +
    	    "\"value\" DOUBLE NOT NULL, " +
    	    "FOREIGN KEY (provider_id) REFERENCES signal_providers(provider_id), " +
    	    "UNIQUE (provider_id, stat_type, recorded_date))";
    
    // Erweiterte Tabelle für Provider-Notizen und Risiko-Kategorie
    private static final String CREATE_PROVIDER_NOTES_TABLE = 
            "CREATE TABLE IF NOT EXISTS provider_notes (" +
            "provider_id INT PRIMARY KEY, " +
            "notes TEXT, " +
            "risk_category INT DEFAULT 0, " +
            "last_updated TIMESTAMP, " +
            "FOREIGN KEY (provider_id) REFERENCES signal_providers(provider_id))";
    
    // Tabelle für Martingale und Grid Analyse
    private static final String CREATE_PROVIDER_ANALYSIS_TABLE = 
            "CREATE TABLE IF NOT EXISTS provider_analysis (" +
            "provider_id INT PRIMARY KEY, " +
            "is_martingale BOOLEAN DEFAULT FALSE, " +
            "is_grid BOOLEAN DEFAULT FALSE, " +
            "last_analyzed TIMESTAMP, " +
            "FOREIGN KEY (provider_id) REFERENCES signal_providers(provider_id))";
    
    // Tabelle für gelöschte Einträge
    private static final String CREATE_DELETED_RECORDS_LOG = 
            "CREATE TABLE IF NOT EXISTS deleted_records_log (" +
            "id INT AUTO_INCREMENT PRIMARY KEY, " +
            "table_name VARCHAR(50) NOT NULL, " +
            "record_id INT NOT NULL, " +
            "deletion_date TIMESTAMP NOT NULL, " +
            "reason VARCHAR(255))";
    
    // Tabelle für Datenbankänderungen
    private static final String CREATE_DB_CHANGE_LOG = 
            "CREATE TABLE IF NOT EXISTS db_change_log (" +
            "id INT AUTO_INCREMENT PRIMARY KEY, " +
            "change_date TIMESTAMP NOT NULL, " +
            "change_type VARCHAR(50) NOT NULL, " +
            "table_name VARCHAR(50) NOT NULL, " +
            "description VARCHAR(1000))";
    
    private static final String INSERT_PROVIDER = 
            "INSERT INTO signal_providers (provider_name) VALUES (?)";
    
    private static final String GET_PROVIDER_ID = 
            "SELECT provider_id FROM signal_providers WHERE provider_name = ?";
    
    private static final String INSERT_STAT_VALUE = 
    	    "INSERT INTO stat_values (provider_id, stat_type, recorded_date, \"value\") VALUES (?, ?, ?, ?)";
    
    private static final String GET_LATEST_STAT_VALUE = 
    	    "SELECT \"value\" FROM stat_values " +
    	    "WHERE provider_id = ? AND stat_type = ? " +
    	    "ORDER BY recorded_date DESC LIMIT 1";
    
    private static final String GET_STAT_HISTORY = 
    	    "SELECT recorded_date, \"value\" FROM stat_values " +
    	    "WHERE provider_id = ? AND stat_type = ? " +
    	    "ORDER BY recorded_date DESC";
    
    private static final String LOG_DB_CHANGE =
            "INSERT INTO db_change_log (change_date, change_type, table_name, description) VALUES (?, ?, ?, ?)";
    
    /**
     * Privater Konstruktor für Singleton-Pattern
     */
    private HistoryDatabaseManager(String rootPath) {
        // Validiere den Pfad und korrigiere ihn, falls nötig
        this.rootPath = ApplicationConstants.validateRootPath(rootPath, "HistoryDatabaseManager.constructor");
        
        initDatabase();
    }

    
    public static synchronized HistoryDatabaseManager getInstance(String rootPath) {
        if (instance == null) {
            instance = new HistoryDatabaseManager(rootPath);
        }
        return instance;
    }

    public static synchronized HistoryDatabaseManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("HistoryDatabaseManager muss zuerst mit einem Pfad initialisiert werden");
        }
        return instance;
    }
    
    /**
     * Initialisiert die Datenbankverbindung und erstellt die Tabellen
     */
    private void initDatabase() {
        try {
            // H2-Treiber laden
            Class.forName("org.h2.Driver");
            
            // Verbindung zur Datenbank auf der Festplatte
            String dbPath = rootPath + File.separator + "database" + File.separator + "providerhistorydb";
            File dbDir = new File(rootPath + File.separator + "database");
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }
            
            connection = DriverManager.getConnection(
                "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE;DATABASE_TO_UPPER=false", 
                "sa", "");
            
            // Erstelle alle Tabellen
            createDatabaseSchema();
            
            // Aktualisiere Tabellenschema falls nötig
            updateDatabaseSchema();
            
            LOGGER.info("Provider History Datenbank erfolgreich initialisiert: " + dbPath);
        } catch (ClassNotFoundException | SQLException e) {
            LOGGER.severe("Fehler beim Initialisieren der Datenbank: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Erstellt die Datenbankstruktur mit allen Tabellen
     */
    private void createDatabaseSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Tabelle für Provider erstellen
            stmt.execute(CREATE_PROVIDERS_TABLE);
            
            // Tabelle für statistische Werte erstellen
            stmt.execute(CREATE_STAT_VALUES_TABLE);
            
            // Tabelle für Provider-Notizen erstellen
            stmt.execute(CREATE_PROVIDER_NOTES_TABLE);
            
            // Tabelle für Martingale und Grid Analyse erstellen
            stmt.execute(CREATE_PROVIDER_ANALYSIS_TABLE);
            
            // Tabelle für gelöschte Einträge erstellen
            stmt.execute(CREATE_DELETED_RECORDS_LOG);
            
            // Tabelle für Datenbankänderungen erstellen
            stmt.execute(CREATE_DB_CHANGE_LOG);
            
            // Eintrag zur Initialisierung in die Änderungslog-Tabelle
            logDbChange("INIT", "ALL", "Datenbank-Schema initialisiert oder überprüft");
            
            // Prüfen, ob alle Tabellen existieren
            checkTables();
        }
    }
    
    /**
     * Aktualisiert das Datenbankschema für die Risiko-Kategorie-Funktion
     */
    private void updateDatabaseSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Prüfe, ob die risk_category Spalte bereits existiert
            ResultSet rs = connection.getMetaData().getColumns(null, null, "PROVIDER_NOTES", "RISK_CATEGORY");
            boolean riskColumnExists = rs.next();
            
            if (!riskColumnExists) {
                // Füge die risk_category Spalte hinzu
                stmt.execute("ALTER TABLE provider_notes ADD COLUMN risk_category INT DEFAULT 0");
                logDbChange("ALTER", "provider_notes", "Spalte risk_category hinzugefügt");
                LOGGER.info("Spalte risk_category zur Tabelle provider_notes hinzugefügt");
            }
        }
    }
    
    /**
     * Prüft, ob alle erforderlichen Tabellen existieren
     */
    private void checkTables() throws SQLException {
        String[] tableNames = {"signal_providers", "stat_values", "provider_notes", "provider_analysis", "deleted_records_log", "db_change_log"};
        boolean allTablesExist = true;
        
        for (String tableName : tableNames) {
            ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null);
            boolean tableExists = rs.next();
            allTablesExist &= tableExists;
            LOGGER.info("Tabelle " + tableName + " existiert: " + tableExists);
        }
        
        if (!allTablesExist) {
            LOGGER.severe("Nicht alle erforderlichen Tabellen konnten erstellt werden!");
        }
    }
    
    /**
     * Fügt einen Eintrag zum Datenbankänderungslog hinzu
     */
    private void logDbChange(String changeType, String tableName, String description) {
        try (PreparedStatement stmt = connection.prepareStatement(LOG_DB_CHANGE)) {
            stmt.setObject(1, LocalDateTime.now());
            stmt.setString(2, changeType);
            stmt.setString(3, tableName);
            stmt.setString(4, description);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.warning("Fehler beim Logging der Datenbankänderung: " + e.getMessage());
        }
    }
    
    /**
     * Speichert einen statistischen Wert für einen Provider mit Sicherheitsmechanismus
     * Es werden nur neue Werte hinzugefügt, keine bestehenden überschrieben oder gelöscht
     */
    public boolean storeStatValue(String providerName, String statType, double value, boolean forceUpdate) {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return false;
        }
        
        try {
            // Prüfen, ob der Wert sich geändert hat (nur wenn forceUpdate=false)
            if (!forceUpdate) {
                Double latestValue = getLatestStatValue(providerName, statType);
                if (latestValue != null && Math.abs(latestValue - value) < 0.001) {
                    // Wert hat sich nicht signifikant geändert, keine Speicherung notwendig
                    LOGGER.fine(statType + "-Wert für " + providerName + " hat sich nicht geändert, keine Speicherung");
                    return true;
                }
            }
            
            // Provider-ID holen oder erstellen
            int providerId = getOrCreateProvider(providerName);
            
            // Aktuelles Datum/Zeit
            LocalDateTime now = LocalDateTime.now();
            
            // Sicherheitsabfrage: Prüfen ob genau derselbe Eintrag bereits existiert
            try (PreparedStatement checkStmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM stat_values WHERE provider_id = ? AND stat_type = ? AND recorded_date = ? AND \"value\" = ?")) {
                checkStmt.setInt(1, providerId);
                checkStmt.setString(2, statType);
                checkStmt.setObject(3, now);
                checkStmt.setDouble(4, value);
                
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    // Exakt derselbe Eintrag existiert bereits
                    LOGGER.fine("Exakt derselbe Eintrag existiert bereits. Keine doppelte Speicherung.");
                    return true;
                }
            }
            
            // Speichern als NEUER Eintrag (niemals bestehende ersetzen)
            try (PreparedStatement insertStmt = connection.prepareStatement(
                    INSERT_STAT_VALUE)) {
                insertStmt.setInt(1, providerId);
                insertStmt.setString(2, statType);
                insertStmt.setObject(3, now);
                insertStmt.setDouble(4, value);
                insertStmt.executeUpdate();
                
                logDbChange("INSERT", "stat_values", 
                        String.format("Neuer %s-Wert %.4f für Provider %s hinzugefügt", 
                                statType, value, providerName));
            }
            
            LOGGER.info(String.format("%s-Wert %.4f für %s gespeichert (Datum: %s)", 
                    statType, value, providerName, now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
            return true;
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Speichern des " + statType + "-Werts: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
   
    
    private int getOrCreateProvider(String providerName) throws SQLException {
        if (providerName == null || providerName.trim().isEmpty()) {
            throw new SQLException("Provider-Name darf nicht leer sein");
        }

        // Versuche zuerst, die ID zu bekommen
        try (PreparedStatement stmt = connection.prepareStatement(GET_PROVIDER_ID)) {
            stmt.setString(1, providerName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        
        // Wenn nicht gefunden, füge neuen Provider hinzu
        try (PreparedStatement stmt = connection.prepareStatement(
                INSERT_PROVIDER, 
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, providerName);
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Provider konnte nicht erstellt werden, keine Zeilen betroffen");
            }
            
            // Log die Erstellung des neuen Providers
            logDbChange("INSERT", "signal_providers", "Neuer Provider hinzugefügt: " + providerName);
            
            // Hole generierte ID
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    throw new SQLException("Konnte keine ID für den neu eingefügten Provider erhalten");
                }
            }
        } catch (SQLException e) {
            // Falls es einen Unique-Constraint-Fehler gibt (z.B. wenn der Provider in der Zwischenzeit
            // von einem anderen Thread eingefügt wurde), versuche es noch einmal mit SELECT
            if (e.getMessage().contains("Unique index or primary key violation") || 
                e.getMessage().contains("Unique constraint violation") ||
                e.getMessage().contains("Eindeutiger Index oder Primärschlüsselverletzung")) {
                try (PreparedStatement stmt = connection.prepareStatement(GET_PROVIDER_ID)) {
                    stmt.setString(1, providerName);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            throw e;
        }
    }
    
    /**
     * Holt den letzten gespeicherten statistischen Wert für einen Provider
     * 
     * @param providerName Name des Signal Providers
     * @param statType Art des statistischen Werts
     * @return Der letzte gespeicherte Wert oder null, wenn keiner gefunden wurde
     */
    public Double getLatestStatValue(String providerName, String statType) {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return null;
        }
        
        try {
            // Provider-ID holen
            int providerId = -1;
            try (PreparedStatement stmt = connection.prepareStatement(GET_PROVIDER_ID)) {
                stmt.setString(1, providerName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    providerId = rs.getInt(1);
                } else {
                    // Provider nicht gefunden
                    return null;
                }
            }
            
            // Letzten Wert holen
            try (PreparedStatement stmt = connection.prepareStatement(GET_LATEST_STAT_VALUE)) {
                stmt.setInt(1, providerId);
                stmt.setString(2, statType);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getDouble(1);
                } else {
                    // Keine Werte für diesen Provider
                    return null;
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Abrufen des letzten " + statType + "-Werts: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Holt die Historie der statistischen Werte für einen Provider
     * 
     * @param providerName Name des Signal Providers
     * @param statType Art des statistischen Werts
     * @return Liste der historischen Einträge
     */
    public List<HistoryEntry> getStatHistory(String providerName, String statType) {
        List<HistoryEntry> history = new ArrayList<>();
        
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return history;
        }
        
        try {
            // Provider-ID holen
            int providerId = -1;
            try (PreparedStatement stmt = connection.prepareStatement(GET_PROVIDER_ID)) {
                stmt.setString(1, providerName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    providerId = rs.getInt(1);
                } else {
                    // Provider nicht gefunden
                    return history;
                }
            }
            
            // Historie holen
            try (PreparedStatement stmt = connection.prepareStatement(GET_STAT_HISTORY)) {
                stmt.setInt(1, providerId);
                stmt.setString(2, statType);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    LocalDateTime date = rs.getObject(1, LocalDateTime.class);
                    double value = rs.getDouble(2);
                    history.add(new HistoryEntry(date, value));
                }
            }
            
            return history;
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Abrufen der " + statType + "-Historie: " + e.getMessage());
            e.printStackTrace();
            return history;
        }
    }
    
    /**
     * Backup-Methode, um eine Sicherungskopie der Datenbank zu erstellen
     * @return true wenn das Backup erfolgreich erstellt wurde
     */
    public boolean createBackup() {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return false;
        }
        
        try {
            // Generiere Zeitstempel für den Backup-Dateinamen
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "backup_" + timestamp + ".zip";
            String backupPath = rootPath + File.separator + "database" + File.separator + "backups";
            
            // Stelle sicher, dass der Backup-Ordner existiert
            File backupDir = new File(backupPath);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            // Vollständiger Pfad zur Backup-Datei
            String backupFile = backupPath + File.separator + backupFileName;
            
            // H2-Backup-Befehl ausführen
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("BACKUP TO '" + backupFile + "'");
                
                // Log die Backup-Erstellung
                logDbChange("BACKUP", "ALL", "Datenbank-Backup erstellt: " + backupFile);
                
                LOGGER.info("Datenbank-Backup erfolgreich erstellt: " + backupFile);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Erstellen des Datenbank-Backups: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Holt eine Liste aller Provider aus der Datenbank
     * 
     * @return Liste aller Providernamen
     */
    public List<String> getAllProviders() {
        List<String> providers = new ArrayList<>();
        
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return providers;
        }
        
        try (Statement stmt = connection.createStatement()) {
            String query = "SELECT provider_name FROM signal_providers ORDER BY provider_name";
            ResultSet rs = stmt.executeQuery(query);
            
            while (rs.next()) {
                providers.add(rs.getString(1));
            }
            
            return providers;
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Abrufen aller Provider: " + e.getMessage());
            e.printStackTrace();
            return providers;
        }
    }
    
    /**
     * Zählt die Gesamtanzahl der Einträge in der Datenbank
     */
    public int countAllEntries() {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return 0;
        }
        
        try (Statement stmt = connection.createStatement()) {
            // Zähle Einträge in signal_providers
            ResultSet rs1 = stmt.executeQuery("SELECT COUNT(*) FROM signal_providers");
            int providerCount = rs1.next() ? rs1.getInt(1) : 0;
            
            // Zähle Einträge in stat_values
            ResultSet rs2 = stmt.executeQuery("SELECT COUNT(*) FROM stat_values");
            int statValueCount = rs2.next() ? rs2.getInt(1) : 0;
            
            return providerCount + statValueCount;
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Zählen der Datenbankeinträge: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * Prüft, ob die Datenbank Datenverluste aufweist, indem die Anzahl der Einträge überprüft wird
     */
    public boolean checkDataIntegrity() {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return false;
        }
        
        try (Statement stmt = connection.createStatement()) {
            // Rufe den zuletzt protokollierten Eintragsstand ab
            ResultSet rs = stmt.executeQuery(
                "SELECT description FROM db_change_log " +
                "WHERE change_type = 'INTEGRITY_CHECK' " +
                "ORDER BY change_date DESC LIMIT 1");
            
            int lastRecordedCount = 0;
            if (rs.next()) {
                String desc = rs.getString(1);
                // Extrahiere die Anzahl aus dem Format "Integritätsprüfung: XYZ Einträge vorhanden"
                if (desc.contains(":")) {
                    String countStr = desc.split(":")[1].trim().split(" ")[0];
                    lastRecordedCount = Integer.parseInt(countStr);
                }
            }
            
            // Aktuelle Anzahl der Einträge
            int currentCount = countAllEntries();
            
            // Wenn die aktuelle Anzahl kleiner ist als die letzte protokollierte Anzahl,
            // könnte ein Datenverlust vorliegen
            boolean dataLoss = currentCount < lastRecordedCount;
            
            if (dataLoss) {
                LOGGER.severe("Möglicher Datenverlust erkannt! Aktuelle Einträge: " + currentCount + 
                        ", Letzte bekannte Anzahl: " + lastRecordedCount);
                
                // Protokolliere den potentiellen Datenverlust
                logDbChange("INTEGRITY_WARNING", "ALL", 
                        "Möglicher Datenverlust! Aktuelle Einträge: " + currentCount + 
                        ", Letzte bekannte Anzahl: " + lastRecordedCount);
                
                // Erstelle automatisch ein Backup
                createBackup();
            } else {
                // Protokolliere den aktuellen Zustand für zukünftige Prüfungen
                logDbChange("INTEGRITY_CHECK", "ALL", 
                        "Integritätsprüfung: " + currentCount + " Einträge vorhanden");
            }
            
            return !dataLoss;
        } catch (SQLException e) {
            LOGGER.severe("Fehler bei der Integritätsprüfung: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Schließt die Datenbankverbindung
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                // Erstelle ein Backup vor dem Schließen
                createBackup();
                
                // Prüfe die Datenintegrität
                checkDataIntegrity();
                
                // Protokolliere das Schließen
                logDbChange("SHUTDOWN", "ALL", "Datenbankverbindung wird ordnungsgemäß geschlossen");
                
                // Schließe die Verbindung
                connection.close();
                
                LOGGER.info("Datenbankverbindung geschlossen");
            } catch (SQLException e) {
                LOGGER.severe("Fehler beim Schließen der Datenbankverbindung: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Speichert Notizen für einen Signal Provider
     * 
     * @param providerName Name des Signal Providers
     * @param notes Die zu speichernden Notizen
     * @return true wenn die Notizen erfolgreich gespeichert wurden
     */
    public boolean saveProviderNotes(String providerName, String notes) {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return false;
        }
        
        try {
            // Provider-ID holen oder erstellen
            int providerId = getOrCreateProvider(providerName);
            
            // Prüfen, ob bereits Notizen existieren
            boolean exists = false;
            try (PreparedStatement checkStmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM provider_notes WHERE provider_id = ?")) {
                checkStmt.setInt(1, providerId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    exists = rs.getInt(1) > 0;
                }
            }
            
            // SQL für Insert oder Update
            String sql = exists ? 
                    "UPDATE provider_notes SET notes = ?, last_updated = ? WHERE provider_id = ?" :
                    "INSERT INTO provider_notes (provider_id, notes, risk_category, last_updated) VALUES (?, ?, 0, ?)";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                if (exists) {
                    stmt.setString(1, notes);
                    stmt.setObject(2, LocalDateTime.now());
                    stmt.setInt(3, providerId);
                } else {
                    stmt.setInt(1, providerId);
                    stmt.setString(2, notes);
                    stmt.setObject(3, LocalDateTime.now());
                }
                stmt.executeUpdate();
                
                // Log die Änderung
                logDbChange(exists ? "UPDATE" : "INSERT", "provider_notes", 
                        "Notizen für Provider " + providerName + " " + (exists ? "aktualisiert" : "hinzugefügt"));
                
                LOGGER.info("Notizen für Provider " + providerName + " erfolgreich gespeichert");
                return true;
            }
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Speichern der Notizen: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lädt Notizen für einen Signal Provider
     * 
     * @param providerName Name des Signal Providers
     * @return Die gespeicherten Notizen oder leerer String, wenn keine vorhanden
     */
    public String getProviderNotes(String providerName) {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return "";
        }
        
        try {
            // Provider-ID holen
            try (PreparedStatement stmt = connection.prepareStatement(GET_PROVIDER_ID)) {
                stmt.setString(1, providerName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int providerId = rs.getInt(1);
                    
                    // Notizen abfragen
                    try (PreparedStatement notesStmt = connection.prepareStatement(
                            "SELECT notes FROM provider_notes WHERE provider_id = ?")) {
                        notesStmt.setInt(1, providerId);
                        ResultSet notesRs = notesStmt.executeQuery();
                        if (notesRs.next()) {
                            return notesRs.getString(1);
                        }
                    }
                }
            }
            
            // Keine Notizen gefunden
            return "";
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Laden der Notizen: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
    
    /**
     * Speichert die Risiko-Kategorie für einen Signal Provider
     * 
     * @param providerName Name des Signal Providers
     * @param riskCategory Die Risiko-Kategorie (0-10, wobei 0 = kein Risiko gesetzt)
     * @return true wenn die Risiko-Kategorie erfolgreich gespeichert wurde
     */
    public boolean saveProviderRiskCategory(String providerName, int riskCategory) {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return false;
        }
        
        // Validiere Risiko-Kategorie
        if (riskCategory < 0 || riskCategory > 10) {
            LOGGER.warning("Ungültige Risiko-Kategorie: " + riskCategory + ". Erlaubt sind Werte von 0-10.");
            return false;
        }
        
        try {
            // Provider-ID holen oder erstellen
            int providerId = getOrCreateProvider(providerName);
            
            // Prüfen, ob bereits ein Eintrag existiert
            boolean exists = false;
            try (PreparedStatement checkStmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM provider_notes WHERE provider_id = ?")) {
                checkStmt.setInt(1, providerId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    exists = rs.getInt(1) > 0;
                }
            }
            
            // SQL für Insert oder Update
            String sql = exists ? 
                    "UPDATE provider_notes SET risk_category = ?, last_updated = ? WHERE provider_id = ?" :
                    "INSERT INTO provider_notes (provider_id, notes, risk_category, last_updated) VALUES (?, '', ?, ?)";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                if (exists) {
                    stmt.setInt(1, riskCategory);
                    stmt.setObject(2, LocalDateTime.now());
                    stmt.setInt(3, providerId);
                } else {
                    stmt.setInt(1, providerId);
                    stmt.setInt(2, riskCategory);
                    stmt.setObject(3, LocalDateTime.now());
                }
                stmt.executeUpdate();
                
                // Log die Änderung
                logDbChange(exists ? "UPDATE" : "INSERT", "provider_notes", 
                        "Risiko-Kategorie " + riskCategory + " für Provider " + providerName + " gesetzt");
                
                LOGGER.info("Risiko-Kategorie " + riskCategory + " für Provider " + providerName + " erfolgreich gespeichert");
                return true;
            }
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Speichern der Risiko-Kategorie: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lädt die Risiko-Kategorie für einen Signal Provider
     * 
     * @param providerName Name des Signal Providers
     * @return Die gespeicherte Risiko-Kategorie oder 0, wenn keine gesetzt ist
     */
    public int getProviderRiskCategory(String providerName) {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return 0;
        }
        
        try {
            // Provider-ID holen
            try (PreparedStatement stmt = connection.prepareStatement(GET_PROVIDER_ID)) {
                stmt.setString(1, providerName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int providerId = rs.getInt(1);
                    
                    // Risiko-Kategorie abfragen
                    try (PreparedStatement riskStmt = connection.prepareStatement(
                            "SELECT risk_category FROM provider_notes WHERE provider_id = ?")) {
                        riskStmt.setInt(1, providerId);
                        ResultSet riskRs = riskStmt.executeQuery();
                        if (riskRs.next()) {
                            return riskRs.getInt(1);
                        }
                    }
                }
            }
            // Keine Risiko-Kategorie gefunden
            return 0;
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Laden der Risiko-Kategorie: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * Klasse für Analyseergebnisse (Martingale und Grid)
     */
    public static class AnalysisResult {
        private final boolean isMartingale;
        private final boolean isGrid;
        private final LocalDateTime lastAnalyzed;

        public AnalysisResult(boolean isMartingale, boolean isGrid, LocalDateTime lastAnalyzed) {
            this.isMartingale = isMartingale;
            this.isGrid = isGrid;
            this.lastAnalyzed = lastAnalyzed;
        }

        public boolean isMartingale() { return isMartingale; }
        public boolean isGrid() { return isGrid; }
        public LocalDateTime getLastAnalyzed() { return lastAnalyzed; }
    }

    /**
     * Speichert das Analyseergebnis (Martingale / Grid) für einen Signal Provider
     */
    public boolean saveProviderAnalysis(String providerName, boolean isMartingale, boolean isGrid) {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return false;
        }
        
        try {
            int providerId = getOrCreateProvider(providerName);
            
            boolean exists = false;
            try (PreparedStatement checkStmt = connection.prepareStatement(
                    "SELECT COUNT(*) FROM provider_analysis WHERE provider_id = ?")) {
                checkStmt.setInt(1, providerId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    exists = rs.getInt(1) > 0;
                }
            }
            
            String sql = exists ?
                    "UPDATE provider_analysis SET is_martingale = ?, is_grid = ?, last_analyzed = ? WHERE provider_id = ?" :
                    "INSERT INTO provider_analysis (provider_id, is_martingale, is_grid, last_analyzed) VALUES (?, ?, ?, ?)";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                if (exists) {
                    stmt.setBoolean(1, isMartingale);
                    stmt.setBoolean(2, isGrid);
                    stmt.setObject(3, LocalDateTime.now());
                    stmt.setInt(4, providerId);
                } else {
                    stmt.setInt(1, providerId);
                    stmt.setBoolean(2, isMartingale);
                    stmt.setBoolean(3, isGrid);
                    stmt.setObject(4, LocalDateTime.now());
                }
                stmt.executeUpdate();
                
                logDbChange(exists ? "UPDATE" : "INSERT", "provider_analysis", 
                        String.format("Analyse (Martingale=%b, Grid=%b) für Provider %s %s", 
                                isMartingale, isGrid, providerName, exists ? "aktualisiert" : "hinzugefügt"));
                
                LOGGER.info("Analyse für Provider " + providerName + " erfolgreich gespeichert");
                return true;
            }
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Speichern der Analyse: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lädt das Analyseergebnis (Martingale / Grid) für einen Signal Provider
     */
    public AnalysisResult getProviderAnalysis(String providerName) {
        if (connection == null) {
            LOGGER.warning("Keine Datenbankverbindung verfügbar");
            return null;
        }
        
        try {
            try (PreparedStatement stmt = connection.prepareStatement(GET_PROVIDER_ID)) {
                stmt.setString(1, providerName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int providerId = rs.getInt(1);
                    
                    try (PreparedStatement analysisStmt = connection.prepareStatement(
                            "SELECT is_martingale, is_grid, last_analyzed FROM provider_analysis WHERE provider_id = ?")) {
                        analysisStmt.setInt(1, providerId);
                        ResultSet analysisRs = analysisStmt.executeQuery();
                        if (analysisRs.next()) {
                            boolean isMartingale = analysisRs.getBoolean(1);
                            boolean isGrid = analysisRs.getBoolean(2);
                            LocalDateTime lastAnalyzed = analysisRs.getObject(3, LocalDateTime.class);
                            return new AnalysisResult(isMartingale, isGrid, lastAnalyzed);
                        }
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            LOGGER.severe("Fehler beim Laden der Analyse: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Klasse für Historieneinträge
     */
    public static class HistoryEntry {
        private final LocalDateTime date;
        private final double value;
        
        public HistoryEntry(LocalDateTime date, double value) {
            this.date = date;
            this.value = value;
        }
        
        public LocalDateTime getDate() {
            return date;
        }
        
        public double getValue() {
            return value;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %.4f", 
                    date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), value);
        }
    }
}