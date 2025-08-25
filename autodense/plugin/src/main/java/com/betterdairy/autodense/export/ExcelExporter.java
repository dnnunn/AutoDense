package com.betterdairy.autodense.export;

import org.apache.poi.ss.usermodel.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Excel export utilities for AutoDense analysis results
 */
public class ExcelExporter {
    
    /**
     * Write purification tracking data to Excel sheet
     */
    public static void writePurificationSheet(Workbook wb, JSONArray steps) {
        Sheet sh = wb.createSheet("Purification");
        int r = 0;
        Row header = sh.createRow(r++);
        String[] cols = {
            "Step", "Lanes", "Volumes (mL)", "Target Area (loaded sum)", 
            "Target Area (total est)", "Purity (area frac)", "Step Recovery", "Cumulative Recovery"
        };
        
        for (int c = 0; c < cols.length; c++) {
            header.createCell(c).setCellValue(cols[c]);
        }
        
        for (int i = 0; i < steps.length(); i++) {
            JSONObject s = steps.getJSONObject(i);
            Row row = sh.createRow(r++);
            
            row.createCell(0).setCellValue(s.getString("name"));
            row.createCell(1).setCellValue(s.getJSONArray("lanes").toString());
            
            String volumesStr = "";
            if (s.has("volumes_ml") && !s.isNull("volumes_ml")) {
                JSONArray volumes = s.getJSONArray("volumes_ml");
                volumesStr = volumes.toString();
            }
            row.createCell(2).setCellValue(volumesStr);
            
            row.createCell(3).setCellValue(s.getDouble("target_area_loaded_sum"));
            row.createCell(4).setCellValue(s.getDouble("target_area_total_est"));
            row.createCell(5).setCellValue(s.getDouble("purity_est"));
            row.createCell(6).setCellValue(s.getDouble("step_recovery"));
            row.createCell(7).setCellValue(s.getDouble("cum_recovery"));
        }
        
        // Auto-size columns
        for (int c = 0; c < cols.length; c++) {
            sh.autoSizeColumn(c);
        }
    }
}