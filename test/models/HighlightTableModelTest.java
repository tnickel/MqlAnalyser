package models;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import db.HistoryDatabaseManager;

public class HighlightTableModelTest {

    static {
        System.setProperty("mql.test", "true");
        System.setProperty("java.awt.headless", "true");
    }

    @TempDir
    Path tempDir;

    private HighlightTableModel model;
    private Method normalizeMethod;
    private Method calculateTrendMethod;

    @BeforeEach
    public void setUp() throws Exception {
        String rootPath = tempDir.toAbsolutePath().toString();
        // Initialize HistoryDatabaseManager singleton first
        HistoryDatabaseManager.getInstance(rootPath);
        model = new HighlightTableModel(rootPath);

        // Access private helper methods via reflection for direct unit testing
        normalizeMethod = HighlightTableModel.class.getDeclaredMethod("normalize", double.class, double.class, double.class, boolean.class);
        normalizeMethod.setAccessible(true);

        calculateTrendMethod = HighlightTableModel.class.getDeclaredMethod("calculateTrend", Map.class, String.class);
        calculateTrendMethod.setAccessible(true);
    }

    private double invokeNormalize(double val, double min, double max, boolean higherIsBetter) throws Exception {
        return (double) normalizeMethod.invoke(model, val, min, max, higherIsBetter);
    }

    private double invokeCalculateTrend(Map<String, Double> monthlyProfits, String currentMonth) throws Exception {
        return (double) calculateTrendMethod.invoke(model, monthlyProfits, currentMonth);
    }

    @Test
    public void testColumnNamesAndCount() {
        assertEquals(33, model.getColumnCount());
        String[] expectedColumnNames = {
            "No.", "Signal Provider", "Score", "Balance", "Subscribers", "3MPDD", "6MPDD", "9MPDD", "12MPDD", 
            "3MProfProz", "Trades", "Trade Days", "Days", "Win Rate %", "Total Profit", 
            "Avg Profit/Trade", "Max Drawdown %", "Equity Drawdown %", "Profit Factor", 
            "MaxTrades", "MaxLots", "Max Duration (h)", "Risiko", "Risk Score", "S/L", "T/P", 
            "Start Date", "End Date", "Stabilitaet", "Steigung", "MaxDDGraphic", "EquityDrawdown3M%", "M/G"
        };
        for (int i = 0; i < expectedColumnNames.length; i++) {
            assertEquals(expectedColumnNames[i], model.getColumnName(i), "Column name mismatch at index " + i);
        }
    }

