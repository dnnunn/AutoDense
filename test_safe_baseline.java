import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.IJ;
import com.betterdairy.autodense.analysis.LaneDetector;
import com.betterdairy.autodense.model.Models.Lane;
import java.util.List;

/**
 * Quick test to verify the safe baseline system is working in LaneDetector.findLanes()
 */
public class test_safe_baseline {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java test_safe_baseline <image_path>");
            System.exit(1);
        }
        
        String imagePath = args[0];
        System.out.println("[TEST] Loading image: " + imagePath);
        
        ImagePlus imp = IJ.openImage(imagePath);
        if (imp == null) {
            System.err.println("[TEST] ERROR: Could not load image: " + imagePath);
            System.exit(1);
        }
        
        System.out.printf("[TEST] Image loaded: %dx%d pixels%n", imp.getWidth(), imp.getHeight());
        
        // Test lane detection with expected count of 15 lanes
        int expectedLanes = 15;
        System.out.printf("[TEST] Calling LaneDetector.findLanes() with expectedCount=%d%n", expectedLanes);
        
        List<Lane> lanes = LaneDetector.findLanes(imp, expectedLanes, false);
        
        System.out.printf("[TEST] RESULT: Found %d lanes (expected %d)%n", lanes.size(), expectedLanes);
        
        if (lanes.size() > 0) {
            System.out.println("[TEST] SUCCESS: Safe baseline system is working!");
            System.out.println("[TEST] Lane details:");
            for (int i = 0; i < Math.min(5, lanes.size()); i++) {
                Lane lane = lanes.get(i);
                System.out.printf("  Lane %d: x-range [%d, %d]%n", 
                    lane.getId(), lane.getXLeft(), lane.getXRight());
            }
        } else {
            System.out.println("[TEST] WARNING: No lanes detected - may indicate issue with baseline system");
        }
    }
}