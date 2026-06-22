package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import components.MainTable;
import ui.components.AppUIStyle;
import utils.ScoreConfig;

/**
 * Dialog for configuring strategy evaluation scoring weights.
 */
public class ScoreConfigDialog extends JDialog {
    private final MainTable mainTable;
    private final JSlider sliderProfit;
    private final JSlider slider3Mpdd;
    private final JSlider sliderDrawdown;
    private final JSlider sliderTrades;
    private final JSlider sliderTradeDays;
    private final JSlider sliderSubscribers;

    public ScoreConfigDialog(JFrame parent, MainTable mainTable) {
        super(parent, "Bewertungs-Konfiguration", true);
        this.mainTable = mainTable;
        
        ScoreConfig config = ScoreConfig.getInstance();
        
        sliderProfit = createWeightSlider(config.getWeightProfit());
        slider3Mpdd = createWeightSlider(config.getWeight3Mpdd());
        sliderDrawdown = createWeightSlider(config.getWeightDrawdown());
        sliderTrades = createWeightSlider(config.getWeightTrades());
        sliderTradeDays = createWeightSlider(config.getWeightTradeDays());
        sliderSubscribers = createWeightSlider(config.getWeightSubscribers());

        initializeUI();
        
        setSize(new Dimension(500, 450));
        setLocationRelativeTo(parent);
    }

    private JSlider createWeightSlider(int initialValue) {
        JSlider slider = new JSlider(0, 10, initialValue);
        slider.setMajorTickSpacing(2);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        return slider;
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.weightx = 1.0;

        // Header info
        JLabel headerLabel = new JLabel("<html><b>Konfigurieren Sie die Wichtigkeit (Gewichtung 0-10) der einzelnen Faktoren:</b><br>"
                + "Die Gesamtbewertung wird als gewichteter Durchschnitt (0-100) berechnet.</html>");
        headerLabel.setFont(AppUIStyle.REGULAR_FONT);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        contentPanel.add(headerLabel, gbc);
        
        gbc.gridwidth = 1;
        
        int row = 1;
        addSliderRow(contentPanel, gbc, row++, "Profit/Monat % (Gewinn):", sliderProfit);
        addSliderRow(contentPanel, gbc, row++, "3MPDD (Profit/drawdown):", slider3Mpdd);
        addSliderRow(contentPanel, gbc, row++, "Equity Drawdown % (Risiko):", sliderDrawdown);
        addSliderRow(contentPanel, gbc, row++, "Anzahl Trades (Aktivität):", sliderTrades);
        addSliderRow(contentPanel, gbc, row++, "Trading Days:", sliderTradeDays);
        addSliderRow(contentPanel, gbc, row++, "Abonnenten (Subscribers):", sliderSubscribers);

        add(contentPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = AppUIStyle.createStyledButton("Speichern & Berechnen");
        saveButton.addActionListener(e -> {
            saveChanges();
            dispose();
        });
        
        JButton cancelButton = new JButton("Abbrechen");
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void addSliderRow(JPanel panel, GridBagConstraints gbc, int gridy, String labelText, JSlider slider) {
        gbc.gridx = 0;
        gbc.gridy = gridy;
        gbc.weightx = 0.3;
        JLabel label = AppUIStyle.createStyledLabel(labelText);
        panel.add(label, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.6;
        panel.add(slider, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.1;
        JLabel valLabel = AppUIStyle.createStyledLabel(String.valueOf(slider.getValue()));
        valLabel.setFont(AppUIStyle.BOLD_FONT);
        panel.add(valLabel, gbc);

        slider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                valLabel.setText(String.valueOf(slider.getValue()));
            }
        });
    }

    private void saveChanges() {
        ScoreConfig config = ScoreConfig.getInstance();
        config.setWeightProfit(sliderProfit.getValue());
        config.setWeight3Mpdd(slider3Mpdd.getValue());
        config.setWeightDrawdown(sliderDrawdown.getValue());
        config.setWeightTrades(sliderTrades.getValue());
        config.setWeightTradeDays(sliderTradeDays.getValue());
        config.setWeightSubscribers(sliderSubscribers.getValue());
        config.save();

        // Refresh and reinitialize the table to update all scores
        mainTable.forceCompleteReinitialize();
    }
}
