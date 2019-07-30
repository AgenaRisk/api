package com.agenarisk.test.composite;

import com.agenarisk.api.exception.LinkException;
import com.agenarisk.api.io.JSONAdapter;
import com.agenarisk.api.model.CrossNetworkLink;
import com.agenarisk.api.model.Link;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import uk.co.agena.minerva.model.extendedbn.ExtendedState;

/**
 * The purpose of this test is to exhaustively try to create cross network links between nodes of different types and check that all expected link types are allowed and all others are not.
 *
 * @author Eugene Dementiev
 */
public class LinkTypeTest {

	public static void main(String[] args) throws Exception {

		Model model = Model.createModel();

		testValidLinks(model);
		testFaultyInput(model);

		JSONObject json = JSONAdapter.toJSONObject(model.getLogicModel());

		model = Model.createModel(json);

		model.calculate();
	}

	private static void testValidLinks(Model model) throws Exception {
		Node nodeSim;
		Network netSrc = model.createNetwork("src");
		netSrc.createNode("n_bool", Node.Type.Boolean);
		netSrc.createNode("n_cont", Node.Type.ContinuousInterval);

		nodeSim = netSrc.createNode("n_cont_sim", Node.Type.ContinuousInterval);
		nodeSim.setSimulated(true);
		nodeSim.setTableFunction("Arithmetic(1)");

		netSrc.createNode("n_disc", Node.Type.DiscreteReal);
		netSrc.createNode("n_int", Node.Type.IntegerInterval);

		nodeSim = netSrc.createNode("n_int_sim", Node.Type.IntegerInterval);
		nodeSim.setSimulated(true);
		nodeSim.setTableFunction("Arithmetic(1)");

		netSrc.createNode("n_lab", Node.Type.Labelled);
		netSrc.createNode("n_ranked", Node.Type.Ranked);

		Node nodeSrc;
		Network netDst;
		Node nodeDst;

		JSONObject netJson = JSONAdapter.toJSONObject(netSrc.getLogicNetwork());

		nodeSrc = netSrc.getNode("n_bool");
		netJson.put("id", "dst_bool");
		netJson.put("name", "dst_bool");
		netDst = model.createNetwork(netJson);
		Node.linkNodes(nodeSrc, netDst.getNode("n_bool"), CrossNetworkLink.Type.Marginals);
		Node.linkNodes(nodeSrc, netDst.getNode("n_cont_sim"), CrossNetworkLink.Type.State, "False");
		Node.linkNodes(nodeSrc, netDst.getNode("n_int_sim"), CrossNetworkLink.Type.State, "False");

		nodeSrc = netSrc.getNode("n_cont");
		netJson.put("id", "dst_cont");
		netJson.put("name", "dst_cont");
		netDst = model.createNetwork(netJson);
		Node.linkNodes(nodeSrc, netDst.getNode("n_cont"), CrossNetworkLink.Type.Marginals);

		nodeSrc = netSrc.getNode("n_cont_sim");
		netJson.put("id", "dst_cont_sim");
		netJson.put("name", "dst_cont_sim");
		netDst = model.createNetwork(netJson);
		Node.linkNodes(nodeSrc, netDst.getNode("n_cont"), CrossNetworkLink.Type.Marginals);
		Node.linkNodes(nodeSrc, netDst.getNode("n_cont_sim"), CrossNetworkLink.Type.Marginals);

		nodeSrc = netSrc.getNode("n_disc");
		netJson.put("id", "dst_disc");
		netJson.put("name", "dst_disc");
		netDst = model.createNetwork(netJson);
		Node.linkNodes(nodeSrc, netDst.getNode("n_disc"), CrossNetworkLink.Type.Marginals);

		nodeSrc = netSrc.getNode("n_int");
		netJson.put("id", "dst_int");
		netJson.put("name", "dst_int");
		netDst = model.createNetwork(netJson);
		Node.linkNodes(nodeSrc, netDst.getNode("n_int"), CrossNetworkLink.Type.Marginals);

		nodeSrc = netSrc.getNode("n_int_sim");
		netJson.put("id", "dst_int_sim");
		netJson.put("name", "dst_int_sim");
		netDst = model.createNetwork(netJson);
		Node.linkNodes(nodeSrc, netDst.getNode("n_int"), CrossNetworkLink.Type.Marginals);
		Node.linkNodes(nodeSrc, netDst.getNode("n_int_sim"), CrossNetworkLink.Type.Marginals);

		nodeSrc = netSrc.getNode("n_lab");
		netJson.put("id", "dst_lab");
		netJson.put("name", "dst_lab");
		netDst = model.createNetwork(netJson);
		Node.linkNodes(nodeSrc, netDst.getNode("n_lab"), CrossNetworkLink.Type.Marginals);
		Node.linkNodes(nodeSrc, netDst.getNode("n_cont_sim"), CrossNetworkLink.Type.State, "State_1");
		Node.linkNodes(nodeSrc, netDst.getNode("n_int_sim"), CrossNetworkLink.Type.State, "State_1");

		nodeSrc = netSrc.getNode("n_ranked");
		netJson.put("id", "dst_ranked");
		netJson.put("name", "dst_ranked");
		netDst = model.createNetwork(netJson);
		Node.linkNodes(nodeSrc, netDst.getNode("n_ranked"), CrossNetworkLink.Type.Marginals);
		Node.linkNodes(nodeSrc, netDst.getNode("n_cont_sim"), CrossNetworkLink.Type.State, "Medium");
		Node.linkNodes(nodeSrc, netDst.getNode("n_int_sim"), CrossNetworkLink.Type.State, "Medium");
	}

	private static void testFaultyInput(Model model) {
		// Now test that all other types of links fail
		model.getNetworkList().forEach(netDst -> {
			List<Network> netParents = new ArrayList(netDst.getParents());
			if (netParents.isEmpty()) {
				// This is the src network
				return;
			}

			Network netSrc = netParents.get(0);
			Node nodeSrc = netDst.getNodes().values().stream().filter(node -> !node.getParents().isEmpty()).findFirst().get().getLinksIn().get(0).getFromNode();
			// Network must have at least one node with incoming links, otherwise we have a problem any way.

			netDst.getNodes().values().stream().filter(node -> node.getParents().isEmpty()).forEach(nodeDst -> {
				Link link = null;
				boolean fail = false;

				try {
					link = Node.linkNodes(nodeSrc, nodeDst, CrossNetworkLink.Type.Marginals);
				}
				catch (LinkException ex) {
					//System.out.println("Declined: " + ex.getMessage());
					fail = true;
				}

				try {
					String stateName = ((List<ExtendedState>) nodeSrc.getLogicNode().getExtendedStates()).get(0).getName().getShortDescription();
					link = Node.linkNodes(nodeSrc, nodeDst, CrossNetworkLink.Type.Marginals, stateName);
				}
				catch (LinkException ex) {
					//System.out.println("Declined: " + ex.getMessage());
					fail = true;
				}

				if (!fail) {
					throw new RuntimeException("Invalid link created: " + link);
				}

			});

		});
	}
}
