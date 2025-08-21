package com.betterdairy.autodense.analysis;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Utility class for managing molecular weight standards and markers.
 * Provides access to comprehensive protein and DNA marker databases.
 */
public final class MolecularWeightStandards {
    
    private static JSONObject standards;
    private static boolean loaded = false;
    
    private MolecularWeightStandards() {}
    
    /**
     * Load standards from the JSON resource file
     */
    private static synchronized void loadStandards() {
        if (loaded) return;
        
        try (InputStream is = MolecularWeightStandards.class
                .getResourceAsStream("/standards.json")) {
            if (is == null) {
                throw new RuntimeException("Could not find standards.json resource");
            }
            
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            standards = new JSONObject(json);
            loaded = true;
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load molecular weight standards", e);
        }
    }
    
    /**
     * Get all protein standards
     */
    public static List<ProteinStandard> getProteinStandards() {
        loadStandards();
        
        JSONArray proteinArray = standards.getJSONArray("protein_standards");
        List<ProteinStandard> result = new ArrayList<>();
        
        for (int i = 0; i < proteinArray.length(); i++) {
            JSONObject obj = proteinArray.getJSONObject(i);
            result.add(parseProteinStandard(obj));
        }
        
        return result;
    }
    
    /**
     * Get all DNA standards
     */
    public static List<DNAStandard> getDNAStandards() {
        loadStandards();
        
        JSONArray dnaArray = standards.getJSONArray("dna_standards");
        List<DNAStandard> result = new ArrayList<>();
        
        for (int i = 0; i < dnaArray.length(); i++) {
            JSONObject obj = dnaArray.getJSONObject(i);
            result.add(parseDNAStandard(obj));
        }
        
        return result;
    }
    
