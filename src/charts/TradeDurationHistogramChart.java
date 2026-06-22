package charts;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;

import data.Trade;

public class TradeDurationHistogramChart extends JPanel {
    
    public TradeDurationHistogramChart(List<Trade> trades) {
        DefaultCategoryDataset dataset = createDataset(trades);
        JFreeChart chart = createChart(dataset);
        
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(950, 300));
        
        setLayout(new java.awt.BorderLayout());
        add(chartPanel);
    }
    
    private DefaultCategoryDataset createDataset(List<Trade> trades) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        // Verwende LinkedHashMap, um die Reihenfolge der Bins zu bewahren
        Map<String, Integer> bins = new LinkedHashMap<>();
        bins.put("< 1 Min", 0);
        bins.put("1 - 10 Min", 0);
        bins.put("10 - 60 Min", 0);
        bins.put("1 - 4 Std", 0);
        bins.put("4 - 24 Std", 0);
        bins.put("1 - 3 Tage", 0);
        bins.put("3 - 7 Tage", 0);
        bins.put("> 7 Tage", 0);
        
        for (Trade trade : trades) {
            long seconds = Duration.between(trade.getOpenTime(), trade.getCloseTime()).getSeconds();
            String bin;
            
            if (seconds < 60) {
                bin = "< 1 Min";
            } else if (seconds < 600) {
                bin = "1 - 10 Min";
            } else if (seconds < 3600) {
                bin = "10 - 60 Min";
            } else if (seconds < 14400) {
                bin = "1 - 4 Std";
            } else if (seconds < 86400) {
                bin = "4 - 24 Std";
            } else if (seconds < 259200) {
                bin = "1 - 3 Tage";
            } else if (seconds < 604800) {
                bin = "3 - 7 Tage";
            } else {
                bin = "> 7 Tage";
            }
            
            bins.put(bin, bins.get(bin) + 1);
        }
        
        // Daten zum Dataset hinzufügen
        for (Map.Entry<String, Integer> entry : bins.entrySet()) {
            dataset.addValue(entry.getValue(), "Trades", entry.getKey());
        }
        
        return dataset;
    }
    
    private JFreeChart createChart(DefaultCategoryDataset dataset) {
        JFreeChart chart = ChartFactory.createBarChart(
            "Verteilung der Haltedauer (Trade Duration)", // Titel
            "Haltedauer",                                 // x-Achse
            "Anzahl der Trades",                          // y-Achse
            dataset,                                      // Daten
            PlotOrientation.VERTICAL,                     // Orientierung
            false,                                        // Keine Legende nötig
            true,                                         // Tooltips
            false                                         // URLs
        );
        
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 10));
        
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        rangeAxis.setLowerBound(0);
        
        // Finde Max-Wert zur Skalierung der Labels
        double maxValue = 0;
        for (int i = 0; i < dataset.getColumnCount(); i++) {
            Number val = dataset.getValue(0, i);
            if (val != null && val.doubleValue() > maxValue) {
                maxValue = val.doubleValue();
            }
        }
        rangeAxis.setUpperBound(maxValue > 0 ? maxValue * 1.15 : 10);
        
        BarRenderer renderer = new BarRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setSeriesPaint(0, new Color(51, 153, 255)); // Helles Blau für Haltedauer
        renderer.setMaximumBarWidth(0.06);
        
        // Labels über den Balken
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultPositiveItemLabelPosition(
            new ItemLabelPosition(
                ItemLabelAnchor.OUTSIDE12, 
                TextAnchor.BOTTOM_CENTER
            )
        );
        renderer.setDefaultItemLabelFont(new Font("SansSerif", Font.BOLD, 10));
        
        plot.setRenderer(renderer);
        
        return chart;
    }
}
