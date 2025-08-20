package com.betterdairy.autodense.model;

public final class Models {
    private Models() {}
    public static record Lane(int index, int xStart, int xEnd) {}
    public static record Band(int index, int y, double area, double baseline, double mwKDa) {}
    public static record CalibrationModel(double a, double b, double r2) {}
}
