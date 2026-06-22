package utils;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import data.ProviderStats;
import data.Trade;

public class PortfolioOptimizerTest {
    
    private Map<String, ProviderStats> statsMap;
    private MockHtmlDatabase mockHtmlDb;
    
    private static class MockHtmlDatabase extends HtmlDatabase {
        private final Map<String, Integer> subscribers = new HashMap<>();
        private final Map<String, Double> drawdowns = new HashMap<>();
        private final Map<String, Map<Integer, Double>> avgMonthlyProfits = new HashMap<>();
        private final Map<String, Map<String, Double>> monthlyProfitPercentages = new HashMap<>();
        
        public MockHtmlDatabase() {
            super("dummyPath");
        }
        
        public void setSubscribers(String file, int subs) { subscribers.put(file, subs); }
        public void setDrawdown(String file, double dd) { drawdowns.put(file, dd); }
        public void setAvgMonthlyProfit(String file, int months, double profit) {
            avgMonthlyProfits.computeIfAbsent(file, k -> new HashMap<>()).put(months, profit);
        }
        public void setMonthlyProfitPercentages(String file, Map<String, Double> profits) {
            monthlyProfitPercentages.put(file, profits);
        }
        
        @Override
        public int getSubscribers(String fileName) { return subscribers.getOrDefault(fileName, 0); }
        @Override
        public double getEquityDrawdown(String fileName) { return drawdowns.getOrDefault(fileName, 1.0); }
        @Override
        public double getAverageMonthlyProfit(String fileName, int months) {
            if (avgMonthlyProfits.containsKey(fileName)) {
                return avgMonthlyProfits.get(fileName).getOrDefault(months, 0.0);
            }
            return 0.0;
        }
        @Override
        public Map<String, Double> getMonthlyProfitPercentages(String fileName) {
            return monthlyProfitPercentages.getOrDefault(fileName, new HashMap<>());
        }
    }
    
    @BeforeEach
    public void setUp() {
        statsMap = new HashMap<>();
        mockHtmlDb = new MockHtmlDatabase();
    }
    
