package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import models.FilterCriteria;
import models.FilterCriteria.FilterRange;

public class FilterDialog extends JDialog {
    private final JTable filterTable;
    private JTextField currencyPairsField; // Neu: Textfeld für Währungspaare
    private FilterCriteria currentFilters;
    
    private static final String[] COLUMN_NAMES = {
        "Column Name", "Min Value", "Max Value"
    };
    
    private static final String[] TABLE_COLUMNS = {
        "No.", "Signal Provider", "Score", "Balance", "Subscribers", "3MPDD", "6MPDD", "9MPDD", "12MPDD", 
        "3MProfProz", "Trades", "Trade Days", "Pairs", "Days", "Win Rate %", "Total Profit", 
        "Avg Profit/Trade", "Max Drawdown %", "Equity Drawdown %", "Profit Factor", 
        "MaxTrades", "MaxLots", "Max Duration (h)", "Avg Duration (h)", "Risiko", "Risk Score", "S/L", "T/P", 
        "Start Date", "End Date", "Stabilitaet", "Steigung", "MaxDDGraphic", "EquityDrawdown3M%", "M/G"
    };

    public FilterDialog(JFrame parent, FilterCriteria filters) {
        super(parent, "Filter Settings", true);
        this.currentFilters = filters;
        
        DefaultTableModel model = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // "Max Drawdown %"-Zeile nicht editierbar machen
                String rowName = (String) getValueAt(row, 0);
                if (rowName.equals("Max Drawdown %")) {
                    return false;
                }
                return column > 0; 
            }
            
            @Override
            public void setValueAt(Object value, int row, int col) {
                // Verhindere Setzen von Werten für "Max Drawdown %"-Zeile
                String rowName = (String) getValueAt(row, 0);
                if (rowName.equals("Max Drawdown %")) {
                    return;
                }
                
                // Validiere Eingabe sofort
                if (col > 0) {  // Nur für Min und Max Spalten
                    String strValue = value.toString().trim();
                    
                    // Für Text-Spalten (Signal Provider, Start Date, End Date)
                    String columnNameText = getValueAt(row, 0).toString();
                    if ("Signal Provider".equals(columnNameText) || "Start Date".equals(columnNameText) || "End Date".equals(columnNameText)) {
                        super.setValueAt(strValue, row, col);
                        return;
                    }
                    
                    // Für numerische Spalten
                    if (!strValue.isEmpty()) {
                        try {
                            Double.parseDouble(strValue);
                            super.setValueAt(strValue, row, col);
                        } catch (NumberFormatException e) {
                            JOptionPane.showMessageDialog(null,
                                "Bitte geben Sie eine gültige Zahl ein.",
                                "Ungültige Eingabe",
                                JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                    } else {
                        super.setValueAt(strValue, row, col);
                    }
                }
            }
        };
        
        // Model mit existierenden Filtern befüllen
        for (int i = 0; i < TABLE_COLUMNS.length; i++) {
            // Max Drawdown komplett überspringen/auslassen
            if (TABLE_COLUMNS[i].equals("Max Drawdown %")) {
                continue; // Eintrag komplett auslassen
            }
            
            Object minValue = "";
            Object maxValue = "";
            
            if (filters.getFilters().containsKey(i)) {
                FilterRange range = filters.getFilters().get(i);
                minValue = range.getMin() != null ? range.getMin().toString() : "";
                maxValue = range.getMax() != null ? range.getMax().toString() : "";
            }
            model.addRow(new Object[]{TABLE_COLUMNS[i], minValue, maxValue});
        }
        
        filterTable = new JTable(model);
        
        // Verbesserte Zellenbearbeitung
        filterTable.setDefaultEditor(Object.class, new DefaultCellEditor(new JTextField()) {
            {
                getComponent().addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) {
                        stopCellEditing();
                    }
                });
            }
            
            @Override
            public boolean stopCellEditing() {
                try {
                    JTextField textField = (JTextField) getComponent();
                    String value = textField.getText().trim();
                    if (!value.isEmpty()) {
                        int row = filterTable.getEditingRow();
                        if (row != -1) {
                            String rowName = (String) filterTable.getValueAt(row, 0);
                            
                            if (!("Signal Provider".equals(rowName) || "Start Date".equals(rowName) || "End Date".equals(rowName))) {  // Nicht für Text-Spalten
                                Double.parseDouble(value);
                            }
                        }
                    }
                    return super.stopCellEditing();
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        });
        
