package renderers;

import java.awt.Component;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Renderer, der Zahlen mit maximal 2 Nachkommastellen formatiert
 */
public class NumberFormatRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;
    
    private final NumberFormat formatter;
    private final HighlightRenderer baseRenderer;
    
    public NumberFormatRenderer(HighlightRenderer baseRenderer) {
        // Formatter für maximal 2 Nachkommastellen
        formatter = new DecimalFormat("#0.00");
        this.baseRenderer = baseRenderer;
    }
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                  boolean isSelected, boolean hasFocus,
                                                  int row, int column) {
        // Überprüfen, ob der Wert eine Zahl ist und formatieren
        if (value instanceof Number) {
            Number num = (Number) value;
            if (num instanceof Integer || num instanceof Long) {
                value = String.valueOf(num.longValue());
            } else {
                value = formatter.format(num.doubleValue());
            }
        }
        
        // Nutze den HighlightRenderer für Hintergrundfarben und andere Formatierungen
        Component comp = baseRenderer.getTableCellRendererComponent(
            table, value, isSelected, hasFocus, row, column);
        
        return comp;
    }
    
    /**
     * Gibt den zugrundeliegenden HighlightRenderer zurück
     * @return Der HighlightRenderer
     */
    public HighlightRenderer getBaseRenderer() {
        return this.baseRenderer;
    }
}