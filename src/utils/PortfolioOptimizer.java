package utils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import data.ProviderStats;
import data.Trade;

public class PortfolioOptimizer {
    private static final Logger LOGGER = Logger.getLogger(PortfolioOptimizer.class.getName());
    
    private final Map<String, ProviderStats> providerStats;
    private final HtmlDatabase htmlDatabase;
    private List<Candidate> candidates;
    private LocalDate globalStartDate;
    private LocalDate globalEndDate;
    private int totalDays;
    private LocalDate activeStartDate;
    private int activeStartIndex;
    
    public static class Candidate {
        private final String name;
        private final ProviderStats stats;
        private double score;
        private double[] dailyReturns; // indexed from 0 to totalDays - 1
        private double scaleFactor;
        
        // Metrics that can be recalculated for different timeframes
        private double tradesCount;
        private double tradeDays;
        private double mpdd3;
        private double equityDrawdown;
        private final double subscribers;
        private double avgMonthlyProfit;
        
        public Candidate(String name, ProviderStats stats, double tradesCount, double tradeDays,
                         double mpdd3, double equityDrawdown, double subscribers, double avgMonthlyProfit, double scaleFactor) {
            this.name = name;
            this.stats = stats;
            this.tradesCount = tradesCount;
            this.tradeDays = tradeDays;
            this.mpdd3 = mpdd3;
            this.equityDrawdown = equityDrawdown;
            this.subscribers = subscribers;
            this.avgMonthlyProfit = avgMonthlyProfit;
            this.scaleFactor = scaleFactor;
        }
        
        public String getName() { return name; }
        public ProviderStats getStats() { return stats; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public double[] getDailyReturns() { return dailyReturns; }
        public void setDailyReturns(double[] dailyReturns) { this.dailyReturns = dailyReturns; }
        public double getScaleFactor() { return scaleFactor; }
        public void setScaleFactor(double scaleFactor) { this.scaleFactor = scaleFactor; }
        
        public double getTradesCount() { return tradesCount; }
        public double getTradeDays() { return tradeDays; }
        public double getMpdd3() { return mpdd3; }
        public double getEquityDrawdown() { return equityDrawdown; }
        public double getSubscribers() { return subscribers; }
        public double getAvgMonthlyProfit() { return avgMonthlyProfit; }
    }
    
    public PortfolioOptimizer(Map<String, ProviderStats> providerStats, HtmlDatabase htmlDatabase) {
        this.providerStats = providerStats;
        this.htmlDatabase = htmlDatabase;
        initializeCandidates();
    }
    