        // Verbesserte Tabelleneinstellungen
        filterTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        filterTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        filterTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        filterTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        
        setLayout(new BorderLayout(5, 5));
        
        // Panel für Checkboxen und Währungspaar-Filter
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        
        JScrollPane scrollPane = new JScrollPane(filterTable);
        scrollPane.setPreferredSize(new Dimension(400, 400));
        
        // Panel für Währungspaar-Filter
        JPanel currencyPanel = new JPanel(new BorderLayout(5, 0));
        currencyPanel.setBorder(BorderFactory.createTitledBorder("Währungspaare Filter"));
        
        JLabel currencyLabel = new JLabel("Währungspaare (durch Komma getrennt):");
        currencyPairsField = new JTextField(30);
        currencyPairsField.setText(filters.getCurrencyPairsFilter());
        
        JPanel currencyFieldPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        currencyFieldPanel.add(currencyLabel);
        currencyFieldPanel.add(currencyPairsField);
        
        JLabel currencyHintLabel = new JLabel(
                "<html>Geben Sie Währungspaare ein, die alle vorhanden sein müssen (z.B. EURUSD, GBPUSD).<br>" +
                "Die Einträge werden als Präfixe behandelt, d.h. EURUSD findet auch EURUSD.ru, EURUSD.X, etc.</html>");
        currencyHintLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        
        currencyPanel.add(currencyFieldPanel, BorderLayout.NORTH);
        currencyPanel.add(currencyHintLabel, BorderLayout.CENTER);
        
        // Haupt-Panel zusammensetzen
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(currencyPanel, BorderLayout.SOUTH);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("Apply");
        JButton cancelButton = new JButton("Cancel");
        JButton resetButton = new JButton("Reset");
        JButton cleanButton = new JButton("Clean"); // Neuer Clean-Button

        okButton.addActionListener(e -> {
            stopEditing();
            if (validateAndSaveFilters()) {
                dispose();
            }
        });

        cancelButton.addActionListener(e -> dispose());

        resetButton.addActionListener(e -> {
            setupDefaultValues();
        });

        cleanButton.addActionListener(e -> {
            cleanAllValues();
        });

