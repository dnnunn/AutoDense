package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.analysis.BandQuantification.*;
import ij.ImagePlus;
import ij.gui.Roi;
import java.util.List;
import java.util.ArrayList;

/**
 * @deprecated Use BandDetector instead. This class now delegates all calls to BandDetector.
 * Enhanced band detector with Fiji's proven quantification algorithms.
 * Implements methods from _BandPeakQuantification.ijm by Kenji OHGANE (University of Tokyo).
 */
@Deprecated
public class FijiBandDetector {
    
    /**
     * @deprecated Use BandDetector.quantifyBand() instead.
     * Quantifies a single band using Fiji's proven algorithms.
     * 
     * @param imp The image containing the band
     * @param roi The ROI defining the band boundaries
     * @param region Background sampling region method
     * @param method Statistical method for background calculation
     * @param expansionPixels Number of pixels to expand background region
     * @param channelWeights RGB channel weights (null for default)
     * @param resetScale Whether to reset image scale before measurement
     * @param laneId Lane identifier
     * @param bandId Band identifier
     * @return BandQuantification object with all measurements
     */
    @Deprecated
    public static BandQuantification quantifyBand(ImagePlus imp, Roi roi,
                                                BackgroundRegion region,
                                                BackgroundMethod method,
                                                int expansionPixels,
                                                ChannelWeights channelWeights,
                                                boolean resetScale,
                                                String laneId, String bandId) {
        
        System.out.println("WARNING: FijiBandDetector.quantifyBand() is deprecated. Use BandDetector.quantifyBand() instead.");
        
        return BandDetector.quantifyBand(imp, roi, region, method, expansionPixels, 
                                       channelWeights, resetScale, laneId, bandId);
    }

    /**
     * @deprecated Use BandDetector.quantifyBand() for multiple bands instead.
     * Quantifies multiple bands using Fiji's proven algorithms.
     */
    @Deprecated
    public static List<BandQuantification> quantifyBands(ImagePlus imp, List<Roi> bandRois,
                                                        BackgroundRegion region,
                                                        BackgroundMethod method,
                                                        int expansionPixels,
                                                        ChannelWeights channelWeights,
                                                        boolean resetScale,
                                                        String laneId) {
        
        System.out.println("WARNING: FijiBandDetector.quantifyBands() is deprecated. Use BandDetector.quantifyBand() for each band instead.");
        
        List<BandQuantification> results = new ArrayList<>();
        for (int i = 0; i < bandRois.size(); i++) {
            Roi roi = bandRois.get(i);
            String bandId = "band_" + (i + 1);
            BandQuantification result = BandDetector.quantifyBand(imp, roi, region, method, 
                                                                expansionPixels, channelWeights, 
                                                                resetScale, laneId, bandId);
            results.add(result);
        }
        return results;
    }

    /**
     * @deprecated Use BandDetector.quantifyBand() instead.
     * Standard quantification with default parameters.
     */
    @Deprecated
    public static BandQuantification quantifyBandStandard(ImagePlus imp, Roi roi, 
                                                        String laneId, String bandId) {
        
        System.out.println("WARNING: FijiBandDetector.quantifyBandStandard() is deprecated. Use BandDetector.quantifyBand() instead.");
        
        return BandDetector.quantifyBand(imp, roi, BackgroundRegion.ALL, BackgroundMethod.MEDIAN,
                                       10, ChannelWeights.LUMINANCE, true, laneId, bandId);
    }

    /**
     * @deprecated Use BandDetector.quantifyBand() instead.
     * Robust quantification with conservative parameters.
     */
    @Deprecated
    public static BandQuantification quantifyBandRobust(ImagePlus imp, Roi roi,
                                                      String laneId, String bandId) {
        
        System.out.println("WARNING: FijiBandDetector.quantifyBandRobust() is deprecated. Use BandDetector.quantifyBand() instead.");
        
        return BandDetector.quantifyBand(imp, roi, BackgroundRegion.SIDES, BackgroundMethod.MEDIAN,
                                       15, ChannelWeights.LUMINANCE, true, laneId, bandId);
    }
}