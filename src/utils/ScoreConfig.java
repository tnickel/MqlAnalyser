package utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Singleton configuration for managing strategy evaluation weights.
 */
public class ScoreConfig {
    private static final Logger LOGGER = Logger.getLogger(ScoreConfig.class.getName());
    private static final String CONFIG_FILENAME = "score_config.properties";
    private final Properties properties = new Properties();

    private int weightProfit = 8;
    private int weight3Mpdd = 10;
    private int weightDrawdown = 7;
    private int weightTrades = 5;
    private int weightTradeDays = 5;
    private int weightSubscribers = 4;

    private static ScoreConfig instance;

    private ScoreConfig() {
        load();
    }

    public static synchronized ScoreConfig getInstance() {
        if (instance == null) {
            instance = new ScoreConfig();
        }
        return instance;
    }

    public void load() {
        File file = new File(CONFIG_FILENAME);
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                properties.load(in);
                weightProfit = Integer.parseInt(properties.getProperty("weightProfit", "8"));
                weight3Mpdd = Integer.parseInt(properties.getProperty("weight3Mpdd", "10"));
                weightDrawdown = Integer.parseInt(properties.getProperty("weightDrawdown", "7"));
                weightTrades = Integer.parseInt(properties.getProperty("weightTrades", "5"));
                weightTradeDays = Integer.parseInt(properties.getProperty("weightTradeDays", "5"));
                weightSubscribers = Integer.parseInt(properties.getProperty("weightSubscribers", "4"));
                LOGGER.info("Successfully loaded scoring weights from " + CONFIG_FILENAME);
            } catch (Exception e) {
                LOGGER.warning("Could not load score configuration: " + e.getMessage());
            }
        }
    }

    public void save() {
        properties.setProperty("weightProfit", String.valueOf(weightProfit));
        properties.setProperty("weight3Mpdd", String.valueOf(weight3Mpdd));
        properties.setProperty("weightDrawdown", String.valueOf(weightDrawdown));
        properties.setProperty("weightTrades", String.valueOf(weightTrades));
        properties.setProperty("weightTradeDays", String.valueOf(weightTradeDays));
        properties.setProperty("weightSubscribers", String.valueOf(weightSubscribers));

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILENAME)) {
            properties.store(out, "MqlAnalyser Strategy Scoring Weights");
            LOGGER.info("Successfully saved scoring weights to " + CONFIG_FILENAME);
        } catch (IOException e) {
            LOGGER.severe("Could not save score configuration: " + e.getMessage());
        }
    }

    public int getWeightProfit() {
        return weightProfit;
    }

    public void setWeightProfit(int weightProfit) {
        this.weightProfit = weightProfit;
    }

    public int getWeight3Mpdd() {
        return weight3Mpdd;
    }

    public void setWeight3Mpdd(int weight3Mpdd) {
        this.weight3Mpdd = weight3Mpdd;
    }

    public int getWeightDrawdown() {
        return weightDrawdown;
    }

    public void setWeightDrawdown(int weightDrawdown) {
        this.weightDrawdown = weightDrawdown;
    }

    public int getWeightTrades() {
        return weightTrades;
    }

    public void setWeightTrades(int weightTrades) {
        this.weightTrades = weightTrades;
    }

    public int getWeightTradeDays() {
        return weightTradeDays;
    }

    public void setWeightTradeDays(int weightTradeDays) {
        this.weightTradeDays = weightTradeDays;
    }

    public int getWeightSubscribers() {
        return weightSubscribers;
    }

    public void setWeightSubscribers(int weightSubscribers) {
        this.weightSubscribers = weightSubscribers;
    }
}
