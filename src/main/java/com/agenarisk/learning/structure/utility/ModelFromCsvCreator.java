package com.agenarisk.learning.structure.utility;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.logger.BLogger;
import java.util.List;

/**
 *
 * @author Eugene Dementiev
 */
public class ModelFromCsvCreator {
	public static Model create(List<List<String>> lines, String networkId, String networkName){
		if (networkName == null){
			networkName = "";
		}
		
		if (networkName.trim().isEmpty()){
			networkName = "network";
		}
		Model model = Model.createModel();
		Network network = model.createNetwork(networkId, networkName);
		
		if (lines.size() <= 1){
			return model;
		}
		
		for (int i = 1; i < lines.size(); i++) {
			List<String> line = lines.get(i);
			String id1 = line.get(1);
			String id2 = line.get(3);
			String direction = line.get(2);
			
			Node node1 = network.getNode(id1);
			if (node1 == null){
				node1 = network.createNode(id1, Node.Type.Labelled);
			}
			
			Node node2 = network.getNode(id2);
			if (node2 == null){
				node2 = network.createNode(id2, Node.Type.Labelled);
			}
			
			try {
				if (direction.equals("<-")){
					node1.linkFrom(node2);
				}
				else {
					// If undirectional edge discovered, we assume it is directed from 1 to 2
					node1.linkTo(node2);
				}
			}
			catch (Exception ex){
				BLogger.logConditional("Failed to create an edge when building averaged model: "+ex.getMessage());
				BLogger.logThrowableIfDebug(ex);
			}
		}
		
		return model;
	}
}
