package com.agenarisk.api.model;

import com.agenarisk.api.exception.AgenaRiskRuntimeException;
import com.agenarisk.api.exception.LinkException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import uk.co.agena.minerva.model.ConstantMessagePassingLink;
import uk.co.agena.minerva.model.ConstantStateMessagePassingLink;
import uk.co.agena.minerva.model.ConstantSummaryMessagePassingLink;
import uk.co.agena.minerva.model.MessagePassingLink;
import uk.co.agena.minerva.model.MessagePassingLinks;
import uk.co.agena.minerva.model.ModelEvent;
import uk.co.agena.minerva.model.corebn.CoreBNException;
import uk.co.agena.minerva.model.extendedbn.ContinuousEN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;
import uk.co.agena.minerva.model.extendedbn.ExtendedBNException;
import uk.co.agena.minerva.model.extendedbn.ExtendedNode;
import uk.co.agena.minerva.model.extendedbn.ExtendedNodeFunction;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;
import uk.co.agena.minerva.model.extendedbn.LabelledEN;
import uk.co.agena.minerva.model.extendedbn.RankedEN;
import uk.co.agena.minerva.util.helpers.MathsHelper;
import com.agenarisk.api.model.interfaces.Storable;
import org.json.JSONObject;
import uk.co.agena.minerva.model.extendedbn.DiscreteRealEN;
import uk.co.agena.minerva.util.model.MinervaRangeException;
import uk.co.agena.minerva.util.model.NameDescription;
import uk.co.agena.minerva.util.model.Variable;
import uk.co.agena.minerva.util.nptgenerator.Arithmetic;
import uk.co.agena.minerva.util.nptgenerator.Normal;

/**
 * CrossNetworkLink represents a link between Nodes in different Networks.
 * <br>
 * It is a special case of a general Link due to the underlying requirements of AgenaRisk logic.
 * 
 * @author Eugene Dementiev
 */
public class CrossNetworkLink extends Link implements Storable {
	
	/**
	 * This is set of fields for input/output to XML and JSON format
	 */
	public static enum Field {
		sourceNetwork,
		targetNetwork,
		sourceNode,
		targetNode,
		type,
		passState
	}
	
	/**
	 * Possible types of data that the Link will pass
	 */
	public static enum Type {
		Marginals,
		Mean,
		Median,
		Variance,
		StandardDeviation,
		LowerPercentile,
		UpperPercentile,
		State
	}
	
	/**
	 * The underlying logical link in AgenaRisk Extended classes
	 */
	private MessagePassingLink logicLink;
	
	/**
	 * The type of the CrossNetworkLink
	 */
	private Type type = null;
	
	/**
	 * The state to pass (if any)
	 */
	private String stateToPass = null;
	
	/**
	 * Constructor for CrossNetworkLink.
	 * <br>
	 * Only sets instance variable values, does not do any checks and does not create the underlying logical link.
	 * <br>
	 * Should only be used by the createCrossNetworkLink() factory method.
	 * 
	 * @param fromNode the source Link node
	 * @param toNode the target Link node
	 * @param type the type of CrossNetworkLink
	 * @param stateToPass state to pass (if any)
	 */
	private CrossNetworkLink(Node fromNode, Node toNode, Type type, String stateToPass) {
		super(fromNode, toNode);
		this.type = type;
		this.stateToPass = stateToPass;
	}
	
