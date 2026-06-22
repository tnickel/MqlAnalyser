package charts;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Paint;
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

public class ProfitLossHistogramChart extends JPanel {
    
    public ProfitLossHistogramChart(List<Trade> trades) {
        DefaultCategoryDataset dataset = createDataset(trades);
        JFreeChart chart = createChart(dataset);
        
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(950, 300));
        
        setLayout(new java.awt.BorderLayout());
        add(chartPanel);
    }
    
    private DefaultCategoryDataset createDataset(List<Trade> trades) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        Map<String, Integer> bins = new LinkedHashMap<>();
        bins.put("Verlust < -100", 0);
        bins.put("Verlust -100 bis -20", 0);
        bins.put("Verlust -20 bis 0", 0);
        bins.put("Gewinn 0 bis 20", 0);
        bins.put("Gewinn 20 bis 100", 0);
        bins.put("Gewinn > 100", 0);
        
        for (Trade trade : trades) {
            double profit = trade.getProfit();
            String bin;
            
            if (profit < -100) {
                bin = "Verlust < -100";
            } else if (profit < -20) {
                bin = "Verlust -100 bis -20";
            } else if (profit < 0) {
                bin = "Verlust -20 bis 0";
            } else if (profit <= 20) {
                bin = "Gewinn 0 bis 20";
            } else if (profit <= 100) {
                bin = "Gewinn 20 bis 100";
            } else {
                bin = "Gewinn > 100";
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
            "Verteilung der Trade-Ergebnisse (Profit / Verlust)", // Titel
            "Ergebnis-Klasse",                                    // x-Achse
            "Anzahl der Trades",                                  // y-Achse
            dataset,                                              // Daten
            PlotOrientation.VERTICAL,                             // Orientierung
            false,                                                // Keine Legende nötig
            true,                                                 // Tooltips
            false                                                 // URLs
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
        
        // Custom Renderer für rot/grün Farben
        BarRenderer renderer = new BarRenderer() {
            @Override
            public Paint getItemPaint(int row, int column) {
                String binKey = dataset.getColumnKey(column).toString();
                if (binKey.startsWith("Gewinn")) {
                    return new Color(34, 139, 34); // Forest Green für Gewinn-Klassen
                } else {
                    return new Color(178, 34, 34);  // Firebrick Red für Verlust-Klassen
                }
            }
        };
        
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setMaximumBarWidth(0.08);
        
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
