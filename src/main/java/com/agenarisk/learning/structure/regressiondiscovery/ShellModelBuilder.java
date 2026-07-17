package com.agenarisk.learning.structure.regressiondiscovery;

import com.agenarisk.api.exception.NetworkException;
import com.agenarisk.api.exception.NodeException;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.CsvReader;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a fully-typed "shell" model (node types/states declared, no links) directly from raw CSV data and a set of
 * per-column {@link VariableDeclaration}s - no pre-existing {@code .cmpx} model file required. Used by
 * {@link com.agenarisk.learning.structure.config.RegressionStructureSearchExecutor}, which discovers the links
 * itself, so only variable typing is needed up front.
 * <br>
 * Columns without an explicit declaration get a sensible default: numeric columns become a simulated
 * {@code ContinuousInterval} (matching the same "pristine" default athena's Variable Mapping panel already uses for
 * an untouched node), non-numeric columns become {@code Labelled} with states auto-detected from the data's distinct
 * values - a scoped, node-list-aware alternative to
 * {@link com.agenarisk.learning.structure.utility.NodeStatesFromDataPopulator#populate}, which unconditionally
 * overwrites *every* node's states network-wide and would clobber an explicit declaration (e.g. a {@code Ranked}
 * node's deliberate ordering).
 *
 * @author Eugene Dementiev
 */
public class ShellModelBuilder {

	private ShellModelBuilder() {
	}

	/**
	 * @param csvHeaders column names, in CSV column order - each becomes one node id
	 * @param dataPath path to the CSV data (only read for undeclared columns, to auto-detect numeric-ness/states)
	 * @param declarations explicit per-column declarations, keyed by column/node id; columns not present here get
	 * the numeric/non-numeric default described above
	 *
	 * @return the built shell Model, with exactly one network and no links
	 */
	public static Model build(List<String> csvHeaders, Path dataPath, Map<String, VariableDeclaration> declarations) {
		try {
			Model model = Model.createModel();
			Network network = model.createNetwork("network");

			for (String columnId : csvHeaders){
				VariableDeclaration declaration = declarations.get(columnId);
				if (declaration == null){
					continue;
				}
				Node node = network.createNode(columnId, declaration.getType());
				if (isContinuousType(declaration.getType()) && declaration.isSimulated()){
					node.convertToSimulated();
				}
				else if (!declaration.getStates().isEmpty()){
					node.setStates(declaration.getStates());
				}
			}

			applyDefaultsForUndeclaredColumns(network, csvHeaders, dataPath, declarations);

			return model;
		}
		catch (NetworkException | NodeException ex){
			throw new StructureLearningException("Failed to build shell model from variable declarations", ex);
		}
	}

	private static void applyDefaultsForUndeclaredColumns(Network network, List<String> csvHeaders, Path dataPath, Map<String, VariableDeclaration> declarations) throws NetworkException, NodeException {

		List<String> undeclaredColumns = new ArrayList<>();
		for (String columnId : csvHeaders){
			if (!declarations.containsKey(columnId)){
				undeclaredColumns.add(columnId);
			}
		}
		if (undeclaredColumns.isEmpty()){
			return;
		}

		Map<String, LinkedHashMap<String, Integer>> frequencyByColumn = new LinkedHashMap<>();
		Map<String, Boolean> numericByColumn = new LinkedHashMap<>();
		for (String columnId : undeclaredColumns){
			frequencyByColumn.put(columnId, new LinkedHashMap<>());
			numericByColumn.put(columnId, true);
		}

		List<List<String>> rows;
		try {
			rows = CsvReader.readCsv(dataPath);
		}
		catch (IOException ex){
			throw new StructureLearningException("Unable to read data from file: " + dataPath, ex);
		}
		if (rows.isEmpty()){
			throw new StructureLearningException("No data loaded from " + dataPath);
		}

		for (int r = 1; r < rows.size(); r++){
			List<String> row = rows.get(r);
			for (int c = 0; c < csvHeaders.size() && c < row.size(); c++){
				String columnId = csvHeaders.get(c);
				LinkedHashMap<String, Integer> frequency = frequencyByColumn.get(columnId);
				if (frequency == null){
					continue;
				}
				String cellValue = row.get(c);
				frequency.merge(cellValue, 1, Integer::sum);
				if (numericByColumn.get(columnId)){
					try {
						Double.parseDouble(cellValue);
					}
					catch (NumberFormatException ex){
						numericByColumn.put(columnId, false);
					}
				}
			}
		}

		for (String columnId : undeclaredColumns){
			boolean numeric = numericByColumn.getOrDefault(columnId, false);
			if (numeric){
				Node node = network.createNode(columnId, Node.Type.ContinuousInterval);
				node.convertToSimulated();
			}
			else {
				Node node = network.createNode(columnId, Node.Type.Labelled);
				node.setStates(new ArrayList<>(frequencyByColumn.get(columnId).keySet()));
			}
		}
	}

	private static boolean isContinuousType(Node.Type type) {
		return type == Node.Type.ContinuousInterval || type == Node.Type.IntegerInterval;
	}
}
