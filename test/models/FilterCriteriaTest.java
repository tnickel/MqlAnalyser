package models;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import data.ProviderStats;
import data.Trade;
import models.FilterCriteria.FilterRange;

public class FilterCriteriaTest {

    static {
        System.setProperty("mql.test", "true");
    }

    private static final String SAVE_FILE = "filter_criteria.ser";

    @BeforeEach
    @AfterEach
    public void cleanUp() {
        File file = new File(SAVE_FILE);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testEmptyFilterCriteriaMatchesAll() {
        FilterCriteria criteria = new FilterCriteria();
        ProviderStats stats = new ProviderStats();
        stats.setSignalProviderInfo("TestProvider", "");
        Object[] rowData = new Object[]{"1", "TestProvider", 5.0, 1000.0};
        assertTrue(criteria.matches(stats, rowData));
    }

    @Test
    public void testFilterRangeMinOnly() {
        FilterRange range = new FilterRange(10.0, null);
        assertTrue(range.matches(15.0));
        assertTrue(range.matches(10.0));
        assertFalse(range.matches(5.0));
    }

    @Test
    public void testFilterRangeMaxOnly() {
        FilterRange range = new FilterRange(null, 20.0);
        assertTrue(range.matches(15.0));
        assertTrue(range.matches(20.0));
        assertFalse(range.matches(25.0));
    }

    @Test
    public void testFilterRangeMinAndMax() {
        FilterRange range = new FilterRange(10.0, 20.0);
        assertTrue(range.matches(15.0));
        assertTrue(range.matches(10.0));
        assertTrue(range.matches(20.0));
        assertFalse(range.matches(5.0));
        assertFalse(range.matches(25.0));
    }

    @Test
    public void testFilterRangeStringRepresentation() {
        FilterRange range = new FilterRange(10.0, 20.0);
        assertEquals(10.0, range.getMin());
        assertEquals(20.0, range.getMax());
        assertNull(range.getTextFilter());
    }

    @Test
    public void testFilterRangeTextFilter() {
        FilterRange range = new FilterRange("EURUSD");
        assertTrue(range.matches("EURUSD"));
        assertTrue(range.matches("eurusd"));
        assertTrue(range.matches("EURUSD.ru"));
        assertFalse(range.matches("GBPUSD"));
        assertNull(range.getMin());
        assertNull(range.getMax());
        assertEquals("EURUSD", range.getTextFilter());
    }

    @Test
    public void testFilterRangeMatchesNull() {
        FilterRange range = new FilterRange(10.0, 20.0);
        assertFalse(range.matches(null));
        
        FilterRange textRange = new FilterRange("test");
        assertFalse(textRange.matches(null));
    }

    @Test
    public void testFilterRangeInvalidNumberFormatMatchesFalse() {
        FilterRange range = new FilterRange(10.0, 20.0);
        assertFalse(range.matches("not a number"));
    }

    @Test
    public void testAddFilterAndGetFilters() {
        FilterCriteria criteria = new FilterCriteria();
        FilterRange range1 = new FilterRange(10.0, null);
        FilterRange range2 = new FilterRange(null, 50.0);
        
        criteria.addFilter(3, range1);
        criteria.addFilter(4, range2);
        
        Map<Integer, FilterRange> filters = criteria.getFilters();
        assertEquals(2, filters.size());
        assertEquals(range1, filters.get(3));
        assertEquals(range2, filters.get(4));
    }

    @Test
    public void testAddFilterIgnoresMaxDrawdownColumn16() {
        FilterCriteria criteria = new FilterCriteria();
        FilterRange range = new FilterRange(0.0, 15.0);
        criteria.addFilter(16, range);
        
        assertTrue(criteria.getFilters().isEmpty());
    }

    @Test
    public void testSetFiltersIgnoresMaxDrawdownColumn16() {
        FilterCriteria criteria = new FilterCriteria();
        Map<Integer, FilterRange> filters = new HashMap<>();
        filters.put(3, new FilterRange(10.0, null));
        filters.put(16, new FilterRange(0.0, 15.0));
        
        criteria.setFilters(filters);
        
        assertEquals(1, criteria.getFilters().size());
        assertNull(criteria.getFilters().get(16));
        assertNotNull(criteria.getFilters().get(3));
    }

    @Test
    public void testMatchesCurrencyPairsSingleMatch() {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setCurrencyPairsFilter("EURUSD");
        
        ProviderStats stats = new ProviderStats();
        stats.setSignalProviderInfo("TestProvider", "");
        stats.addTrade(LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Buy", "EURUSD", 1.0, 1.1000, 1.1100, 0.0, 0.0, 0.0, 0.0, 100.0);
        
        Object[] rowData = new Object[]{"1", "TestProvider"};
        assertTrue(criteria.matches(stats, rowData));
    }

    @Test
    public void testMatchesCurrencyPairsSingleNoMatch() {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setCurrencyPairsFilter("GBPUSD");
        
        ProviderStats stats = new ProviderStats();
        stats.setSignalProviderInfo("TestProvider", "");
        stats.addTrade(LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Buy", "EURUSD", 1.0, 1.1000, 1.1100, 0.0, 0.0, 0.0, 0.0, 100.0);
        
        Object[] rowData = new Object[]{"1", "TestProvider"};
        assertFalse(criteria.matches(stats, rowData));
    }

    @Test
    public void testMatchesCurrencyPairsMultipleAllMatch() {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setCurrencyPairsFilter("EURUSD, GBPUSD");
        
        ProviderStats stats = new ProviderStats();
        stats.setSignalProviderInfo("TestProvider", "");
        stats.addTrade(LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Buy", "EURUSD", 1.0, 1.1000, 1.1100, 0.0, 0.0, 0.0, 0.0, 100.0);
        stats.addTrade(LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Sell", "GBPUSD.X", 1.0, 1.3000, 1.2900, 0.0, 0.0, 0.0, 0.0, 150.0);
        
        Object[] rowData = new Object[]{"1", "TestProvider"};
        assertTrue(criteria.matches(stats, rowData));
    }

    @Test
    public void testMatchesCurrencyPairsMultipleOneMissing() {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setCurrencyPairsFilter("EURUSD, USDJPY");
        
        ProviderStats stats = new ProviderStats();
        stats.setSignalProviderInfo("TestProvider", "");
        stats.addTrade(LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Buy", "EURUSD", 1.0, 1.1000, 1.1100, 0.0, 0.0, 0.0, 0.0, 100.0);
        stats.addTrade(LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Sell", "GBPUSD", 1.0, 1.3000, 1.2900, 0.0, 0.0, 0.0, 0.0, 150.0);
        
        Object[] rowData = new Object[]{"1", "TestProvider"};
        assertFalse(criteria.matches(stats, rowData));
    }

    @Test
    public void testSpecialRiskCategoryFiltering() {
        FilterCriteria criteria = new FilterCriteria();
        // Column 22 is "Risiko" in HighlightTableModel
        criteria.addFilter(22, new FilterRange(1.0, 3.0)); // Min risk 1, max risk 3
        
        ProviderStats stats = new ProviderStats();
        stats.setSignalProviderInfo("TestProvider", "");
        // Let's set riskCategory via stats. Make sure stats contains the mocked or set value
        // The stats class risk Category comes from RiskAnalysisServ or is calculated.
        // Let's verify stats.getRiskCategory()
        stats.setRiskCategory(2); // Medium risk
        
        Object[] rowData = new Object[23];
        rowData[22] = "Low/Medium"; // String representation in rowData
        
        assertTrue(criteria.matches(stats, rowData));
        
        stats.setRiskCategory(4); // High risk
        assertFalse(criteria.matches(stats, rowData));
    }

    @Test
    public void testSaveAndLoadFiltersRoundtrip() {
        FilterCriteria criteria = new FilterCriteria();
        criteria.addFilter(3, new FilterRange(100.0, 500.0));
        criteria.addFilter(10, new FilterRange(50.0, null));
        criteria.setCurrencyPairsFilter("EURUSD,GBPUSD");
        
        criteria.saveFilters();
        
        FilterCriteria loaded = new FilterCriteria();
        loaded.loadFilters();
        
        assertEquals("EURUSD,GBPUSD", loaded.getCurrencyPairsFilter());
        
        Map<Integer, FilterRange> filters = loaded.getFilters();
        assertEquals(2, filters.size());
        assertEquals(100.0, filters.get(3).getMin());
        assertEquals(500.0, filters.get(3).getMax());
        assertEquals(50.0, filters.get(10).getMin());
        assertNull(filters.get(10).getMax());
    }

    @Test
    public void testLoadFiltersWhenFileDoesNotExist() {
        FilterCriteria criteria = new FilterCriteria();
        // Delete save file if exists
        cleanUp();
        
        criteria.loadFilters();
        assertTrue(criteria.getFilters().isEmpty());
        assertEquals("", criteria.getCurrencyPairsFilter());
    }

    @Test
    public void testMatchesCurrencyPairsEmptyFilterMatchesTrue() {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setCurrencyPairsFilter("");
        
        ProviderStats stats = new ProviderStats();
        stats.setSignalProviderInfo("TestProvider", "");
        Object[] rowData = new Object[]{"1", "TestProvider"};
        assertTrue(criteria.matches(stats, rowData));
    }

    @Test
    public void testMatchesCurrencyPairsNullFilterMatchesTrue() {
        FilterCriteria criteria = new FilterCriteria();
        criteria.setCurrencyPairsFilter(null);
        
        ProviderStats stats = new ProviderStats();
        stats.setSignalProviderInfo("TestProvider", "");
        Object[] rowData = new Object[]{"1", "TestProvider"};
        assertTrue(criteria.matches(stats, rowData));
    }

    @TestFactory
    public Collection<DynamicTest> testFilterCriteriaIntensiveSuite() {
        List<DynamicTest> tests = new ArrayList<>();
        int count = 1;

        // --- PART 1: Numeric Range Permutations (20+ test cases) ---
        double[] testVals = {-50.0, 0.0, 15.3, 100.0};
        Double[] mins = {-100.0, 0.0, 10.0, null};
        Double[] maxs = {-10.0, 50.0, 200.0, null};

        for (double val : testVals) {
            for (Double min : mins) {
                for (Double max : maxs) {
                    final double fVal = val;
                    final Double fMin = min;
                    final Double fMax = max;
                    
                    tests.add(DynamicTest.dynamicTest(
                        String.format("FilterRangeMatch #%d: val=%.1f, min=%s, max=%s", count++, fVal, fMin, fMax),
                        () -> {
                            FilterRange range = new FilterRange(fMin, fMax);
                            boolean expected = true;
                            if (fMin != null && fVal < fMin) expected = false;
                            if (fMax != null && fVal > fMax) expected = false;
                            assertEquals(expected, range.matches(fVal));
                            assertEquals(expected, range.matches(String.valueOf(fVal))); // test parsing
                        }
                    ));
                }
            }
        }

        // --- PART 2: Currency Pair Matching Permutations (20+ test cases) ---
        String[][] currencyTestMatrix = {
            {"EURUSD", "EURUSD", "true"},
            {"eurusd", "EURUSD", "true"},
            {"EURUSD", "EURUSD.X", "true"},
            {"EURUSD", "EURUSD.ru", "true"},
            {"EURUSD", "GBPUSD", "false"},
            {"EURUSD,GBPUSD", "EURUSD,GBPUSD", "true"},
            {"EURUSD , GBPUSD", "EURUSD.ru,GBPUSD.X", "true"},
            {"EURUSD, GBPUSD", "EURUSD,USDJPY", "false"},
            {"", "EURUSD", "true"},
            {"   ", "EURUSD", "true"},
            {"EURUSD", "EURUSD,GBPUSD", "true"},
            {"GBPUSD", "EURUSD,GBPUSD", "true"},
            {"EURUSD.ru", "EURUSD", "false"},
            {"EUR", "EURUSD", "true"},
            {"EUR", "EURUSD,GBPUSD", "true"},
            {"EUR,GBP", "EURUSD,GBPUSD", "true"},
            {"EUR,JPY", "EURUSD,GBPUSD", "false"},
            {"eurusd, gbpusd", "EURUSD,GBPUSD,USDJPY", "true"},
            {"EURUSD, , GBPUSD", "EURUSD,GBPUSD", "true"},
            {"EURUSD", "", "false"}
        };

        for (String[] testCase : currencyTestMatrix) {
            final String filterStr = testCase[0];
            final String providerSyms = testCase[1];
            final boolean expected = Boolean.parseBoolean(testCase[2]);
            
            tests.add(DynamicTest.dynamicTest(
                String.format("CurrencyMatch #%d: filter='%s', symbols='%s'", count++, filterStr, providerSyms),
                () -> {
                    FilterCriteria criteria = new FilterCriteria();
                    criteria.setCurrencyPairsFilter(filterStr);
                    
                    ProviderStats stats = new ProviderStats();
                    stats.setSignalProviderInfo("TestProvider", "");
                    if (!providerSyms.trim().isEmpty()) {
                        for (String sym : providerSyms.split(",")) {
                            stats.addTrade(LocalDateTime.now(), LocalDateTime.now().plusHours(1), "Buy", sym.trim(), 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 100.0);
                        }
                    }
                    
                    assertEquals(expected, criteria.matches(stats, new Object[]{}));
                }
            ));
        }

        // --- PART 3: Risk Category (Column 22) Matching (10+ test cases) ---
        int[] riskStatsValues = {0, 1, 3, 5, 8, 10};
        Double[] riskMinFilters = {0.0, 2.0, 5.0, null};
        Double[] riskMaxFilters = {4.0, 8.0, 10.0, null};

        for (int rVal : riskStatsValues) {
            for (Double min : riskMinFilters) {
                for (Double max : riskMaxFilters) {
                    final int fRisk = rVal;
                    final Double fMin = min;
                    final Double fMax = max;
                    
                    tests.add(DynamicTest.dynamicTest(
                        String.format("RiskColumnMatch #%d: riskVal=%d, min=%s, max=%s", count++, fRisk, fMin, fMax),
                        () -> {
                            FilterCriteria criteria = new FilterCriteria();
                            criteria.addFilter(22, new FilterRange(fMin, fMax));
                            
                            ProviderStats stats = new ProviderStats();
                            stats.setSignalProviderInfo("RiskProvider", "");
                            stats.setRiskCategory(fRisk);
                            
                            Object[] rowData = new Object[23];
                            rowData[22] = "Some String"; 
                            
                            boolean expected = true;
                            if (fMin != null && fRisk < fMin) expected = false;
                            if (fMax != null && fRisk > fMax) expected = false;
                            
                            assertEquals(expected, criteria.matches(stats, rowData));
                        }
                    ));
                }
            }
        }

        // --- PART 4: Ignore Max Drawdown (Column 16) Constraints ---
        tests.add(DynamicTest.dynamicTest("Ignore Column 16 on addFilter", () -> {
            FilterCriteria criteria = new FilterCriteria();
            criteria.addFilter(16, new FilterRange(0.0, 10.0));
            assertTrue(criteria.getFilters().isEmpty());
        }));
        
        tests.add(DynamicTest.dynamicTest("Ignore Column 16 on setFilters", () -> {
            FilterCriteria criteria = new FilterCriteria();
            Map<Integer, FilterRange> map = new HashMap<>();
            map.put(16, new FilterRange(0.0, 10.0));
            map.put(3, new FilterRange(5.0, null));
            criteria.setFilters(map);
            assertEquals(1, criteria.getFilters().size());
            assertNull(criteria.getFilters().get(16));
        }));

        tests.add(DynamicTest.dynamicTest("Ignore Column 16 on save and load roundtrip", () -> {
            FilterCriteria criteria = new FilterCriteria();
            criteria.addFilter(3, new FilterRange(1.0, 2.0));
            
            Field field = FilterCriteria.class.getDeclaredField("columnFilters");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Integer, FilterRange> columnFilters = (Map<Integer, FilterRange>) field.get(criteria);
            columnFilters.put(16, new FilterRange(0.0, 10.0)); 
            
            criteria.saveFilters();
            
            FilterCriteria loaded = new FilterCriteria();
            loaded.loadFilters();
            
            assertFalse(loaded.getFilters().containsKey(16));
            assertTrue(loaded.getFilters().containsKey(3));
        }));

        return tests;
    }
}
