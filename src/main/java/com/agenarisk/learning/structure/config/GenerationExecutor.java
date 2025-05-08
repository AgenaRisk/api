package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.CsvReader;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.logger.BLogger;
import com.agenarisk.learning.structure.utility.NodeStatesFromDataPopulator;
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
				NodeStatesFromDataPopulator.populate(network, originalConfigurer.getDataPath());
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