    private void initializeCandidates() {
        candidates = new ArrayList<>();
        if (providerStats == null || providerStats.isEmpty()) {
            return;
        }
        
        // Determine global start and end date across all providers
        LocalDate minDate = LocalDate.MAX;
        LocalDate maxDate = LocalDate.MIN;
        
        for (Map.Entry<String, ProviderStats> entry : providerStats.entrySet()) {
            ProviderStats stats = entry.getValue();
            if (stats == null || stats.getTrades() == null || stats.getTrades().isEmpty()) {
                continue;
            }
            LocalDate start = stats.getStartDate();
            LocalDate end = stats.getEndDate();
            if (start.isBefore(minDate)) minDate = start;
            if (end.isAfter(maxDate)) maxDate = end;
        }
        
        // Handle empty trade case
        if (minDate == LocalDate.MAX) {
            minDate = LocalDate.now().minusMonths(3);
            maxDate = LocalDate.now();
        }
        
        this.globalStartDate = minDate;
        this.globalEndDate = maxDate;
        this.totalDays = (int) ChronoUnit.DAYS.between(globalStartDate, globalEndDate) + 1;
        
        LOGGER.info(String.format("Global time alignment: %s to %s (%d days)", globalStartDate, globalEndDate, totalDays));
        
        // Create candidates and gather metrics for normalization
        for (Map.Entry<String, ProviderStats> entry : providerStats.entrySet()) {
            String name = entry.getKey();
            ProviderStats stats = entry.getValue();
            if (stats == null || stats.getTrades() == null || stats.getTrades().isEmpty()) {
                continue;
            }
            
            double equityDrawdown = htmlDatabase.getEquityDrawdown(name);
            int subscribers = htmlDatabase.getSubscribers(name);
            double threeMonthProfit = htmlDatabase.getAverageMonthlyProfit(name, 3);
            double mpdd3 = equityDrawdown == 0.0 ? 0.0 : threeMonthProfit / equityDrawdown;
            
            double trades = stats.getTrades().size();
            double tradeDays = stats.getTradeDays();
            
            // Calculate average monthly profit % over entire lifetime
            Map<String, Double> monthlyProfits = htmlDatabase.getMonthlyProfitPercentages(name);
            double avgMonthlyProfit = 0.0;
            if (monthlyProfits != null && !monthlyProfits.isEmpty()) {
                double sum = 0.0;
                for (double val : monthlyProfits.values()) {
                    sum += val;
                }
                avgMonthlyProfit = sum / monthlyProfits.size();
            }
            
            double initialBalance = stats.getInitialBalance();
            double scaleFactor = 10000.0 / (initialBalance > 0 ? initialBalance : 1000.0);
            
            Candidate candidate = new Candidate(name, stats, trades, tradeDays, mpdd3, equityDrawdown, subscribers, avgMonthlyProfit, scaleFactor);
            candidates.add(candidate);
            
            // Calculate daily returns (unscaled!)
            double[] returns = new double[totalDays];
            for (Trade trade : stats.getTrades()) {
                LocalDate closeDate = trade.getCloseTime().toLocalDate();
                int dayOffset = (int) ChronoUnit.DAYS.between(globalStartDate, closeDate);
                if (dayOffset >= 0 && dayOffset < totalDays) {
                    returns[dayOffset] += trade.getProfit();
                }
            }
            candidate.setDailyReturns(returns);
        }
    }
    
    private double normalize(double val, double min, double max, boolean higherIsBetter) {
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return 0.0;
        }
        if (max <= min) {
            return 50.0;
        }
        double norm;
        if (higherIsBetter) {
            norm = (val - min) / (max - min) * 100.0;
        } else {
            norm = (max - val) / (max - min) * 100.0;
        }
        return Math.max(0.0, Math.min(100.0, norm));
    }
    
    /**
     * Recalculates metrics for candidates based on a specific timeframe limit.
     */
    public void recalculateMetricsForTimeframe(int startIndex, LocalDate startDate) {
        for (Candidate c : candidates) {
            // Calculate unscaled starting balance at startDate
            double unscaledBalance = c.stats.getInitialBalance();
            for (Trade t : c.stats.getTrades()) {
                if (t.getCloseTime().toLocalDate().isBefore(startDate)) {
                    unscaledBalance += t.getProfit();
                }
            }
            
            double initialRef = unscaledBalance;
            if (initialRef <= 0) {
                initialRef = c.stats.getInitialBalance() > 0 ? c.stats.getInitialBalance() : 1000.0;
            }
            double timeframeScaleFactor = 10000.0 / initialRef;
            c.setScaleFactor(timeframeScaleFactor);

            double trades = 0;
            Set<LocalDate> closeDates = new HashSet<>();
            double profitSum = 0;
            
            for (Trade t : c.stats.getTrades()) {
                LocalDate closeDate = t.getCloseTime().toLocalDate();
                if (!closeDate.isBefore(startDate) && !closeDate.isAfter(globalEndDate)) {
                    trades++;
                    closeDates.add(closeDate);
                    profitSum += t.getProfit() * timeframeScaleFactor;
                }
            }
            
            double tradeDays = closeDates.size();
            
            // Drawdown calculation in timeframe starting exactly at 10,000.0 (initialRef * timeframeScaleFactor)
            double currentBalance = initialRef * timeframeScaleFactor;
            double highWaterMark = currentBalance;
            double maxDrawdownPercent = 0.0;
            double[] returns = c.getDailyReturns();
            
            for (int i = startIndex; i < totalDays; i++) {
                currentBalance += returns[i] * timeframeScaleFactor;
                if (currentBalance > highWaterMark) {
                    highWaterMark = currentBalance;
                } else if (highWaterMark > 0) {
                    double drawdownPercent = (highWaterMark - currentBalance) / highWaterMark * 100;
                    maxDrawdownPercent = Math.max(maxDrawdownPercent, drawdownPercent);
                }
            }
            
            int localTotalDays = totalDays - startIndex;
            double months = Math.max(1.0, localTotalDays / 30.4375);
            double avgMonthlyProfit = profitSum / months;
            
            double mpdd3 = maxDrawdownPercent == 0.0 ? 0.0 : avgMonthlyProfit / maxDrawdownPercent;
            
            // Update candidate fields
            c.tradesCount = trades;
            c.tradeDays = tradeDays;
            c.avgMonthlyProfit = avgMonthlyProfit;
            c.equityDrawdown = maxDrawdownPercent;
            c.mpdd3 = mpdd3;
        }
    }
    
