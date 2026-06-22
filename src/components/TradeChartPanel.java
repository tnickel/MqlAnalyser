package components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ToolTipManager;

import data.Trade;

public class TradeChartPanel extends JPanel {
    private List<Trade> trades;
    private LocalDateTime startTime;
    private final int PADDING = 20;
    private final int LEFT_PADDING = 180;
    private final int RIGHT_PADDING = 20;
    private final int ROW_HEIGHT = 40;
    private final Color BUY_COLOR = new Color(0, 150, 0);
    private final Color SELL_COLOR = new Color(200, 0, 0);
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    private ChartPanel chartPanel;
    private Trade selectedTrade;
    
    public interface TradeSelectionListener {
        void onTradeSelected(Trade trade);
    }
    
    private TradeSelectionListener selectionListener;

    public TradeChartPanel() {
        setLayout(new BorderLayout());
        chartPanel = new ChartPanel();
        JScrollPane scrollPane = new JScrollPane(chartPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }
    
    public void setTradeSelectionListener(TradeSelectionListener listener) {
        this.selectionListener = listener;
    }

    public void updateTrades(List<Trade> trades, LocalDateTime startTime) {
        this.trades = trades;
        this.startTime = startTime;
        chartPanel.trades = trades;
        chartPanel.startTime = startTime;
        
        int preferredHeight = Math.max(350, trades.size() * ROW_HEIGHT + 2 * PADDING + 30);
        chartPanel.setPreferredSize(new Dimension(0, preferredHeight));
        
        revalidate();
        repaint();
    }

    private class ChartPanel extends JPanel {
        private List<Trade> trades;
        private LocalDateTime startTime;
        private Trade hoveredTrade;
        private Point mousePosition;

        public ChartPanel() {
            setBackground(Color.WHITE);
            
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    mousePosition = e.getPoint();
                    hoveredTrade = findTradeAtPosition(e.getPoint());
                    repaint();
                }
            });
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Trade clickedTrade = findTradeAtPosition(e.getPoint());
                    if (clickedTrade != null && selectionListener != null) {
                        selectedTrade = clickedTrade;
                        selectionListener.onTradeSelected(clickedTrade);
                        repaint();
                    }
                }
            });
            
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        private Trade findTradeAtPosition(Point p) {
            if (trades == null || trades.isEmpty()) return null;

            int y = PADDING;
            for (Trade trade : trades) {
                if (p.y >= y && p.y < y + ROW_HEIGHT) {
                    return trade;
                }
                y += ROW_HEIGHT;
            }
            return null;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            Trade trade = findTradeAtPosition(e.getPoint());
            if (trade != null) {
                return String.format("<html>Symbol: %s<br>Type: %s<br>Lots: %.2f<br>Open: %s<br>Close: %s<br>Provider: %s</html>",
                    trade.getSymbol(),
                    trade.getType(),
                    trade.getLots(),
                    trade.getOpenTime().format(timeFormatter),
                    trade.getCloseTime().format(timeFormatter),
                    trade.getSignalProvider());
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (trades == null || trades.isEmpty() || startTime == null) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int chartWidth = getWidth() - LEFT_PADDING - RIGHT_PADDING;
            int gridHeight = trades.size() * ROW_HEIGHT;

            drawGrid(g2, chartWidth, gridHeight);

            int y = PADDING;
            for (Trade trade : trades) {
                drawTrade(g2, trade, chartWidth, y);
                y += ROW_HEIGHT;
            }

            drawTimeAxis(g2, chartWidth, gridHeight);
            
            if (hoveredTrade != null) {
                int hoverY = PADDING + trades.indexOf(hoveredTrade) * ROW_HEIGHT;
                g2.setColor(new Color(240, 240, 255, 128));
                g2.fillRect(0, hoverY, getWidth(), ROW_HEIGHT);
            }
        }

        private void drawGrid(Graphics2D g2, int chartWidth, int gridHeight) {
            g2.setColor(new Color(240, 240, 240));
            
            LocalDateTime earliest = startTime;
            LocalDateTime latest = earliest.plusHours(1); // Standardwert, falls keine Trades vorhanden sind
            
            // Finden des spätesten Close-Time unter den Trades
            if (trades != null && !trades.isEmpty()) {
                for (Trade trade : trades) {
                    if (trade.getCloseTime().isAfter(latest)) {
                        latest = trade.getCloseTime();
                    }
                }
            }

            int timeRange = (int) java.time.Duration.between(earliest, latest).toHours();
            
            // Vermeidung von Division durch Null
            if (timeRange <= 0) timeRange = 1;
            
            int markInterval = Math.max(1, timeRange / 10);

            for (int i = 0; i <= timeRange; i += markInterval) {
                int x = LEFT_PADDING + (int)(i * chartWidth / timeRange);
                g2.drawLine(x, PADDING, x, gridHeight + PADDING);
            }

            for (int i = 0; i <= trades.size(); i++) {
                int y = PADDING + i * ROW_HEIGHT;
                g2.drawLine(LEFT_PADDING, y, LEFT_PADDING + chartWidth, y);
            }
        }

        private void drawTimeAxis(Graphics2D g2, int chartWidth, int gridHeight) {
            if (startTime == null) return;
            
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.PLAIN, 10));
            
            LocalDateTime earliest = startTime;
            LocalDateTime latest = earliest.plusHours(1); // Standardwert, falls keine Trades vorhanden sind
            
            // Finden des spätesten Close-Time unter den Trades
            if (trades != null && !trades.isEmpty()) {
                for (Trade trade : trades) {
                    if (trade.getCloseTime().isAfter(latest)) {
                        latest = trade.getCloseTime();
                    }
                }
            }

            int timeRange = (int) java.time.Duration.between(earliest, latest).toHours();
            
            // Vermeidung von Division durch Null
            if (timeRange <= 0) timeRange = 1;
            
            int markInterval = Math.max(1, timeRange / 10);

            for (int i = 0; i <= timeRange; i += markInterval) {
                LocalDateTime markTime = earliest.plusHours(i);
                int x = LEFT_PADDING + (int)(i * chartWidth / timeRange);
                g2.drawString(markTime.format(timeFormatter), x - 25, gridHeight + PADDING + 15);
            }
        }

        private void drawTrade(Graphics2D g2, Trade trade, int chartWidth, int y) {
            if (startTime == null) return;
            
            // Berechne früheste und späteste Zeit
            LocalDateTime earliest = startTime;
            LocalDateTime latest = earliest.plusHours(1); // Standardwert, falls nur ein Trade vorhanden ist
            
            // Finden des spätesten Close-Time unter den Trades
            if (trades != null && !trades.isEmpty()) {
                for (Trade t : trades) {
                    if (t.getCloseTime().isAfter(latest)) {
                        latest = t.getCloseTime();
                    }
                }
            }
            
            // Berechne Gesamtminuten für den Zeitraum
            int totalMinutes = (int) java.time.Duration.between(earliest, latest).toMinutes();
            
            // Vermeidung von Division durch Null
            if (totalMinutes <= 0) totalMinutes = 60; // Verwende 1 Stunde als Standard
            
            long startDiff = java.time.Duration.between(earliest, trade.getOpenTime()).toMinutes();
            long duration = java.time.Duration.between(trade.getOpenTime(), trade.getCloseTime()).toMinutes();
            
            int x1 = LEFT_PADDING + (int)(startDiff * chartWidth / totalMinutes);
            int x2 = LEFT_PADDING + (int)((startDiff + duration) * chartWidth / totalMinutes);
            
            // Minimum-Breite für sehr kurze Trades
            if (x2 - x1 < 4) x2 = x1 + 4;
            
            int barHeight = (int)(ROW_HEIGHT * 0.5);
            double scale = 1.0 + Math.min(1.0, trade.getLots());
            int actualBarHeight = (int)(barHeight * scale);
            if (actualBarHeight > ROW_HEIGHT - 6) {
                actualBarHeight = ROW_HEIGHT - 6;
            }
            
            int yCenter = y + ROW_HEIGHT / 2;

            g2.setColor(trade.getType().equalsIgnoreCase("buy") ? BUY_COLOR : SELL_COLOR);
            g2.fillRect(x1, yCenter - actualBarHeight/2, Math.max(x2 - x1, 2), actualBarHeight);
            
            // Dünner Rahmen um den Balken
            g2.setColor(g2.getColor().darker());
            g2.drawRect(x1, yCenter - actualBarHeight/2, Math.max(x2 - x1, 2), actualBarHeight);
            
            // Symbol und Lot-Größe links
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            String tradeInfo = String.format("%s (%.2f Lots)", trade.getSymbol(), trade.getLots());
            g2.drawString(tradeInfo, 10, yCenter + 5);
        }

        @Override
        public Dimension getPreferredSize() {
            if (trades == null || trades.isEmpty()) {
                return new Dimension(800, 300);
            }
            return new Dimension(800, trades.size() * ROW_HEIGHT + 2 * PADDING + 30);
        }
    }
}