    /**
     * Get protein standard by ID
     */
    public static ProteinStandard getProteinStandard(String id) {
        return getProteinStandards().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get DNA standard by ID
     */
    public static DNAStandard getDNAStandard(String id) {
        return getDNAStandards().stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get default protein standard
     */
    public static ProteinStandard getDefaultProteinStandard() {
        return getProteinStandards().stream()
                .filter(ProteinStandard::isDefault)
                .findFirst()
                .orElse(getProteinStandards().get(0)); // Fallback to first
    }
    
    /**
     * Get default DNA standard
     */
    public static DNAStandard getDefaultDNAStandard() {
        return getDNAStandards().stream()
                .filter(DNAStandard::isDefault)
                .findFirst()
                .orElse(getDNAStandards().get(0)); // Fallback to first
    }
    
    /**
     * Get standards by manufacturer
     */
    public static List<ProteinStandard> getProteinStandardsByManufacturer(String manufacturer) {
        return getProteinStandards().stream()
                .filter(s -> s.getManufacturer().equalsIgnoreCase(manufacturer))
                .collect(Collectors.toList());
    }
    
    /**
     * Get DNA standards by manufacturer
     */
    public static List<DNAStandard> getDNAStandardsByManufacturer(String manufacturer) {
        return getDNAStandards().stream()
                .filter(s -> s.getManufacturer().equalsIgnoreCase(manufacturer))
                .collect(Collectors.toList());
    }
    
    /**
     * Find protein standard by approximate molecular weight range
     */
    public static List<ProteinStandard> findProteinStandardsByRange(double minKda, double maxKda) {
        return getProteinStandards().stream()
                .filter(s -> {
                    double[] bands = s.getBandsKda();
                    return bands[bands.length - 1] >= minKda && bands[0] <= maxKda;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Find DNA standard by approximate fragment size range
     */
    public static List<DNAStandard> findDNAStandardsByRange(int minBp, int maxBp) {
        return getDNAStandards().stream()
                .filter(s -> {
                    int[] bands = s.getBandsBp();
                    return bands[bands.length - 1] >= minBp && bands[0] <= maxBp;
                })
                .collect(Collectors.toList());
    }
    
    private static ProteinStandard parseProteinStandard(JSONObject obj) {
        String id = obj.getString("id");
        String name = obj.getString("name");
        String manufacturer = obj.getString("manufacturer");
        String catalog = obj.optString("catalog", "");
        String description = obj.optString("description", "");
        String color = obj.optString("color", "blue");
        boolean prestained = obj.optBoolean("prestained", true);
        boolean isDefault = obj.optBoolean("default", false);
        
        JSONArray bandsArray = obj.getJSONArray("bands_kda");
        double[] bands = new double[bandsArray.length()];
        for (int i = 0; i < bandsArray.length(); i++) {
            bands[i] = bandsArray.getDouble(i);
        }
        
        JSONArray refArray = obj.optJSONArray("reference_bands");
        double[] refBands = new double[0];
        if (refArray != null) {
            refBands = new double[refArray.length()];
            for (int i = 0; i < refArray.length(); i++) {
                refBands[i] = refArray.getDouble(i);
            }
        }
        
        return new ProteinStandard(id, name, manufacturer, catalog, bands, refBands, 
                                  color, prestained, isDefault, description);
    }
    
    private static DNAStandard parseDNAStandard(JSONObject obj) {
        String id = obj.getString("id");
        String name = obj.getString("name");
        String manufacturer = obj.getString("manufacturer");
        String catalog = obj.optString("catalog", "");
        String description = obj.optString("description", "");
        String fragmentRange = obj.optString("fragment_range", "");
        boolean isDefault = obj.optBoolean("default", false);
        
        JSONArray bandsArray = obj.getJSONArray("bands_bp");
        int[] bands = new int[bandsArray.length()];
        for (int i = 0; i < bandsArray.length(); i++) {
            bands[i] = bandsArray.getInt(i);
        }
        
        JSONArray refArray = obj.optJSONArray("reference_bands");
        int[] refBands = new int[0];
        if (refArray != null) {
            refBands = new int[refArray.length()];
            for (int i = 0; i < refArray.length(); i++) {
                refBands[i] = refArray.getInt(i);
            }
        }
        
        return new DNAStandard(id, name, manufacturer, catalog, bands, refBands,
                              fragmentRange, isDefault, description);
    }
    
    /**
     * Protein molecular weight standard
     */
    public static class ProteinStandard {
        private final String id;
        private final String name;
        private final String manufacturer;
        private final String catalog;
        private final double[] bandsKda;
        private final double[] referenceBands;
        private final String color;
        private final boolean prestained;
        private final boolean isDefault;
        private final String description;
        
        public ProteinStandard(String id, String name, String manufacturer, String catalog,
                              double[] bandsKda, double[] referenceBands, String color,
                              boolean prestained, boolean isDefault, String description) {
            this.id = id;
            this.name = name;
            this.manufacturer = manufacturer;
            this.catalog = catalog;
            this.bandsKda = bandsKda;
            this.referenceBands = referenceBands;
            this.color = color;
            this.prestained = prestained;
            this.isDefault = isDefault;
            this.description = description;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getManufacturer() { return manufacturer; }
        public String getCatalog() { return catalog; }
        public double[] getBandsKda() { return bandsKda; }
        public double[] getReferenceBands() { return referenceBands; }
        public String getColor() { return color; }
        public boolean isPrestained() { return prestained; }
        public boolean isDefault() { return isDefault; }
        public String getDescription() { return description; }
        
        public String getDisplayName() {
            return name + (catalog.isEmpty() ? "" : " (" + catalog + ")");
        }
        
        public String getMolecularWeightRange() {
            if (bandsKda.length == 0) return "N/A";
            return String.format("%.1f - %.0f kDa", 
                                bandsKda[bandsKda.length - 1], bandsKda[0]);
        }
        
        @Override
        public String toString() {
            return getDisplayName() + " [" + getMolecularWeightRange() + "]";
        }
    }
    
    /**
     * DNA molecular weight standard
     */
    public static class DNAStandard {
        private final String id;
        private final String name;
        private final String manufacturer;
        private final String catalog;
        private final int[] bandsBp;
        private final int[] referenceBands;
        private final String fragmentRange;
        private final boolean isDefault;
        private final String description;
        
        public DNAStandard(String id, String name, String manufacturer, String catalog,
                          int[] bandsBp, int[] referenceBands, String fragmentRange,
                          boolean isDefault, String description) {
            this.id = id;
            this.name = name;
            this.manufacturer = manufacturer;
            this.catalog = catalog;
            this.bandsBp = bandsBp;
            this.referenceBands = referenceBands;
            this.fragmentRange = fragmentRange;
            this.isDefault = isDefault;
            this.description = description;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getManufacturer() { return manufacturer; }
        public String getCatalog() { return catalog; }
        public int[] getBandsBp() { return bandsBp; }
        public int[] getReferenceBands() { return referenceBands; }
        public String getFragmentRange() { return fragmentRange; }
        public boolean isDefault() { return isDefault; }
        public String getDescription() { return description; }
        
        public String getDisplayName() {
            return name + (catalog.isEmpty() ? "" : " (" + catalog + ")");
        }
        
        public String getFragmentSizeRange() {
            if (bandsBp.length == 0) return "N/A";
            if (!fragmentRange.isEmpty()) return fragmentRange;
            return String.format("%d - %,d bp", 
                                bandsBp[bandsBp.length - 1], bandsBp[0]);
        }
        
        @Override
        public String toString() {
            return getDisplayName() + " [" + getFragmentSizeRange() + "]";
        }
    }
}