package autodense.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Simple CSV utility for reading/writing Map-based data
 */
public final class Csv {
    
    public static List<Map<String,String>> read(Path csvPath) throws IOException {
        List<Map<String,String>> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath.toFile()))) {
            String headerLine = br.readLine();
            if (headerLine == null) return rows;
            
            String[] headers = headerLine.split(",");
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                Map<String,String> row = new HashMap<>();
                for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                    row.put(headers[i].trim(), values[i].trim());
                }
                rows.add(row);
            }
        }
        return rows;
    }
    
    public static void write(Path csvPath, List<Map<String,Object>> rows, List<String> columnOrder) throws IOException {
        if (rows.isEmpty()) return;
        
        try (FileWriter fw = new FileWriter(csvPath.toFile())) {
            // Write header
            fw.write(String.join(",", columnOrder));
            fw.write("\n");
            
            // Write rows
            for (Map<String,Object> row : rows) {
                List<String> values = new ArrayList<>();
                for (String col : columnOrder) {
                    Object val = row.get(col);
                    values.add(val != null ? val.toString() : "");
                }
                fw.write(String.join(",", values));
                fw.write("\n");
            }
        }
    }
}