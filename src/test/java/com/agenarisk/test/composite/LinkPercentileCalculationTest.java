package com.agenarisk.test.composite;

import com.agenarisk.api.model.CrossNetworkLink;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End to end: a percentile set on the LINK changes the number the engine actually passes.
 *
 * <p>Everything else about this feature can be true — the field round-trips, the CMPX carries it — and
 * the value could still never reach propagation. These tests calculate a real model and read what
 * arrived at the far end of the link.</p>
 *
 * <p>The source is a standard normal, so the answers are recognisable: the 5th percentile is about
 * -1.64 and the 95th about +1.64. Tolerances are loose because these are dynamically discretised.</p>
 */
public class LinkPercentileCalculationTest {

	/** Dynamic discretisation, so agreement is approximate by nature. */
	private static final double DD_TOLERANCE = 0.25;

	/**
	 * net1.src ~ Normal(0,1) --percentile--> net2.tgt = Arithmetic(dynamic_src)
	 * so tgt collapses onto whatever single number the link passed.
	 */
	private static double passedValue(CrossNetworkLink.Type type, Double linkPercentile,
			Double nodeLower, Double nodeUpper) throws Exception {

		Model model = Model.createModel();
		Network net1 = model.createNetwork("net1");
		Network net2 = model.createNetwork("net2");

		Node src = net1.createNode("src", Node.Type.ContinuousInterval);
		src.convertToSimulated();
		src.setTableFunction("Normal(0,1)");

		Node tgt = net2.createNode("tgt", Node.Type.ContinuousInterval);
		tgt.convertToSimulated();

		if (nodeLower != null && nodeUpper != null) {
			src.setCustomPercentileSettings(nodeLower, nodeUpper);
		}

		Node.linkNodes(src, tgt, type, null, linkPercentile);

		DataSet ds = model.createDataSet("ds");
		model.calculate();

		return ds.getCalculationResult(tgt).getMean();
	}

	@Test
	public void aLinkPercentileOf5PassesTheFifthPercentile() throws Exception {
		assertEquals(-1.645d, passedValue(CrossNetworkLink.Type.LowerPercentile, 5d, null, null), DD_TOLERANCE);
	}

	@Test
	public void aLinkPercentileOf95PassesTheNinetyFifthPercentile() throws Exception {
		assertEquals(1.645d, passedValue(CrossNetworkLink.Type.UpperPercentile, 95d, null, null), DD_TOLERANCE);
	}

	/**
	 * The whole point: the SAME link type with a different number must pass a different value. If the
	 * link percentile were being ignored, both of these would come back as the node's default 25th.
	 */
	@Test
	public void differentLinkPercentilesPassDifferentValues() throws Exception {
		double at5 = passedValue(CrossNetworkLink.Type.LowerPercentile, 5d, null, null);
		double at25 = passedValue(CrossNetworkLink.Type.LowerPercentile, 25d, null, null);
		assertTrue(at5 < at25 - 0.5d,
				"the 5th percentile must be clearly below the 25th, got " + at5 + " and " + at25);
	}

	/** With no percentile on the link, the engine must still read the source node's own setting. */
	@Test
	public void withoutALinkPercentileTheSourceNodesSettingIsUsed() throws Exception {
		// Node set to 5/95; a link carrying no number must pick up the 5 for a LowerPercentile link.
		assertEquals(-1.645d, passedValue(CrossNetworkLink.Type.LowerPercentile, null, 5d, 95d), DD_TOLERANCE);
	}

	/** And the link's own number must WIN over the node's when both are present. */
	@Test
	public void theLinkPercentileOverridesTheSourceNodeSetting() throws Exception {
		// Node says 40/60, link says 5 -> the link wins, so we get about -1.64 rather than about -0.25.
		double v = passedValue(CrossNetworkLink.Type.LowerPercentile, 5d, 40d, 60d);
		assertEquals(-1.645d, v, DD_TOLERANCE);
		assertTrue(v < -1.0d, "the node's 40th percentile would be near -0.25; got " + v);
	}

	/** Mode of a standard normal is 0 — and it must be computable through a link at all. */
	@Test
	public void aModeLinkPassesTheModeOfTheSource() throws Exception {
		assertEquals(0d, passedValue(CrossNetworkLink.Type.Mode, null, null, null), 0.5d);
	}
}
