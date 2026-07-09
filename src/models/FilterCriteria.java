package models;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import data.ProviderStats;
import data.Trade;
import utils.ApplicationConstants;

public class FilterCriteria implements Serializable
{
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(FilterCriteria.class.getName());
    
    private Map<Integer, FilterRange> columnFilters;
    private String currencyPairsFilter; // Filter für Währungspaare

    private static String getSaveFilePath()
    {
        if (Boolean.getBoolean("mql.test"))
        {
            return "filter_criteria.ser";
        }
        java.io.File configDir = new java.io.File(ApplicationConstants.ROOT_PATH, "config");
        if (!configDir.exists())
        {
            configDir.mkdirs();
        }
        return new java.io.File(configDir, "filter_criteria.ser").getAbsolutePath();
    }
    
    public FilterCriteria()
    {
        this.columnFilters = new HashMap<>();
        this.currencyPairsFilter = ""; // Initialisierung
    }
    
    public void addFilter(int column, FilterRange range)
    {
        // Ignoriere den Filter für MaxDrawdown (column 17)
        if (column != 17) {
            columnFilters.put(column, range);
        }
    }
    
    public void setCurrencyPairsFilter(String filter) {
        this.currencyPairsFilter = filter != null ? filter.trim() : "";
    }
    
    public String getCurrencyPairsFilter() {
        return currencyPairsFilter;
    }
    
    public boolean matches(ProviderStats stats, Object[] rowData)
    {
        // Überprüfe zuerst die Spaltenfilter
        for (Map.Entry<Integer, FilterRange> entry : columnFilters.entrySet())
        {
            int column = entry.getKey();
            FilterRange range = entry.getValue();
            
            // Ignoriere den MaxDrawdown-Filter (column 17)
            if (column == 17) {
                continue;
            }
            
            // Prüfe dass wir keinen Index-Bereich überschreiten
            if (column >= rowData.length) {
                LOGGER.warning("Filter für Spalte " + column + " übersteigt verfügbare Spalten (" + rowData.length + ")");
                continue;
            }
            
            // Spezielle Behandlung für Risiko-Spalte (Index 24)
            if (column == 24) {
                // Risiko-Wert aus ProviderStats holen, da in der Tabelle als String dargestellt
                int riskValue = stats.getRiskCategory();
                if (!range.matches(riskValue)) {
                    return false;
                }
                continue;
            }
            
            if (!range.matches(rowData[column]))
            {
                return false;
            }
        }
        
        // Wenn Währungspaar-Filter vorhanden ist, überprüfe ob alle angegebenen Währungspaare verwendet werden
        if (currencyPairsFilter != null && !currencyPairsFilter.isEmpty()) {
            return matchesCurrencyPairs(stats);
        }
        
        return true;
    }
    
    private boolean matchesCurrencyPairs(ProviderStats stats) {
        if (currencyPairsFilter == null || currencyPairsFilter.isEmpty()) {
            return true;
        }
        
        // Trenne die Filter-Währungspaare durch Komma
        String[] requestedPairs = currencyPairsFilter.split(",");
        
        // Erstelle eine Liste aller im Provider vorhandenen Währungspaare
        Set<String> providerCurrencyPairs = stats.getTrades().stream()
                .map(Trade::getSymbol)
                .collect(Collectors.toSet());
        
        // Überprüfe, ob alle angeforderten Währungspaare (oder Präfixe) vorhanden sind
        for (String requestedPair : requestedPairs) {
            String trimmedPair = requestedPair.trim().toUpperCase(); // Zu Großbuchstaben konvertieren
            if (trimmedPair.isEmpty()) continue;
            
            boolean pairFound = false;
            for (String providerPair : providerCurrencyPairs) {
                // Beide Strings in Großbuchstaben konvertieren beim Vergleich
                if (providerPair.toUpperCase().startsWith(trimmedPair)) {
                    pairFound = true;
                    break;
                }
            }
            
            if (!pairFound) {
                return false;
            }
        }
        
        return true;
    }
    
    public Map<Integer, FilterRange> getFilters()
    {
        // Entferne den MaxDrawdown-Filter, falls er versehentlich enthalten ist
        columnFilters.remove(17);
        return columnFilters;
    }
    
    public void setFilters(Map<Integer, FilterRange> filters)
    {
        this.columnFilters = new HashMap<>(filters);
        // Entferne den MaxDrawdown-Filter, falls er enthalten ist
        this.columnFilters.remove(17);
    }
    
    public void saveFilters()
    {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(getSaveFilePath())))
        {
            // Entferne den MaxDrawdown-Filter vor dem Speichern
            Map<Integer, FilterRange> filtersToSave = new HashMap<>(columnFilters);
            filtersToSave.remove(17);
            
            // Speichere Spaltenfilter
            oos.writeObject(filtersToSave);
            
            // Speichere Währungspaar-Filter
            oos.writeObject(currencyPairsFilter);
            
            LOGGER.info("Filter wurden gespeichert.");
        } catch (IOException e)
        {
            LOGGER.severe("Fehler beim Speichern der Filter: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @SuppressWarnings("unchecked")
    public void loadFilters()
    {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(getSaveFilePath())))
        {
            // Lade Spaltenfilter
            columnFilters = (Map<Integer, FilterRange>) ois.readObject();
            
            // Entferne den MaxDrawdown-Filter, falls er enthalten ist
            columnFilters.remove(17);
            
            // Lade Währungspaar-Filter
            try {
                currencyPairsFilter = (String) ois.readObject();
            } catch (Exception e) {
                currencyPairsFilter = ""; // Falls das Format älter ist und keinen Währungspaar-Filter enthält
            }
            
            LOGGER.info("Filter wurden geladen.");
        } catch (IOException | ClassNotFoundException e)
        {
            columnFilters = new HashMap<>(); // Falls Datei nicht existiert
            currencyPairsFilter = "";
            LOGGER.info("Keine gespeicherten Filter gefunden oder Fehler beim Laden.");
        }
    }
    
    public static class FilterRange implements Serializable
    {
        private static final long serialVersionUID = 1L;
        private final Double min;
        private final Double max;
        private final String textFilter;
        
        public FilterRange(Double min, Double max)
        {
            this.min = min;
            this.max = max;
            this.textFilter = null;
        }
        
        public FilterRange(String textFilter)
        {
            this.min = null;
            this.max = null;
            this.textFilter = textFilter;
        }
        
        public boolean matches(Object value)
        {
            if (value == null)
                return false;
            
            if (textFilter != null)
            {
                return value.toString().toLowerCase().contains(textFilter.toLowerCase());
            }
            
            try
            {
                double numValue = value instanceof Number ? ((Number) value).doubleValue()
                        : Double.parseDouble(value.toString());
            
                if (min != null && numValue < min)
                    return false;
                if (max != null && numValue > max)
                    return false;
                return true;
            } catch (NumberFormatException e)
            {
                Logger.getLogger(FilterRange.class.getName()).warning("Number format exception for value: " + value);
                return false;
            }
        }
        
        public Double getMin()
        {
            return min;
        }
        
        public Double getMax()
        {
            return max;
        }
        
        public String getTextFilter()
        {
            return textFilter;
        }
    }
}