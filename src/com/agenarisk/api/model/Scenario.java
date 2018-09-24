package com.agenarisk.api.model;

import com.agenarisk.api.exception.ModelException;
import com.agenarisk.api.exception.ScenarioException;
import com.agenarisk.api.model.interfaces.Identifiable;
import uk.co.agena.minerva.util.model.NameDescription;

/**
 *
 * @author Eugene Dementiev
 */
public class Scenario implements Identifiable<ScenarioException>{

	private final Model model;
	
	private final uk.co.agena.minerva.model.scenario.Scenario logicScenario;

	private Scenario(Model model, uk.co.agena.minerva.model.scenario.Scenario logicScenario) {
		this.model = model;
		this.logicScenario = logicScenario;
	}
	
	/**
	 * Factory method to create a Scenario and add it to the given Model
	 * To be used by 
	 * @param model Model to create and add Scenario to
	 * @param id unique ID/name of the Scenario
	 * @return created Scenario
	 */
	protected static Scenario createScenario(Model model, String id){
		uk.co.agena.minerva.model.scenario.Scenario logicScenario = model.getLogicModel().addScenario(id);
		Scenario scenario = new Scenario(model, logicScenario);
		return scenario;
	}
	
	/**
	 * Returns the underlying logical ExtendedBN network
	 * @return the underlying logical ExtendedBN network
	 */
	protected final uk.co.agena.minerva.model.scenario.Scenario getLogicScenario() {
		return logicScenario;
	}
	
	/**
	 * Returns the Model that this Scenario belongs to
	 * @return the Model that this Scenario belongs to
	 */
	public final Model getModel() {
		return model;
	}

	/**
	 * Gets the ID of this Scenario
	 * @return the ID of this Scenario
	 */
	@Override
	public final String getId() {
		return getLogicScenario().getName().getShortDescription();
	}
	
	/**
	 * Changes the ID of this Network to the provided ID, if the new ID is not already taken
	 * Will lock IDContainer.class while doing so
	 * @param id the new ID
	 * @throws ScenarioException if fails to change ID
	 */
	@Override
	public final void setId(String id) throws ScenarioException {
		
		try {
			getModel().changeContainedId(this, id);
		}
		catch (ModelException ex){
			throw new ScenarioException("Failed to change ID of Network `" + getId() + "`", ex);
		}
		
		getLogicScenario().setName(new NameDescription(id, id));
	}
}