    /**
     * Scores all candidates using normalized metrics and weights.
     */
    public void scoreCandidates(double wProfit, double w3Mpdd, double wDrawdown, double wTrades, double wTradeDays, double wSubscribers) {
        if (candidates.isEmpty()) return;
        
        double minTrades = candidates.stream().mapToDouble(Candidate::getTradesCount).min().orElse(0);
        double maxTrades = candidates.stream().mapToDouble(Candidate::getTradesCount).max().orElse(1);
        
        double minTradeDays = candidates.stream().mapToDouble(Candidate::getTradeDays).min().orElse(0);
        double maxTradeDays = candidates.stream().mapToDouble(Candidate::getTradeDays).max().orElse(1);
        
        double minMpdd = candidates.stream().mapToDouble(Candidate::getMpdd3).min().orElse(0);
        double maxMpdd = candidates.stream().mapToDouble(Candidate::getMpdd3).max().orElse(1);
        
        double minEd = candidates.stream().mapToDouble(Candidate::getEquityDrawdown).min().orElse(0);
        double maxEd = candidates.stream().mapToDouble(Candidate::getEquityDrawdown).max().orElse(1);
        
        double maxSubscribers = candidates.stream().mapToDouble(Candidate::getSubscribers).max().orElse(1);
        double maxMonthlyProfit = candidates.stream().mapToDouble(Candidate::getAvgMonthlyProfit).max().orElse(1);
        
        double totalWeight = wProfit + w3Mpdd + wDrawdown + wTrades + wTradeDays + wSubscribers;
        
        for (Candidate c : candidates) {
            double score = 0.0;
            if (totalWeight > 0) {
                double scoreT = normalize(c.tradesCount, minTrades, maxTrades, true);
                double scoreD = normalize(c.tradeDays, minTradeDays, maxTradeDays, true);
                double scoreM = normalize(c.mpdd3, minMpdd, maxMpdd, true);
                double scoreED = normalize(c.equityDrawdown, minEd, maxEd, false);
                
                double scoreS = 0.0;
                if (c.subscribers > 0) {
                    if (c.subscribers == 1) {
                        scoreS = 20.0;
                    } else {
                        scoreS = 50.0 + normalize(c.subscribers, 2, maxSubscribers, true) * 0.5;
                    }
                }
                
                double scoreP = 0.0;
                if (c.avgMonthlyProfit > 0.0) {
                    scoreP = normalize(c.avgMonthlyProfit, 0.0, maxMonthlyProfit, true);
                }
                
                score = (wProfit * scoreP + w3Mpdd * scoreM + wDrawdown * scoreED + wTrades * scoreT + wTradeDays * scoreD + wSubscribers * scoreS) / totalWeight;
            }
            c.setScore(score);
        }
    }
    
