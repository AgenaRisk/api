package com.agenarisk.api.tools;

/**
 * Presentation-neutral carrier for a pre-rendered chart image.
 * Core report writers accept a list of these; desktop injects them after
 * rendering via JFreeChart.  HID/VOI pass an empty list (they use JS charts).
 */
public class ChartImage {

    public final String key;
    public final byte[] png;

    public ChartImage(String key, byte[] png) {
        this.key = key;
        this.png = png;
    }
}
