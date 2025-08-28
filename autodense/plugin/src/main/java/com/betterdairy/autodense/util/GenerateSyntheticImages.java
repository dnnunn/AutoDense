package com.betterdairy.autodense.util;

import ij.ImagePlus;
import ij.io.FileSaver;
import java.io.File;

/**
 * Utility to generate synthetic test images for algorithm validation
 */
public class GenerateSyntheticImages {
    
    public static void main(String[] args) {
        String outputDir = "../../tmp/"; // Relative to plugin directory
        
        // Create output directory if it doesn't exist
        new File(outputDir).mkdirs();
        
        // Generate synthetic EtBr gel (12 lanes, 4 bands per lane)
        ImagePlus etbrGel = SyntheticImageGenerator.generateEtBrGel(800, 600, 12, 4);
        FileSaver etbrSaver = new FileSaver(etbrGel);
        String etbrPath = outputDir + "synthetic_etbr_gel.jpg";
        etbrSaver.saveAsJpeg(etbrPath);
        System.out.println("Saved: " + etbrPath);
        
        // Generate synthetic SDS-PAGE gel (8 lanes)
        ImagePlus sdsGel = SyntheticImageGenerator.generateSdsPageGel(600, 800, 8);
        FileSaver sdsSaver = new FileSaver(sdsGel);
        String sdsPath = outputDir + "synthetic_sds_gel.jpg";
        sdsSaver.saveAsJpeg(sdsPath);
        System.out.println("Saved: " + sdsPath);
        
        // Generate synthetic colony plate (50 colonies)
        ImagePlus colonyPlate = SyntheticImageGenerator.generateColonyPlate(800, 800, 50);
        FileSaver colonySaver = new FileSaver(colonyPlate);
        String colonyPath = outputDir + "synthetic_colony_plate.jpg";
        colonySaver.saveAsJpeg(colonyPath);
        System.out.println("Saved: " + colonyPath);
        
        System.out.println("All synthetic images generated successfully!");
    }
}