    /**
     * Calculates the Pearson correlation between two candidates over a specific range.
     */
    public double calculateCorrelation(Candidate a, Candidate b, int startIndex, int endIndex) {
        double[] rA = a.getDailyReturns();
        double[] rB = b.getDailyReturns();
        
        int length = endIndex - startIndex;
        if (length <= 0) return 0.0;
        
        double sumA = 0;
        double sumB = 0;
        for (int i = startIndex; i < endIndex; i++) {
            sumA += rA[i];
            sumB += rB[i];
        }
        double meanA = sumA / length;
        double meanB = sumB / length;
        
        double num = 0;
        double denA = 0;
        double denB = 0;
        
        for (int i = startIndex; i < endIndex; i++) {
            double diffA = rA[i] - meanA;
            double diffB = rB[i] - meanB;
            num += diffA * diffB;
            denA += diffA * diffA;
            denB += diffB * diffB;
        }
        
        if (denA == 0 || denB == 0) {
            return 0.0; 
        }
        
        return num / Math.sqrt(denA * denB);
    }
    
    public double calculateCorrelation(Candidate a, Candidate b) {
        return calculateCorrelation(a, b, 0, totalDays);
    }
    
    /**
     * Generates an optimal portfolio of the specified size, timeframe, and search algorithm.
     */
    public List<Candidate> selectOptimalPortfolio(int size, double maxCorrelation, int monthsLimit, String algorithm,
                                                 double wProfit, double w3Mpdd, double wDrawdown,
                                                 double wTrades, double wTradeDays, double wSubscribers) {
        // Calculate start date based on timeframe limit
        LocalDate startDate = globalStartDate;
        int startIndex = 0;
        
        if (monthsLimit > 0) {
            startDate = globalEndDate.minusMonths(monthsLimit);
            if (startDate.isBefore(globalStartDate)) {
                startDate = globalStartDate;
            }
            startIndex = (int) ChronoUnit.DAYS.between(globalStartDate, startDate);
        }
        
        this.activeStartDate = startDate;
        this.activeStartIndex = startIndex;
        
        final int localStartIndex = startIndex;
        final LocalDate localStartDate = startDate;
        
        LOGGER.info(String.format("Optimizing portfolio over range: %s to %s (index %d to %d), Algorithm: %s", 
                localStartDate, globalEndDate, localStartIndex, totalDays, algorithm));
        
        // Recalculate metrics for the selected timeframe
        recalculateMetricsForTimeframe(localStartIndex, localStartDate);
        
        // Score candidates with updated metrics
        scoreCandidates(wProfit, w3Mpdd, wDrawdown, wTrades, wTradeDays, wSubscribers);
        
        // Filter out candidates with no trades in selected timeframe
        List<Candidate> validCandidates = candidates.stream()
                .filter(c -> c.getTradesCount() > 0)
                .sorted(Comparator.comparing(Candidate::getScore).reversed())
                .collect(Collectors.toList());
        
        if (validCandidates.isEmpty()) {
            return new ArrayList<>();
        }
        
        if (validCandidates.size() <= size) {
            return validCandidates;
        }
        
        // Compute pairwise correlations over the selected timeframe sub-range
        Map<String, Map<String, Double>> corrMatrix = new HashMap<>();
        for (int i = 0; i < validCandidates.size(); i++) {
            Candidate cI = validCandidates.get(i);
            corrMatrix.putIfAbsent(cI.getName(), new HashMap<>());
            for (int j = i; j < validCandidates.size(); j++) {
                Candidate cJ = validCandidates.get(j);
                double corr = calculateCorrelation(cI, cJ, localStartIndex, totalDays);
                corrMatrix.get(cI.getName()).put(cJ.getName(), corr);
                corrMatrix.putIfAbsent(cJ.getName(), new HashMap<>());
                corrMatrix.get(cJ.getName()).put(cI.getName(), corr);
            }
        }
        
        List<Candidate> selected = new ArrayList<>();
        
        if ("Greedy".equalsIgnoreCase(algorithm)) {
            // GREEDY ALGORITHM
            for (Candidate cand : validCandidates) {
                boolean ok = true;
                for (Candidate sel : selected) {
                    double corr = corrMatrix.get(cand.getName()).get(sel.getName());
                    if (corr > maxCorrelation) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    selected.add(cand);
                    if (selected.size() == size) {
                        break;
                    }
                }
            }
            
            // Fallback: if greedy is too strict and portfolio is not full, fill with next best ranked
            if (selected.size() < size) {
                LOGGER.info("Greedy selection did not find enough assets matching correlation threshold. Filling remainder.");
                for (Candidate cand : validCandidates) {
                    if (!selected.contains(cand)) {
                        selected.add(cand);
                        if (selected.size() == size) {
                            break;
                        }
                    }
                }
            }
            return selected;
            
        } else {
            // COMBINATORIAL ALGORITHM
            // Take top M candidates to prevent search explosion
            int m = Math.min(20, validCandidates.size());
            List<Candidate> topCandidates = validCandidates.subList(0, m);
            
            List<List<Candidate>> combinations = new ArrayList<>();
            generateCombinations(topCandidates, size, 0, new ArrayList<>(), combinations);
            
            List<Candidate> bestPortfolio = null;
            double bestFitness = -Double.MAX_VALUE;
            
            for (List<Candidate> portfolio : combinations) {
                boolean satisfiesCorrelation = true;
                double sumPairwiseCorr = 0;
                int countPairs = 0;
                
                for (int i = 0; i < portfolio.size(); i++) {
                    for (int j = i + 1; j < portfolio.size(); j++) {
                        double corr = corrMatrix.get(portfolio.get(i).getName()).get(portfolio.get(j).getName());
                        sumPairwiseCorr += corr;
                        countPairs++;
                        if (corr > maxCorrelation) {
                            satisfiesCorrelation = false;
                        }
                    }
                }
                
                if (satisfiesCorrelation) {
                    double sumScore = portfolio.stream().mapToDouble(Candidate::getScore).sum();
                    double avgCorr = countPairs > 0 ? sumPairwiseCorr / countPairs : 0.0;
                    double fitness = sumScore - (avgCorr * 10.0); // Penalty for correlation
                    
                    if (fitness > bestFitness) {
                        bestFitness = fitness;
                        bestPortfolio = portfolio;
                    }
                }
            }
            
            // Combinatorial Fallback: If no combination satisfies the constraint, choose the one with minimum average correlation
            if (bestPortfolio == null) {
                LOGGER.info("Combinatorial search did not find a portfolio matching correlation threshold. Selecting minimum average correlation portfolio.");
                double minAvgCorr = Double.MAX_VALUE;
                for (List<Candidate> portfolio : combinations) {
                    double sumPairwiseCorr = 0;
                    int countPairs = 0;
                    for (int i = 0; i < portfolio.size(); i++) {
                        for (int j = i + 1; j < portfolio.size(); j++) {
                            double corr = corrMatrix.get(portfolio.get(i).getName()).get(portfolio.get(j).getName());
                            sumPairwiseCorr += corr;
                            countPairs++;
                        }
                    }
                    double avgCorr = countPairs > 0 ? sumPairwiseCorr / countPairs : 0.0;
                    if (avgCorr < minAvgCorr) {
                        minAvgCorr = avgCorr;
                        bestPortfolio = portfolio;
                    }
                }
            }
            
            if (bestPortfolio == null) {
                return validCandidates.subList(0, size);
            }
            return bestPortfolio;
        }
    }
    
    private void generateCombinations(List<Candidate> source, int k, int start, List<Candidate> current, List<List<Candidate>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < source.size(); i++) {
            current.add(source.get(i));
            generateCombinations(source, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
    
    public List<Candidate> getCandidates() {
        return candidates;
    }
    
    public LocalDate getGlobalStartDate() {
        return globalStartDate;
    }
    
    public LocalDate getGlobalEndDate() {
        return globalEndDate;
    }
    
    public int getTotalDays() {
        return totalDays;
    }
    
    public LocalDate getActiveStartDate() {
        return activeStartDate != null ? activeStartDate : globalStartDate;
    }
    
    public int getActiveStartIndex() {
        return activeStartIndex;
    }
}