        buttonPanel.add(cleanButton);     // Neuer Clean-Button zuerst
        buttonPanel.add(resetButton);
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        add(new JLabel("Set min/max values for filtering:"), BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                stopEditing();
            }
        });
        
        pack();
        setLocationRelativeTo(parent);
    }
    
    private void stopEditing() {
        if (filterTable.isEditing()) {
            filterTable.getCellEditor().stopCellEditing();
        }
    }
    
    // Methode zum Setzen der Standardwerte - überarbeitet mit neuen Standardwerten
 // Diese Methode befindet sich in der Klasse src/ui/FilterDialog.java
    private void setupDefaultValues() {
        // Standardwerte definieren gemäß den Anforderungen
        Map<String, Object[]> defaultValues = new HashMap<>();
        
        // Format: Spaltenname -> [Min-Wert, Max-Wert]
        // Wenn nur Min oder nur Max gesetzt werden soll, den anderen Wert als null lassen
        
        // No > 1.0
        defaultValues.put("No.", new Object[]{1.0, null});
        
        // 3Mpdd > 2
        defaultValues.put("3MPDD", new Object[]{2.0, null});
        
        // Days > 63
        defaultValues.put("Days", new Object[]{63.0, null});
        
        // Trades > 60
        defaultValues.put("Trades", new Object[]{60.0, null});
        
        // WinRate% < 80%
        defaultValues.put("Win Rate %", new Object[]{null, 80.0});
        
        // Steigung > 0
        defaultValues.put("Steigung", new Object[]{0.0, null});
        
        // MaxTrades < 15
        defaultValues.put("MaxTrades", new Object[]{null, 15.0});
        
        // MaxDuration < 120 Stunden
        defaultValues.put("Max Duration (h)", new Object[]{null, 120.0});
        
        // MaxEquityDrawdown % < 15%
        defaultValues.put("Equity Drawdown %", new Object[]{null, 15.0});
        
        // Risiko <= 5 (Neue Standardeinstellung)
        defaultValues.put("Risiko", new Object[]{null, 5.0});
        
        // Über alle Zeilen gehen und Standardwerte setzen wo vorhanden
        for (int row = 0; row < filterTable.getRowCount(); row++) {
            String columnName = (String) filterTable.getValueAt(row, 0);
            
            if (defaultValues.containsKey(columnName)) {
                Object[] values = defaultValues.get(columnName);
                
                // Min-Wert setzen (wenn nicht null)
                if (values[0] != null) {
                    filterTable.setValueAt(values[0].toString(), row, 1);
                } else {
                    filterTable.setValueAt("", row, 1);
                }
                
                // Max-Wert setzen (wenn nicht null)
                if (values[1] != null) {
                    filterTable.setValueAt(values[1].toString(), row, 2);
                } else {
                    filterTable.setValueAt("", row, 2);
                }
            } else {
                // Für alle anderen Spalten beide Werte leeren
                filterTable.setValueAt("", row, 1);
                filterTable.setValueAt("", row, 2);
            }
        }
        
        // Währungspaar-Feld zurücksetzen
        currencyPairsField.setText("");
    }
    
    private boolean validateAndSaveFilters() {
        stopEditing();
        
        FilterCriteria criteria = new FilterCriteria();
        boolean hasAnyFilter = false;
        
        // Währungspaar-Filter einstellen
        String currencyFilter = currencyPairsField.getText().trim();
        criteria.setCurrencyPairsFilter(currencyFilter);
        
        if (!currencyFilter.isEmpty()) {
            hasAnyFilter = true;
        }
        
        for (int row = 0; row < filterTable.getRowCount(); row++) {
            String columnName = (String) filterTable.getValueAt(row, 0);
            
            String minStr = ((String) filterTable.getValueAt(row, 1)).trim();
            String maxStr = ((String) filterTable.getValueAt(row, 2)).trim();
            
            if (minStr.isEmpty() && maxStr.isEmpty()) {
                continue;
            }
            
            // Finde die entsprechende Spalten-ID
            int columnId = -1;
            for (int i = 0; i < TABLE_COLUMNS.length; i++) {
                if (TABLE_COLUMNS[i].equals(columnName)) {
                    columnId = i;
                    break;
                }
            }
            
            if (columnId == -1) continue;
            
            // Textfilter für Signal Provider, Start Date und End Date
            if ("Signal Provider".equals(columnName) || "Start Date".equals(columnName) || "End Date".equals(columnName)) {
                if (!minStr.isEmpty()) {
                    criteria.addFilter(columnId, new FilterRange(minStr));
                    hasAnyFilter = true;
                }
                continue;
            }
            
            try {
                Double min = minStr.isEmpty() ? null : Double.parseDouble(minStr);
                Double max = maxStr.isEmpty() ? null : Double.parseDouble(maxStr);
                
                // Prüfe, ob Min kleiner als Max ist
                if (min != null && max != null && min > max) {
                    JOptionPane.showMessageDialog(this,
                        "Minimum muss kleiner als Maximum sein in Zeile " + (row + 1),
                        "Validierungsfehler",
                        JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                
                if (min != null || max != null) {
                    criteria.addFilter(columnId, new FilterRange(min, max));
                    hasAnyFilter = true;
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                    "Ungültiges Zahlenformat in Zeile " + (row + 1),
                    "Validierungsfehler",
                    JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        
        if (!hasAnyFilter) {
            JOptionPane.showMessageDialog(this,
                "Bitte mindestens ein Filterkriterium setzen",
                "Validierungsfehler",
                JOptionPane.WARNING_MESSAGE);
            return false;
        }
        
        this.currentFilters = criteria;
        criteria.saveFilters();
        return true;
    }
    
    public FilterCriteria showDialog() {
        setVisible(true);
        return currentFilters;
    }
    private void cleanAllValues() {
        // Über alle Zeilen gehen und alle Werte leeren, außer für "No."
        for (int row = 0; row < filterTable.getRowCount(); row++) {
            String columnName = (String) filterTable.getValueAt(row, 0);
            
            if (columnName.equals("No.")) {
                // No. > 1.0 beibehalten
                filterTable.setValueAt("1.0", row, 1); // Min-Wert setzen
                filterTable.setValueAt("", row, 2);    // Max-Wert leeren
            } else {
                // Alle anderen Werte leeren
                filterTable.setValueAt("", row, 1); // Min-Wert leeren
                filterTable.setValueAt("", row, 2); // Max-Wert leeren
            }
        }
        
        // Währungspaar-Feld zurücksetzen
        currencyPairsField.setText("");
    }
}