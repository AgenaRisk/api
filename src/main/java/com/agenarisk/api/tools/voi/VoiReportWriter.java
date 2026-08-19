package com.agenarisk.api.tools.voi;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringEscapeUtils;
import com.agenarisk.api.tools.ChartImage;
import uk.co.agena.minerva.util.model.NodeBNPair;
import uk.co.agena.minerva.model.Model;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.scenario.Scenario;
import uk.co.agena.minerva.util.Config;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.helpers.CompatibilityMediator;

/**
 * Core HTML report generator for VOI analysis.
 * Returns the rendered HTML string; no disk I/O — the desktop writes the file.
 */
public class VoiReportWriter {

    private static final int GRAPH_WIDTH = 900;

    private VoiAnalyser voiAnalyser;
    private NodeBNPair decisionNode;
    private NodeBNPair utilityNode;

    NumberFormat numberFormat = NumberFormat.getInstance();
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    {
        int decPlaces = 3;
        numberFormat.setGroupingUsed(false);
        numberFormat.setMaximumFractionDigits(decPlaces);
        numberFormat.setMinimumFractionDigits(0);
    }

    /**
     * Generates the HTML report for a completed VOI analysis.
     *
     * @param analyser the completed VoiAnalyser
     * @param charts ignored for VOI (template uses JS charts); pass empty list
     * @return fully rendered HTML string
     */
    public String generateHtml(VoiAnalyser analyser, List<ChartImage> charts) {
        this.voiAnalyser = analyser;
        this.decisionNode = analyser.getDecisionNode();
        this.utilityNode = analyser.getUtilityNode();

        Model model = analyser.model;
        Scenario scenario = analyser.scenario;
        ExtendedBN ebn = decisionNode.getBN();

        InputStream input_template = getClass().getResourceAsStream(
                "/uk/co/agena/minerva/guicomponents/valueofinformation/templates/voi_report_multiple_unc.html.tmp");
        String out = new BufferedReader(new InputStreamReader(input_template))
                .lines().collect(Collectors.joining("\n"));

        String lib_path = Config.getDirectoryWorking() + "lib" + Environment.FILE_SEPARATOR
                + "lib" + Environment.FILE_SEPARATOR + "js" + Environment.FILE_SEPARATOR;
        String license_path = Config.getDirectoryWorking() + "lib" + Environment.FILE_SEPARATOR
                + "lib" + Environment.FILE_SEPARATOR + "licenses" + Environment.FILE_SEPARATOR;
        String js_path = StringEscapeUtils.escapeJava(
                (new File(lib_path)).toURI().toString().replaceFirst("file:/+", "file:///"));
        if (!js_path.endsWith("/")) js_path += "/";

        String model_file_name = "";
        String bfModelPath = model.getOriginalBFModelPath();
        if (bfModelPath != null) model_file_name = new File(bfModelPath).getName();
        Model currModel = CompatibilityMediator.getCurrentModel();
        if (currModel != null && currModel.getFilePathAbsolute() != null) {
            model_file_name = new File(currModel.getFilePathAbsolute()).getName();
        }

        boolean single_unc_node = analyser.uncertaintyNodes.size() == 1;

        out = fillTemplate(out, new Object[][]{
            {"date_time",  ZonedDateTime.now(Config.TIMEZONE).format(Config.DATE_TIME_FORMAT)},
            {"scenario",   scenario.getName().getShortDescription()},
            {"ebn",        ebn.getName().getShortDescription() + " [" + ebn.getConnID() + "]"},
            {"model",      StringEscapeUtils.escapeHtml4(model_file_name)},
            {"installation_lib_path",           js_path},
            {"installation_lib_path_exact",     StringEscapeUtils.escapeJava(lib_path)},
            {"installation_license_path_exact", StringEscapeUtils.escapeJava(license_path)},
            {"duration",   analyser.startTime.until(analyser.endTime, ChronoUnit.MILLIS) + " ms"},
            {"emv",        numberFormat.format(analyser.emv)},
            {"evppis",     generateEVPPIRows()},
            {"dec_name",   formatNodeNameIDEscaped(decisionNode.getNode())},
            {"unc_names",  generateListOfUncNodes()},
            {"util_name",  formatNodeNameIDEscaped(utilityNode.getNode())},
            {"optimisation", analyser.isMaximiseUtility() ? "maximum" : "minimum"},
            {"hide_if_single_unc", single_unc_node ? "hide" : ""},
            {"graph_evppi", single_unc_node ? "" : generateGraph(analyser.evppi)},
            {"graph_evpi",  single_unc_node ? "" : generateGraph(analyser.evpi)}
        });

        return out;
    }