	/**
	 * Factory method to create a CrossNetworkLink instance.
	 * <br>
	 * Does not create the underlying logical link.
	 * <br>
	 * Should be used by Node.linkNodes().
	 * 
	 * @param fromNode the source Link node
	 * @param toNode the target Link node
	 * @param type the type of CrossNetworkLink
	 * @param stateToPass state to pass (if any)
	 * @return CrossNetworkLink created
	 * @throws LinkException if source and target are in the same Network; no CrossNetworkLink type specified; type passes state, but state not specified; type does not pass a state but state specified
	 */
	protected static CrossNetworkLink createCrossNetworkLink(Node fromNode, Node toNode, Type type, String stateToPass) throws LinkException{
		if (Objects.equals(fromNode.getNetwork(), toNode.getNetwork())){
			throw new LinkException("Trying to link nodes in same network by a cross network link");
		}
		
		if (type == null){
			throw new LinkException("Cross network link type not specified");
		}
		
		if (type.equals(Type.State) && stateToPass == null){
			throw new LinkException("State to pass not provided");
		}
		
		if (stateToPass != null && !type.equals(Type.State)){
			throw new LinkException("Link type `"+type.toString()+"` does not pass a state `" + stateToPass + "`");
		}
		
		CrossNetworkLink link = new CrossNetworkLink(fromNode, toNode, type, stateToPass);
		
		return link;
	}
	
	
	/**
	 * This will create a link in the underlying logic.
	 * <br>
	 * The underlying table of the child node will be reset to some default value.
	 * 
	 * @throws LinkException if logical link already exists or target Node already has parents; link has invalid type; or error/inconsistency in the underlying logic
	 */
	@Override
	protected void createLogicLink() throws LinkException {
		if (logicLink != null){
			throw new LinkException("Link already created");
		}
		
		ExtendedBN ebn1 = getFromNode().getNetwork().getLogicNetwork();
		ExtendedNode en1 = getFromNode().getLogicNode();
		
		ExtendedBN ebn2 = getToNode().getNetwork().getLogicNetwork();
		ExtendedNode en2 = getToNode().getLogicNode();
		
		
		try {
			en2.setConnectableInputNode(true);
		}
		catch (ExtendedBNException ex){
			throw new LinkException("Logic node " + getToNode().toStringExtra() + " already has parents", ex);
		}
		en1.setConnectableOutputNode(true);
		
		if (Type.Marginals.equals(type)){
			// Passing state marginals
			if (en2 instanceof LabelledEN || en2 instanceof DiscreteRealEN || en2 instanceof RankedEN){
				// If target is labelled node, just copy states from source to target
				List<ExtendedState> states = new ArrayList<>();
				for(ExtendedState es1: (List<ExtendedState>)en1.getExtendedStates()){
					ExtendedState es2 = new ExtendedState();
					es2.setName(new NameDescription(es1.getName().getShortDescription(), es1.getName().getLongDescription()));
					es2.setRange(es1.getRange());
					es2.setNumericalValue(es1.getNumericalValue());
					es2.setId(es1.getId());
					states.add(es2);
				}

				try {
					en2.setExtendedStates(states);
				}
				catch (CoreBNException ex){
					throw new LinkException("Failed to create states for link pass " + toStringExtra(), ex);
				}
			}
			else {
				// Passing numeric marginals
				List parameters = new ArrayList();
				parameters.add("0");
				parameters.add("1000000");
				ExtendedNodeFunction enf = new ExtendedNodeFunction(Normal.displayName, parameters);
				((ContinuousEN)en2).setExpression(enf);
			}

			logicLink = new MessagePassingLink(ebn2.getId(), ebn1.getId(), en2.getId(), en1.getId());
		}
		else if (Type.State.equals(type)){
			// Translating a state
			String stateName = stateToPass;

			// ID of the selected state
			int stateID = -1;
			for(ExtendedState es: (List<ExtendedState>)en1.getExtendedStates()){
				String esname = es.getName().getShortDescription();
				if (esname.equalsIgnoreCase(stateName)){
					stateID = es.getId();
				}
			}

			if (stateID == -1){
				throw new LinkException("Invalid state being passed in link " + toStringExtra());
			}

			// Create constant to translate the source node
			String constantName = ConstantMessagePassingLink.createConstantName(en1);
			try {
				Variable variable = ebn2.addExpressionVariable(en2, constantName, new Double(0), false);
				variable.setValueSet(true);
			}
			catch (ExtendedBNException ex){
				throw new LinkException("Failed to create variable for link " + toStringExtra(), ex);
			}

			// Create states for target node
			if (en2 instanceof ContinuousEN && !(en2 instanceof RankedEN)){
				// Translating probability of a state
				// Create a single state of range 0 - 1 to express this probability
				List extendedStates = new ArrayList();
				ExtendedState estate;
				try {
					estate = ExtendedState.createContinuousIntervalState(0, 1);
					extendedStates.add(estate);
					en2.setExtendedStates(extendedStates);
				}
				catch (MinervaRangeException | CoreBNException ex){
					// Should not happen
					throw new LinkException("Failed to create a state for input node in " + toStringExtra(), ex);
				}

				// Set target node to use function equal to the constant
				List parameters = new ArrayList();
				parameters.add(constantName);
				ExtendedNodeFunction enf = new ExtendedNodeFunction(Arithmetic.displayName, parameters);
				en2.setExpression(enf);
			}
			else {
				// Copy states
				List<ExtendedState> states = new ArrayList<>();
				for(ExtendedState es: (List<ExtendedState>)en1.getExtendedStates()){
					String esname = es.getName().getShortDescription();
					states.add(ExtendedState.createLabelledState(esname, esname));
				}
				try {
					en2.setExtendedStates(states);
				}
				catch (CoreBNException ex){
					throw new LinkException("Failed to copy states to input node in " + toStringExtra(), ex);
				}
			}

			logicLink = new ConstantStateMessagePassingLink(stateID, constantName, ebn2.getId(), ebn1.getId(), en2.getId(), en1.getId());
		}
		else {
			// Translating a summary statistic
			// Create constant to translate the source node
			String constantName = ConstantMessagePassingLink.createConstantName(en1);
			try {
				Variable variable = ebn2.addExpressionVariable(en2, constantName, new Double(0), false);
				variable.setValueSet(true);
			}
			catch (ExtendedBNException ex){
				throw new IllegalArgumentException("Failed to create variable for link pass from `"+ebn1.getConnID()+"`.`"+en1.getConnNodeId()+"` to `"+ebn2.getConnID()+"`.`"+en2.getConnNodeId()+"`", ex);
			}

			// Set target node to use function equal to the constant
			List parameters = new ArrayList();
			parameters.add(constantName);
			ExtendedNodeFunction enf = new ExtendedNodeFunction(Arithmetic.displayName, parameters);
			en2.setExpression(enf);

			// Determine statistic to pass
			MathsHelper.SummaryStatistic summaryStat;

			if (null == type){
				throw new LinkException("Invalid cross network link type: `"+type+"`");
			}
			
			switch (type) {
				case Mean:
					summaryStat = MathsHelper.SummaryStatistic.MEAN;
					break;
				case Median:
					summaryStat = MathsHelper.SummaryStatistic.MEDIAN;
					break;
				case StandardDeviation:
					summaryStat = MathsHelper.SummaryStatistic.STANDARD_DEVIATION;
					break;
				case Variance:
					summaryStat = MathsHelper.SummaryStatistic.VARIANCE;
					break;
				case LowerPercentile:
					summaryStat = MathsHelper.SummaryStatistic.LOWER_PERCENTILE;
					break;
				case UpperPercentile:
					summaryStat = MathsHelper.SummaryStatistic.UPPER_PERCENTILE;
					break;
				default:
					throw new LinkException("Invalid cross network link type: `"+type+"`");
			}

			logicLink = new ConstantSummaryMessagePassingLink(summaryStat,constantName, ebn2.getId(),  ebn1.getId(), en2.getId(), en1.getId());
		}
		
		uk.co.agena.minerva.model.Model model = getFromNode().getNetwork().getModel().getLogicModel();
		MessagePassingLinks mpls = new MessagePassingLinks();
		mpls.setId(model.getMessagePassingLinks().size());
		mpls.getLinks().add(logicLink);
		model.getMessagePassingLinks().add(mpls);
		model.fireModelChangedEvent(model, ModelEvent.MESSAGE_PASSING_LINKS_CHANGED, model.getMessagePassingLinks());
		
	}
	
	/**
	 * Destroys the underlying logic link.
	 * <br>
	 * Has no effect if there is no link or the underlying logical networks do not contain either of the nodes.
	 */
	@Override
	protected void destroyLogicLink() {
		getFromNode().getNetwork().getModel().getLogicModel().removeMessageParseLinks(getFromNode().getLogicNode(), getToNode().getLogicNode());
		logicLink = null;
	}

	/**
	 * Returns the underlying logical link.
	 * 
	 * @return the underlying logical link
	 */
	protected MessagePassingLink getLogicLink() {
		return logicLink;
	}
	
	/**
	 * Returns the type of this cross-network link.
	 * 
	 * @return link type
	 */
	public Type getType(){
		return type;
	}

	/**
	 * Returns the state to pass with this cross-network link.
	 * 
	 * @return the state to pass with this cross-network link or null if not passing a state
	 */
	public String getStateToPass() {
		return stateToPass;
	}
	
	/**
	 * Creates a JSON representing this Link, ready for file storage.
	 * 
	 * @return JSONObject representing this Link
	 */
	@Override
	public JSONObject toJson() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
	
}