    @TestFactory
    public Stream<DynamicTest> testColumnClasses() {
        // Generate dynamic tests for all 33 columns to verify their assigned classes
        Map<Integer, Class<?>> expectedClasses = new HashMap<>();
        // Integers
        int[] integerCols = {0, 4, 10, 11, 12, 19, 21, 23, 24, 25};
        for (int col : integerCols) {
            expectedClasses.put(col, Integer.class);
        }
        // Doubles
        int[] doubleCols = {2, 3, 5, 6, 7, 8, 9, 13, 14, 15, 16, 17, 18, 20, 28, 29, 30, 31};
        for (int col : doubleCols) {
            expectedClasses.put(col, Double.class);
        }

        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            final int colIndex = i;
            final Class<?> expectedClass = expectedClasses.getOrDefault(colIndex, String.class);
            tests.add(DynamicTest.dynamicTest("Column Class Test - Index " + colIndex + " (" + model.getColumnName(colIndex) + ")", () -> {
                assertEquals(expectedClass, model.getColumnClass(colIndex), "Incorrect class for column index: " + colIndex);
            }));
        }
        return tests.stream();
    }

    @ParameterizedTest
    @CsvSource({
        "10, 5",
        "0, 0",
        "5, 20"
    })
    public void testEditableCells(int row, int col) {
        assertFalse(model.isCellEditable(row, col));
    }

    @Test
    public void testCalculateMPDD() {
        // Normal case
        assertEquals(2.0, model.calculateMPDD(10.0, 5.0), 0.001);
        // Zero drawdown (division by zero prevention)
        assertEquals(0.0, model.calculateMPDD(10.0, 0.0), 0.001);
        // Negative profit
        assertEquals(-1.5, model.calculateMPDD(-7.5, 5.0), 0.001);
    }

    @TestFactory
    public Collection<DynamicTest> testNormalizationLogic() {
        List<DynamicTest> tests = new ArrayList<>();

        // Generate 200 variations of normalization calculations to test clamping, inverted logic, and boundaries
        double[] testValues = {-150.0, -10.0, 0.0, 1.5, 5.0, 8.5, 10.0, 25.0, 100.0, 1000.0};
        double[] mins = {-50.0, 0.0, 5.0, 10.0, 10.0};
        double[] maxs = {-10.0, 10.0, 5.0, 20.0, 10.0}; // Note: min > max, min == max cases
        boolean[] preferences = {true, false};

        int count = 1;
        for (double val : testValues) {
            for (double min : mins) {
                for (double max : maxs) {
                    for (boolean higherIsBetter : preferences) {
                        final double fVal = val;
                        final double fMin = min;
                        final double fMax = max;
                        final boolean fBetter = higherIsBetter;
                        
                        tests.add(DynamicTest.dynamicTest(
                            String.format("NormTest #%d: val=%.1f, min=%.1f, max=%.1f, highBetter=%b", count++, fVal, fMin, fMax, fBetter),
                            () -> {
                                double norm = invokeNormalize(fVal, fMin, fMax, fBetter);
                                assertTrue(norm >= 0.0 && norm <= 100.0, "Normalized value must be clamped between 0 and 100");
                                
                                if (fMax <= fMin) {
                                    assertEquals(50.0, norm, 0.001, "If max <= min, normalized value must be 50.0");
                                } else {
                                    if (fVal >= fMax) {
                                        assertEquals(fBetter ? 100.0 : 0.0, norm, 0.001);
                                    } else if (fVal <= fMin) {
                                        assertEquals(fBetter ? 0.0 : 100.0, norm, 0.001);
                                    }
                                }
                            }
                        ));
                    }
                }
            }
        }
        return tests;
    }

    @TestFactory
    public Collection<DynamicTest> testTrendStabilitySlopeCalculation() {
        List<DynamicTest> tests = new ArrayList<>();
        int count = 1;

        // Base cases for trend calculations
        // Case 1: Empty map
        tests.add(DynamicTest.dynamicTest("Trend empty map", () -> {
            assertEquals(0.0, invokeCalculateTrend(new HashMap<>(), "2026/05"));
        }));

        // Case 2: Null inputs
        tests.add(DynamicTest.dynamicTest("Trend null month", () -> {
            Map<String, Double> map = new HashMap<>();
            map.put("2026/01", 5.0);
            assertEquals(0.0, invokeCalculateTrend(map, null));
        }));

        // Case 3: Missing month from map
        tests.add(DynamicTest.dynamicTest("Trend missing current month", () -> {
            Map<String, Double> map = new HashMap<>();
            map.put("2026/01", 5.0);
            assertEquals(0.0, invokeCalculateTrend(map, "2026/02"));
        }));

        // Case 4: 1 previous month available in history (monthsAvailable == 1, Case 3 runs)
        double[] singleMonthProfits = {-10.0, -0.5, 0.0, 5.5, 20.0};
        for (double profit : singleMonthProfits) {
            final double p = profit;
            tests.add(DynamicTest.dynamicTest("Trend 1 previous month: " + p, () -> {
                Map<String, Double> map = new LinkedHashMap<>();
                map.put("2025/12", p); // previous month
                map.put("2026/01", 0.0); // current month
                double expected = p * 0.2;
                assertEquals(expected, invokeCalculateTrend(map, "2026/01"), 0.001);
            }));
        }

        // Case 5: 2 previous months available in history (monthsAvailable == 2, Case 2 runs)
        double[][] twoMonthProfits = {
            {10.0, 15.0}, // Positive slope
            {15.0, 10.0}, // Negative slope
            {5.0, 5.0},   // Zero slope
            {-5.0, 5.0},  // Crossing zero
        };
        for (double[] profits : twoMonthProfits) {
            final double p1 = profits[0];
            final double p2 = profits[1];
            tests.add(DynamicTest.dynamicTest(String.format("Trend 2 previous months: %.1f -> %.1f", p1, p2), () -> {
                Map<String, Double> map = new LinkedHashMap<>();
                map.put("2025/11", p1);
                map.put("2025/12", p2);
                map.put("2026/01", 0.0); // current month
                
                double slope = p2 - p1;
                double expected = slope * 0.8;
                assertEquals(expected, invokeCalculateTrend(map, "2026/01"), 0.001);
            }));
        }

        // Case 6: 3 or more previous months available in history (monthsAvailable >= 3, Case 1 runs)
        double[] vals1 = {-5.0, 0.0, 10.0};
        double[] vals2 = {-2.0, 0.0, 15.0};
        double[] vals3 = {-10.0, 0.0, 20.0};
        
        for (double v1 : vals1) {
            for (double v2 : vals2) {
                for (double v3 : vals3) {
                    final double p1 = v1;
                    final double p2 = v2;
                    final double p3 = v3;
                    tests.add(DynamicTest.dynamicTest(
                        String.format("Trend 3 previous months #%d: %.1f, %.1f, %.1f", count++, p1, p2, p3),
                        () -> {
                            Map<String, Double> map = new LinkedHashMap<>();
                            map.put("2025/10", p1);
                            map.put("2025/11", p2);
                            map.put("2025/12", p3);
                            map.put("2026/01", 0.0); // current month
                            
                            double slope1 = p2 - p1;
                            double slope2 = p3 - p2;
                            double expected;
                            if (slope1 > 0 && slope2 > 0) {
                                expected = (slope1 + slope2) / 2.0;
                            } else if (slope1 > 0 || slope2 > 0) {
                                expected = Math.max(slope1, slope2) / 4.0;
                            } else {
                                expected = (slope1 + slope2) / 2.0;
                            }
                            assertEquals(expected, invokeCalculateTrend(map, "2026/01"), 0.001);
                        }
                    ));
                }
            }
        }

        return tests;
    }
}
