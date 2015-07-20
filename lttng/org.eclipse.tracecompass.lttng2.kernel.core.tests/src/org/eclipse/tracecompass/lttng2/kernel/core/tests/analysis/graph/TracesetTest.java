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

package org.eclipse.tracecompass.lttng2.kernel.core.tests.analysis.graph;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.junit.Test;

/**
 * Check the Traceset class for proper functioning.
 *
 * */
public class TracesetTest {

    private static final String[] TRACESET_NAMES = {
            Traceset.TRACESET_WK_BOSSTHREAD_U,
            Traceset.TRACESET_WK_HEARTBEAT_K_U,
            Traceset.TRACESET_WK_IMBALANCE_K,
            Traceset.TRACESET_WK_THREADTREE_U,
            Traceset.TRACESET_WK_MUTEX_K,
            Traceset.TRACESET_WK_PIPETTE_CONS_K,
            Traceset.TRACESET_WK_PULSE_PREEMPT_2X_K,
            Traceset.TRACESET_WK_IOBURST_512_K,
            Traceset.TRACESET_WK_CPM2_K,
            Traceset.TRACESET_WK_FUNCTRACE_K_U,
            Traceset.TRACESET_DD_100M_K,
            Traceset.TRACESET_WK_PIPETTE_PROD_K,
            Traceset.TRACESET_WK_THREADTREE_K_U,
            Traceset.TRACESET_WK_PIPELINE_K,
            Traceset.TRACESET_WK_IOBURST_512_SYNC_K,
            Traceset.TRACESET_WK_HEARTBEAT_U,
            Traceset.TRACESET_SLEEP_1X_1SEC_K,
            Traceset.TRACESET_NETCAT_UDP_K,
            Traceset.TRACESET_WK_DTHREAD_K,
            Traceset.TRACESET_WK_INCEPTION_3X_100MS_K,
            Traceset.TRACESET_WK_FUNCTRACE_U,
            Traceset.TRACESET_WK_BOSSTHREAD_K_U,
            Traceset.TRACESET_WK_REPARENT_K,
            Traceset.TRACESET_WK_DEADLOCK_K,
            Traceset.TRACESET_BURNP6_16X_1SEC_K,
            Traceset.TRACESET_WK_LOCKFIGHT_K,
            Traceset.TRACESET_WK_CPM1_K,
            Traceset.TRACESET_BURNP6_8X_1SEC_K,
            Traceset.TRACESET_WK_RPC_100MS_K,
            Traceset.TRACESET_BURNP6_1X_1SEC_K,
            Traceset.TRACESET_BURNP6_3X_1SEC_K,
            Traceset.TRACESET_WK_CPM3_K,
            Traceset.TRACESET_NETCAT_TCP_K,
    };

    /**
     * Test that traces are loading
     */
    @Test
    public void testLoadTraces() {
        for (String name: TRACESET_NAMES) {
            assertNotNull(name); // do not put null values in the string array!
            ITmfTrace t1 = Traceset.load(name);
            assertNotNull(t1);
            assertTrue("validate that the trace has at least one child", t1.getChildren().size() >= 1);
        }
    }

}
