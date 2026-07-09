package components;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import javax.swing.JTable;
import javax.swing.table.TableColumn;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import db.HistoryDatabaseManager;
import models.HighlightTableModel;

public class TableColumnManagerTest {

    static {
        System.setProperty("mql.test", "true");
        System.setProperty("java.awt.headless", "true");
    }

    private static final String CONFIG_FILENAME = "column_config.properties";
    private static final String BACKUP_FILENAME = "column_config.properties.bak";
    
    private static boolean backupExists = false;
    
    @TempDir
    Path tempDir;

    private JTable table;
    private HighlightTableModel tableModel;
    private TableColumnManager manager;

    @BeforeAll
    public static void backupConfigFile() throws IOException {
        File configFile = new File(CONFIG_FILENAME);
        if (configFile.exists()) {
            Files.copy(configFile.toPath(), Path.of(BACKUP_FILENAME), StandardCopyOption.REPLACE_EXISTING);
            backupExists = true;
            configFile.delete();
        }
    }

    @AfterAll
    public static void restoreConfigFile() throws IOException {
        File configFile = new File(CONFIG_FILENAME);
        if (configFile.exists()) {
            configFile.delete();
        }
        if (backupExists) {
            Files.move(Path.of(BACKUP_FILENAME), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        // Ensure properties file is deleted before each test for clean isolation
        File configFile = new File(CONFIG_FILENAME);
        if (configFile.exists()) {
            configFile.delete();
        }
        
        String rootPath = tempDir.toAbsolutePath().toString();
        // Initialize db manager first
        HistoryDatabaseManager.getInstance(rootPath);
        
        tableModel = new HighlightTableModel(rootPath);
        table = new JTable(tableModel);
        
        // Give all columns some initial preferred width for visibility tracking
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(100);
        }
        
        manager = new TableColumnManager(table);
    }

    @AfterEach
    public void cleanUp() {
        File configFile = new File(CONFIG_FILENAME);
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @Test
    public void testDefaultColumnVisibility() {
        manager.loadColumnVisibilitySettings();
        
        // Col 0, 1, 2 must be visible
        assertTrue(manager.isColumnVisible(0));
        assertTrue(manager.isColumnVisible(1));
        assertTrue(manager.isColumnVisible(2));
        
        // Max Drawdown % (Index 17) must always be hidden
        assertFalse(manager.isColumnVisible(17));
        
        // Check other standard visible columns
        // isStandardVisible logic: (i <= 2) || (i == 4) || (i == 5) || (i == 6) || (i == 10) || (i == 14) || (i == 18) || (i == 24) || (i == 25) || (i == 34)
        assertTrue(manager.isColumnVisible(4));
        assertTrue(manager.isColumnVisible(5));
        assertTrue(manager.isColumnVisible(6));
        assertTrue(manager.isColumnVisible(10));
        assertTrue(manager.isColumnVisible(14));
        assertTrue(manager.isColumnVisible(18));
        assertTrue(manager.isColumnVisible(24));
        assertTrue(manager.isColumnVisible(25));
        assertTrue(manager.isColumnVisible(34));
        
        // Column 3 is not in standard visible list, should be hidden by default
        assertFalse(manager.isColumnVisible(3));
    }

    @Test
    public void testSetColumnVisibilityConstraints() {
        // Columns 0 and 1 can never be hidden
        manager.setColumnVisible(0, false);
        manager.setColumnVisible(1, false);
        assertTrue(manager.isColumnVisible(0));
        assertTrue(manager.isColumnVisible(1));
        
        // Max Drawdown % (index 17) can never be made visible
        manager.setColumnVisible(17, false); // Explicitly hide it first
        manager.setColumnVisible(17, true);  // Try to show it
        assertFalse(manager.isColumnVisible(17)); // Verify it remains hidden
    }

    @Test
    public void testHideAndShowColumnWidthRestoration() {
        int targetCol = 5; // 3MPDD column
        
        // Setup initial visible width
        TableColumn col = table.getColumnModel().getColumn(targetCol);
        col.setPreferredWidth(125);
        
        // Hide
        manager.setColumnVisible(targetCol, false);
        assertFalse(manager.isColumnVisible(targetCol));
        assertEquals(0, col.getMinWidth());
        assertEquals(0, col.getMaxWidth());
        assertEquals(0, col.getPreferredWidth());
        
        // Show again
        manager.setColumnVisible(targetCol, true);
        assertTrue(manager.isColumnVisible(targetCol));
        assertEquals(125, col.getPreferredWidth());
        assertEquals(0, col.getMinWidth());
        assertEquals(Integer.MAX_VALUE, col.getMaxWidth());
    }

    @Test
    public void testOutOfBoundsVisibilityHandling() {
        // Check that calling setColumnVisible or isColumnVisible with out of bounds indices does not crash
        assertFalse(manager.isColumnVisible(-1));
        assertFalse(manager.isColumnVisible(999));
        
        assertDoesNotThrow(() -> manager.setColumnVisible(-1, true));
        assertDoesNotThrow(() -> manager.setColumnVisible(999, false));
    }

    @Test
    public void testSaveAndLoadSettingsRoundtrip() {
        // Toggle visibility of some columns
        manager.setColumnVisible(3, true); // Balance
        manager.setColumnVisible(5, false); // 3MPDD
        manager.setColumnVisible(10, false); // Trades
        manager.setColumnVisible(30, true); // Stabilität (now index 30)
        
        manager.saveColumnSettings();
        
        // Verify properties file is created
        File file = new File(CONFIG_FILENAME);
        assertTrue(file.exists());
        
        // Re-setup table to clear state
        table = new JTable(tableModel);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(100);
        }
        manager = new TableColumnManager(table);
        
        // Load settings
        manager.loadColumnVisibilitySettings();
        
        // Verify state is restored correctly
        assertTrue(manager.isColumnVisible(3));
        assertFalse(manager.isColumnVisible(5));
        assertFalse(manager.isColumnVisible(10));
        assertTrue(manager.isColumnVisible(30));
        
        // Constraints must still hold
        assertTrue(manager.isColumnVisible(0));
        assertTrue(manager.isColumnVisible(1));
        assertFalse(manager.isColumnVisible(17));
    }

    @Test
    public void testLoadMalformedSettingsFallbackToDefault() throws IOException {
        // Create malformed config file
        File file = new File(CONFIG_FILENAME);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("this is not a properties format string !!".getBytes());
        }
        
        // Should not crash, and should fall back to default visibility settings
        assertDoesNotThrow(() -> manager.loadColumnVisibilitySettings());
        
        // Columns 0, 1, 2 should be visible
        assertTrue(manager.isColumnVisible(0));
        assertTrue(manager.isColumnVisible(1));
        assertTrue(manager.isColumnVisible(2));
        assertFalse(manager.isColumnVisible(17));
    }

