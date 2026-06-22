package db;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HistoryDatabaseManagerTest {

    static {
        System.setProperty("mql.test", "true");
        System.setProperty("java.awt.headless", "true");
    }

    @TempDir
    Path tempDir;

    private HistoryDatabaseManager dbManager;
    private String rootPath;

    @BeforeEach
    public void setUp() throws Exception {
        // Reset the singleton instance before each test
        resetSingleton();
        rootPath = tempDir.toAbsolutePath().toString();
        dbManager = HistoryDatabaseManager.getInstance(rootPath);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (dbManager != null) {
            dbManager.closeConnection();
        }
        resetSingleton();
    }

    private void resetSingleton() throws Exception {
        Field instanceField = HistoryDatabaseManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private Connection getRawConnection() throws Exception {
        Field connectionField = HistoryDatabaseManager.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        return (Connection) connectionField.get(dbManager);
    }

    @Test
    public void testSingletonInitialization() {
        assertNotNull(dbManager);
        HistoryDatabaseManager secondInstance = HistoryDatabaseManager.getInstance();
        assertSame(dbManager, secondInstance);
    }

    @Test
    public void testExceptionOnUninitializedGetInstance() throws Exception {
        resetSingleton();
        assertThrows(IllegalStateException.class, () -> {
            HistoryDatabaseManager.getInstance();
        });
    }

    @Test
    public void testDatabaseSchemaInitialization() throws Exception {
        Connection conn = getRawConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());

        DatabaseMetaData meta = conn.getMetaData();
        
        // Verify tables exist
        String[] expectedTables = {
            "signal_providers", 
            "stat_values", 
            "provider_notes", 
            "provider_analysis", 
            "deleted_records_log", 
            "db_change_log"
        };
        
        for (String tableName : expectedTables) {
            boolean found = false;
            try (ResultSet rs = meta.getTables(null, null, tableName.toLowerCase(), null)) {
                if (rs.next()) found = true;
            }
            if (!found) {
                try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), null)) {
                    if (rs.next()) found = true;
                }
            }
            assertTrue(found, "Table " + tableName + " should exist");
        }
    }

    @Test
    public void testStoreAndGetStatValue() {
        String provider = "TestProvider";
        String statType = "3MPDD";
        double value = 14.55;

        // Store value
        assertTrue(dbManager.storeStatValue(provider, statType, value, false));

        // Retrieve latest value
        Double retrieved = dbManager.getLatestStatValue(provider, statType);
        assertNotNull(retrieved);
        assertEquals(value, retrieved, 0.001);

        // Store same value again without forcing - should return true but not add another record
        assertTrue(dbManager.storeStatValue(provider, statType, value, false));
        List<HistoryDatabaseManager.HistoryEntry> history = dbManager.getStatHistory(provider, statType);
        assertEquals(1, history.size());

        // Store same value forcing update - should add another record
        assertTrue(dbManager.storeStatValue(provider, statType, value, true));
        history = dbManager.getStatHistory(provider, statType);
        assertEquals(2, history.size());
    }

    @Test
    public void testGetStatHistoryOrdering() throws Exception {
        String provider = "OrderedProvider";
        String statType = "WinRate";

        // Store multiple values with delay to ensure order or force distinct values
        dbManager.storeStatValue(provider, statType, 60.0, true);
        Thread.sleep(10); // short pause to ensure time differences
        dbManager.storeStatValue(provider, statType, 65.5, true);
        Thread.sleep(10);
        dbManager.storeStatValue(provider, statType, 70.2, true);

        List<HistoryDatabaseManager.HistoryEntry> history = dbManager.getStatHistory(provider, statType);
        assertEquals(3, history.size());
        
        // Latest should be first in results since query has ORDER BY recorded_date DESC
        assertEquals(70.2, history.get(0).getValue());
        assertEquals(65.5, history.get(1).getValue());
        assertEquals(60.0, history.get(2).getValue());
    }

    @Test
    public void testUniqueConstraintsOnStatValues() throws Exception {
        String providerName = "UniqueConstraintProvider";
        String statType = "Drawdown";
        double val = 12.34;
        
        // Directly insert duplicate via raw SQL to check index violations
        dbManager.storeStatValue(providerName, statType, val, true);
        
        Connection conn = getRawConnection();
        // Retrieve provider id
        int providerId = -1;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT provider_id FROM signal_providers WHERE provider_name = '" + providerName + "'")) {
            if (rs.next()) {
                providerId = rs.getInt(1);
            }
        }
        assertTrue(providerId > 0);

        // Retrieve last inserted recorded_date
        LocalDateTime recordedDate = null;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT recorded_date FROM stat_values WHERE provider_id = " + providerId)) {
            if (rs.next()) {
                recordedDate = rs.getObject(1, LocalDateTime.class);
            }
        }
        assertNotNull(recordedDate);

        // Attempting to insert exact same provider_id, stat_type, recorded_date should fail unique constraint
        final int pId = providerId;
        final LocalDateTime rDate = recordedDate;
        try (Statement stmt = conn.createStatement()) {
            assertThrows(Exception.class, () -> {
                stmt.execute(String.format(
                    "INSERT INTO stat_values (provider_id, stat_type, recorded_date, \"value\") VALUES (%d, '%s', '%s', %f)",
                    pId, statType, rDate.toString().replace('T', ' '), 99.9
                ));
            });
        }
    }

    @Test
    public void testSaveAndGetProviderNotes() {
        String provider = "NotesProvider";
        String notesText = "This is a detailed test note with special characters: \", ', \\, \n, and emoji 🚀";

        // Should return empty string for non-existent notes
        assertEquals("", dbManager.getProviderNotes(provider));

        // Save notes
        assertTrue(dbManager.saveProviderNotes(provider, notesText));
        assertEquals(notesText, dbManager.getProviderNotes(provider));

        // Update notes
        String updatedNotesText = "Updated notes.";
        assertTrue(dbManager.saveProviderNotes(provider, updatedNotesText));
        assertEquals(updatedNotesText, dbManager.getProviderNotes(provider));
    }

    @Test
    public void testSaveAndGetRiskCategory() {
        String provider = "RiskProvider";

        // Default should be 0
        assertEquals(0, dbManager.getProviderRiskCategory(provider));

        // Save valid risk category
        assertTrue(dbManager.saveProviderRiskCategory(provider, 5));
        assertEquals(5, dbManager.getProviderRiskCategory(provider));

        // Save another valid risk category
        assertTrue(dbManager.saveProviderRiskCategory(provider, 10));
        assertEquals(10, dbManager.getProviderRiskCategory(provider));

        // Invalid risk category boundary tests (<0 and >10)
        assertFalse(dbManager.saveProviderRiskCategory(provider, -1));
        assertFalse(dbManager.saveProviderRiskCategory(provider, 11));
        
        // Check that risk category remained unchanged after invalid attempts
        assertEquals(10, dbManager.getProviderRiskCategory(provider));
    }

    @Test
    public void testSaveAndGetProviderAnalysis() {
        String provider = "AnalysisProvider";

        // Non-existent should return null
        assertNull(dbManager.getProviderAnalysis(provider));

        // Save Martingale only
        assertTrue(dbManager.saveProviderAnalysis(provider, true, false));
        HistoryDatabaseManager.AnalysisResult result = dbManager.getProviderAnalysis(provider);
        assertNotNull(result);
        assertTrue(result.isMartingale());
        assertFalse(result.isGrid());
        assertNotNull(result.getLastAnalyzed());

        // Save Grid only
        assertTrue(dbManager.saveProviderAnalysis(provider, false, true));
        result = dbManager.getProviderAnalysis(provider);
        assertNotNull(result);
        assertFalse(result.isMartingale());
        assertTrue(result.isGrid());

        // Save both
        assertTrue(dbManager.saveProviderAnalysis(provider, true, true));
        result = dbManager.getProviderAnalysis(provider);
        assertNotNull(result);
        assertTrue(result.isMartingale());
        assertTrue(result.isGrid());
    }

    @Test
    public void testBackupSystem() {
        // Backup directory path inside rootPath/database/backups
        File backupDir = new File(rootPath + File.separator + "database" + File.separator + "backups");
        assertFalse(backupDir.exists() || (backupDir.exists() && backupDir.listFiles().length > 0));

        // Create backup
        assertTrue(dbManager.createBackup());

        // Verify file is created
        assertTrue(backupDir.exists());
        File[] files = backupDir.listFiles();
        assertNotNull(files);
        assertTrue(files.length > 0, "Backup file should be created");
        assertTrue(files[0].getName().startsWith("backup_"));
        assertTrue(files[0].getName().endsWith(".zip"));
    }

    @Test
    public void testIntegrityCheckAndDataLossDetection() throws Exception {
        // Run check initially (no previous checks exist)
        assertTrue(dbManager.checkDataIntegrity());

        // Write some records
        dbManager.storeStatValue("P1", "Type1", 10.0, true);
        dbManager.storeStatValue("P2", "Type2", 20.0, true);

        // Run integrity check to log current count
        assertTrue(dbManager.checkDataIntegrity());

        // Clear or decrease count manually in database to simulate data loss
        Connection conn = getRawConnection();
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM stat_values");
        }

        // Run integrity check again - should detect lower count than last check and warn (but return false because of dataLoss)
        assertFalse(dbManager.checkDataIntegrity());
    }

    @Test
    public void testDatabaseSchemaColumnMigration() throws Exception {
        // Test that table provider_notes upgrades column if it is missing
        dbManager.closeConnection();
        resetSingleton();

        // 1. Create a dummy H2 DB and create provider_notes without risk_category
        String dbPath = rootPath + File.separator + "database" + File.separator + "providerhistorydb";
        try (Connection conn = DriverManager.getConnection("jdbc:h2:file:" + dbPath + ";DATABASE_TO_UPPER=false", "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS provider_notes");
                stmt.execute("DROP TABLE IF EXISTS provider_analysis");
                stmt.execute("DROP TABLE IF EXISTS stat_values");
                stmt.execute("DROP TABLE IF EXISTS signal_providers");
                
                stmt.execute("CREATE TABLE signal_providers (provider_id INT AUTO_INCREMENT PRIMARY KEY, provider_name VARCHAR(255) UNIQUE NOT NULL)");
                stmt.execute("CREATE TABLE provider_notes (provider_id INT PRIMARY KEY, notes TEXT, last_updated TIMESTAMP, FOREIGN KEY (provider_id) REFERENCES signal_providers(provider_id))");
            }
        }

        // 2. Initialize HistoryDatabaseManager - it should run updateDatabaseSchema and add risk_category
        dbManager = HistoryDatabaseManager.getInstance(rootPath);
        
        // 3. Verify column exists now
        Connection conn = getRawConnection();
        assertTrue(columnExists(conn, "provider_notes", "risk_category"), "Column risk_category should have been added by migration");
    }

    @Test
    public void testGetAllProviders() {
        List<String> emptyList = dbManager.getAllProviders();
        assertTrue(emptyList.isEmpty());

        dbManager.storeStatValue("Provider C", "Metric", 1.0, true);
        dbManager.storeStatValue("Provider A", "Metric", 2.0, true);
        dbManager.storeStatValue("Provider B", "Metric", 3.0, true);

        List<String> providers = dbManager.getAllProviders();
        assertEquals(3, providers.size());
        
        // Verify alphabetical sorting
        assertEquals("Provider A", providers.get(0));
        assertEquals("Provider B", providers.get(1));
        assertEquals("Provider C", providers.get(2));
    }

    @Test
    public void testConcurrentProviderCreation() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        String sharedProviderName = "SharedConcurrentProvider";

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await(); // wait for start signal
                    dbManager.storeStatValue(sharedProviderName, "Metric", Math.random(), true);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); // trigger concurrent run
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // There should be no exceptions (no unique key constraint issues on provider insertion due to handling)
        assertTrue(exceptions.isEmpty(), "Concurrency exception occurred: " + (exceptions.isEmpty() ? "" : exceptions.get(0).getMessage()));
        
        // Verify provider is created and has a single entry in signal_providers
        Connection conn = getRawConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM signal_providers WHERE provider_name = '" + sharedProviderName + "'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
            if (rs.next()) return true;
        }
        return false;
    }
}
