import com.betterdairy.autodense.analysis.BaselineParams;
import java.util.Map;
import java.util.HashMap;

/**
 * Test the config integration for BaselineParams.fromConfig()
 */
public class test_config_integration {
    
    public static void main(String[] args) {
        System.out.println("[TEST] Testing BaselineParams.fromConfig() integration");
        
        // Test 1: SDS-like config
        Map<String,Object> sdsConfig = createSdsConfig();
        int roiWidth = 800; // typical SDS gel width
        
        BaselineParams bp = BaselineParams.fromConfig(sdsConfig, roiWidth);
        System.out.printf("[TEST] SDS Config - Method: %s, WindowPx: %d, Quantile: %.2f%n", 
                         bp.method, bp.windowPx, bp.quantile);
        
        // Should be: Method=PERCENTILE, WindowPx=16 (0.02*800=16, clamped 3-12 = 12), Quantile=0.10
        if (bp.method == BaselineParams.Method.PERCENTILE && bp.windowPx == 12 && bp.quantile == 0.10) {
            System.out.println("[TEST] ✅ SDS config parsing PASSED");
        } else {
            System.out.println("[TEST] ❌ SDS config parsing FAILED");
        }
        
        // Test 2: Colony config (method=none)
        Map<String,Object> colonyConfig = createColonyConfig();
        BaselineParams colonyBp = BaselineParams.fromConfig(colonyConfig, roiWidth);
        System.out.printf("[TEST] Colony Config - Method: %s%n", colonyBp.method);
        
        if (colonyBp.method == BaselineParams.Method.NONE) {
            System.out.println("[TEST] ✅ Colony config (method=none) PASSED");
        } else {
            System.out.println("[TEST] ❌ Colony config FAILED");
        }
        
        // Test 3: Empty config (should use defaults)
        Map<String,Object> emptyConfig = Map.of();
        BaselineParams defaultBp = BaselineParams.fromConfig(emptyConfig, roiWidth);
        System.out.printf("[TEST] Default Config - Method: %s, WindowPx: %d%n", 
                         defaultBp.method, defaultBp.windowPx);
        
        // Should use safe defaults
        if (defaultBp.method == BaselineParams.Method.PERCENTILE && defaultBp.windowPx >= 3 && defaultBp.windowPx <= 15) {
            System.out.println("[TEST] ✅ Default config PASSED");
        } else {
            System.out.println("[TEST] ❌ Default config FAILED");
        }
        
        System.out.println("[TEST] Config integration test completed");
    }
    
    private static Map<String,Object> createSdsConfig() {
        Map<String,Object> detect = new HashMap<>();
        
        Map<String,Object> baseline = new HashMap<>();
        baseline.put("method", "percentile");
        baseline.put("window_frac", 0.02);
        baseline.put("window_px", 0);
        baseline.put("clamp_min_px", 3);
        baseline.put("clamp_max_px", 12);
        baseline.put("quantile", 0.10);
        
        detect.put("baseline", baseline);
        
        Map<String,Object> config = new HashMap<>();
        config.put("detect", detect);
        
        return config;
    }
    
    private static Map<String,Object> createColonyConfig() {
        Map<String,Object> detect = new HashMap<>();
        
        Map<String,Object> baseline = new HashMap<>();
        baseline.put("method", "none");
        baseline.put("window_frac", 0.01);
        baseline.put("clamp_min_px", 2);
        baseline.put("clamp_max_px", 8);
        
        detect.put("baseline", baseline);
        
        Map<String,Object> config = new HashMap<>();
        config.put("detect", detect);
        
        return config;
    }
}