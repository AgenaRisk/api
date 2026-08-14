package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writing a relation that is known to hold exactly onto a node, instead of fitting one.
 *
 * <p>Some columns are arithmetic on other columns — a total and its parts, a rate and its numerator and
 * denominator. Fitting such a node returns R2 = 1 and a residual variance that only avoids being zero because
 * {@link ContinuousRegressionLearner} floors it, so the fitted node asserts an uncertainty that does not exist,
 * and carries coefficients estimated from a sample when the true ones are known by algebra. Writing
 * {@code Arithmetic(expression)} states the relation instead of approximating it.
 *
 * <p>Shared by regression structure discovery (which fits tables as part of its own run) and regression
 * parameter learning (which fits an existing structure), so a candidate is treated the same way whichever
 * produced it.
 */
public final class DeterministicExpressions {

	private DeterministicExpressions() {
	}

	/**
	 * One declared relation: either a single expression, or one per state of a partition parent when the formula
	 * changes with a category ("in a crisis the total is computed this way, otherwise that way").
	 */
	public static final class Declaration {

		private final String expression;
		private final String partitionedOn;
		private final Map<String, String> byState;

		private Declaration(String expression, String partitionedOn, Map<String, String> byState) {
			this.expression = expression;
			this.partitionedOn = partitionedOn;
			this.byState = byState;
		}

		public static Declaration of(String expression) {
			return new Declaration(expression, null, null);
		}

		public static Declaration partitioned(String partitionedOn, Map<String, String> byState) {
			return new Declaration(null, partitionedOn, byState);
		}

		public boolean isPartitioned() {
			return partitionedOn != null;
		}

		public String getExpression() {
			return expression;
		}

		public String getPartitionedOn() {
			return partitionedOn;
		}

		public Map<String, String> getByState() {
			return byState;
		}
	}

	/**
	 * Read the {@code deterministicExpressions} option: {@code { nodeId: "expr" }} for a plain relation, or
	 * {@code { nodeId: { partitionedOn: "regime", byState: { "crisis": "...", "normal": "..." } } }} when the
	 * formula is selected by a category.
	 *
	 * <p>The per-state form is keyed by STATE rather than by table slot on purpose: the order the engine expects
	 * its partitioned expressions in is a property of the engine (see {@link PartitionEnumerator}), and a caller
	 * computing it independently would mis-map formulas to states without ever producing an error.
	 */
	public static Map<String, Declaration> parse(org.json.JSONObject json) {
		Map<String, Declaration> out = new LinkedHashMap<>();
		if (json == null){
			return out;
		}
		for (String nodeId : json.keySet()){
			String id = nodeId.trim();
			if (id.isEmpty()){
				continue;
			}
			org.json.JSONObject jPartitioned = json.optJSONObject(nodeId);
			if (jPartitioned != null){
				String on = jPartitioned.optString("partitionedOn", "").trim();
				org.json.JSONObject jStates = jPartitioned.optJSONObject("byState");
				if (on.isEmpty() || jStates == null || jStates.isEmpty()){
					continue;
				}
				Map<String, String> byState = new LinkedHashMap<>();
				for (String state : jStates.keySet()){
					String expression = jStates.optString(state, "").trim();
					if (!expression.isEmpty()){
						byState.put(state, expression);
					}
				}
				if (!byState.isEmpty()){
					out.put(id, Declaration.partitioned(on, byState));
				}
				continue;
			}
			String expression = json.optString(nodeId, "").trim();
			if (!expression.isEmpty()){
				out.put(id, Declaration.of(expression));
			}
		}
		return out;
	}

	/**
	 * Why the expression cannot be written onto this node, or null if it can.
	 *
	 * <p>The caller derives these relations from data, not from the model, so the model has to be checked to
	 * agree before one is imposed: a search that honours a directed constraint only softly can leave the node
	 * with different parents, and an expression naming a non-parent cannot be resolved.
	 *
	 * <p>Names are matched by consuming each parent id out of the expression and checking that nothing
	 * alphabetic survives, rather than by tokenising — node ids come from CSV headers and may contain spaces
	 * or punctuation that no identifier pattern would match.
	 */
	public static String rejection(Node node, String expression) {
		if (node == null){
			return "the node is not in this model";
		}
		if (!isContinuous(node)){
			return "node '" + node.getId() + "' is " + node.getType()
					+ ", and an arithmetic expression over discretised states has no meaning";
		}
		Set<Node> parents = node.getParents();
		if (parents.isEmpty()){
			return "node '" + node.getId() + "' has no parents in this structure";
		}
		List<Node> byLongestId = new ArrayList<>(parents);
		byLongestId.sort((a, b) -> b.getId().length() - a.getId().length());
		String remainder = expression;
		for (Node parent : byLongestId){
			if (!isContinuous(parent)){
				return "parent '" + parent.getId() + "' is " + parent.getType() + ", not continuous";
			}
			if (!remainder.contains(parent.getId())){
				return "the expression does not use parent '" + parent.getId() + "'";
			}
			remainder = remainder.replace(parent.getId(), " ");
		}
		for (char c : remainder.toCharArray()){
			if (Character.isLetter(c)){
				return "the expression refers to something that is not a parent of '" + node.getId() + "'";
			}
		}
		return null;
	}

