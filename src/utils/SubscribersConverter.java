package utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class SubscribersConverter {
    public static class ConversionResult {
        public int processedCount = 0;
        public int updatedCount = 0;
        public int errorCount = 0;
        public List<String> errors = new ArrayList<>();
    }

    public static ConversionResult convert(String downloadPathStr) {
        ConversionResult result = new ConversionResult();
        File downloadDir = new File(downloadPathStr);
        if (!downloadDir.exists() || !downloadDir.isDirectory()) {
            result.errors.add("Download-Pfad existiert nicht oder ist kein Verzeichnis: " + downloadPathStr);
            return result;
        }

        List<File> htmlFiles = new ArrayList<>();
        findHtmlFiles(downloadDir, htmlFiles);

        for (File htmlFile : htmlFiles) {
            result.processedCount++;
            try {
                // Finde zugehörige _root.txt Datei
                String txtPathStr = htmlFile.getAbsolutePath().replace("_root.html", "_root.txt");
                File txtFile = new File(txtPathStr);
                if (!txtFile.exists()) {
                    continue; // Überspringen, wenn keine txt-Datei existiert
                }

                // Lese HTML-Inhalt und parse Abonnenten
                String htmlContent = readFileRobustly(htmlFile);
                int subscribers = parseSubscribers(htmlContent);

                // Aktualisiere _root.txt
                String txtContent = readFileRobustly(txtFile);
                String updatedContent = updateOrInsertSubscribers(txtContent, subscribers);
                
                if (!txtContent.equals(updatedContent)) {
                    Files.writeString(txtFile.toPath(), updatedContent, StandardCharsets.UTF_8);
                    result.updatedCount++;
                }
            } catch (Exception e) {
                result.errorCount++;
                result.errors.add("Fehler bei Datei " + htmlFile.getName() + ": " + e.getMessage());
            }
        }
        return result;
    }

    private static String readFileRobustly(File file) throws IOException {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (java.nio.charset.MalformedInputException e) {
            try {
                return Files.readString(file.toPath(), StandardCharsets.ISO_8859_1);
            } catch (Exception e2) {
                try {
                    return Files.readString(file.toPath(), java.nio.charset.Charset.forName("windows-1252"));
                } catch (Exception e3) {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    return new String(bytes, StandardCharsets.ISO_8859_1);
                }
            }
        }
    }

    private static void findHtmlFiles(File dir, List<File> htmlFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                findHtmlFiles(f, htmlFiles);
            } else if (f.getName().endsWith("_root.html")) {
                htmlFiles.add(f);
            }
        }
    }

    private static int parseSubscribers(String htmlContent) {
        Document doc = Jsoup.parse(htmlContent);
        
        // Nach dem Element mit dem Label "Abonnenten:" suchen
        Elements elements = doc.select("div.s-list-info__item:contains(Abonnenten:) .s-list-info__value");
        
        if (elements.isEmpty()) {
            // Alternative Suche nach "Subscribers:"
            elements = doc.select("div.s-list-info__item:contains(Subscribers:) .s-list-info__value");
        }
        
        if (!elements.isEmpty()) {
            String subStr = elements.first().text();
            subStr = subStr.replaceAll("[^0-9]", "").trim();
            if (!subStr.isEmpty()) {
                return Integer.parseInt(subStr);
            }
        }
        return 0;
    }

    private static String updateOrInsertSubscribers(String txtContent, int subscribers) {
        String[] lines = txtContent.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        boolean balanceFound = false;
        int balanceIndex = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("Subscribers=")) {
                lines[i] = "Subscribers=" + subscribers;
                found = true;
            } else if (line.startsWith("Balance=")) {
                balanceFound = true;
                balanceIndex = i;
            }
        }

        if (found) {
            for (String line : lines) {
                sb.append(line).append("\n");
            }
        } else {
            // Wenn nicht gefunden, direkt nach Balance=... einfügen
            for (int i = 0; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
                if (i == balanceIndex) {
                    sb.append("Subscribers=").append(subscribers).append("\n");
                }
            }
            if (!balanceFound) {
                sb.append("Subscribers=").append(subscribers).append("\n");
            }
        }
        return sb.toString();
    }
}

