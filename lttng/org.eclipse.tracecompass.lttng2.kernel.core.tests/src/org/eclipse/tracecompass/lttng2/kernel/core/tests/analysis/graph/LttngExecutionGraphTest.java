/*******************************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.lttng2.kernel.core.tests.analysis.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.graph.core.base.IGraphWorker;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge.EdgeType;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex.EdgeDirection;
import org.eclipse.tracecompass.analysis.graph.core.building.TmfGraphBuilderModule;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngWorker;
import org.eclipse.tracecompass.lttng2.kernel.core.tests.Activator;
import org.eclipse.tracecompass.lttng2.kernel.core.trace.LttngKernelTrace;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisManager;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.eclipse.tracecompass.tmf.tests.stubs.trace.xml.TmfXmlTraceStub;
import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.collect.Multimap;

/**
 * Test that the execution graph is built correctly
 *
 * @author Geneviève Bastien
 */
public class LttngExecutionGraphTest {

    private static final @NonNull String TEST_ANALYSIS_ID = "org.eclipse.tracecompass.lttng2.kernel.core.tests.kernelgraph";

    /**
     * Setup the trace for the tests
     *
     * @param traceFile
     *            File name relative to this plugin for the trace file to load
     * @return The trace with its graph module executed
     */
    public ITmfTrace setUpTrace(String traceFile) {
        ITmfTrace trace = new TmfXmlTraceStub();
        IPath filePath = Activator.getAbsoluteFilePath(traceFile);
        IStatus status = trace.validate(null, filePath.toOSString());
        if (!status.isOK()) {
            fail(status.getException().getMessage());
        }
        try {
            trace.initTrace(null, filePath.toOSString(), TmfEvent.class);
        } catch (TmfTraceException e) {
            fail(e.getMessage());
        }
        ((TmfTrace) trace).traceOpened(new TmfTraceOpenedSignal(this, trace, null));
        IAnalysisModule module = null;
        for (IAnalysisModule mod : TmfTraceUtils.getAnalysisModulesOfClass(trace, TmfGraphBuilderModule.class)) {
            module = mod;
        }
        assertNotNull(module);
        module.schedule();
        module.waitForCompletion();
        return trace;
    }

