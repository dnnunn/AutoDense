package autodense.util;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility for exporting ImageJ overlays to PNG files
 */
public final class OverlayExporter {
    
    public static void exportOverlayPNG(ImagePlus imp, File outFile) throws IOException {
        int w = imp.getWidth(), h = imp.getHeight();
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = png.createGraphics();
        g.setComposite(AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Only export from Overlay, never from pixel data
        Overlay ov = imp.getOverlay();
        if (ov != null) {
            for (Roi r : ov.toArray()) {
                // Ensure ROI has proper annotation styling
                if (r.getStrokeColor() == null) {
                    r.setStrokeColor(Color.CYAN); // default annotation color
                }
                if (r.getStrokeWidth() == 0) {
                    r.setStrokeWidth(2); // default annotation width
                }
                r.drawOverlay(g); // respects stroke/fill/labels from Overlay
            }
        }
        
        g.dispose();
        ImageIO.write(png, "PNG", outFile);
    }
}