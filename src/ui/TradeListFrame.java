package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import components.TradeChartPanel;
import components.TradeListTable;
import data.ProviderStats;
import data.Trade;
import utils.TradeUtils;

public class TradeListFrame extends JFrame {
    private final TradeListTable tradeTable;
    private final JPanel detailPanel;
    private final TradeChartPanel chartPanel;
    private JSplitPane mainSplitPane;
    private final ProviderStats stats; // Referenz auf die ProviderStats

    public TradeListFrame(String providerName, ProviderStats stats) {
        super("Trade List: " + providerName);
        this.stats = stats; // ProviderStats speichern
        this.tradeTable = new TradeListTable(stats);
        this.detailPanel = new JPanel();
        this.chartPanel = new TradeChartPanel();
        
        initializeUI();
        setupSelectionListener();
        
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        // Escape-Taste zum Schließen
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
        Action escapeAction = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        };
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", escapeAction);
    }

    private void initializeUI() {
        setSize(1600, 900);
        setLayout(new BorderLayout(5, 0));

        // Hauptbereich mit Tabelle
        JScrollPane scrollPane = new JScrollPane(tradeTable);
        
        // Rechtes Panel für Chart und Concurrent Trades
        JPanel rightPanel = new JPanel(new BorderLayout(0, 5));
        
        // Chart Panel im oberen Bereich
        JPanel chartContainer = new JPanel(new BorderLayout());
        chartContainer.setBorder(BorderFactory.createTitledBorder("Open Trades Timeline"));
        chartPanel.setPreferredSize(new Dimension(500, 400)); // Schön groß machen
        chartContainer.add(chartPanel, BorderLayout.CENTER);
        
        // Detail Panel für konkurrierende Trades
        detailPanel.setBorder(BorderFactory.createTitledBorder("Concurrent Trades"));
        detailPanel.setLayout(new BorderLayout());

        // Zusammenführen der Panels
        rightPanel.add(chartContainer, BorderLayout.NORTH);
        rightPanel.add(detailPanel, BorderLayout.CENTER);
        
        // Haupt-Split Pane
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, rightPanel);
        mainSplitPane.setResizeWeight(0.6); // 60% Breite für die Tabelle (mehr Platz für das rechte Panel)

        // Toolbar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        
        JButton exportButton = new JButton("Export");
        exportButton.addActionListener(e -> exportTrades());
        toolBar.add(exportButton);

        // Layout zusammenbauen
        add(toolBar, BorderLayout.NORTH);
        add(mainSplitPane, BorderLayout.CENTER);
        setLocationRelativeTo(null);
        
        // Explizites Setzen der initialen Divider-Position
        SwingUtilities.invokeLater(() -> {
            mainSplitPane.setDividerLocation(0.6);
        });
    }
    
    private void setupSelectionListener() {
        tradeTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int row = tradeTable.getSelectedRow();
                    if (row >= 0) {
                        Trade selectedTrade = tradeTable.getTradeAt(row);
                        // Verwende TradeUtils.getActiveTradesAt direkt mit den Trades aus stats
                        List<Trade> concurrentTrades = TradeUtils.getActiveTradesAt(
                            stats.getTrades(), 
                            selectedTrade.getOpenTime()
                        );
                        updateDetailPanel(selectedTrade, concurrentTrades);
                    }
                }
            }
        });
    }

    private void updateDetailPanel(Trade selectedTrade, List<Trade> concurrentTrades) {
        detailPanel.removeAll();
        
        // Chart aktualisieren
        chartPanel.updateTrades(concurrentTrades, selectedTrade.getOpenTime());
        
        // Details Panel
        JPanel content = new JPanel();
        content.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Übersichtsinformationen
        JPanel summaryPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        summaryPanel.setBorder(BorderFactory.createTitledBorder("Summary"));
        
        summaryPanel.add(new JLabel("Time: " + selectedTrade.getOpenTime()));
        summaryPanel.add(new JLabel("Concurrent Trades: " + concurrentTrades.size()));
        
        double totalLots = concurrentTrades.stream().mapToDouble(Trade::getLots).sum();
        summaryPanel.add(new JLabel("Total Lots: " + String.format("%.3f", totalLots)));
        
        content.add(summaryPanel, gbc);
        gbc.gridy++;

        // Details für jeden konkurrierenden Trade
        for (Trade trade : concurrentTrades) {
            JPanel tradePanel = createTradePanel(trade);
            content.add(tradePanel, gbc);
            gbc.gridy++;
        }

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);
        
        detailPanel.add(scrollPane, BorderLayout.CENTER);
        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private JPanel createTradePanel(Trade trade) {
        JPanel tradePanel = new JPanel(new GridBagLayout());
        tradePanel.setBorder(BorderFactory.createTitledBorder("Trade"));
        
        GridBagConstraints tgbc = new GridBagConstraints();
        tgbc.gridx = 0;
        tgbc.gridy = 0;
        tgbc.anchor = GridBagConstraints.WEST;
        tgbc.insets = new Insets(2, 5, 2, 5);
        tgbc.fill = GridBagConstraints.HORIZONTAL;
        tgbc.weightx = 1.0;

        addTradeDetail(tradePanel, "Symbol:", trade.getSymbol(), tgbc);
        addTradeDetail(tradePanel, "Type:", trade.getType(), tgbc);
        addTradeDetail(tradePanel, "Lots:", String.format("%.2f", trade.getLots()), tgbc);
        addTradeDetail(tradePanel, "Open Price:", String.format("%.5f", trade.getOpenPrice()), tgbc);
        if (trade.getStopLoss() > 0) {
            addTradeDetail(tradePanel, "Stop Loss:", String.format("%.5f", trade.getStopLoss()), tgbc);
        }
        if (trade.getTakeProfit() > 0) {
            addTradeDetail(tradePanel, "Take Profit:", String.format("%.5f", trade.getTakeProfit()), tgbc);
        }
        
        return tradePanel;
    }

    private void addTradeDetail(JPanel panel, String label, String value, GridBagConstraints gbc) {
        JPanel rowPanel = new JPanel(new BorderLayout(10, 0));
        rowPanel.add(new JLabel(label), BorderLayout.WEST);
        rowPanel.add(new JLabel(value), BorderLayout.CENTER);
        panel.add(rowPanel, gbc);
        gbc.gridy++;
    }

    private void exportTrades() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Trades");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setSelectedFile(new File("trades_export.csv"));
        
        FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files (*.csv)", "csv");
        fileChooser.setFileFilter(filter);
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("Open Time,Close Time,Type,Symbol,Lots,Open Price,Close Price," +
                             "Profit/Loss,Commission,Swap,Total");
                
                // Exportiere die originalen Trades aus stats
                List<Trade> tradesToExport = stats.getTrades();
                
                for (Trade trade : tradesToExport) {
                    writer.printf("%s,%s,%s,%s,%.2f,%.5f,%.5f,%.2f,%.2f,%.2f,%.2f%n",
                        trade.getOpenTime(),
                        trade.getCloseTime(),
                        trade.getType(),
                        trade.getSymbol(),
                        trade.getLots(),
                        trade.getOpenPrice(),
                        trade.getClosePrice(),
                        trade.getProfit(),
                        trade.getCommission(),
                        trade.getSwap(),
                        trade.getTotalProfit()
                    );
                }
                
                JOptionPane.showMessageDialog(this, 
                    "Export successful: " + file.getAbsolutePath(), 
                    "Export Complete", 
                    JOptionPane.INFORMATION_MESSAGE);
                    
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Error exporting file: " + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }
}