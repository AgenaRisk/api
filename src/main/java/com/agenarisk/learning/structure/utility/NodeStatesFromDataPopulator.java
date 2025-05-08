package com.agenarisk.learning.structure.utility;

import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.CsvReader;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.logger.BLogger;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author Eugene Dementiev
 */
public class NodeStatesFromDataPopulator {
	public static void populate(Network network, Path dataPath){
		HashMap<String, LinkedHashMap<String, Integer>> frequencyTables = new HashMap<>();
		HashMap<String, Integer> variableFrequency = new HashMap<>();
		try {
			List<List<String>> data = CsvReader.readCsv(dataPath);

			if (data.isEmpty() || data.get(0).isEmpty()){
				throw new StructureLearningException("No data loaded from " + dataPath);
			}

			for (int r = 0; r < data.size(); r++) {
				for (int c = 0; c < data.get(r).size(); c++) {
					String varName = data.get(0).get(c);
					String cellValue = data.get(r).get(c);
					if (r == 0){
						// Cell value is variable name
						frequencyTables.put(varName, new LinkedHashMap<>());
						variableFrequency.put(varName, 0);
					}
					else {
						variableFrequency.put(varName, variableFrequency.get(varName)+1);
						// Cell value is state name, numeric or missing
						if (frequencyTables.get(varName).get(cellValue) == null){
							frequencyTables.get(varName).put(cellValue, 0);
						}
						frequencyTables.get(varName).put(cellValue, frequencyTables.get(varName).get(cellValue)+1);
					}
				}
			}
		}
		catch(IOException ex){
			throw new StructureLearningException("Unable to read data from file", ex);
		}

		for(Node node: network.getNodeList()){
			try {
				node.setStates(frequencyTables.get(node.getId()).keySet().stream().collect(Collectors.toList()));
			}
			catch (Exception ex){
				BLogger.logConditional("Unable to set states from frequency data for node " + node.getId());
				BLogger.logThrowable(ex);
			}
		}
	}
}