    /**
     * Test the graph building with sched events only
     *
     * TODO: Add wakeup events to this test case
     */
    @Test
    public void testSchedEvents() {
        ITmfTrace trace = setUpTrace("testfiles/graph/sched_only.xml");
        assertNotNull(trace);

        TmfGraphBuilderModule module = TmfTraceUtils.getAnalysisModuleOfClass(trace, TmfGraphBuilderModule.class, TEST_ANALYSIS_ID);
        assertNotNull(module);
        module.schedule();
        assertTrue(module.waitForCompletion());

        TmfGraph graph = module.getGraph();
        assertNotNull(graph);

        Set<IGraphWorker> workers = graph.getWorkers();
        assertEquals(2, workers.size());
        for (IGraphWorker worker : workers) {
            assertTrue(worker instanceof LttngWorker);
            LttngWorker lttngWorker = (LttngWorker) worker;
            switch (lttngWorker.getHostThread().getTid()) {
            case 1: {
                List<TmfVertex> nodesOf = graph.getNodesOf(lttngWorker);
                assertEquals(4, nodesOf.size());
                /* Check first vertice has outgoing edge preempted */
                TmfVertex v = nodesOf.get(0);
                assertEquals(10, v.getTs());
                assertNull(v.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.OUTGOING_VERTICAL_EDGE));
                TmfEdge edge = v.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE);
                assertNotNull(edge);
                assertEquals(EdgeType.PREEMPTED, edge.getType());
                v = nodesOf.get(1);
                assertEquals(v, edge.getVertexTo());

                /* Check second vertice has outgoing edge running */
                assertEquals(20, v.getTs());
                assertNull(v.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.OUTGOING_VERTICAL_EDGE));
                assertNotNull(v.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE));
                edge = v.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE);
                assertNotNull(edge);
                assertEquals(EdgeType.RUNNING, edge.getType());
                v = nodesOf.get(2);
                assertEquals(v, edge.getVertexTo());

                /* Check third vertice has outgoing edge preempted */
                assertEquals(30, v.getTs());
                assertNull(v.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.OUTGOING_VERTICAL_EDGE));
                assertNotNull(v.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE));
                edge = v.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE);
                assertNotNull(edge);
                assertEquals(EdgeType.PREEMPTED, edge.getType());
                v = nodesOf.get(3);
                assertEquals(v, edge.getVertexTo());

                /* Check 4th vertice */
                assertEquals(40, v.getTs());
                assertNull(v.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.OUTGOING_VERTICAL_EDGE));
                assertNotNull(v.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE));
            }
                break;
            case 2: {
                List<TmfVertex> nodesOf = graph.getNodesOf(lttngWorker);
                assertEquals(4, nodesOf.size());
                /* Check first vertice has outgoing edge preempted */
                TmfVertex v = nodesOf.get(0);
                assertEquals(10, v.getTs());
                assertNull(v.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.OUTGOING_VERTICAL_EDGE));
                TmfEdge edge = v.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE);
                assertNotNull(edge);
                assertEquals(EdgeType.RUNNING, edge.getType());
                v = nodesOf.get(1);
                assertEquals(v, edge.getVertexTo());

                /* Check second vertice has outgoing edge running */
                assertEquals(20, v.getTs());
                assertNull(v.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.OUTGOING_VERTICAL_EDGE));
                assertNotNull(v.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE));
                edge = v.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE);
                assertNotNull(edge);
                assertEquals(EdgeType.BLOCKED, edge.getType());
                v = nodesOf.get(2);
                assertEquals(v, edge.getVertexTo());

                /* Check third vertice has outgoing edge preempted */
                assertEquals(30, v.getTs());
                assertNull(v.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.OUTGOING_VERTICAL_EDGE));
                assertNotNull(v.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE));
                edge = v.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE);
                assertNotNull(edge);
                assertEquals(EdgeType.RUNNING, edge.getType());
                v = nodesOf.get(3);
                assertEquals(v, edge.getVertexTo());

                /* Check 4th vertice */
                assertEquals(40, v.getTs());
                assertNull(v.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.OUTGOING_VERTICAL_EDGE));
                assertNotNull(v.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE));
                assertNull(v.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE));
            }
                break;
            default:
                fail("Unknown worker");
                break;
            }
        }
    }

    /**
     * @author francis
     *
     */
    public static class IterCount implements Function<Iterable<?>, Integer> {

        @Override
        public Integer apply(Iterable<?> input) {
            int x = 0;
            Iterator<?> iterator = input.iterator();
            while(iterator.hasNext()) {
                iterator.next();
                x++;
            }
            return x;
        }

    }

    @Test
    public void testCreateGraph() {
//        ITmfTrace trace = Traceset.load(Traceset.TRACESET_WK_RPC_100MS_K);
//        Traceset.open(trace);
//        Multimap<String, IAnalysisModuleHelper> modules = TmfAnalysisManager.getAnalysisModules();
//        for (Entry<String, IAnalysisModuleHelper> entry: modules.entries()) {
//            System.out.println(entry);
//        }
//        System.out.println(modules);
        /*
        for (IAnalysisModuleHelper helper : modules.values()) {
            try {
                IAnalysisModule module = helper.newModule(this);


        LttngKernelExecutionGraph mod = TmfTraceUtils.getAnalysisModuleOfClass(trace,
                LttngKernelExecutionGraph.class, LttngKernelExecutionGraph.ANALYSIS_ID);
        assertNotNull(mod);
        */
        Path path = Paths.get(Traceset.TRACESET_PATH, Traceset.TRACESET_WK_RPC_100MS_K);
        List<Path> dirs = Traceset.findDirectories(path, Traceset.GLOB_METADATA);

        /* can't instantiate TmfTrace class, it is an abstract class */
        //ITmfTrace tmf = Traceset.makeTraceCollectionGeneric(dirs, TmfTrace.class, TmfEvent.class);
        ITmfTrace ctf = Traceset.makeTraceCollectionGeneric(dirs, CtfTmfTrace.class, CtfTmfEvent.class);
        ITmfTrace ltt = Traceset.makeTraceCollectionGeneric(dirs, LttngKernelTrace.class, CtfTmfEvent.class);

        /*
        LttngKernelExecutionGraph module = new LttngKernelExecutionGraph();
        module.getGraph();
        */

        /*
    LttngKernelExecutionGraph mod = TmfTraceUtils.getAnalysisModuleOfClass(t,
            LttngKernelExecutionGraph.class, LttngKernelExecutionGraph.ANALYSIS_ID);
        System.out.println(t.getClass().getSimpleName() + ": " + mod);
        */

        /*
         * Pourquoi le nombre de modules n'est pas le même pour getAnalysisModules() et getAnalysisModuleOfClass()?
         * question de signal. les traces enfants ne reçoivent pas le signal lorsque la trace parent est ouverte.
         *
         * execution graph analysis module: org.eclipse.tracecompass.lttng2.kernel.core.execgraph
         */
        Multimap<String, IAnalysisModuleHelper> analysisModules = TmfAnalysisManager.getAnalysisModules();

        IterCount count = new IterCount();
        System.out.println("available analysis modules: " + analysisModules.size());
        for (ITmfTrace root: new ITmfTrace[] { ctf, ltt }) {
            Traceset.open(root);
            System.out.println("root analysis modules: " + count.apply(root.getAnalysisModules()));
            for (IAnalysisModule module : root.getAnalysisModules()) {
                System.out.println(module);
            }
            List<ITmfTrace> children = root.getChildren(ITmfTrace.class);
            for (ITmfTrace child: children) {
                Traceset.open(child);
                System.out.println("child analysis modules: " + count.apply(child.getAnalysisModules()));
                for (IAnalysisModule module : child.getAnalysisModules()) {
                    System.out.println("    " + module);
                }
            }
        }
    }

}
