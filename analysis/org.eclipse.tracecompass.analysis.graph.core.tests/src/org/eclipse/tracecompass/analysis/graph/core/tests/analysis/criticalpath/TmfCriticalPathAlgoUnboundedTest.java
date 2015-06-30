/*******************************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Francis Giraldeau - Initial API and implementation
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.graph.core.tests.analysis.criticalpath;

import static org.junit.Assert.assertNotNull;

import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;
import org.eclipse.tracecompass.analysis.graph.core.criticalpath.ICriticalPathAlgorithm;
import org.eclipse.tracecompass.analysis.graph.core.tests.stubs.GraphBuilder;
import org.eclipse.tracecompass.internal.analysis.graph.core.criticalpath.CriticalPathAlgorithmUnbounded;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test the {@link CriticalPathAlgorithmUnbounded} class
 *
 * TODO: Ignored test cases don't have an unbounded critical path defined
 *
 * @author Francis Giraldeau
 * @author Geneviève Bastien
 */
public class TmfCriticalPathAlgoUnboundedTest extends TmfCriticalPathAlgorithmTest {

    @Override
    protected TmfGraph computeCriticalPath(TmfGraph graph, TmfVertex start) {
        assertNotNull(graph);
        ICriticalPathAlgorithm cp = new CriticalPathAlgorithmUnbounded(graph);
        TmfGraph bounded = cp.compute(start, null);
        return bounded;
    }

    @Override
    protected TmfGraph getExpectedCriticalPath(GraphBuilder builder) {
        return builder.criticalPathUnbounded();
    }

    @Override
    @Ignore
    @Test
    public void testCriticalPathWakeupOpened() {
        super.testCriticalPathWakeupOpened();
    }

    @Override
    @Ignore
    @Test
    public void testCriticalPathWakeupOpenedDelay() {
        super.testCriticalPathWakeupOpenedDelay();
    }

    @Override
    @Ignore
    @Test
    public void testCriticalPathWakeupMissing() {
        super.testCriticalPathWakeupMissing();
    }

    @Override
    @Ignore
    @Test
    public void testCriticalPathWakeupEmbedded() {
        super.testCriticalPathWakeupEmbedded();
    }

    @Override
    @Ignore
    @Test
    public void testCriticalPathWakeupInterleave() {
        super.testCriticalPathWakeupInterleave();
    }

    @Override
    @Test
    @Ignore
    public void testCriticalPathWakeupNet1() {
        super.testCriticalPathWakeupNet1();
    }

}
