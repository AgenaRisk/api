package com.agenarisk.api.model;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.model.Progressable;

/**
 * A calculation that detours onto a factorisation working copy must still report progress.
 *
 * <p>The engine server polls {@code Model.getPropagationAlgorithm()} every 120ms on a second thread
 * and streams {@code getCurrentProgress()} / {@code getProgressMessage()} to the UI's progress bar.
 * The comparative/mlogit pre-pass propagates a deep copy, so the caller's own propagation field stayed
 * null for the whole calculation: the poller saw nothing, the rich per-BN DD detail disappeared, and
 * cancellation was dead too. Because a comparative over a continuous parent qualifies — {@code
 * if(x>0,...)} — that was almost every real model, not an edge case.
 *
 * <p>This test polls the way the server does rather than reaching into internals, so it fails if the
 * delegation regresses for any reason, not just the one fixed here.
 */
public class DetourProgressVisibleTest {

  private static final String CHAIN =
      "C:/Users/marti/Desktop/test cases/Factorisation chain FACTORISED.cmpx";

  @Test
  public void progressIsObservableWhileADetouredModelCalculates() throws Exception {
    // Environment-dependent: CHAIN is an absolute path on one developer's machine, so this test
    // can only run there. Skip rather than fail elsewhere, as the DD-dependent tests already do --
    // a permanently red test is how a real regression gets missed.
    org.junit.jupiter.api.Assumptions.assumeTrue(
        new java.io.File(CHAIN).isFile(),
        "Skipped: test model not present at " + CHAIN);

    Model m = Model.loadModel(CHAIN);
    final uk.co.agena.minerva.model.Model lm = m.getLogicModel();

    final AtomicReference<Throwable> err = new AtomicReference<>();
    final java.util.concurrent.atomic.AtomicBoolean done =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    Thread worker =
        new Thread(
            () -> {
              try {
                m.calculate();
              } catch (Throwable t) {
                err.set(t);
              } finally {
                done.set(true);
              }
            },
            "calc");
    worker.start();

    // Poll exactly as EngineServer's stream handler does.
    int sawPropagation = 0;
    String anyMessage = null;
    int maxTotal = 0;
    java.util.Set<String> distinct = new java.util.LinkedHashSet<>();
    while (!done.get()) {
      Progressable pa = lm.getPropagationAlgorithm();
      if (pa != null) {
        sawPropagation++;
        try {
          maxTotal = Math.max(maxTotal, pa.getLengthOfProgressableTask());
          String msg = pa.getProgressMessage();
          if (msg != null && !msg.trim().isEmpty()) {
            anyMessage = msg;
            distinct.add(msg);
          }
        } catch (Throwable ignore) {
          // Benign: the getters race with the propagation's own bookkeeping. Keep polling.
        }
      }
      Thread.sleep(20);
    }
    worker.join(10000);

    if (err.get() != null) {
      throw new AssertionError("calculate() failed: " + err.get(), err.get());
    }
    System.out.println(
        "PROGRESS polls-with-propagation=" + sawPropagation + " maxTotal=" + maxTotal
            + " distinctMessages=" + distinct.size());
    int i = 0;
    for (String d : distinct) {
      i++;
      if (i == 1 || i == distinct.size()) { // first and last: the detail grows as DD proceeds
        System.out.println("PROGRESS MSG#" + i + " | " + d.replace("\n", " \\n "));
      }
    }

    assertTrue(
        sawPropagation > 0,
        "getPropagationAlgorithm() was null for the whole calculation — the progress bar would have "
            + "nothing to poll and Cancel would be dead");
    assertNotNull(
        anyMessage,
        "no progress message was ever produced — the bar would show as an indeterminate blank");
    assertTrue(maxTotal > 0, "progress total never exceeded 0, so no percentage can be computed");
    // Not just any message: the rich multi-line status is the thing that went missing, so require the
    // per-network breakdown and require it to actually evolve rather than repeat one static line.
    assertTrue(
        anyMessage.contains("Networks calculated"),
        "progress message lacks the per-network breakdown: " + anyMessage);
    assertTrue(
        distinct.size() > 1,
        "progress message never changed during the calculation — the bar would look frozen");
  }
}
