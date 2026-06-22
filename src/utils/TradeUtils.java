package utils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import data.Trade;

public class TradeUtils {
   
   /**
    * Berechnet die Anzahl gleichzeitig offener Trades zu einem bestimmten Zeitpunkt
    */
   public static int calculateOpenTradesAt(List<Trade> trades, LocalDateTime time) {
       return getActiveTradesAt(trades, time).size();
   }
   
   /**
    * Berechnet die Gesamtzahl der Lots von offenen Trades zu einem bestimmten Zeitpunkt
    */
   public static double calculateOpenLotsAt(List<Trade> trades, LocalDateTime time) {
       return getActiveTradesAt(trades, time)
           .stream()
           .mapToDouble(Trade::getLots)
           .sum();
   }
   
   /**
    * Findet die maximale Anzahl gleichzeitig offener Trades
    */
   public static int findMaxConcurrentTrades(List<Trade> trades) {
       if (trades == null || trades.isEmpty()) {
           return 0;
       }
       List<Trade> activeTrades = new ArrayList<>();
       
       // Sortiere alle Trades nach OpenTime
       List<Trade> sortedTrades = new ArrayList<>(trades);
       sortedTrades.sort((t1, t2) -> t1.getOpenTime().compareTo(t2.getOpenTime()));
       
       int maxTrades = 0;
       
       for (Trade trade : sortedTrades) {
           // Entferne zuerst alle Trades die bereits geschlossen wurden
           activeTrades.removeIf(t -> t.getCloseTime().compareTo(trade.getOpenTime()) <= 0);
           
           // Fge den neuen Trade hinzu
           activeTrades.add(trade);
           
           // Aktualisiere das Maximum
           maxTrades = Math.max(maxTrades, activeTrades.size());
       }
       
       return maxTrades;
   }
   
   /**
    * Findet die maximale Anzahl gleichzeitig offener Lots
    */
   public static double findMaxConcurrentLots(List<Trade> trades) {
       if (trades == null || trades.isEmpty()) {
           return 0.0;
       }
       List<Trade> activeTrades = new ArrayList<>();
       
       // Sortiere alle Trades nach OpenTime
       List<Trade> sortedTrades = new ArrayList<>(trades);
       sortedTrades.sort((t1, t2) -> t1.getOpenTime().compareTo(t2.getOpenTime()));
       
       double maxLots = 0.0;
       
       for (Trade trade : sortedTrades) {
           // Entferne zuerst alle Trades die bereits geschlossen wurden
           activeTrades.removeIf(t -> t.getCloseTime().compareTo(trade.getOpenTime()) <= 0);
           
           // Fge den neuen Trade hinzu
           activeTrades.add(trade);
           
           // Berechne aktuelle Lots und aktualisiere Maximum
           double currentLots = activeTrades.stream()
                                          .mapToDouble(Trade::getLots)
                                          .sum();
           maxLots = Math.max(maxLots, currentLots);
       }
       
       return maxLots;
   }
   
   /**
    * Gibt die Liste der aktiven Trades zu einem bestimmten Zeitpunkt zurck
    */
   public static List<Trade> getActiveTradesAt(List<Trade> trades, LocalDateTime time) {
       List<Trade> activeTrades = new ArrayList<>();
       if (trades == null || time == null) {
           return activeTrades;
       }
       
       for (Trade trade : trades) {
           if (trade.getOpenTime().compareTo(time) <= 0 && 
               trade.getCloseTime().compareTo(time) > 0) {
               activeTrades.add(trade);
           }
       }
       
       return activeTrades;
   }
}