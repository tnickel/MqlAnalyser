package charts;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;

import data.Trade;

public class SymbolDistributionChart extends JPanel {
    private final JFreeChart chart;
    private final DefaultPieDataset dataset;
    
    private static final Color[] COLORS = {
        new Color(0, 136, 254),   // Blau
        new Color(0, 196, 159),   // Grün
        new Color(255, 187, 40),  // Orange
        new Color(255, 128, 66),  // Hellrot
        new Color(136, 132, 216), // Violett
        new Color(128, 128, 128)  // Grau für "Andere"
    };
    
    public SymbolDistributionChart(List<Trade> trades) {
        setLayout(new BorderLayout());
        dataset = new DefaultPieDataset();
        
        // Trades pro Symbol zählen
        Map<String, Long> symbolCounts = trades.stream()
            .collect(Collectors.groupingBy(Trade::getSymbol, Collectors.counting()));
        
        // Gesamtanzahl der Trades berechnen
        long totalTrades = symbolCounts.values().stream().mapToLong(Long::longValue).sum();
        
        // Daten zum Dataset hinzufügen
        symbolCounts.forEach((symbol, count) -> {
            double percentage = (count * 100.0) / totalTrades;
            String label = String.format("%s (%d)", symbol, count);
            dataset.setValue(label, count);
        });
        
        chart = ChartFactory.createPieChart(
            "Trade Distribution by Symbol",  // Titel
            dataset,                        // Datensatz
            true,                          // Legende anzeigen
            true,                          // Tooltips anzeigen
            false                          // URLs nicht anzeigen
        );
        
        customizeChart();
        
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(950, 300));
        add(chartPanel, BorderLayout.CENTER);
    }
    
    private void customizeChart() {
        chart.setBackgroundPaint(Color.WHITE);
        
        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setInteriorGap(0.04);
        
        // Font für Labels
        Font labelFont = new Font("SansSerif", Font.PLAIN, 11);
        plot.setLabelFont(labelFont);
        
        // Label-Format anpassen
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator(
            "{0}",
            new DecimalFormat("#,##0"),
            new DecimalFormat("0.0%")
        ));
        
        // Farben für die Sektionen
        int colorIndex = 0;
        for (Object key : dataset.getKeys()) {
            plot.setSectionPaint((Comparable<?>) key, COLORS[colorIndex % COLORS.length]);
            colorIndex++;
        }
        
        // Legende anpassen
        chart.getLegend().setItemFont(new Font("SansSerif", Font.PLAIN, 11));
        chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 14));
    }
    
    public JFreeChart getChart() {
        return chart;
    }
    
    public void setTitle(String title) {
        chart.setTitle(title);
    }
}