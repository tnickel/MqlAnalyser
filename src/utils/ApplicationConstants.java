package utils;

import java.util.logging.Logger;

import javax.swing.JOptionPane;

/**
 * Zentrale Klasse für globale Konstanten der Anwendung
 */
public class ApplicationConstants {
    private static final Logger LOGGER = Logger.getLogger(ApplicationConstants.class.getName());
    
    // Pfad-Konstanten
    public static final String ROOT_PATH = "c:\\Forex\\MqlAnalyzer";
    
    // Private Konstruktor um Instanziierung zu verhindern
    private ApplicationConstants() {}
    
    /**
     * Methode zur Validierung des Rootpaths
     * 
     * @param path Der zu prüfende Pfad
     * @return true wenn der Pfad gültig ist, sonst false
     */
    public static boolean isValidRootPath(String path) {
        if (Boolean.getBoolean("mql.test")) {
            return true;
        }
        return ROOT_PATH.equals(path);
    }
    
    /**
     * Prüft, ob der übergebene Pfad gültig ist und zeigt einen Dialog an, falls nicht
     * 
     * @param path Der zu prüfende Pfad
     * @param callerInfo Information über den Aufrufer (für Fehlermeldungen)
     * @return Der korrigierte Pfad (entweder der ursprüngliche oder der gültige Pfad)
     */
    public static String validateRootPath(String path, String callerInfo) {
        if (!isValidRootPath(path)) {
            // Log the error
            LOGGER.severe("Ungültiger Rootpath in " + callerInfo + ": " + path);
            
            // Show error dialog if not headless
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                try {
                    JOptionPane.showMessageDialog(
                        null,
                        "Der Rootpath muss '" + ROOT_PATH + "' sein.\n\nAktueller Wert: " + path + 
                        "\n\nDies kann zu falschen Dateipfaden führen!" +
                        "\nAufrufer: " + callerInfo,
                        "Ungültiger Rootpath",
                        JOptionPane.ERROR_MESSAGE
                    );
                } catch (Exception e) {
                    LOGGER.warning("Konnte JOptionPane nicht anzeigen: " + e.getMessage());
                }
            }
            
            // Return the correct path
            return ROOT_PATH;
        }
        
        return path;
    }
}