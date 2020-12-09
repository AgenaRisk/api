package com.agenarisk.api.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;

/**
 *
 * @author Eugene Dementiev
 */
public class ModelTest {

	@Test
	public void testRemoveNetwork(){
		Model model = Model.createModel();
		Network net1 = model.createNetwork("test1");
		Network net2 = model.createNetwork("test2");
		
		model.removeNetwork(net1);
		Assertions.assertEquals(1, model.getNetworkList().size());
		Assertions.assertEquals(net2, model.getNetworkList().get(0));
		
		Assertions.assertEquals(1, model.getLogicModel().getExtendedBNList().getExtendedBNs().size());
		Assertions.assertEquals(net2.getId(), ((ExtendedBN) model.getLogicModel().getExtendedBNList().getExtendedBNs().get(0)).getConnID());
		
		model.removeNetwork(net2.getId());
		Assertions.assertEquals(0, model.getNetworkList().size());
		Assertions.assertEquals(0, model.getLogicModel().getExtendedBNList().getExtendedBNs().size());
	}
	
}
