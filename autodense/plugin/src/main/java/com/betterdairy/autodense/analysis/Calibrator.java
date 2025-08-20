package com.betterdairy.autodense.analysis;

import com.betterdairy.autodense.model.Models.CalibrationModel;

public final class Calibrator {
    private Calibrator() {}

    public static CalibrationModel fit(/* ladder lane, ladder spec */) {
        // Placeholder
        return new CalibrationModel(0.0, 0.0, 0.0);
    }

    public static double assignMw(CalibrationModel m, double distancePx) {
        return Math.pow(10, m.a() * distancePx + m.b());
    }
}
