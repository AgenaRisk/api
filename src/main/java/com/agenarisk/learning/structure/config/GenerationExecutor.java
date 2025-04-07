package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.CsvReader;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.logger.BLogger;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author Eugene Dementiev
 */
public class GenerationExecutor extends Configurer<GenerationExecutor> implements Executable {
	
	private GenerationConfigurer originalConfigurer;
	
	protected GenerationExecutor(Config config) {
		super(config);
	}
	
	protected GenerationExecutor() {
		super();
	}

	public void setOriginalConfigurer(GenerationConfigurer originalConfigurer) {
		this.originalConfigurer = originalConfigurer;
	}
	
	@Override
	public void execute() throws StructureLearningException {
		try {
			if (originalConfigurer == null){
				return;
			}
			
			List<String> headers = CsvReader.readHeaders(originalConfigurer.getDataPath());
			Model model = Model.createModel();
			Network network = model.createNetwork("network");
			for(String nodeId: headers){
				Node node = network.createNode(nodeId, Node.Type.Labelled);
			}
			
			if (originalConfigurer.isStatesFromData()){
				HashMap<String, LinkedHashMap<String, Integer>> frequencyTables = new HashMap<>();
				HashMap<String, Integer> variableFrequency = new HashMap<>();
				int rowsCount = 0;
				try {
					List<List<String>> data = CsvReader.readCsv(originalConfigurer.getDataPath());

					if (data.isEmpty() || data.get(0).isEmpty()){
						throw new StructureLearningException("No data loaded from " + originalConfigurer.getDataPath());
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
							rowsCount += 1;
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
			
			if (originalConfigurer.getStrategy().equals(GenerationConfigurer.Strategy.random)){
				List<Node> nodes = network.getNodeList();
				int failedAttempt = 0;
				int edgeCount = 0;
				int randMult = nodes.size() - 1;
				while (failedAttempt < 1000 && edgeCount < originalConfigurer.getMaximumEdgeCount()){
					try {
						Node node1 = nodes.get((int)Math.round(Math.random()*randMult));
						Node node2 = nodes.get((int)Math.round(Math.random()*randMult));
						node1.linkTo(node2);
						edgeCount++;
						failedAttempt = 0;
					}
					catch (Exception ex){
						failedAttempt++;
					}
				}
			}
			model.save(originalConfigurer.getModelPath().toString());
			originalConfigurer.setModel(model);
		}
		catch (Exception ex){
			throw new StructureLearningException(ex.getMessage(), ex);
		}
	}

}
