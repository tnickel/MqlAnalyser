package utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ScoreConfigTest {

    @Test
    public void testGetInstance_ReturnsNonNull() {
        ScoreConfig config = ScoreConfig.getInstance();
        assertNotNull(config);
    }

    @Test
    public void testDefaultWeights() {
        ScoreConfig config = ScoreConfig.getInstance();
        // Since config might have been modified by previous loads on the system, we test if getters return valid ranges
        assertTrue(config.getWeightProfit() >= 0);
        assertTrue(config.getWeight3Mpdd() >= 0);
        assertTrue(config.getWeightDrawdown() >= 0);
        assertTrue(config.getWeightTrades() >= 0);
        assertTrue(config.getWeightTradeDays() >= 0);
        assertTrue(config.getWeightSubscribers() >= 0);
    }

    @Test
    public void testSetAndGetWeights() {
        ScoreConfig config = ScoreConfig.getInstance();
        
        int origProfit = config.getWeightProfit();
        int orig3Mpdd = config.getWeight3Mpdd();
        
        config.setWeightProfit(15);
        config.setWeight3Mpdd(20);
        
        assertEquals(15, config.getWeightProfit());
        assertEquals(20, config.getWeight3Mpdd());
        
        // Restore original
        config.setWeightProfit(origProfit);
        config.setWeight3Mpdd(orig3Mpdd);
    }

    @Test
    public void testSetWeightDrawdown() {
        ScoreConfig config = ScoreConfig.getInstance();
        int orig = config.getWeightDrawdown();
        config.setWeightDrawdown(12);
        assertEquals(12, config.getWeightDrawdown());
        config.setWeightDrawdown(orig);
    }

    @Test
    public void testSetWeightTrades() {
        ScoreConfig config = ScoreConfig.getInstance();
        int orig = config.getWeightTrades();
        config.setWeightTrades(8);
        assertEquals(8, config.getWeightTrades());
        config.setWeightTrades(orig);
    }

    @Test
    public void testSetWeightTradeDays() {
        ScoreConfig config = ScoreConfig.getInstance();
        int orig = config.getWeightTradeDays();
        config.setWeightTradeDays(9);
        assertEquals(9, config.getWeightTradeDays());
        config.setWeightTradeDays(orig);
    }

    @Test
    public void testSetWeightSubscribers() {
        ScoreConfig config = ScoreConfig.getInstance();
        int orig = config.getWeightSubscribers();
        config.setWeightSubscribers(10);
        assertEquals(10, config.getWeightSubscribers());
        config.setWeightSubscribers(orig);
    }
}