	/**
	 * Apply every declared expression the model agrees with. Returns one outcome line per declared node —
	 * applied or declined with a reason — so the caller can report it rather than let a silently-unapplied
	 * expression pass for an applied one.
	 */
	public static Map<String, String> apply(Network network, Map<String, Declaration> declared) {
		Map<String, String> outcomes = new LinkedHashMap<>();
		if (network == null || declared == null || declared.isEmpty()){
			return outcomes;
		}
		for (Map.Entry<String, Declaration> entry : declared.entrySet()){
			Node node = network.getNode(entry.getKey());
			Declaration declaration = entry.getValue();
			try {
				outcomes.put(entry.getKey(), declaration.isPartitioned()
						? applyPartitioned(node, declaration)
						: applyPlain(node, declaration.getExpression()));
			}
			catch (Exception ex){
				outcomes.put(entry.getKey(), "not applied: " + ex.getMessage());
			}
		}
		return outcomes;
	}

	private static String applyPlain(Node node, String expression) throws Exception {
		String reason = rejection(node, expression);
		if (reason != null){
			return "not applied: " + reason;
		}
		node.setTableFunction("Arithmetic(" + expression + ")");
		return "set to " + expression;
	}

	/**
	 * Write one expression per state of the partition parent.
	 *
	 * <p>The expressions are ordered by {@link PartitionEnumerator}, which knows the order the engine reads a
	 * partitioned table in, and each slot is filled by looking its state up in the declaration. Nothing here
	 * assumes an ordering of its own: a formula silently attached to the wrong state would produce a model that
	 * is confidently wrong rather than one that fails.
	 */
	private static String applyPartitioned(Node node, Declaration declaration) throws Exception {
		if (node == null){
			return "not applied: the node is not in this model";
		}
		if (!isContinuous(node)){
			return "not applied: node '" + node.getId() + "' is " + node.getType()
					+ ", and an arithmetic expression over discretised states has no meaning";
		}
		Node partitionParent = null;
		for (Node parent : node.getParents()){
			if (parent.getId().equals(declaration.getPartitionedOn())){
				partitionParent = parent;
				break;
			}
		}
		if (partitionParent == null){
			return "not applied: '" + declaration.getPartitionedOn() + "' is not a parent of '" + node.getId() + "'";
		}
		if (isContinuous(partitionParent)){
			return "not applied: cannot partition on '" + partitionParent.getId() + "', which is continuous";
		}
		// Every state must have a formula, or the table would have holes the engine
		// fills with something nobody chose.
		for (com.agenarisk.api.model.State state : partitionParent.getStates()){
			if (!declaration.getByState().containsKey(state.getLabel())){
				return "not applied: no expression given for '" + partitionParent.getId() + "' = "
						+ state.getLabel();
			}
		}
		// Every remaining parent must be used by every state's expression.
		for (String expression : declaration.getByState().values()){
			String reason = rejectionAgainstParents(node, partitionParent, expression);
			if (reason != null){
				return "not applied: " + reason;
			}
		}

		List<Node> partitionParents = new ArrayList<>();
		partitionParents.add(partitionParent);
		List<String> expressions = new ArrayList<>();
		for (PartitionEnumerator.Combination combination : PartitionEnumerator.enumerate(partitionParents)){
			String state = combination.getState(partitionParent.getId());
			expressions.add("Arithmetic(" + declaration.getByState().get(state) + ")");
		}
		node.setTableFunctions(expressions, partitionParents);
		return "partitioned on '" + partitionParent.getId() + "' with " + expressions.size() + " expressions";
	}

	/** Like {@link #rejection}, ignoring the parent that selects the partition. */
	private static String rejectionAgainstParents(Node node, Node partitionParent, String expression) {
		List<Node> parents = new ArrayList<>(node.getParents());
		parents.remove(partitionParent);
		if (parents.isEmpty()){
			return "node '" + node.getId() + "' has no parents besides '" + partitionParent.getId() + "'";
		}
		parents.sort((a, b) -> b.getId().length() - a.getId().length());
		String remainder = expression;
		for (Node parent : parents){
			if (!isContinuous(parent)){
				return "parent '" + parent.getId() + "' is " + parent.getType() + ", not continuous";
			}
			if (!remainder.contains(parent.getId())){
				return "the expression does not use parent '" + parent.getId() + "'";
			}
			remainder = remainder.replace(parent.getId(), " ");
		}
		for (char c : remainder.toCharArray()){
			if (Character.isLetter(c)){
				return "the expression refers to something that is not a parent of '" + node.getId() + "'";
			}
		}
		return null;
	}

	public static boolean isContinuous(Node node) {
		return node.getType() == Node.Type.ContinuousInterval || node.getType() == Node.Type.IntegerInterval;
	}
}
