package ui.components;

import java.awt.Component;
import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import charts.CurrencyPairTradesChart;
import charts.DurationProfitChart;
import charts.EfficiencyChart;
import charts.EquityDrawdownChart;
import charts.MonthlyTradeCountChart;
import charts.ProviderStatHistoryChart;
import charts.SymbolDistributionChart;
import charts.ThreeMonthProfitChart;
import charts.TradeStackingChart;
import charts.WeeklyLotsizeChart;
import charts.TradeDurationHistogramChart;
import charts.ProfitLossHistogramChart;
import charts.HourlyActivityHistogramChart;
import data.ProviderStats;
import utils.ChartFactoryUtil;
import utils.HtmlDatabase;
import utils.UIStyle;
import services.ProviderHistoryService;

/**
 * Factory-Klasse zur Erstellung aller Chart-Panels für die Performanceanalyse
 */
public class ChartsPanelFactory {
    
    /**
     * Erstellt ein Panel mit allen Chart-Komponenten für die Analyse
     * 
     * @param stats Die ProviderStats-Daten
     * @param providerName Der Name des Signal Providers
     * @param htmlDatabase Die HTML-Datenbank mit zusätzlichen Daten
     * @return Ein JPanel mit allen Charts
     */
    public static JPanel createChartPanels(ProviderStats stats, String providerName, HtmlDatabase htmlDatabase) {
        JPanel chartsPanel = new JPanel();
        chartsPanel.setLayout(new BoxLayout(chartsPanel, BoxLayout.Y_AXIS));
        chartsPanel.setOpaque(false);
        
        // ChartFactory für die Standardcharts
        ChartFactoryUtil chartFactory = new ChartFactoryUtil();
        
        // Standard-Charts hinzufügen
        addStandardCharts(chartsPanel, stats, providerName, htmlDatabase, chartFactory);
        
        // Spezial-Charts hinzufügen
        addSpecialCharts(chartsPanel, stats);
        
        return chartsPanel;
    }
    
    /**
     * Fügt die Standardcharts zum Panel hinzu
     */
    private static void addStandardCharts(JPanel panel, ProviderStats stats, 
                                         String providerName, HtmlDatabase htmlDatabase,
                                         ChartFactoryUtil chartFactory) {
        // Hole den MaxDrawdownGraphic-Wert für das neue Drawdown-Chart
        double maxDrawdownGraphic = htmlDatabase.getEquityDrawdownGraphic(providerName + ".csv");
        
        // Standard-Charts mit gleicher Größe
        Component[] standardCharts = new Component[] {
            chartFactory.createEquityCurveChart(stats),
            chartFactory.createMonthlyProfitChart(stats),
            // Aktualisierte Verwendung des Konstruktors mit htmlDatabase-Parameter
            new EquityDrawdownChart(stats, maxDrawdownGraphic, htmlDatabase),
            new ThreeMonthProfitChart(
                htmlDatabase.getMonthlyProfitPercentages(providerName + ".csv"), 
                htmlDatabase.getEquityDrawdown(providerName)
            ),
            createMpddHistoryChart(providerName),
            createSubscribersHistoryChart(providerName),
            new TradeStackingChart(stats.getTrades()),
            new DurationProfitChart(stats.getTrades()),
            new TradeDurationHistogramChart(stats.getTrades()),
            new ProfitLossHistogramChart(stats.getTrades()),
            new HourlyActivityHistogramChart(stats.getTrades()),
            new EfficiencyChart(stats.getTrades()),
            new WeeklyLotsizeChart(stats.getTrades()),
            new MonthlyTradeCountChart(stats.getTrades()),
            chartFactory.createWeekdayProfitChart(stats),
            chartFactory.createMartingaleVisualizationChart(stats),
            new SymbolDistributionChart(stats.getTrades())
        };
        
        String[] chartTitles = new String[] {
            "Equity Curve",
            "Monthly Performance Overview", 
            "Drawdown Performance",  // Neuer Titel für das Drawdown-Chart
            "3-Month Profit & Drawdown Analysis",
            "3MPDD History",
            "Subscribers History",
            "Trade Stacking Analysis",
            "Duration vs Profit Analysis",
            "Trade Duration Distribution",
            "Profit/Loss Distribution",
            "Hourly Activity Distribution",
            "Trading Efficiency Analysis",
            "Weekly Lot Size Analysis",
            "Monthly Trade Count",
            "Profit by Weekday",
            "Martingale Strategy Detection",
            "Symbol Distribution"
        };
        
        // Durchlaufe alle Standard-Charts und füge sie zum Panel hinzu
        for (int i = 0; i < standardCharts.length; i++) {
            boolean isDurationChart = i == 7; // DurationProfitChart ist jetzt an Position 7
            Dimension chartSize = isDurationChart ? 
                    UIStyle.DURATION_CHART_SIZE : UIStyle.DEFAULT_CHART_SIZE;
            
            ChartPanel decoratedChart = new ChartPanel(
                    standardCharts[i], chartTitles[i], chartSize);
            panel.add(decoratedChart);
            panel.add(Box.createRigidArea(new Dimension(0, UIStyle.PANEL_SPACING)));
        }
    }
    
    /**
     * Fügt spezielle Charts hinzu, die andere Größen oder Eigenschaften haben
     */
    private static void addSpecialCharts(JPanel panel, ProviderStats stats) {
        // CurrencyPairTradesChart - Dieses Chart braucht eine spezielle Größe
        CurrencyPairTradesChart currencyPairTradesChart = new CurrencyPairTradesChart(stats.getTrades());
        ChartPanel currencyPairPanel = new ChartPanel(
                currencyPairTradesChart, "Currency Pair Analysis", UIStyle.CURRENCY_PAIR_CHART_SIZE);
        panel.add(currencyPairPanel);
    }
    
    /**
     * Erstellt ein MPDD History Chart
     */
    private static ProviderStatHistoryChart createMpddHistoryChart(String providerName) {
        ProviderStatHistoryChart chart = new ProviderStatHistoryChart();
        chart.loadProviderHistory(providerName);
        return chart;
    }

    /**
     * Erstellt ein Subscribers History Chart
     */
    private static ProviderStatHistoryChart createSubscribersHistoryChart(String providerName) {
        ProviderStatHistoryChart chart = new ProviderStatHistoryChart("Abonnenten-Verlauf", "Anzahl Abonnenten");
        chart.loadProviderHistory(providerName, ProviderHistoryService.STAT_TYPE_SUBSCRIBERS);
        return chart;
    }
}