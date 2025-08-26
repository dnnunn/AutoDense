package com.betterdairy.autodense.img;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.plugin.frame.RoiManager;

/**
 * ImageJ utilities for overlay management and ROI Manager suppression.
 * Ensures AutoDense uses clean overlay-only approach without ROI Manager interference.
 */
public final class IJUtils {
    
    private IJUtils() {} // Utility class
    
    /**
     * Suppress ROI Manager and ensure clean overlay-only workflow.
     * Call this at the start of every gel analysis tool to prevent ROI Manager popups.
     * 
     * @param imp The image to prepare for overlay-only analysis
     */
    public static void silenceRoiManager(ImagePlus imp) {
        RoiManager rm = RoiManager.getInstance2();
        if (rm != null) {
            rm.reset();
            rm.setVisible(false);
        }
        Overlay ov = imp.getOverlay();
        if (ov == null) {
            imp.setOverlay(new Overlay());
        }
    }
    
    /**
     * Refresh image display and bring window to front.
     * Call this after overlay modifications to ensure UI updates correctly.
     * 
     * @param imp The image to refresh
     */
    public static void refresh(ImagePlus imp) {
        imp.updateAndDraw();
        if (imp.getWindow() != null) {
            imp.getWindow().toFront();
        }
    }
}