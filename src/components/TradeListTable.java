package components;

import java.awt.Color;
import java.awt.Component;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;

import data.ProviderStats;
import data.Trade;
import data.TradeComparator;
import utils.TradeUtils;

public class TradeListTable extends JTable {
    private final DecimalFormat df = new DecimalFormat("#,##0.00");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DefaultTableModel model;
    private List<Trade> sortedTrades;

    public TradeListTable(ProviderStats stats) {
        this.model = createTableModel(stats);
        initializeTable();
        setupRenderers();
    }

    public Trade getTradeAt(int row) {
        return sortedTrades.get(row);
    }

    private DefaultTableModel createTableModel(ProviderStats stats) {
        String[] columnNames = { 
            "No.", "Open Time", "Close Time", "Duration", "Type", "Symbol", "Lots", 
            "Open Price", "Close Price", "Profit/Loss", "Commission", "Swap", 
            "Total", "Running Profit", "Open Trades", "Open Lots", "Open Equity" 
        };

        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 0:  // No
                    case 14: // Open Trades
                        return Integer.class;
                    case 6:  // Lots
                    case 7:  // Open Price
                    case 8:  // Close Price
                    case 9:  // Profit/Loss
                    case 10: // Commission
                    case 11: // Swap
                    case 12: // Total
                    case 13: // Running Profit
                    case 15: // Open Lots
                    case 16: // Open Equity
                        return Double.class;
                    default:
                        return String.class;
                }
            }
        };

        populateData(model, stats);
        return model;
    }

    private void populateData(DefaultTableModel model, ProviderStats stats) {
        this.sortedTrades = new ArrayList<>(stats.getTrades());
        sortedTrades.sort(new TradeComparator());

        double runningProfit = 0.0;

        for (int i = 0; i < sortedTrades.size(); i++) {
            Trade trade = sortedTrades.get(i);
            double totalProfit = trade.getTotalProfit();
            runningProfit += totalProfit;

            // Berechnung der Dauer als String mit Einheiten
            String formattedDuration = formatDuration(trade.getOpenTime(), trade.getCloseTime());

            List<Trade> activeTrades = TradeUtils.getActiveTradesAt(sortedTrades, trade.getOpenTime());
            int openTradesCount = activeTrades.size();
            double openLotsCount = activeTrades.stream().mapToDouble(Trade::getLots).sum();
            double openEquity = estimateOpenEquity(activeTrades, trade.getOpenTime());

            model.addRow(new Object[] {
                i + 1,
                trade.getOpenTime().format(dateFormatter),
                trade.getCloseTime().format(dateFormatter),
                formattedDuration,
                trade.getType(),
                trade.getSymbol(),
                trade.getLots(),
                trade.getOpenPrice(),
                trade.getClosePrice(),
                trade.getProfit(),
                trade.getCommission(),
                trade.getSwap(),
                totalProfit,
                runningProfit,
                openTradesCount,
                openLotsCount,
                openEquity
            });
        }
    }

    /**
     * Formatiert die Dauer zwischen Öffnung und Schließung eines Trades
     * mit passenden Einheiten (s, m, h, T)
     */
    private String formatDuration(LocalDateTime open, LocalDateTime close) {
        long seconds = Duration.between(open, close).getSeconds();
        
        // Weniger als 2 Minuten: in Sekunden anzeigen
        if (seconds < 120) {
            return seconds + "s";
        }
        
        // Weniger als 2 Stunden: in Minuten anzeigen
        long minutes = seconds / 60;
        if (minutes < 120) {
            double mins = seconds / 60.0;
            return String.format("%.2fm", mins);
        }
        
        // Weniger als 5 Tage: in Stunden anzeigen
        long hours = seconds / 3600;
        if (hours < 120) {
            double hrs = seconds / 3600.0;
            return String.format("%.2fh", hrs);
        }
        
        // Mehr als 5 Tage: in Tagen anzeigen
        double days = seconds / (3600.0 * 24);
        return String.format("%.2fT", days);
    }

    private void initializeTable() {
        setModel(model);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);

        Comparator<String> dateComparator = new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                try {
                    LocalDateTime dt1 = LocalDateTime.parse(s1, dateFormatter);
                    LocalDateTime dt2 = LocalDateTime.parse(s2, dateFormatter);
                    return dt1.compareTo(dt2);
                } catch (Exception e) {
                    return s1.compareTo(s2);
                }
            }
        };

        sorter.setComparator(1, dateComparator); // Open Time
        sorter.setComparator(2, dateComparator); // Close Time
        
        // Spezieller Comparator für die Duration-Spalte, der das Format berücksichtigt
        sorter.setComparator(3, new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                try {
                    // Extrahiere Basiswert in Sekunden für den Vergleich
                    double val1 = getDurationInSeconds(s1);
                    double val2 = getDurationInSeconds(s2);
                    return Double.compare(val1, val2);
                } catch (Exception e) {
                    return s1.compareTo(s2);
                }
            }
        });

        setRowSorter(sorter);
        setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        int[] preferredWidths = { 
            50,  // No
            140, // Open Time
            140, // Close Time
            70,  // Duration
            60,  // Type
            80,  // Symbol
            70,  // Lots
            90,  // Open Price
            90,  // Close Price
            90,  // Profit/Loss
            90,  // Commission
            90,  // Swap
            90,  // Total
            100, // Running Profit
            90,  // Open Trades
            90,  // Open Lots
            90   // Open Equity
        };

        for (int i = 0; i < preferredWidths.length; i++) {
            getColumnModel().getColumn(i).setPreferredWidth(preferredWidths[i]);
        }
    }

    /**
     * Hilfsmethode zum Konvertieren der formatierten Dauer in Sekunden für den Vergleich
     */
    private double getDurationInSeconds(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return 0.0;
        }
        
        try {
            String unitChar = durationStr.substring(durationStr.length() - 1);
            String numberStr = durationStr.substring(0, durationStr.length() - 1).replace(',', '.');
            double value = Double.parseDouble(numberStr);
            
            switch (unitChar) {
                case "s": return value;
                case "m": return value * 60;
                case "h": return value * 3600;
                case "T": return value * 86400; // 24 * 3600
                default: return 0.0;
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    private void setupRenderers() {
        // Renderer für Geldbeträge
        TableCellRenderer moneyRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                         boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Double) {
                    double amount = (Double) value;
                    if (!isSelected) {
                        c.setForeground(amount >= 0 ? new Color(0, 150, 0) : Color.RED);
                    }
                    setText(df.format(amount));
                }
                setHorizontalAlignment(SwingConstants.RIGHT);
                return c;
            }
        };

        // Money Columns
        int[] moneyColumns = {8, 9, 10, 11, 12, 13, 14, 15, 16}; 
        for (int column : moneyColumns) {
            getColumnModel().getColumn(column).setCellRenderer(moneyRenderer);
        }
        
        // Einheitsbasierter Renderer für die Duration-Spalte
        DefaultTableCellRenderer durationRenderer = new DefaultTableCellRenderer() {
            // Definiere Farben für die verschiedenen Einheiten
            private final Color SECONDS_COLOR = new Color(0, 0, 220);    // Blau
            private final Color MINUTES_COLOR = new Color(0, 150, 0);    // Grün
            private final Color HOURS_COLOR = new Color(255, 140, 0);    // Orange
            private final Color DAYS_COLOR = new Color(220, 0, 0);       // Rot
            
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                          boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                
                if (!isSelected && value != null) {
                    String durationStr = value.toString();
                    
                    // Färbe basierend auf der Einheit, nicht auf dem Wert
                    if (durationStr.endsWith("s")) {
                        // Sekunden: BLAU
                        setForeground(SECONDS_COLOR);
                    } 
                    else if (durationStr.endsWith("m")) {
                        // Minuten: GRÜN
                        setForeground(MINUTES_COLOR);
                    } 
                    else if (durationStr.endsWith("h")) {
                        // Stunden: ORANGE
                        setForeground(HOURS_COLOR);
                    } 
                    else if (durationStr.endsWith("T")) {
                        // Tage: ROT
                        setForeground(DAYS_COLOR);
                    }
                }
                
                return this;
            }
        };
        
        getColumnModel().getColumn(3).setCellRenderer(durationRenderer);
    }
    private double estimateOpenEquity(List<Trade> activeTrades, LocalDateTime currentTime) {
        double totalEquity = 0.0;
        
        for (Trade trade : activeTrades) {
            double progress = calculateTradeProgress(trade, currentTime);
            double estimatedProfit = interpolateProfit(trade, progress);
            
            if (trade.getProfit() < 0) {
                estimatedProfit *= 1.5;
                double acceleratedProgress = Math.pow(progress, 0.7);
                estimatedProfit = trade.getProfit() * acceleratedProgress;
            } else {
                double delayedProgress = Math.pow(progress, 1.3);
                estimatedProfit = trade.getProfit() * delayedProgress;
            }
            
            double lotRiskFactor = 1.0 + (trade.getLots() * 0.2);
            if (estimatedProfit < 0) {
                estimatedProfit *= lotRiskFactor;
            }
            
            totalEquity += estimatedProfit;
        }
        
        double totalRiskFactor = 1.0 + (activeTrades.size() * 0.1);
        if (totalEquity < 0) {
            totalEquity *= totalRiskFactor;
        }
        
        return totalEquity;
    }

    private double calculateTradeProgress(Trade trade, LocalDateTime currentTime) {
        long totalDuration = Duration.between(trade.getOpenTime(), trade.getCloseTime()).toMillis();
        long currentDuration = Duration.between(trade.getOpenTime(), currentTime).toMillis();
        return Math.min(1.0, Math.max(0.0, (double) currentDuration / totalDuration));
    }

    private double interpolateProfit(Trade trade, double progress) {
        return trade.getProfit() * progress;
    }
}