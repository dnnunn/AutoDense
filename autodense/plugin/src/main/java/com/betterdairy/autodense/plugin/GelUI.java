package com.betterdairy.autodense.plugin;

import org.scijava.Context;

import javax.swing.*;
import java.awt.*;

/** Minimal Swing shell to unblock compilation. */
public class GelUI {
    private final Context context;

    public GelUI(Context context) {
        this.context = context;
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("AutoDense - " + String.valueOf(context));
            f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            f.setSize(new Dimension(800, 600));
            f.setLocationByPlatform(true);
            f.add(new JLabel("AutoDense UI placeholder. Drop image support will be added."), BorderLayout.CENTER);
            f.setVisible(true);
        });
    }
}
