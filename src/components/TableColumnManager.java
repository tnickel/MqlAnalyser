package components;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import javax.swing.JTable;
import javax.swing.table.TableColumn;

public class TableColumnManager {
    private static final Logger LOGGER = Logger.getLogger(TableColumnManager.class.getName());
    private static final String CONFIG_FILENAME = "column_config.properties";
    private static final String PREF_PREFIX = "column_visible_";
    
    private final JTable table;
    private final Map<Integer, Integer> originalColumnWidths = new HashMap<>();
    private final Properties properties = new Properties();

    public TableColumnManager(JTable table) {
        this.table = table;
    }

    public void loadColumnVisibilitySettings() {
        File configFile = new File(CONFIG_FILENAME);
        
        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                properties.load(in);
                
                // Prüfen, ob überhaupt Spalteneinstellungen existieren
                boolean hasSettings = false;
                for (int i = 0; i < table.getColumnCount(); i++) {
                    String key = "column_visible_" + i;
                    if (properties.getProperty(key) != null) {
                        hasSettings = true;
                        break;
                    }
                }
                
                if (hasSettings) {
                    // Bestehende Einstellungen laden
                    for (int i = 0; i < table.getColumnCount(); i++) {
                        // Erste 2 Spalten sind immer sichtbar
                        if (i <= 1) continue;
                        
                        String columnName = table.getColumnName(i);
                        
                        // MaxDrawdown-Spalte immer ausblenden
                        if (columnName.equals("Max Drawdown %")) {
                            TableColumn column = table.getColumnModel().getColumn(i);
                            int originalWidth = column.getPreferredWidth();
                            originalColumnWidths.put(i, originalWidth);
                            column.setMinWidth(0);
                            column.setPreferredWidth(0);
                            column.setMaxWidth(0);
                            continue;
                        }
                        
                        String key = "column_visible_" + i;
                        boolean visible = Boolean.parseBoolean(properties.getProperty(key, "true"));
                        
                        // Prüfe, ob die Spalte sichtbar sein soll
                        if (!visible) {
                            // Spalte verstecken
                            TableColumn column = table.getColumnModel().getColumn(i);
                            int originalWidth = column.getPreferredWidth();
                            originalColumnWidths.put(i, originalWidth);
                            column.setMinWidth(0);
                            column.setPreferredWidth(0);
                            column.setMaxWidth(0);
                        }
                    }
                } else {
                    // Wenn keine Einstellungen existieren, Standardwerte anwenden
                    setDefaultColumnVisibility();
                }
                
                LOGGER.info("Spalteneinstellungen wurden geladen aus: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                LOGGER.warning("Fehler beim Laden der Spalteneinstellungen: " + e.getMessage());
                e.printStackTrace();
                
                // Bei Fehler: Standardeinstellungen verwenden
                setDefaultColumnVisibility();
            }
        } else {
            // Wenn keine Konfigurationsdatei existiert, Standardeinstellungen verwenden
            setDefaultColumnVisibility();
        }
    }

    private void setDefaultColumnVisibility() {
        // Standardeinstellungen: Nur wichtige Spalten anzeigen, andere ausblenden
        // Beispiel: Spalten 0, 1, 3, 4, 8, 11, 15, 20, 21 sind sichtbar (Index-basiert)
        for (int i = 0; i < table.getColumnCount(); i++) {
            String columnName = table.getColumnName(i);
            
            // MaxDrawdown-Spalte immer ausblenden
            if (columnName.equals("Max Drawdown %")) {
                TableColumn column = table.getColumnModel().getColumn(i);
                int originalWidth = column.getPreferredWidth();
                originalColumnWidths.put(i, originalWidth);
                column.setMinWidth(0);
                column.setPreferredWidth(0);
                column.setMaxWidth(0);
                continue;
            }
            
            boolean isStandardVisible = (i <= 2) || (i == 4) || (i == 5) || (i == 6) || 
                                       (i == 10) || (i == 13) || (i == 17) || (i == 22) || (i == 23) || (i == 32);
            
            if (!isStandardVisible) {
                // Spalte verstecken
                TableColumn column = table.getColumnModel().getColumn(i);
                int originalWidth = column.getPreferredWidth();
                originalColumnWidths.put(i, originalWidth);
                column.setMinWidth(0);
                column.setPreferredWidth(0);
                column.setMaxWidth(0);
            }
        }
    }

    public boolean isColumnVisible(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= table.getColumnCount()) {
            return false;
        }
        
        return !originalColumnWidths.containsKey(columnIndex);
    }
   
    public void setColumnVisible(int columnIndex, boolean visible) {
        if (columnIndex < 0 || columnIndex >= table.getColumnCount()) {
            return;
        }
        
        // Verhindere, dass die ersten beiden Spalten jemals ausgeblendet werden
        if (columnIndex <= 1 && !visible) {
            return;
        }
        
        // Verhindere, dass die MaxDrawdown-Spalte jemals eingeblendet wird
        String columnName = table.getColumnName(columnIndex);
        if (columnName.equals("Max Drawdown %") && visible) {
            return;
        }
        
        try {
            TableColumn column = table.getColumnModel().getColumn(columnIndex);
            
            if (visible) {
                // Spalte wieder sichtbar machen
                if (originalColumnWidths.containsKey(columnIndex)) {
                    // Originale Breite wiederherstellen
                    column.setMinWidth(0);
                    column.setMaxWidth(Integer.MAX_VALUE);
                    column.setPreferredWidth(originalColumnWidths.get(columnIndex));
                    originalColumnWidths.remove(columnIndex);
                }
            } else {
                // Spalte unsichtbar machen
                if (!originalColumnWidths.containsKey(columnIndex)) {
                    // Originale Breite speichern
                    originalColumnWidths.put(columnIndex, column.getPreferredWidth());
                    
                    // Spalte auf minimale Breite setzen
                    column.setMinWidth(0);
                    column.setPreferredWidth(0);
                    column.setMaxWidth(0);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Fehler beim Ändern der Spaltensichtbarkeit: " + e.getMessage());
        }
    }
    
    public void saveColumnSettings() {
        try (FileOutputStream out = new FileOutputStream(CONFIG_FILENAME)) {
            Properties props = new Properties();
            
            // Speichere Sichtbarkeit für jede Spalte
            for (int i = 0; i < table.getColumnCount(); i++) {
                boolean visible = isColumnVisible(i);
                props.setProperty(PREF_PREFIX + i, String.valueOf(visible));
            }
            
            props.store(out, "Tabellen-Spalten Konfiguration");
            LOGGER.info("Spalteneinstellungen wurden gespeichert in: " + CONFIG_FILENAME);
        } catch (IOException e) {
            LOGGER.warning("Fehler beim Speichern der Spalteneinstellungen: " + e.getMessage());
        }
    }
}