    @Test
    public void testPearsonCorrelation() {
        // Create 2 providers
        ProviderStats s1 = new ProviderStats();
        s1.setInitialBalance(1000.0);
        // We will add trades to establish the date range
        s1.addTrade(LocalDateTime.of(2026, 6, 20, 10, 0), LocalDateTime.of(2026, 6, 20, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 10.0);
        s1.addTrade(LocalDateTime.of(2026, 6, 21, 10, 0), LocalDateTime.of(2026, 6, 21, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 20.0);
        s1.addTrade(LocalDateTime.of(2026, 6, 22, 10, 0), LocalDateTime.of(2026, 6, 22, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 30.0);
        
        ProviderStats s2 = new ProviderStats();
        s2.setInitialBalance(1000.0);
        s2.addTrade(LocalDateTime.of(2026, 6, 20, 10, 0), LocalDateTime.of(2026, 6, 20, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 2.0);
        s2.addTrade(LocalDateTime.of(2026, 6, 21, 10, 0), LocalDateTime.of(2026, 6, 21, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 4.0);
        s2.addTrade(LocalDateTime.of(2026, 6, 22, 10, 0), LocalDateTime.of(2026, 6, 22, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 6.0);
        
        statsMap.put("p1.csv", s1);
        statsMap.put("p2.csv", s2);
        
        PortfolioOptimizer optimizer = new PortfolioOptimizer(statsMap, mockHtmlDb);
        List<PortfolioOptimizer.Candidate> candidates = optimizer.getCandidates();
        
        assertEquals(2, candidates.size());
        
        PortfolioOptimizer.Candidate c1 = candidates.stream().filter(c -> c.getName().equals("p1.csv")).findFirst().get();
        PortfolioOptimizer.Candidate c2 = candidates.stream().filter(c -> c.getName().equals("p2.csv")).findFirst().get();
        
        // Pearson correlation of [10, 20, 30] and [2, 4, 6] should be 1.0 (perfect positive correlation)
        double corr = optimizer.calculateCorrelation(c1, c2);
        assertEquals(1.0, corr, 0.0001);
    }
    
    @Test
    public void testNegativePearsonCorrelation() {
        ProviderStats s1 = new ProviderStats();
        s1.setInitialBalance(1000.0);
        s1.addTrade(LocalDateTime.of(2026, 6, 20, 10, 0), LocalDateTime.of(2026, 6, 20, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 10.0);
        s1.addTrade(LocalDateTime.of(2026, 6, 21, 10, 0), LocalDateTime.of(2026, 6, 21, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 20.0);
        
        ProviderStats s2 = new ProviderStats();
        s2.setInitialBalance(1000.0);
        s2.addTrade(LocalDateTime.of(2026, 6, 20, 10, 0), LocalDateTime.of(2026, 6, 20, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, -10.0);
        s2.addTrade(LocalDateTime.of(2026, 6, 21, 10, 0), LocalDateTime.of(2026, 6, 21, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, -20.0);
        
        statsMap.put("p1.csv", s1);
        statsMap.put("p2.csv", s2);
        
        PortfolioOptimizer optimizer = new PortfolioOptimizer(statsMap, mockHtmlDb);
        List<PortfolioOptimizer.Candidate> candidates = optimizer.getCandidates();
        
        PortfolioOptimizer.Candidate c1 = candidates.stream().filter(c -> c.getName().equals("p1.csv")).findFirst().get();
        PortfolioOptimizer.Candidate c2 = candidates.stream().filter(c -> c.getName().equals("p2.csv")).findFirst().get();
        
        // Pearson correlation of [10, 20] and [-10, -20] should be -1.0 (perfect negative correlation)
        double corr = optimizer.calculateCorrelation(c1, c2);
        assertEquals(-1.0, corr, 0.0001);
    }
    
    @Test
    public void testFlatReturnCorrelation() {
        ProviderStats s1 = new ProviderStats();
        s1.setInitialBalance(1000.0);
        s1.addTrade(LocalDateTime.of(2026, 6, 20, 10, 0), LocalDateTime.of(2026, 6, 20, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 0.0);
        s1.addTrade(LocalDateTime.of(2026, 6, 21, 10, 0), LocalDateTime.of(2026, 6, 21, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 0.0);
        
        ProviderStats s2 = new ProviderStats();
        s2.setInitialBalance(1000.0);
        s2.addTrade(LocalDateTime.of(2026, 6, 20, 10, 0), LocalDateTime.of(2026, 6, 20, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 5.0);
        s2.addTrade(LocalDateTime.of(2026, 6, 21, 10, 0), LocalDateTime.of(2026, 6, 21, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 10.0);
        
        statsMap.put("p1.csv", s1);
        statsMap.put("p2.csv", s2);
        
        PortfolioOptimizer optimizer = new PortfolioOptimizer(statsMap, mockHtmlDb);
        List<PortfolioOptimizer.Candidate> candidates = optimizer.getCandidates();
        
        PortfolioOptimizer.Candidate c1 = candidates.stream().filter(c -> c.getName().equals("p1.csv")).findFirst().get();
        PortfolioOptimizer.Candidate c2 = candidates.stream().filter(c -> c.getName().equals("p2.csv")).findFirst().get();
        
        // Pearson correlation should be 0.0 to prevent division by zero (variance of c1 returns is 0)
        double corr = optimizer.calculateCorrelation(c1, c2);
        assertEquals(0.0, corr, 0.0001);
    }
    
    @Test
    public void testGreedySelection() {
        ProviderStats s1 = new ProviderStats();
        s1.setInitialBalance(1000.0);
        s1.addTrade(LocalDateTime.of(2026, 6, 20, 10, 0), LocalDateTime.of(2026, 6, 20, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 50.0);
        s1.addTrade(LocalDateTime.of(2026, 6, 21, 10, 0), LocalDateTime.of(2026, 6, 21, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 60.0);
        
        ProviderStats s2 = new ProviderStats();
        s2.setInitialBalance(1000.0);
        s2.addTrade(LocalDateTime.of(2026, 6, 20, 10, 0), LocalDateTime.of(2026, 6, 20, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 50.0);
        s2.addTrade(LocalDateTime.of(2026, 6, 21, 10, 0), LocalDateTime.of(2026, 6, 21, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 60.0);
        
        ProviderStats s3 = new ProviderStats();
        s3.setInitialBalance(1000.0);
        // s3 trades are opposite of s1, so negatively correlated
        s3.addTrade(LocalDateTime.of(2026, 6, 20, 10, 0), LocalDateTime.of(2026, 6, 20, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, -50.0);
        s3.addTrade(LocalDateTime.of(2026, 6, 21, 10, 0), LocalDateTime.of(2026, 6, 21, 15, 0), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, -60.0);
        
        statsMap.put("p1.csv", s1);
        statsMap.put("p2.csv", s2);
        statsMap.put("p3.csv", s3);
        
        mockHtmlDb.setSubscribers("p1.csv", 100);
        mockHtmlDb.setSubscribers("p2.csv", 80);
        mockHtmlDb.setSubscribers("p3.csv", 50);
        
        PortfolioOptimizer optimizer = new PortfolioOptimizer(statsMap, mockHtmlDb);
        // Greedy selection of size 2 with max correlation 0.3. 
        // p1 (highest score) selected first.
        // p2 is perfectly correlated with p1, so it should be rejected.
        // p3 is negatively correlated, so it should be accepted.
        // Portfolio should contain p1 and p3.
        List<PortfolioOptimizer.Candidate> selected = optimizer.selectOptimalPortfolio(
            2, 0.3, -1, "Greedy", 1, 1, 1, 1, 1, 5);
            
        assertEquals(2, selected.size());
        assertTrue(selected.stream().anyMatch(c -> c.getName().equals("p1.csv")));
        assertTrue(selected.stream().anyMatch(c -> c.getName().equals("p3.csv")));
        assertFalse(selected.stream().anyMatch(c -> c.getName().equals("p2.csv")));
    }
}
