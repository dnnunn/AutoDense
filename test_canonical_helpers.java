// Simple test to verify canonical helpers are working
import com.betterdairy.autodense.analysis.BlueIndex;
import com.betterdairy.autodense.analysis.PetriColonyMask;

public class test_canonical_helpers {
    public static void main(String[] args) {
        // Test 1: BlueIndex with blue color
        double blueIndex = BlueIndex.blueIndex(50, 100, 200); // Blue-ish color
        System.out.println("Blue index for (50,100,200): " + blueIndex);
        
        // Test 2: BlueIndex with white color  
        double whiteIndex = BlueIndex.blueIndex(255, 255, 255);
        System.out.println("Blue index for white (255,255,255): " + whiteIndex);
        
        // Test 3: CIELAB b* values
        double bStar = BlueIndex.bStar(50, 100, 200);
        System.out.println("CIELAB b* for blue color: " + bStar);
        
        // Test 4: Colony detection parameters
        PetriColonyMask.ColonyParams params = new PetriColonyMask.ColonyParams();
        System.out.println("Default colony params - minSize: " + params.minSize + 
                          ", maxSize: " + params.maxSize + 
                          ", minCircularity: " + params.minCircularity);
        
        System.out.println("✅ All canonical helpers loaded successfully!");
    }
}