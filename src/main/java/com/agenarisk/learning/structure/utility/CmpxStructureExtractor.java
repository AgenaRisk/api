package com.agenarisk.learning.structure.utility;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.logger.BLogger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;

/**
 *
 * @author Eugene Dementiev
 */
public class CmpxStructureExtractor {
	
	public static ArrayList<List<Object>> extract(Model model, List<Object> headers){
		ArrayList<List<Object>> lines = new ArrayList<>();
		if (headers != null && !headers.isEmpty()){
			lines.add(headers);
		}
		
		try {
			int i = 1;
			ExtendedBN ebn = model.getNetworkList().get(0).getLogicNetwork();
			for(ExtendedNode en: (List<ExtendedNode>) ebn.getExtendedNodes()){
				for (ExtendedNode enParent: (List<ExtendedNode>)ebn.getParentNodes(en)){
					lines.add(Arrays.asList(String.valueOf(i), enParent.getConnNodeId(), "->", en.getConnNodeId()));
					i+=1;
				}
			}
		}
		catch (Exception ex){
			BLogger.logConditional(ex);
		}
		
		return lines;
	}
	
	public static ArrayList<List<Object>> extract(Model model){
		return extract(model, Arrays.asList("ID", "Variable 1", "Dependency", "Variable 2"));
	}
}
