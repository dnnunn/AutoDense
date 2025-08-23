package com.betterdairy.autodense.plugin;

import ij.IJ;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.util.Iterator;

/**
 * Runtime classpath auditor to verify critical dependencies are loaded correctly.
 * 
 * This helps debug issues with:
 * - ImageIO codec registration (TwelveMonkeys)
 * - Version conflicts between AutoDense and Fiji dependencies
 * - Missing or duplicate libraries
 * - SPI (ServiceLoader) discovery problems
 */
public final class ClasspathAudit {
    
    /**
     * Run comprehensive classpath audit and report findings
     */
    public static void run() {
        IJ.log("🔍 Running AutoDense Classpath Audit...");
        IJ.log("==========================================");
        
        auditImageIOCodecs();
        auditJSONLibraries();
        auditHTTPClients();  
        auditExcelSupport();
        auditBioFormats();
        auditMathLibraries();
        auditVersionConflicts();
        
        IJ.log("==========================================");
        IJ.log("✅ Classpath audit completed");
    }
    
    /**
     * Audit ImageIO codec availability (critical for format support)
     */
    private static void auditImageIOCodecs() {
        IJ.log("📸 ImageIO Codec Audit:");
        
        // Test HEIC support (should be missing currently)
        Iterator<ImageReader> heicReaders = ImageIO.getImageReadersBySuffix("heic");
        IJ.log("  HEIC reader present: " + (heicReaders.hasNext() ? "✅ YES" : "❌ NO (expected)"));
        
        // Test enhanced JPEG support (TwelveMonkeys)
        Iterator<ImageReader> jpegReaders = ImageIO.getImageReadersBySuffix("jpeg");
        boolean hasTwelveMonkeysJPEG = false;
        while (jpegReaders.hasNext()) {
            ImageReader reader = jpegReaders.next();
            if (reader.getClass().getName().contains("twelvemonkeys")) {
                hasTwelveMonkeysJPEG = true;
                break;
            }
        }
        IJ.log("  TwelveMonkeys JPEG: " + (hasTwelveMonkeysJPEG ? "✅ YES" : "⚠️  NO"));
        
        // Test TIFF support
        Iterator<ImageReader> tiffReaders = ImageIO.getImageReadersBySuffix("tiff");
        boolean hasTwelveMonkeysTIFF = false;
        while (tiffReaders.hasNext()) {
            ImageReader reader = tiffReaders.next();
            if (reader.getClass().getName().contains("twelvemonkeys")) {
                hasTwelveMonkeysTIFF = true;
                break;
            }
        }
        IJ.log("  TwelveMonkeys TIFF: " + (hasTwelveMonkeysTIFF ? "✅ YES" : "⚠️  NO"));
        
        // Count total supported formats
        String[] formats = ImageIO.getReaderFormatNames();
        IJ.log("  Total formats supported: " + formats.length);
    }
    
    /**
     * Audit JSON processing libraries
     */
    private static void auditJSONLibraries() {
        IJ.log("📄 JSON Libraries Audit:");
        
        // org.json (should be present - used by AutoDense)
        try {
            Class.forName("org.json.JSONObject");
            IJ.log("  org.json: ✅ Present");
        } catch (ClassNotFoundException e) {
            IJ.log("  org.json: ❌ Missing (CRITICAL)");
        }
        
        // Jackson (currently injected - should be shaded)
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            IJ.log("  Jackson: ⚠️  Present (should be SHADED to avoid conflicts)");
        } catch (ClassNotFoundException e) {
            IJ.log("  Jackson: ✅ Missing (good - not needed or properly shaded)");
        }
        
