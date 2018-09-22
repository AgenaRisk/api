package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.CalculationException;
import com.agenarisk.api.exception.FileIOException;
import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.model.interfaces.IDContainer;
import com.agenarisk.api.model.interfaces.Storable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.sling.commons.json.JSONObject;
import uk.co.agena.minerva.model.MessagePassingLinkException;
import uk.co.agena.minerva.model.PropagationException;
import uk.co.agena.minerva.model.PropagationTerminatedException;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.scenario.ScenarioNotFoundException;
import uk.co.agena.minerva.util.Environment;
import uk.co.agena.minerva.util.io.FileHandlingException;

/**
 *
 * @author Eugene Dementiev
 */
public class Model implements IDContainer<ModelException, Network>, Storable {
	
	private final Map<String, Network> networks = Collections.synchronizedMap(new HashMap<>());
	
	private final uk.co.agena.minerva.model.Model logicModel;
	
	private Model(){
		
		String outputMode = "system";
		if (Environment.isGuiMode()){
			outputMode = "all";
		}
		
		try {
			logicModel = uk.co.agena.minerva.model.Model.createEmptyModel(outputMode);
			logicModel.removeExtendedBNs(logicModel.getExtendedBNList().getExtendedBNs(), true);
			logicModel.removeScenario(logicModel.getScenarioAtIndex(0));
		}
		catch (uk.co.agena.minerva.model.ModelException | ScenarioNotFoundException ex){
			throw new AgenaRiskRuntimeException("Failed to initialise the model", ex);
		}
	}
	
	public static Model createModel(){
		return new Model();
	}
	
	public static Model createFromJSON(JSONObject json){
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
	
	@Override
	public JSONObject toJSONObject() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
	
	public Network createNetwork(String id, String name) throws ModelException {
		synchronized (IDContainer.class){
			if (networks.containsKey(id)){
				throw new ModelException("Network with id `" + id + "` already exists");
			}
			networks.put(id, null);
		}
		
		Network network;
		
		try {
			network = Network.createNetwork(this, id, name);
			networks.put(id, network);
		}
		catch (AgenaRiskRuntimeException ex){
			networks.remove(id);
			throw new ModelException("Failed to add network `" + id + "`", ex);
		}
		
		return network;
	}
	
	public Network createNetwork(JSONObject json) throws ModelException {
		throw new ModelException("Not implemented");
	}

	/**
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public Map<String, Network> getIDMap() {
		return networks;
	}

	/**
	 * @throws com.agenarisk.api.exception.ModelException
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwIdExistsException(String id) throws ModelException {
		throw new ModelException("Network with ID `" + id + "` already exists");
	}
	
	/**
	 * @throws com.agenarisk.api.exception.ModelException
	 * @deprecated For internal use only
	 */
	@Override
	@Deprecated
	public void throwOldIdNullException(String id) throws ModelException {
		throw new ModelException("Can't change Network ID to `" + id + "` because the Network does not exist in this Model or old ID is null");
	}

	
//	public static void load(String filePath) throws FileHandlingException, JSONException {
//		String XMLString = FileHandler.readFileAsString(filePath, Charset.forName("UTF-8"));
//		XML2JSONConverter.parseXMLAsJSON(XMLString);
//	}
//	
//	public Model(JSONObject jsonModel){
//		
//	}
	
	public Network getNetwork(String id){
		return networks.get(id);
	}

	/**
	 * Returns a copy of ID-Network map. Once generated, membership of this map is not maintained.
	 * @return copy of ID-Network map
	 */
	public Map<String, Network> getNetworks() {
		return new HashMap<>(networks);
	}

	protected uk.co.agena.minerva.model.Model getLogicModel() {
		return logicModel;
	}
	
	public void calculate() throws CalculationException {

		// Temp
		if (getLogicModel().getScenarioList().getScenarios().isEmpty()){
			getLogicModel().addScenario("Scenario 1");
		}
		// Temp
		
		try {
			getLogicModel().calculate();
			if (!getLogicModel().isLastPropagationSuccessful()){
				throw new CalculationException("Calculation failed");
			}
		}
		catch (ExtendedBNException | MessagePassingLinkException | PropagationException | PropagationTerminatedException ex){
			throw new CalculationException("Calculation failed", ex);
		}
	}
	
	public void save(String path) throws FileIOException {
		try {
			getLogicModel().save(path);
		}
		catch (FileHandlingException ex){
			throw new FileIOException("Failed to save the model", ex);
		}
	}

}