    private String generateEVPPIRows() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < voiAnalyser.getUncertaintyNodes().size(); i++) {
            out.append(generateEVPPIRow(i, voiAnalyser.getUncertaintyNodes().get(i)));
        }
        return out.toString();
    }

    private String generateEVPPIRow(int uncertaintyNodeNumber, NodeBNPair uncertaintyNode) {
        ExtendedNode en = uncertaintyNode.getNode();
        InputStream input_template = getClass().getResourceAsStream(
                "/uk/co/agena/minerva/guicomponents/valueofinformation/templates/evppirow.html.tmp");
        String out = new BufferedReader(new InputStreamReader(input_template))
                .lines().collect(Collectors.joining("\n"));

        out = fillTemplate(out, new Object[][]{
            {"chance_node",       StringEscapeUtils.escapeHtml4(en.getName().getShortDescription()) + " [" + en.getConnNodeId() + "]"},
            {"evpi",              formatNumberInf(voiAnalyser.evpi[uncertaintyNodeNumber])},
            {"evppi",             formatNumberInf(voiAnalyser.evppi[uncertaintyNodeNumber])},
            {"states_table_content", generateUncertaintyNodeTable(uncertaintyNodeNumber, decisionNode)},
            {"evpi_eq",           StringEscapeUtils.escapeHtml4(voiAnalyser.evpiEquation[uncertaintyNodeNumber])},
            {"evppi_eq",          StringEscapeUtils.escapeHtml4(voiAnalyser.evppiEquation[uncertaintyNodeNumber])
                                  + " = " + formatNumberInf(voiAnalyser.evppi[uncertaintyNodeNumber])},
            {"collapsed-status",  voiAnalyser.uncertaintyNodes.size() > 1 ? "collapsed" : "expanded"}
        });
        return out;
    }

    private String generateUncertaintyNodeTable(int uncertaintyNodenumber, NodeBNPair decisionNode) {
        StringBuilder html = new StringBuilder();
        NodeBNPair uncertaintyNode = voiAnalyser.getUncertaintyNodes().get(uncertaintyNodenumber);
        ExtendedNode uncn = uncertaintyNode.getNode();
        ExtendedNode decn = decisionNode.getNode();

        html.append("<tr><td colspan='2'></td><th colspan='").append(decn.getExtendedStates().size()).append("'>");
        html.append(StringEscapeUtils.escapeHtml4(decn.getName().getShortDescription())).append("</th></tr>");
        html.append("<tr><td colspan='2'></td>");
        for (ExtendedState es : (List<ExtendedState>) decn.getExtendedStates()) {
            html.append("<td>").append(StringEscapeUtils.escapeHtml4(es.getName().getShortDescription())).append("</td>");
        }
        html.append("</tr>");

        for (int i = 0; i < uncertaintyNode.getNode().getExtendedStates().size(); i++) {
            html.append("<tr>");
            if (i == 0) {
                String unc_name = StringEscapeUtils.escapeHtml4(formatChanceStateName(uncn.getName().getShortDescription()));
                html.append("<th class='vertical-wrapper' rowspan='").append(uncn.getExtendedStates().size()).append("'>");
                html.append("<span class=''>").append(unc_name).append("</span></th>");
            }
            ExtendedState uncertaintyState = uncn.getExtendedStateAtIndex(i);
            html.append("<td>").append(StringEscapeUtils.escapeHtml4(uncertaintyState.getName().getShortDescription())).append("</td>");

            Map<Integer, Double> evpis = voiAnalyser.evpis[uncertaintyNodenumber].get(uncertaintyState.getId());
            for (int j = 0; j < decn.getExtendedStates().size(); j++) {
                html.append("<td class='align-right'>");
                double value = evpis.get(((ExtendedState) decn.getExtendedStates().get(j)).getId());
                String cell_content = (Double.isNaN(value) || Double.isInfinite(value)) ? "&mdash;" : numberFormat.format(value);
                if (value == voiAnalyser.evpiExtreme[uncertaintyNodenumber][i]) {
                    cell_content = "<span class='bold'>" + cell_content + "</span>";
                }
                html.append(cell_content).append("</td>");
            }
            html.append("</tr>");
        }
        return html.toString();
    }

    private String formatChanceStateName(String state_name) {
        String[] parts = state_name.split(" - ");
        if (parts.length != 2) return state_name;
        try {
            return formatNumberInf(Double.parseDouble(parts[0])) + " - " + formatNumberInf(Double.parseDouble(parts[1]));
        } catch (NumberFormatException e) {
            return state_name;
        }
    }

    private String formatNodeNameIDEscaped(ExtendedNode en) {
        return StringEscapeUtils.escapeHtml4(en.getName().getShortDescription() + " [" + en.getConnNodeId() + "]");
    }

    private String generateListOfUncNodes() {
        StringBuilder html = new StringBuilder();
        for (NodeBNPair nb : voiAnalyser.getUncertaintyNodes()) {
            html.append("<p>").append(formatNodeNameIDEscaped(nb.getNode())).append("</p>");
        }
        return html.toString();
    }

    public String generateGraph(double[] evpis) {
        StringBuilder html = new StringBuilder();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        TreeMap<Integer, Double> evpis_map = new TreeMap<>();
        for (int i = 0; i < evpis.length; i++) {
            evpis_map.put(i, evpis[i]);
            if (evpis[i] < min) min = evpis[i];
            if (evpis[i] > max) max = evpis[i];
        }

        ArrayList<Map.Entry<Integer, Double>> evpis_list = new ArrayList<>(evpis_map.entrySet());
        Collections.sort(evpis_list, (o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));

        boolean has_negatives = min < 0;
        boolean has_positives = max > 0;
        boolean double_scales = has_negatives && has_positives;
        boolean full_infinity = double_scales && Double.isInfinite(min) && Double.isInfinite(max);

        double scale_width = double_scales ? (max - min) : Math.max(Math.abs(min), Math.abs(max));
        double scale_proportion = GRAPH_WIDTH / scale_width;

        for (Map.Entry<Integer, Double> evpi_entry : evpis_list) {
            int i = evpi_entry.getKey();
            double evpi = evpis[i];
            boolean negative = evpi < 0;
            int width = (int)(Math.abs(evpi) * scale_proportion);
            if (Double.isInfinite(evpi)) width = full_infinity ? GRAPH_WIDTH / 2 : GRAPH_WIDTH;

            String uncn_name = "Var";
            try {
                uncn_name = voiAnalyser.uncertaintyNodes.get(i).getNode().getName().getShortDescription();
            } catch (NullPointerException e) {
                uncn_name = "Var" + ((int) Math.ceil(Math.random() * 1000));
            }

            String row_class = negative ? "negative" : "";
            row_class = Double.isFinite(evpi) ? row_class : "nani";

            html.append(generateGraphRow(width, negative ? width : 0, row_class,
                    StringEscapeUtils.escapeHtml4(uncn_name), formatNumberInf(evpi)));
            html.append("\n");
        }

        InputStream input_template = getClass().getResourceAsStream(
                "/uk/co/agena/minerva/guicomponents/valueofinformation/templates/evpi_graph.html.tmp");
        String out = new BufferedReader(new InputStreamReader(input_template))
                .lines().collect(Collectors.joining("\n"));
        return fillTemplate(out, new Object[][]{{"graph_evpi_rows", html.toString()}});
    }

    private String generateGraphRow(int width, int margin, String row_class, String uncn_name, String evpi) {
        InputStream input_template = getClass().getResourceAsStream(
                "/uk/co/agena/minerva/guicomponents/valueofinformation/templates/graph_row.html.tmp");
        String out = new BufferedReader(new InputStreamReader(input_template))
                .lines().collect(Collectors.joining("\n"));
        return fillTemplate(out, new Object[][]{
            {"width", width}, {"margin", margin}, {"class", row_class}, {"uncn_name", uncn_name}, {"evpi", evpi}
        });
    }

    private String fillTemplateVariable(String current_html, String variable_name, Object variable_value) {
        return current_html.replaceAll("\\{\\%" + variable_name + "\\%\\}", variable_value + "");
    }

    public String fillTemplate(String current_html, Object[][] pairs) {
        for (Object[] pair : pairs) {
            current_html = fillTemplateVariable(current_html, pair[0] + "", pair[1]);
        }
        return current_html;
    }

    private String formatNumberInf(Double value) {
        if (value == Double.POSITIVE_INFINITY) return "&infin;";
        if (value == Double.NEGATIVE_INFINITY) return "&ndash;&infin;";
        return numberFormat.format(value);
    }
}
