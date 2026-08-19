package com.agenarisk.api.tools.hid;

import uk.co.agena.minerva.analysis.hid.d3dt.DT;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;

/**
 * Immutable result produced by HidSolver after a successful solveAsDT() call.
 * Passed to HidReportWriter to generate the HTML report string.
 */
public class HidResult {

    public final DT dt;
    public final ExtendedBN ebn;
    public final String modelFileName;
    /** null when no scenario was selected */
    public final String scenarioName;
    public final long durationMs;
    public final String durationLog;

    public HidResult(DT dt, ExtendedBN ebn, String modelFileName,
                     String scenarioName, long durationMs, String durationLog) {
        this.dt = dt;
        this.ebn = ebn;
        this.modelFileName = modelFileName;
        this.scenarioName = scenarioName;
        this.durationMs = durationMs;
        this.durationLog = durationLog;
    }
}
