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

package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.building;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.tracecompass.analysis.graph.core.base.IGraphWorker;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge.EdgeType;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex.EdgeDirection;
import org.eclipse.tracecompass.analysis.graph.core.building.AbstractTmfGraphProvider;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.handlers.EventContextHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.handlers.TraceEventHandlerExecutionGraph;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.handlers.TraceEventHandlerSched;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.handlers.TraceEventHandlerStatedump;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngSystemModel;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngWorker;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Graph provider to build the lttng kernel execution graph
 *
 * @author Geneviève Bastien
 * @author Francis Giraldeau
 */
public class LttngKernelExecGraphProvider extends AbstractTmfGraphProvider {

    /**
     * Represents an interrupt context
     */
    public enum Context {
        /** Not in an interrupt */
        NONE,
        /** The interrupt is a soft IRQ */
        SOFTIRQ,
        /** The interrupt is an IRQ */
        IRQ,
        /** The interrupt is a timer */
        HRTIMER
    }

    /**
     * A list of status a thread can be in
     */
    public enum ProcessStatus {
        /** Unknown process status */
        UNKNOWN(0),
        /** Waiting for a fork */
        WAIT_FORK(1),
        /** Waiting for the CPU */
        WAIT_CPU(2),
        /** The thread has exited, but is not dead yet */
        EXIT(3),
        /** The thread is a zombie thread */
        ZOMBIE(4),
        /** The thread is blocked */
        WAIT_BLOCKED(5),
        /** The thread is running */
        RUN(6),
        /** The thread is dead */
        DEAD(7);
        private final int val;

        private ProcessStatus(int val) {
            this.val = val;
        }

        private int value() {
            return val;
        }

        /**
         * Get the ProcessStatus associated with a long value
         *
         * @param val
         *            The long value corresponding to a status
         * @return The {@link ProcessStatus} enum value
         */
        static public ProcessStatus getStatus(long val) {
            for (ProcessStatus e : ProcessStatus.values()) {
                if (e.value() == val) {
                    return e;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * Version number of this graph provider. Please bump this if you modify the
     * contents of the generated graph in some way.
     *
     * FIXME: Not used yet since the graph is in memory only
     */
    private static final int VERSION = 1;

    /**
     * Constructor
     *
     * @param trace
     *            The trace on which to build graph
     */
    public LttngKernelExecGraphProvider(ITmfTrace trace) {
        super(trace, "LTTng Kernel"); //$NON-NLS-1$
        LttngSystemModel system = new LttngSystemModel();

        registerHandler(new TraceEventHandlerStatedump(system));
        registerHandler(new TraceEventHandlerSched(this, system));
        registerHandler(new EventContextHandler(system));
        registerHandler(new TraceEventHandlerExecutionGraph(this, system));
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    /**
     * Simplify graph after construction
     */
    @Override
    public void done() {
        TmfGraph graph = getAssignedGraph();
        if (graph == null) {
            throw new NullPointerException();
        }
        Set<IGraphWorker> keys = graph.getWorkers();
        ArrayList<LttngWorker> kernelWorker = new ArrayList<>();
        /* build the set of worker to eliminate */
        for (Object k : keys) {
            if (k instanceof LttngWorker) {
                LttngWorker w = (LttngWorker) k;
                if (w.getHostThread().getTid() == -1) {
                    kernelWorker.add(w);
                }
            }
        }
        for (LttngWorker k : kernelWorker) {
            if (k == null) {
                throw new NullPointerException();
            }
            List<TmfVertex> nodes = graph.getNodesOf(k);
            for (TmfVertex node : nodes) {
                /* send -> recv, it removes the vertex between the real source and destination */
                TmfEdge nextH = node.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE);
                TmfEdge inV = node.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE);
                if (inV != null &&
                        nextH != null) {

                    TmfVertex next = nextH.getVertexTo();
                    /*
                     * FIXME: Francis, this if is completely useless. Is there a
                     * mistake, and the second condition should be
                     * next.hasNeighbor(OUTH) or can we remove this
                     */
//                    if (!next.hasNeighbor(TmfVertex.OUTGOING_VERTICAL_EDGE) && node.hasNeighbor(TmfVertex.OUTGOING_HORIZONTAL_EDGE)) {
//                        next = NonNullUtils.checkNotNull(node.neighbor(TmfVertex.OUTGOING_HORIZONTAL_EDGE));
//                    }
                    /*
                     * FIXME: Francis, should we make sure the vertical edges
                     * type is network before adding the new link?
                     */
                    TmfEdge nextV = next.getEdge(EdgeDirection.OUTGOING_VERTICAL_EDGE);
                    if (nextV != null) {
                        TmfVertex src = inV.getVertexFrom();
                        TmfVertex dst = nextV.getVertexTo();
                        /* unlink */
                        node.removeEdge(EdgeDirection.INCOMING_VERTICAL_EDGE);
                        next.removeEdge(EdgeDirection.OUTGOING_VERTICAL_EDGE);
                        src.linkVertical(dst).setType(EdgeType.NETWORK);
                    }
                }
            }
        }
    }

}
