package com.agenarisk.api.tools.hid;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONException;
import com.agenarisk.api.tools.ChartImage;
import uk.co.agena.minerva.util.Config;
import uk.co.agena.minerva.util.Environment;

/**
 * Core HTML report generator for HID analysis.
 * Fills the d3dt index template with result data and returns the HTML string.
 * No disk I/O — the desktop layer writes the returned string to a file.
 */
public class HidReportWriter {

    /**
     * Generates the HTML report for a completed HID analysis.
     *
     * @param result the solved HID result
     * @param charts ignored for HID (template uses JS charts); pass empty list
     * @return the fully rendered HTML string
     * @throws HidException if the tree JSON is malformed or the template cannot be loaded
     */
    public String generateHtml(HidResult result, List<ChartImage> charts) throws HidException {
        String tree_data;
        try {
            tree_data = result.dt.getRoot().toD3JSON().toString(5);
        } catch (JSONException e) {
            throw new HidException("Malformed JSON in tree data", e);
        }
        tree_data = tree_data.replaceAll("\\\\n", "\\\\\\\\n");

        int leaf_count = result.dt.leaves.size();

        InputStream input_template = getClass().getResourceAsStream(
                "/uk/co/agena/minerva/analysis/hid/d3dt/resources/index.html.tmp");
        String out = new BufferedReader(new InputStreamReader(input_template))
                .lines().collect(Collectors.joining("\n"));

        out = fill(out, "dt_name",   StringEscapeUtils.escapeHtml4(result.ebn.getConnID()));
        out = fill(out, "leaf_count", leaf_count + "");
        out = fill(out, "tree_data",  tree_data + "");
        out = fill(out, "max_depth",  result.dt.leaves.get(0).getDepthOriginal() + "");
        out = fill(out, "ebn",
                result.ebn.getName().getShortDescription() + " [" + result.ebn.getConnID() + "]");
        out = fill(out, "model",      StringEscapeUtils.escapeHtml4(result.modelFileName));
        out = fill(out, "date_time",
                ZonedDateTime.now(Config.TIMEZONE).format(Config.DATE_TIME_FORMAT));

        String lib_path = Config.getDirectoryWorking() + "lib" + Environment.FILE_SEPARATOR
                + "lib" + Environment.FILE_SEPARATOR + "js" + Environment.FILE_SEPARATOR;
        File lib_path_file = new File(lib_path);
        String license_path = Config.getDirectoryWorking() + "lib" + Environment.FILE_SEPARATOR
                + "lib" + Environment.FILE_SEPARATOR + "licenses" + Environment.FILE_SEPARATOR;
        String js_path = StringEscapeUtils.escapeJava(
                lib_path_file.toURI().toString().replaceFirst("file:/+", "file:///"));
        if (!js_path.endsWith("/")) js_path += "/";

        if (!Environment.isGuiMode()
                || !lib_path_file.exists()
                || !lib_path_file.isDirectory()
                || lib_path_file.listFiles().length < 5) {
            js_path = "https://resources.agenarisk.com/download/archive/lib/lib/js/";
        }

        out = fill(out, "installation_lib_path",             js_path);
        out = fill(out, "installation_lib_path_exact",       StringEscapeUtils.escapeJava(lib_path));
        out = fill(out, "installation_license_path_exact",   StringEscapeUtils.escapeJava(license_path));
        out = fill(out, "scenario",      result.scenarioName != null ? result.scenarioName : "N/A");
        out = fill(out, "duration",      result.durationMs + " ms");
        out = fill(out, "duration_log",  result.durationLog);

        return out;
    }

    private String fill(String html, String variable, String value) {
        return html.replaceAll("\\{\\%" + variable + "\\%\\}", value);
    }
}