    @TestFactory
    public Collection<DynamicTest> testRandomVisibilityConfigurations() {
        List<DynamicTest> tests = new ArrayList<>();
        Random random = new Random(42); // seed to be deterministic

        // Generate 200 different random properties configurations and load them
        for (int run = 1; run <= 200; run++) {
            final int runIndex = run;
            final Properties testProps = new Properties();
            final boolean[] expectedVisibility = new boolean[35];
            
            for (int col = 0; col < 35; col++) {
                boolean visible = random.nextBoolean();
                testProps.setProperty("column_visible_" + col, String.valueOf(visible));
                
                // Work out expected visibility after manager applies restrictions
                if (col <= 1) {
                    expectedVisibility[col] = true; // First two columns always visible
                } else if (col == 17) {
                    expectedVisibility[col] = false; // Max Drawdown % always hidden
                } else {
                    expectedVisibility[col] = visible;
                }
            }

            tests.add(DynamicTest.dynamicTest("VisibilityConfigTest #" + runIndex, () -> {
                // Write props directly
                File file = new File(CONFIG_FILENAME);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    testProps.store(out, null);
                }
                
                // Re-initialize manager and table
                JTable testTable = new JTable(tableModel);
                for (int i = 0; i < testTable.getColumnCount(); i++) {
                    testTable.getColumnModel().getColumn(i).setPreferredWidth(100);
                }
                TableColumnManager testManager = new TableColumnManager(testTable);
                
                // Load config
                testManager.loadColumnVisibilitySettings();
                
                // Verify all column visibilities
                for (int col = 0; col < 35; col++) {
                    assertEquals(
                        expectedVisibility[col], 
                        testManager.isColumnVisible(col), 
                        String.format("Column %d visibility mismatch in run %d", col, runIndex)
                    );
                }
            }));
        }

        return tests;
    }
}
