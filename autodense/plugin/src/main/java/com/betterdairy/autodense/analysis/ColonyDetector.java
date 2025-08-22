package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.Colony;
import com.betterdairy.autodense.model.Models.ColonyColor;
import com.betterdairy.autodense.model.Models.ColonySize;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.OvalRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.EDM;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure functional colony detection using native ImageJ operations.
 * Implements LoG blob detection and watershed splitting.
 */
public final class ColonyDetector {
    
    /**
     * Detect colonies within a plate region
     */
    public static List<Colony> detect(ImagePlus image, OvalRoi plateRoi) {
        return detect(image, plateRoi, 6, 60, true);
    }
    
    /**
     * Detect colonies with full parameter control
     */
    public static List<Colony> detect(ImagePlus image, OvalRoi plateRoi, 
                                    int minDiamPx, int maxDiamPx, boolean splitTouching) {
        
        ImagePlus working = image.duplicate();
        working.setTitle("colony_detection_" + System.currentTimeMillis());
        
        // Step 1: Convert to grayscale if needed
        if (working.getNChannels() > 1) {
            IJ.run(working, "RGB to Luminance", "");
        }
        
        // Step 2: Apply plate mask
        if (plateRoi != null) {
            working.setRoi(plateRoi);
            IJ.run(working, "Clear Outside", "");
            working.killRoi();
        }
        
        // Step 3: LoG-based blob detection via Auto Local Threshold
        IJ.run(working, "Auto Local Threshold", "method=Phansalkar radius=15 parameter_1=0 parameter_2=0 white");
        
        // Step 4: Morphological cleanup
        IJ.run(working, "Fill Holes", "");
        IJ.run(working, "Open", ""); // Remove small noise
        
        // Step 5: Watershed splitting if requested
        if (splitTouching) {
            performWatershedSplitting(working);
        }
        
        // Step 6: Analyze particles to extract colony data
        int minArea = (int) (Math.PI * (minDiamPx / 2.0) * (minDiamPx / 2.0));
        int maxArea = (int) (Math.PI * (maxDiamPx / 2.0) * (maxDiamPx / 2.0));
        
        IJ.run(working, "Set Measurements...", "area mean centroid shape");
        IJ.run(working, "Analyze Particles...", 
               String.format("size=%d-%d pixel show=Nothing display clear", minArea, maxArea));
        
        // Step 7: Convert ResultsTable to Colony objects
        List<Colony> colonies = extractColoniesFromResults(ResultsTable.getResultsTable());
        
        working.close();
        return colonies;
    }
    
    /**
     * Perform watershed splitting to separate touching colonies
     */
    private static void performWatershedSplitting(ImagePlus image) {
        // Distance transform
        EDM edm = new EDM();
        ImageProcessor proc = image.getProcessor();
        
        // Invert for distance transform (colonies should be white)
        proc.invert();
        edm.setup("", image);
        edm.run(proc);
        
        // Find maxima and watershed
        IJ.run(image, "Find Maxima...", "prominence=10 output=[Segmented Particles]");
        
        // Get the segmented result
        ImagePlus segmented = IJ.getImage();
        if (segmented != null && !segmented.equals(image)) {
            image.setProcessor(segmented.getProcessor());
            segmented.close();
        }
    }
    
    /**
     * Extract Colony objects from ImageJ ResultsTable
     */
    private static List<Colony> extractColoniesFromResults(ResultsTable rt) {
        List<Colony> colonies = new ArrayList<>();
        
        if (rt == null || rt.getCounter() == 0) {
            return colonies;
        }
        
        for (int i = 0; i < rt.getCounter(); i++) {
            double x = rt.getValue("X", i);
            double y = rt.getValue("Y", i);
            double area = rt.getValue("Area", i);
            double diameter = 2.0 * Math.sqrt(area / Math.PI);
            double circularity = rt.getValue("Circ.", i);
            double solidity = rt.getValue("Solidity", i);
            double meanIntensity = rt.getValue("Mean", i);
            
            // Convert to mm (will be updated when plate scale is known)
            double diameterMm = diameter / 10.0; // Placeholder conversion
            
            Colony colony = new Colony(
                i + 1,                    // index
                x, y,                     // position
                area, diameter, diameterMm, // size
                circularity, solidity,    // shape
                meanIntensity,           // intensity
                ColonyColor.WHITE,       // placeholder color
                1.0,                     // placeholder confidence
                classifySize(diameterMm), // size classification
                "unclassified"           // placeholder classification
            );
            
            colonies.add(colony);
        }
        
        return colonies;
    }
    
    /**
     * Simple size classification based on diameter
     */
    private static ColonySize classifySize(double diameterMm) {
        if (diameterMm < 1.0) return ColonySize.SMALL;
        if (diameterMm < 2.5) return ColonySize.MEDIUM;
        return ColonySize.LARGE;
    }
}