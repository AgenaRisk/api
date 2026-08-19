package com.agenarisk.api.tools.sensitivity_legacy;

public class SensitivityAnalysisSettings {
    public double upperPercentile = 75;
    public double lowerPercentile = 25;
    public double upperPercentileTornado = 100;
    public double lowerPercentileTornado = 0;
    public boolean displayMean = true, displayMedian = true, displayVariance = true;
    public boolean displaySD = true, displayUpperPercentile = true,
            displayLowerPercentile = true;
    public boolean outputTables = false;
    public boolean outputTornados = false;
    public boolean outputROC = false;
    public boolean outputHeatMap = false;

    @Override
    public String toString() {
        return "SensitivityAnalysisSettings{" + "upperPercentile=" + upperPercentile + ", lowerPercentile=" + lowerPercentile + ", upperPercentileTornado=" + upperPercentileTornado + ", lowerPercentileTornado=" + lowerPercentileTornado + ", displayMean=" + displayMean + ", displayMedian=" + displayMedian + ", displayVariance=" + displayVariance + ", displaySD=" + displaySD + ", displayUpperPercentile=" + displayUpperPercentile + ", displayLowerPercentile=" + displayLowerPercentile + ", outputTables=" + outputTables + ", outputTornados=" + outputTornados + ", outputROC=" + outputROC + '}';
    }
}
