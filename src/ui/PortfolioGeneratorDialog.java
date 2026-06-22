package ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import components.MainTable;
import data.ProviderStats;
import data.Trade;
import ui.components.AppUIStyle;
import utils.HtmlDatabase;
import utils.PortfolioOptimizer;
import utils.PortfolioOptimizer.Candidate;
import utils.ScoreConfig;

public class PortfolioGeneratorDialog extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(PortfolioGeneratorDialog.class.getName());
    
    private final JFrame parentFrame;
    private final Map<String, ProviderStats> providerStats;
    private final HtmlDatabase htmlDatabase;
    private final MainTable mainTable;
    private final String rootPath;
    
    // Sliders for scoring
    private JSlider sliderProfit;
    private JSlider slider3Mpdd;
    private JSlider sliderDrawdown;
    private JSlider sliderTrades;
    private JSlider sliderTradeDays;
    private JSlider sliderSubscribers;
    
    // Portfolio options
    private JSpinner spinnerSize;
    private JSlider sliderMaxCorr;
    private JCheckBox checkSaveGlobal;
    private javax.swing.JComboBox<String> comboTimeframe;
    private javax.swing.JComboBox<String> comboAlgorithm;
    
    // Results view components
    private JTable portfolioTable;
    private DefaultTableModel portfolioModel;
    private JTable correlationTable;
    private DefaultTableModel correlationModel;
    private JPanel chartContainer;
    
    // Stats labels
    private JLabel lblTotalProfit;
    private JLabel lblAvgCorr;
    private JLabel lblCombinedDrawdown;
    private JLabel lblTotalProfitRisk;
    private JLabel lblCombinedDrawdownRisk;
    private JLabel lblRiskFactors;
    
    public PortfolioGeneratorDialog(JFrame parent, Map<String, ProviderStats> stats, HtmlDatabase htmlDb, MainTable mainTable, String rootPath) {
        super("Portfolio Generator & Optimizer");
        this.parentFrame = parent;
        this.providerStats = stats;
        this.htmlDatabase = htmlDb;
        this.mainTable = mainTable;
        this.rootPath = rootPath;
        
        initializeUI();
        
        setSize(1500, 850);
        setLocationRelativeTo(parent);
        
        // ESC to close
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
        Action escapeAction = new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        };
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", escapeAction);
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout());
        
        // Split pane to separate configuration (left) and results (right)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(380);
        splitPane.setContinuousLayout(true);
        
        // 1. CONFIGURATION PANEL (LEFT)
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Portfolio Konfiguration", 
            0, 0, AppUIStyle.SUBTITLE_FONT, AppUIStyle.PRIMARY_COLOR));
            
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.weightx = 1.0;
        
        int gridy = 0;
        
        // Add Slider Row helper
        ScoreConfig config = ScoreConfig.getInstance();
        sliderProfit = createWeightSlider(config.getWeightProfit());
        slider3Mpdd = createWeightSlider(config.getWeight3Mpdd());
        sliderDrawdown = createWeightSlider(config.getWeightDrawdown());
        sliderTrades = createWeightSlider(config.getWeightTrades());
        sliderTradeDays = createWeightSlider(config.getWeightTradeDays());
        sliderSubscribers = createWeightSlider(config.getWeightSubscribers());
        
        JLabel weightsTitle = new JLabel("<html><b>Bewertungs-Gewichtung (0-10):</b></html>");
        weightsTitle.setFont(AppUIStyle.BOLD_FONT);
        gbc.gridx = 0; gbc.gridy = gridy++; gbc.gridwidth = 3;
        configPanel.add(weightsTitle, gbc);
        gbc.gridwidth = 1;
        
        addSliderRow(configPanel, gbc, gridy++, "Profit/Monat %:", sliderProfit);
        addSliderRow(configPanel, gbc, gridy++, "3MPDD (Profit/DD):", slider3Mpdd);
        addSliderRow(configPanel, gbc, gridy++, "Drawdown (Risiko):", sliderDrawdown);
        addSliderRow(configPanel, gbc, gridy++, "Trades (Aktivität):", sliderTrades);
        addSliderRow(configPanel, gbc, gridy++, "Trading Days:", sliderTradeDays);
        addSliderRow(configPanel, gbc, gridy++, "Abonnenten:", sliderSubscribers);
        
        // Separator/Space
        gbc.gridx = 0; gbc.gridy = gridy++; gbc.gridwidth = 3;
        configPanel.add(new javax.swing.JSeparator(), gbc);
        gbc.gridwidth = 1;
        
        // Portfolio parameters title
        JLabel paramsTitle = new JLabel("<html><b>Portfolio Parameter:</b></html>");
        paramsTitle.setFont(AppUIStyle.BOLD_FONT);
        gbc.gridx = 0; gbc.gridy = gridy++; gbc.gridwidth = 3;
        configPanel.add(paramsTitle, gbc);
        gbc.gridwidth = 1;
        
        // Portfolio Size Input
        gbc.gridx = 0; gbc.gridy = gridy; gbc.weightx = 0.4;
        configPanel.add(AppUIStyle.createStyledLabel("Portfolio-Größe:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.6; gbc.gridwidth = 2;
        spinnerSize = new JSpinner(new SpinnerNumberModel(5, 2, 10, 1));
        configPanel.add(spinnerSize, gbc);
        gbc.gridwidth = 1;
        gridy++;

        // Timeframe Input (Zeitraum)
        gbc.gridx = 0; gbc.gridy = gridy; gbc.weightx = 0.4;
        configPanel.add(AppUIStyle.createStyledLabel("Zeitraum:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.6; gbc.gridwidth = 2;
        comboTimeframe = new javax.swing.JComboBox<>(new String[]{
            "Gesamte Historie", "Letzte 12 Monate", "Letzte 6 Monate", "Letzte 3 Monate"
        });
        comboTimeframe.setSelectedItem("Letzte 6 Monate");
        AppUIStyle.applyStylesToComboBox(comboTimeframe);
        configPanel.add(comboTimeframe, gbc);
        gbc.gridwidth = 1;
        gridy++;

        // Algorithm Input (Optimierungsverfahren)
        gbc.gridx = 0; gbc.gridy = gridy; gbc.weightx = 0.4;
        configPanel.add(AppUIStyle.createStyledLabel("Algorithmus:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.6; gbc.gridwidth = 2;
        comboAlgorithm = new javax.swing.JComboBox<>(new String[]{
            "Kombinatorisch (Exakt)", "Greedy (Schnell)"
        });
        AppUIStyle.applyStylesToComboBox(comboAlgorithm);
        configPanel.add(comboAlgorithm, gbc);
        gbc.gridwidth = 1;
        gridy++;
        
        // Max Correlation Input
        gbc.gridx = 0; gbc.gridy = gridy; gbc.weightx = 0.4;
        configPanel.add(AppUIStyle.createStyledLabel("Max. Korrelation:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5;
        sliderMaxCorr = new JSlider(0, 100, 30); // Represents 0.00 to 1.00
        sliderMaxCorr.setMajorTickSpacing(50);
        sliderMaxCorr.setPaintTicks(true);
        configPanel.add(sliderMaxCorr, gbc);
        gbc.gridx = 2; gbc.weightx = 0.1;
        JLabel lblMaxCorrValue = AppUIStyle.createStyledLabel("0.30");
        lblMaxCorrValue.setFont(AppUIStyle.BOLD_FONT);
        configPanel.add(lblMaxCorrValue, gbc);
        sliderMaxCorr.addChangeListener(e -> lblMaxCorrValue.setText(String.format("%.2f", sliderMaxCorr.getValue() / 100.0)));
        gridy++;
        
        // Save global config checkbox
        gbc.gridx = 0; gbc.gridy = gridy++; gbc.gridwidth = 3; gbc.weightx = 1.0;
        checkSaveGlobal = new JCheckBox("Als Standard-Gewichtung speichern");
        checkSaveGlobal.setFont(AppUIStyle.REGULAR_FONT);
        configPanel.add(checkSaveGlobal, gbc);
        gbc.gridwidth = 1;
        
        // Spacer to push button down
        gbc.gridx = 0; gbc.gridy = gridy++; gbc.gridwidth = 3; gbc.weighty = 1.0;
        configPanel.add(new JPanel(), gbc);
        gbc.weighty = 0.0;
        gbc.gridwidth = 1;
        
        // Calculate Button
        gbc.gridx = 0; gbc.gridy = gridy++; gbc.gridwidth = 3;
        JButton btnCalculate = AppUIStyle.createStyledButton("Portfolio Generieren & Optimieren");
        btnCalculate.addActionListener(e -> generatePortfolio());
        configPanel.add(btnCalculate, gbc);
        
        splitPane.setLeftComponent(configPanel);
        
        // 2. RESULTS PANEL (RIGHT/CENTER)
        JPanel resultsPanel = new JPanel(new BorderLayout(10, 10));
        resultsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Split results into Top (Tables) and Bottom (Chart)
        JSplitPane resultsSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        resultsSplitPane.setDividerLocation(320);
        resultsSplitPane.setContinuousLayout(true);
        
        // Top Panel: Grid layout with Selected Portfolio Table and Correlation Matrix Table
        JPanel tablesPanel = new JPanel(new GridBagLayout());
        GridBagConstraints tGbc = new GridBagConstraints();
        tGbc.fill = GridBagConstraints.BOTH;
        tGbc.insets = new Insets(0, 5, 0, 5);
        tGbc.weighty = 1.0;
        
        // Selected Portfolio Table
        portfolioModel = new DefaultTableModel(
            new String[]{"No.", "Signal Provider", "Qualitäts-Score", "Abonnenten", "Trades", "3MPDD", "Drawdown %"}, 0);
        portfolioTable = new JTable(portfolioModel) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        portfolioTable.setRowHeight(22);
        
        // Open details on double-click
        portfolioTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = portfolioTable.getSelectedRow();
                    if (row != -1) {
                        row = portfolioTable.convertRowIndexToModel(row);
                        String displayName = (String) portfolioTable.getValueAt(row, 1);
                        String providerName = displayName + ".csv";
                        ProviderStats stats = providerStats.get(providerName);
                        if (stats != null) {
                            String providerId = "";
                            int lastUnderscore = displayName.lastIndexOf("_");
                            if (lastUnderscore != -1) {
                                providerId = displayName.substring(lastUnderscore + 1);
                            }
                            PerformanceAnalysisDialog detailFrame = new PerformanceAnalysisDialog(
                                displayName, stats, providerId, htmlDatabase, rootPath);
                            detailFrame.setVisible(true);
                        }
                    }
                }
            }
        });
        
        JScrollPane portfolioScroll = new JScrollPane(portfolioTable);
        portfolioScroll.setBorder(BorderFactory.createTitledBorder("Ausgewählte Strategien im Portfolio"));
        tGbc.gridx = 0; tGbc.gridy = 0; tGbc.weightx = 0.55;
        tablesPanel.add(portfolioScroll, tGbc);
        
        // Correlation Matrix Table
        correlationModel = new DefaultTableModel();
        correlationTable = new JTable(correlationModel) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        correlationTable.setRowHeight(22);
        correlationTable.setDefaultRenderer(Object.class, new CorrelationHeatMapRenderer());
        JScrollPane correlationScroll = new JScrollPane(correlationTable);
        correlationScroll.setBorder(BorderFactory.createTitledBorder("Korrelationsmatrix (Pearson-Koeffizienten)"));
        tGbc.gridx = 1; tGbc.gridy = 0; tGbc.weightx = 0.45;
        tablesPanel.add(correlationScroll, tGbc);
        
        resultsSplitPane.setTopComponent(tablesPanel);
        
        // Bottom Panel: Chart Container & Summary Panel
        JPanel bottomContainer = new JPanel(new BorderLayout(10, 10));
        
        // Chart Panel Container (Scrollable)
        chartContainer = new JPanel(new GridBagLayout());
        chartContainer.setBackground(Color.WHITE);
        
        JLabel placeholderChart = new JLabel("Bitte klicken Sie auf 'Portfolio Generieren', um das Ergebnis zu visualisieren.", JLabel.CENTER);
        placeholderChart.setFont(AppUIStyle.REGULAR_FONT);
        placeholderChart.setPreferredSize(new Dimension(600, 300));
        
        GridBagConstraints cGbc = new GridBagConstraints();
        cGbc.fill = GridBagConstraints.BOTH;
        cGbc.weightx = 1.0;
        cGbc.weighty = 1.0;
        cGbc.gridx = 0;
        cGbc.gridy = 0;
        chartContainer.add(placeholderChart, cGbc);
        
        JScrollPane chartScrollPane = new JScrollPane(chartContainer);
        chartScrollPane.setBorder(BorderFactory.createEtchedBorder());
        chartScrollPane.getVerticalScrollBar().setUnitIncrement(16); // Schnelles und flüssiges Scrollen
        bottomContainer.add(chartScrollPane, BorderLayout.CENTER);
        
        // Summary Panel (Right side of bottom container)
        JPanel summaryPanel = new JPanel(new GridBagLayout());
        summaryPanel.setBackground(new Color(245, 247, 250)); // Hellgrau-blauer Premium-Hintergrund
        
        GridBagConstraints sGbc = new GridBagConstraints();
        sGbc.fill = GridBagConstraints.HORIZONTAL;
        sGbc.insets = new Insets(8, 12, 8, 12);
        sGbc.weightx = 1.0;
        sGbc.gridx = 0;
        int sGridy = 0;
        
        // --- SECTION 1: Standard Portfolio ---
        JLabel section1Title = new JLabel("Standard-Portfolio ($10K)");
        section1Title.setFont(AppUIStyle.BOLD_FONT);
        section1Title.setForeground(AppUIStyle.PRIMARY_COLOR);
        summaryPanel.add(section1Title, sGbc);
        sGbc.gridy = ++sGridy;
        
        lblTotalProfit = new JLabel("Gesamtprofit: -");
        lblTotalProfit.setFont(AppUIStyle.REGULAR_FONT);
        summaryPanel.add(lblTotalProfit, sGbc);
        sGbc.gridy = ++sGridy;
        
        lblAvgCorr = new JLabel("Mittlere Korrelation: -");
        lblAvgCorr.setFont(AppUIStyle.REGULAR_FONT);
        summaryPanel.add(lblAvgCorr, sGbc);
        sGbc.gridy = ++sGridy;
        
        lblCombinedDrawdown = new JLabel("Max. Portfolio DD: -");
        lblCombinedDrawdown.setFont(AppUIStyle.REGULAR_FONT);
        summaryPanel.add(lblCombinedDrawdown, sGbc);
        sGbc.gridy = ++sGridy;
        
        // Trennlinie
        summaryPanel.add(new javax.swing.JSeparator(), sGbc);
        sGbc.gridy = ++sGridy;
        
        // --- SECTION 2: Risiko-justiertes Portfolio ---
        JLabel section2Title = new JLabel("Risiko-justiert (10% Target DD)");
        section2Title.setFont(AppUIStyle.BOLD_FONT);
        section2Title.setForeground(AppUIStyle.PRIMARY_COLOR);
        summaryPanel.add(section2Title, sGbc);
        sGbc.gridy = ++sGridy;
        
        lblTotalProfitRisk = new JLabel("Gesamtprofit: -");
        lblTotalProfitRisk.setFont(AppUIStyle.REGULAR_FONT);
        summaryPanel.add(lblTotalProfitRisk, sGbc);
        sGbc.gridy = ++sGridy;
        
        lblCombinedDrawdownRisk = new JLabel("Max. Portfolio DD: -");
        lblCombinedDrawdownRisk.setFont(AppUIStyle.REGULAR_FONT);
        summaryPanel.add(lblCombinedDrawdownRisk, sGbc);
        sGbc.gridy = ++sGridy;
        
        // Trennlinie
        summaryPanel.add(new javax.swing.JSeparator(), sGbc);
        sGbc.gridy = ++sGridy;
        
        // --- SECTION 3: Risikofaktoren ---
        JLabel section3Title = new JLabel("Risiko-Faktoren (Hebel)");
        section3Title.setFont(AppUIStyle.BOLD_FONT);
        section3Title.setForeground(AppUIStyle.PRIMARY_COLOR);
        summaryPanel.add(section3Title, sGbc);
        sGbc.gridy = ++sGridy;
        
        lblRiskFactors = new JLabel("<html><i>Keine Strategien ausgewählt</i></html>");
        lblRiskFactors.setFont(AppUIStyle.REGULAR_FONT);
        summaryPanel.add(lblRiskFactors, sGbc);
        sGbc.gridy = ++sGridy;
        
        // Push summary components up
        sGbc.weighty = 1.0;
        summaryPanel.add(new JPanel(), sGbc);
        
        JScrollPane summaryScrollPane = new JScrollPane(summaryPanel);
        summaryScrollPane.setPreferredSize(new Dimension(280, 200));
        summaryScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Portfolio Statistik", 
            0, 0, AppUIStyle.SUBTITLE_FONT, AppUIStyle.PRIMARY_COLOR));
        
        bottomContainer.add(summaryScrollPane, BorderLayout.EAST);
        
        resultsSplitPane.setBottomComponent(bottomContainer);
        resultsPanel.add(resultsSplitPane, BorderLayout.CENTER);
        
        splitPane.setRightComponent(resultsPanel);
        add(splitPane, BorderLayout.CENTER);
    }
    
    private JSlider createWeightSlider(int initialValue) {
        JSlider slider = new JSlider(0, 10, initialValue);
        slider.setMajorTickSpacing(5);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setPreferredSize(new Dimension(140, 36));
        return slider;
    }
    
    private void addSliderRow(JPanel panel, GridBagConstraints gbc, int gridy, String labelText, JSlider slider) {
        gbc.gridx = 0;
        gbc.gridy = gridy;
        gbc.weightx = 0.35;
        JLabel label = AppUIStyle.createStyledLabel(labelText);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.55;
        panel.add(slider, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.1;
        JLabel valLabel = AppUIStyle.createStyledLabel(String.valueOf(slider.getValue()));
        valLabel.setFont(AppUIStyle.BOLD_FONT);
        panel.add(valLabel, gbc);

        slider.addChangeListener(e -> valLabel.setText(String.valueOf(slider.getValue())));
    }
    
    private void generatePortfolio() {
        if (providerStats == null || providerStats.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Keine Signal-Provider-Daten geladen!", "Warnung", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Read configuration values
        int size = (Integer) spinnerSize.getValue();
        double maxCorrelation = sliderMaxCorr.getValue() / 100.0;
        
        int wProfit = sliderProfit.getValue();
        int w3Mpdd = slider3Mpdd.getValue();
        int wDrawdown = sliderDrawdown.getValue();
        int wTrades = sliderTrades.getValue();
        int wTradeDays = sliderTradeDays.getValue();
        int wSubscribers = sliderSubscribers.getValue();
        
        // Get selected Timeframe
        int monthsLimit = -1;
        String selectedTimeframe = (String) comboTimeframe.getSelectedItem();
        if ("Letzte 12 Monate".equals(selectedTimeframe)) {
            monthsLimit = 12;
        } else if ("Letzte 6 Monate".equals(selectedTimeframe)) {
            monthsLimit = 6;
        } else if ("Letzte 3 Monate".equals(selectedTimeframe)) {
            monthsLimit = 3;
        }
        
        // Get selected Algorithm
        String selectedAlgo = (String) comboAlgorithm.getSelectedItem();
        String algorithm = "Combinatorial";
        if ("Greedy (Schnell)".equals(selectedAlgo)) {
            algorithm = "Greedy";
        }
        
        // Save global if requested
        if (checkSaveGlobal.isSelected()) {
            ScoreConfig config = ScoreConfig.getInstance();
            config.setWeightProfit(wProfit);
            config.setWeight3Mpdd(w3Mpdd);
            config.setWeightDrawdown(wDrawdown);
            config.setWeightTrades(wTrades);
            config.setWeightTradeDays(wTradeDays);
            config.setWeightSubscribers(wSubscribers);
            config.save();
            mainTable.forceCompleteReinitialize(); // Update main application
        }
        
        // Instantiate Optimizer
        PortfolioOptimizer optimizer = new PortfolioOptimizer(providerStats, htmlDatabase);
        List<Candidate> selected = optimizer.selectOptimalPortfolio(
            size, maxCorrelation, monthsLimit, algorithm, wProfit, w3Mpdd, wDrawdown, wTrades, wTradeDays, wSubscribers);
            
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Es konnte kein Portfolio zusammengestellt werden.", "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 1. Populate Selected Portfolio Table
        portfolioModel.setRowCount(0);
        int no = 1;
        for (Candidate c : selected) {
            portfolioModel.addRow(new Object[]{
                no++,
                c.getName().replace(".csv", ""),
                String.format("%.2f", c.getScore()),
                String.format("%.0f", c.getSubscribers()),
                String.format("%.0f", c.getTradesCount()),
                String.format("%.2f", c.getMpdd3()),
                String.format("%.2f", c.getEquityDrawdown())
            });
        }
        
        // 2. Populate Correlation Matrix Table
        int k = selected.size();
        String[] headers = new String[k + 1];
        headers[0] = "Signal Provider";
        for (int i = 0; i < k; i++) {
            headers[i + 1] = selected.get(i).getName().replace(".csv", "");
        }
        
        correlationModel.setDataVector(new Object[k][k + 1], headers);
        
        double sumCorr = 0;
        int countPairs = 0;
        
        for (int i = 0; i < k; i++) {
            Candidate cI = selected.get(i);
            correlationModel.setValueAt(cI.getName().replace(".csv", ""), i, 0);
            for (int j = 0; j < k; j++) {
                Candidate cJ = selected.get(j);
                double corr = optimizer.calculateCorrelation(cI, cJ, optimizer.getActiveStartIndex(), optimizer.getTotalDays());
                correlationModel.setValueAt(corr, i, j + 1);
                
                if (i < j) {
                    sumCorr += corr;
                    countPairs++;
                }
            }
        }
        
        // Adjust column widths of correlation table
        correlationTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        for (int i = 1; i <= k; i++) {
            correlationTable.getColumnModel().getColumn(i).setPreferredWidth(80);
        }
        
        // 3. Generate combined returns and plot chart
        plotPortfolioChart(selected, optimizer.getActiveStartDate(), optimizer.getGlobalEndDate(), optimizer.getTotalDays(), optimizer.getActiveStartIndex());
        
        // 4. Update Summary Statistics (Standard-Portfolio)
        double totalProfitSum = 0;
        for (Candidate c : selected) {
            double candidateProfitInTimeframe = 0;
            for (Trade t : c.getStats().getTrades()) {
                if (!t.getCloseTime().toLocalDate().isBefore(optimizer.getActiveStartDate())) {
                    candidateProfitInTimeframe += t.getProfit() * c.getScaleFactor();
                }
            }
            totalProfitSum += candidateProfitInTimeframe;
        }
        double avgCorr = countPairs > 0 ? sumCorr / countPairs : 0.0;
        
        lblTotalProfit.setText(String.format("Gesamtprofit: %.2f", totalProfitSum));
        lblAvgCorr.setText(String.format("Mittlere Korrelation: %.3f", avgCorr));
        
        double maxCombinedDrawdown = calculateCombinedDrawdown(selected, optimizer.getActiveStartDate(), optimizer.getActiveStartIndex(), optimizer.getTotalDays());
        lblCombinedDrawdown.setText(String.format("Max. Portfolio DD: %.2f%%", maxCombinedDrawdown));
        
        // 5. Update Summary Statistics (Risiko-justiertes Portfolio)
        double totalProfitSumRisk = 0;
        StringBuilder factorsHtml = new StringBuilder("<html>");
        
        for (Candidate c : selected) {
            double dd = c.getEquityDrawdown();
            double riskMultiplier = 10.0 / (dd > 0.0 ? dd : 1.0);
            double riskScaleFactor = c.getScaleFactor() * riskMultiplier;
            
            double candidateProfitInTimeframe = 0;
            for (Trade t : c.getStats().getTrades()) {
                if (!t.getCloseTime().toLocalDate().isBefore(optimizer.getActiveStartDate())) {
                    candidateProfitInTimeframe += t.getProfit() * riskScaleFactor;
                }
            }
            totalProfitSumRisk += candidateProfitInTimeframe;
            
            factorsHtml.append(String.format("%s: <b>x%.2f</b><br>", 
                c.getName().replace(".csv", ""), riskMultiplier));
        }
        factorsHtml.append("</html>");
        
        lblTotalProfitRisk.setText(String.format("Gesamtprofit: %.2f", totalProfitSumRisk));
        
        double maxCombinedDrawdownRisk = calculateCombinedDrawdownRisk(selected, optimizer.getActiveStartDate(), optimizer.getActiveStartIndex(), optimizer.getTotalDays());
        lblCombinedDrawdownRisk.setText(String.format("Max. Portfolio DD: %.2f%%", maxCombinedDrawdownRisk));
        
        lblRiskFactors.setText(factorsHtml.toString());
    }
    
    private double calculateCombinedDrawdown(List<Candidate> selected, LocalDate activeStartDate, int activeStartIndex, int totalDays) {
        double initialSum = 10000.0 * selected.size();
        double currentBalance = initialSum;
        double highWaterMark = initialSum;
        double maxDrawdownPercent = 0.0;
        
        for (int day = activeStartIndex; day < totalDays; day++) {
            double dailyProfit = 0;
            for (Candidate c : selected) {
                dailyProfit += c.getDailyReturns()[day] * c.getScaleFactor();
            }
            currentBalance += dailyProfit;
            
            if (currentBalance > highWaterMark) {
                highWaterMark = currentBalance;
            } else if (highWaterMark > 0) {
                double drawdownPercent = (highWaterMark - currentBalance) / highWaterMark * 100;
                maxDrawdownPercent = Math.max(maxDrawdownPercent, drawdownPercent);
            }
        }
        return maxDrawdownPercent;
    }
    
    private double calculateCombinedDrawdownRisk(List<Candidate> selected, LocalDate activeStartDate, int activeStartIndex, int totalDays) {
        double initialSum = 10000.0 * selected.size();
        double currentBalance = initialSum;
        double highWaterMark = initialSum;
        double maxDrawdownPercent = 0.0;
        
        for (int day = activeStartIndex; day < totalDays; day++) {
            double dailyProfit = 0;
            for (Candidate c : selected) {
                double dd = c.getEquityDrawdown();
                double riskMultiplier = 10.0 / (dd > 0.0 ? dd : 1.0);
                double riskScaleFactor = c.getScaleFactor() * riskMultiplier;
                dailyProfit += c.getDailyReturns()[day] * riskScaleFactor;
            }
            currentBalance += dailyProfit;
            
            if (currentBalance > highWaterMark) {
                highWaterMark = currentBalance;
            } else if (highWaterMark > 0) {
                double drawdownPercent = (highWaterMark - currentBalance) / highWaterMark * 100;
                maxDrawdownPercent = Math.max(maxDrawdownPercent, drawdownPercent);
            }
        }
        return maxDrawdownPercent;
    }
    
    private void styleChart(JFreeChart chart, TimeSeriesCollection dataset) {
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setDefaultShapesVisible(false);
        
        int numSeries = dataset.getSeriesCount();
        for (int s = 0; s < numSeries - 1; s++) {
            renderer.setSeriesStroke(s, new BasicStroke(1.2f));
        }
        // Last series is the combined portfolio
        renderer.setSeriesStroke(numSeries - 1, new BasicStroke(3.0f));
        renderer.setSeriesPaint(numSeries - 1, new Color(220, 50, 50)); // Bright red for combined portfolio
        
        plot.setRenderer(renderer);
        
        DateAxis dateAxis = (DateAxis) plot.getDomainAxis();
        dateAxis.setDateFormatOverride(new SimpleDateFormat("yyyy-MM-dd"));
    }
    
    private void plotPortfolioChart(List<Candidate> selected, LocalDate activeStartDate, LocalDate globalEndDate, int totalDays, int startIndex) {
        int localTotalDays = totalDays - startIndex;
        
        // ----------------------------------------------------
        // CHART 1: Standard Skalierung auf $10K Startkapital
        // ----------------------------------------------------
        TimeSeriesCollection dataset1 = new TimeSeriesCollection();
        
        // 1. Individual candidates' curves
        for (Candidate c : selected) {
            TimeSeries series = new TimeSeries(c.getName().replace(".csv", ""));
            double equity = 10000.0;
            LocalDate currentDate = activeStartDate;
            for (int i = 0; i < localTotalDays; i++) {
                equity += c.getDailyReturns()[startIndex + i] * c.getScaleFactor();
                Date date = Date.from(currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
                series.addOrUpdate(new Day(date), equity);
                currentDate = currentDate.plusDays(1);
            }
            dataset1.addSeries(series);
        }
        
        // 2. Combined curve
        TimeSeries portfolioSeries1 = new TimeSeries("Kombiniertes Portfolio (Summe)");
        double combinedInitial1 = 10000.0 * selected.size();
        double combinedEquity1 = combinedInitial1;
        LocalDate currentDate1 = activeStartDate;
        for (int i = 0; i < localTotalDays; i++) {
            double dayReturn = 0;
            for (Candidate c : selected) {
                dayReturn += c.getDailyReturns()[startIndex + i] * c.getScaleFactor();
            }
            combinedEquity1 += dayReturn;
            Date date = Date.from(currentDate1.atStartOfDay(ZoneId.systemDefault()).toInstant());
            portfolioSeries1.addOrUpdate(new Day(date), combinedEquity1);
            currentDate1 = currentDate1.plusDays(1);
        }
        dataset1.addSeries(portfolioSeries1);
        
        JFreeChart chart1 = ChartFactory.createTimeSeriesChart(
            "Portfolio Equity Curves vs. Einzelstrategien (Start bei $10K)",
            "Zeit",
            "Equity ($)",
            dataset1,
            true,
            true,
            false
        );
        chart1.addSubtitle(new org.jfree.chart.title.TextTitle(
            "Alle Strategien starten mit $10.000 Initial-Equity (Vergleich auf $10k Basis)"));
        styleChart(chart1, dataset1);
        
        // ----------------------------------------------------
        // CHART 2: Risikobasierte Skalierung auf 10% Drawdown
        // ----------------------------------------------------
        TimeSeriesCollection dataset2 = new TimeSeriesCollection();
        
        // 1. Individual candidates' curves
        for (Candidate c : selected) {
            TimeSeries series = new TimeSeries(c.getName().replace(".csv", ""));
            double equity = 10000.0;
            double dd = c.getEquityDrawdown();
            double riskMultiplier = 10.0 / (dd > 0.0 ? dd : 1.0);
            double riskScaleFactor = c.getScaleFactor() * riskMultiplier;
            
            LocalDate currentDate = activeStartDate;
            for (int i = 0; i < localTotalDays; i++) {
                equity += c.getDailyReturns()[startIndex + i] * riskScaleFactor;
                Date date = Date.from(currentDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
                series.addOrUpdate(new Day(date), equity);
                currentDate = currentDate.plusDays(1);
            }
            dataset2.addSeries(series);
        }
        
        // 2. Combined curve
        TimeSeries portfolioSeries2 = new TimeSeries("Kombiniertes Portfolio (Risiko-justiert)");
        double combinedInitial2 = 10000.0 * selected.size();
        double combinedEquity2 = combinedInitial2;
        LocalDate currentDate2 = activeStartDate;
        for (int i = 0; i < localTotalDays; i++) {
            double dayReturn = 0;
            for (Candidate c : selected) {
                double dd = c.getEquityDrawdown();
                double riskMultiplier = 10.0 / (dd > 0.0 ? dd : 1.0);
                double riskScaleFactor = c.getScaleFactor() * riskMultiplier;
                dayReturn += c.getDailyReturns()[startIndex + i] * riskScaleFactor;
            }
            combinedEquity2 += dayReturn;
            Date date = Date.from(currentDate2.atStartOfDay(ZoneId.systemDefault()).toInstant());
            portfolioSeries2.addOrUpdate(new Day(date), combinedEquity2);
            currentDate2 = currentDate2.plusDays(1);
        }
        dataset2.addSeries(portfolioSeries2);
        
        JFreeChart chart2 = ChartFactory.createTimeSeriesChart(
            "Risiko-justierte Equity Curves (Skaliert auf 10% Target DD)",
            "Zeit",
            "Equity ($)",
            dataset2,
            true,
            true,
            false
        );
        chart2.addSubtitle(new org.jfree.chart.title.TextTitle(
            "Gewichtungs-Faktor pro Roboter = 10% / maximaler Drawdown (stabilisiert das Portfolio)"));
        styleChart(chart2, dataset2);
        
        // Update Container
        chartContainer.removeAll();
        
        // Add both charts stacked vertically with PreferredSize to force scrolling
        ChartPanel panel1 = new ChartPanel(chart1);
        panel1.setPreferredSize(new Dimension(600, 360));
        panel1.setMinimumSize(new Dimension(600, 300));
        
        ChartPanel panel2 = new ChartPanel(chart2);
        panel2.setPreferredSize(new Dimension(600, 360));
        panel2.setMinimumSize(new Dimension(600, 300));
        
        GridBagConstraints cGbc = new GridBagConstraints();
        cGbc.fill = GridBagConstraints.BOTH;
        cGbc.weightx = 1.0;
        cGbc.weighty = 0.5;
        cGbc.gridx = 0;
        
        cGbc.gridy = 0;
        chartContainer.add(panel1, cGbc);
        
        // Separator
        cGbc.gridy = 1;
        cGbc.weighty = 0.0;
        chartContainer.add(new javax.swing.JSeparator(), cGbc);
        
        cGbc.gridy = 2;
        cGbc.weighty = 0.5;
        chartContainer.add(panel2, cGbc);
        
        chartContainer.revalidate();
        chartContainer.repaint();
    }
    
    // Custom Table Cell Renderer for heat-map style correlation matrix
    private static class CorrelationHeatMapRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            // Format column 0 (names) normally
            if (column == 0) {
                c.setBackground(table.getBackground());
                c.setForeground(table.getForeground());
                setFont(AppUIStyle.BOLD_FONT);
                setHorizontalAlignment(LEFT);
                return c;
            }
            
            setFont(AppUIStyle.REGULAR_FONT);
            setHorizontalAlignment(CENTER);
            
            // For correlation value columns
            if (value instanceof Double) {
                double corr = (Double) value;
                setText(String.format("%.3f", corr));
                
                if (row == (column - 1)) {
                    // Diagonal (self correlation = 1.0)
                    c.setBackground(Color.LIGHT_GRAY);
                    c.setForeground(Color.BLACK);
                } else {
                    // Heat-map coloring
                    if (corr >= 0.40) {
                        c.setBackground(new Color(255, 200, 200)); // Red for high correlation
                        c.setForeground(new Color(120, 0, 0));
                    } else if (corr >= 0.20) {
                        c.setBackground(new Color(255, 240, 200)); // Yellow/Orange for medium correlation
                        c.setForeground(new Color(150, 80, 0));
                    } else {
                        c.setBackground(new Color(200, 250, 200)); // Green for low correlation (Good!)
                        c.setForeground(new Color(0, 100, 0));
                    }
                }
            } else {
                setText(value != null ? value.toString() : "");
                c.setBackground(table.getBackground());
                c.setForeground(table.getForeground());
            }
            
            return c;
        }
    }
}