        // Gson (currently injected - should be shaded)
        try {
            Class.forName("com.google.gson.Gson");
            IJ.log("  Gson: ⚠️  Present (should be SHADED to avoid conflicts)");
        } catch (ClassNotFoundException e) {
            IJ.log("  Gson: ✅ Missing (good - not needed or properly shaded)");
        }
    }
    
    /**
     * Audit HTTP client libraries
     */
    private static void auditHTTPClients() {
        IJ.log("🌐 HTTP Client Audit:");
        
        // OkHttp (currently injected - should be shaded)
        try {
            Class.forName("okhttp3.OkHttpClient");
            IJ.log("  OkHttp: ⚠️  Present (should be SHADED to avoid conflicts)");
        } catch (ClassNotFoundException e) {
            IJ.log("  OkHttp: ✅ Missing (good - not needed or properly shaded)");
        }
        
        // Apache HttpClient (currently injected - should be shaded)  
        try {
            Class.forName("org.apache.http.client.HttpClient");
            IJ.log("  Apache HttpClient: ⚠️  Present (should be SHADED to avoid conflicts)");
        } catch (ClassNotFoundException e) {
            IJ.log("  Apache HttpClient: ✅ Missing (good - not needed or properly shaded)");
        }
    }
    
    /**
     * Audit Excel/POI support
     */
    private static void auditExcelSupport() {
        IJ.log("📊 Excel Support Audit:");
        
        // Apache POI (not currently added - may be needed for Excel export)
        try {
            Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
            IJ.log("  Apache POI (XLSX): ⚠️  Present (should be SHADED)");
        } catch (ClassNotFoundException e) {
            IJ.log("  Apache POI (XLSX): ❌ Missing (Excel export will fail)");
        }
        
        try {
            Class.forName("org.apache.poi.hssf.usermodel.HSSFWorkbook");
            IJ.log("  Apache POI (XLS): ⚠️  Present (should be SHADED)");
        } catch (ClassNotFoundException e) {
            IJ.log("  Apache POI (XLS): ❌ Missing (legacy Excel export will fail)");
        }
    }
    
    /**
     * Audit Bio-Formats support
     */
    private static void auditBioFormats() {
        IJ.log("🔬 Bio-Formats Audit:");
        
        // Bio-Formats core
        try {
            Class.forName("loci.formats.ImageReader");
            IJ.log("  Bio-Formats core: ✅ Present");
        } catch (ClassNotFoundException e) {
            IJ.log("  Bio-Formats core: ⚠️  Missing (will use Fiji's if available)");
        }
        
        // Bio-Formats plugins
        try {
            Class.forName("loci.plugins.BF");
            IJ.log("  Bio-Formats plugins: ✅ Present");
        } catch (ClassNotFoundException e) {
            IJ.log("  Bio-Formats plugins: ⚠️  Missing (specialized formats unavailable)");
        }
    }
    
    /**
     * Audit math and ML libraries
     */
    private static void auditMathLibraries() {
        IJ.log("🧮 Math Libraries Audit:");
        
        // Commons Math (potentially problematic if Fiji has different version)
        try {
            Class.forName("org.apache.commons.math3.linear.RealMatrix");
            IJ.log("  Apache Commons Math: ⚠️  Present (check for version conflicts)");
        } catch (ClassNotFoundException e) {
            IJ.log("  Apache Commons Math: ✅ Missing (good - ImageJ has math support)");
        }
        
        // EJML (linear algebra)
        try {
            Class.forName("org.ejml.simple.SimpleMatrix");
            IJ.log("  EJML: ✅ Present");
        } catch (ClassNotFoundException e) {
            IJ.log("  EJML: ⚠️  Missing (advanced math operations limited)");
        }
    }
    
    /**
     * Look for potential version conflicts
     */
    private static void auditVersionConflicts() {
        IJ.log("⚠️  Version Conflict Check:");
        
        // Commons libraries (high conflict potential with Fiji)
        String[] commonLibs = {
            "org.apache.commons.lang.StringUtils",      // commons-lang
            "org.apache.commons.io.FileUtils",          // commons-io  
            "org.apache.commons.codec.binary.Base64",   // commons-codec
            "org.apache.commons.logging.Log"            // commons-logging
        };
        
        int conflictCount = 0;
        for (String className : commonLibs) {
            try {
                Class.forName(className);
                conflictCount++;
                IJ.log("  " + className.substring(className.lastIndexOf('.') + 1) + 
                       ": ⚠️  Present (potential Fiji conflict)");
            } catch (ClassNotFoundException e) {
                // Good - not present to cause conflicts
            }
        }
        
        if (conflictCount > 0) {
            IJ.log("  💡 Consider shading " + conflictCount + " commons libraries to avoid version conflicts");
        } else {
            IJ.log("  ✅ No obvious version conflicts detected");
        }
    }
    
    /**
     * Get audit summary for troubleshooting
     */
    public static String getAuditSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("AutoDense Classpath Audit Summary\n");
        summary.append("================================\n");
        
        // Quick checks
        boolean hasOrgJson = isClassPresent("org.json.JSONObject");
        boolean hasTwelveMonkeys = isClassPresent("com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi");
        boolean hasConflicts = isClassPresent("com.fasterxml.jackson.databind.ObjectMapper") || 
                              isClassPresent("okhttp3.OkHttpClient");
        
        summary.append("Essential libraries: ").append(hasOrgJson ? "✅" : "❌").append("\n");
        summary.append("TwelveMonkeys ImageIO: ").append(hasTwelveMonkeys ? "✅" : "❌").append("\n");
        summary.append("Potential conflicts: ").append(hasConflicts ? "⚠️" : "✅").append("\n");
        
        return summary.toString();
    }
    
    /**
     * Utility to check if a class is present
     */
    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}