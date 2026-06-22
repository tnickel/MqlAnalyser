package utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BasicDataProviderTest {

    private BasicDataProvider provider;
    private MockFileDataReader mockReader;

    private static class MockFileDataReader extends FileDataReader {
        private final Map<String, Map<String, String>> mockData = new HashMap<>();

        public MockFileDataReader() {
            super("dummyPath");
        }

        public void putData(String fileName, Map<String, String> data) {
            mockData.put(fileName, data);
        }

        @Override
        public Map<String, String> getFileData(String fileName) {
            return mockData.getOrDefault(fileName, new HashMap<>());
        }
    }

    @BeforeEach
    public void setUp() {
        mockReader = new MockFileDataReader();
        provider = new BasicDataProvider(mockReader);
    }

    @Test
    public void testGetBalanceValid() {
        Map<String, String> data = new HashMap<>();
        data.put("Balance", "10.500,45");
        mockReader.putData("test.csv", data);
        assertEquals(10500.45, provider.getBalance("test.csv"));
    }

    @Test
    public void testGetBalanceMissing() {
        assertEquals(0.0, provider.getBalance("missing.csv"));
    }

    @Test
    public void testGetBalanceMalformed() {
        Map<String, String> data = new HashMap<>();
        data.put("Balance", "abc");
        mockReader.putData("test.csv", data);
        assertEquals(0.0, provider.getBalance("test.csv"));
    }

    @Test
    public void testGetEquityDrawdownGraphicValid() {
        Map<String, String> data = new HashMap<>();
        data.put("MaxDDGraphic", "15,67");
        mockReader.putData("test.csv", data);
        assertEquals(15.67, provider.getEquityDrawdownGraphic("test.csv"));
    }

    @Test
    public void testGetEquityDrawdownGraphicMissing() {
        assertEquals(0.0, provider.getEquityDrawdownGraphic("missing.csv"));
    }

    @Test
    public void testGetEquityDrawdownGraphicMalformed() {
        Map<String, String> data = new HashMap<>();
        data.put("MaxDDGraphic", "malformed");
        mockReader.putData("test.csv", data);
        assertEquals(0.0, provider.getEquityDrawdownGraphic("test.csv"));
    }

    @Test
    public void testGet3MPDDValid() {
        Map<String, String> data = new HashMap<>();
        data.put("3MPDD", "4,1234");
        mockReader.putData("test.csv", data);
        assertEquals(4.1234, provider.get3MPDD("test.csv"));
    }

    @Test
    public void testGet3MPDDMissing() {
        assertEquals(0.0, provider.get3MPDD("missing.csv"));
    }

    @Test
    public void testGet3MPDDMalformed() {
        Map<String, String> data = new HashMap<>();
        data.put("3MPDD", "bad");
        mockReader.putData("test.csv", data);
        assertEquals(0.0, provider.get3MPDD("test.csv"));
    }

    @Test
    public void testGetMPDD6Valid() {
        Map<String, String> data = new HashMap<>();
        data.put("6MPDD", "2,345");
        mockReader.putData("test.csv", data);
        assertEquals(2.345, provider.getMPDD("test.csv", 6));
    }

    @Test
    public void testGetMPDD6Missing() {
        assertEquals(0.0, provider.getMPDD("missing.csv", 6));
    }

    @Test
    public void testGetMPDD9Valid() {
        Map<String, String> data = new HashMap<>();
        data.put("9MPDD", "3,456");
        mockReader.putData("test.csv", data);
        assertEquals(3.456, provider.getMPDD("test.csv", 9));
    }

    @Test
    public void testGetMPDD9Missing() {
        assertEquals(0.0, provider.getMPDD("missing.csv", 9));
    }

    @Test
    public void testGetMPDD12Valid() {
        Map<String, String> data = new HashMap<>();
        data.put("12MPDD", "1,234");
        mockReader.putData("test.csv", data);
        assertEquals(1.234, provider.getMPDD("test.csv", 12));
    }

    @Test
    public void testGetMPDD12Missing() {
        assertEquals(0.0, provider.getMPDD("missing.csv", 12));
    }

    @Test
    public void testGetMonthlyProfitPercentagesValid() {
        Map<String, String> data = new HashMap<>();
        data.put("MonthProfitProz", "2026/01=5.5, 2026/02=-2.3");
        mockReader.putData("test.csv", data);
        Map<String, Double> profits = provider.getMonthlyProfitPercentages("test.csv");
        assertEquals(2, profits.size());
        assertEquals(5.5, profits.get("2026/01"));
        assertEquals(-2.3, profits.get("2026/02"));
    }

    @Test
    public void testGetMonthlyProfitPercentagesMissing() {
        Map<String, Double> profits = provider.getMonthlyProfitPercentages("missing.csv");
        assertTrue(profits.isEmpty());
    }

    @Test
    public void testGetMonthlyProfitPercentagesMalformed() {
        Map<String, String> data = new HashMap<>();
        data.put("MonthProfitProz", "bad_format");
        mockReader.putData("test.csv", data);
        Map<String, Double> profits = provider.getMonthlyProfitPercentages("test.csv");
        assertTrue(profits.isEmpty());
    }

    @Test
    public void testGetSubscribersValid() {
        Map<String, String> data = new HashMap<>();
        data.put("Subscribers", "150");
        mockReader.putData("test.csv", data);
        assertEquals(150, provider.getSubscribers("test.csv"));
    }

    @Test
    public void testGetSubscribersMissing() {
        assertEquals(0, provider.getSubscribers("missing.csv"));
    }

    @Test
    public void testGetSubscribersMalformed() {
        Map<String, String> data = new HashMap<>();
        data.put("Subscribers", "abc");
        mockReader.putData("test.csv", data);
        assertEquals(0, provider.getSubscribers("test.csv"));
    }

    @Test
    public void testGetMPDDTooltip3() {
        Map<String, String> data = new HashMap<>();
        data.put("3MPDD", "4,5678");
        mockReader.putData("test.csv", data);
        String tooltip = provider.getMPDDTooltip("test.csv", 3);
        assertTrue(tooltip.contains("3-Monats-MPDD"));
        assertTrue(tooltip.contains("4.5678") || tooltip.contains("4,5678"));
    }

    @Test
    public void testGetEquityDrawdownValid() {
        Map<String, String> data = new HashMap<>();
        data.put("EquityDrawdown", "12,50");
        mockReader.putData("test.csv", data);
        assertEquals(12.50, provider.getEquityDrawdown("test.csv"));
    }

    @Test
    public void testGetMPDDTooltip6() {
        Map<String, String> data = new HashMap<>();
        data.put("6MPDD", "2,3450");
        mockReader.putData("test.csv", data);
        String tooltip = provider.getMPDDTooltip("test.csv", 6);
        assertTrue(tooltip.contains("6-Monats-MPDD"));
        assertTrue(tooltip.contains("2.3450") || tooltip.contains("2,3450"));
    }
}
