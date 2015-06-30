/*******************************************************************************
 * Copyright (c) 2014 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Francis Giraldeau - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.internal.analysis.graph.core.criticalpath;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.graph.core.base.IGraphWorker;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge.EdgeType;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex.EdgeDirection;

/**
 * Critical path bounded algorithm: backward resolution of blockage all the way
 * until all blockages stop
 *
 * @author Francis Giraldeau
 */
public class CriticalPathAlgorithmUnbounded extends AbstractCriticalPathAlgorithm {

    /**
     * Constructor
     *
     * @param main
     *            The execution graph on which to calculate the critical path
     */
    public CriticalPathAlgorithmUnbounded(TmfGraph main) {
        super(main);
    }

    @Override
    public TmfGraph compute(TmfVertex start, @Nullable TmfVertex end) {
        /* Create new graph for the critical path result */
        TmfGraph criticalPath = new TmfGraph();

        /* Get the main graph from which to get critical path */
        TmfGraph graph = getGraph();

        /* Calculate path starting from the object the start vertex belongs to */
        IGraphWorker parent = graph.getParentOf(start);
        if (parent == null) {
            throw new NullPointerException();
        }
        criticalPath.add(parent, new TmfVertex(start));
        TmfVertex currentVertex = start;
        TmfEdge nextEdge = currentVertex.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE);

        long endTime = Long.MAX_VALUE;
        if (end != null) {
            endTime = end.getTs();
        }

        while (nextEdge != null) {
            TmfVertex nextVertex = nextEdge.getVertexTo();
            if (nextVertex.getTs() >= endTime) {
                break;
            }
            switch (nextEdge.getType()) {
            case USER_INPUT:
            case BLOCK_DEVICE:
            case TIMER:
            case INTERRUPTED:
            case PREEMPTED:
            case RUNNING:
                /**
                 * This edge is not blocked, so nothing to resolve, just add the
                 * edge to the critical path
                 */
                /**
                 * TODO: Normally, the parent of the link's vertex to should be
                 * the object itself, verify if that is true
                 */
                IGraphWorker parentTo = graph.getParentOf(nextEdge.getVertexTo());
                if (parentTo == null) {
                    throw new NullPointerException();
                }
                criticalPath.append(parentTo, new TmfVertex(nextEdge.getVertexTo()), nextEdge.getType());
                break;
            case NETWORK:
            case BLOCKED:
                List<TmfEdge> links = resolveBlockingUnbounded(nextEdge, start);
                Collections.reverse(links);
                stiches(criticalPath, graph, nextEdge, links);
                break;
            case EPS:
                if (nextEdge.getDuration() != 0) {
                    throw new RuntimeException("epsilon duration is not zero " + nextEdge); //$NON-NLS-1$
                }
                break;
            case DEFAULT:
                throw new RuntimeException("Illegal link type " + nextEdge.getType()); //$NON-NLS-1$
            case UNKNOWN:
            default:
                break;
            }
            currentVertex = nextVertex;
            nextEdge = currentVertex.getEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE);
        }
        return criticalPath;
    }

    private void stiches(TmfGraph criticalPath, TmfGraph graph, TmfEdge blocking, List<TmfEdge> links) {
        IGraphWorker currentActor = getGraph().getParentOf(blocking.getVertexFrom());
        if (currentActor == null) {
            throw new NullPointerException();
        }
        if (links.isEmpty()) {
            criticalPath.append(currentActor, new TmfVertex(blocking.getVertexTo()), EdgeType.UNKNOWN);
            return;
        }
        // rewind path if required
        TmfEdge first = links.get(0);
        TmfVertex anchor = criticalPath.getTail(currentActor);
        if (anchor == null) {
            return;
        }
        TmfEdge prevEdge = anchor.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE);
        if (first.getVertexFrom().compareTo(anchor) < 0 && prevEdge != null) {
            while ((first.getVertexFrom().compareTo(anchor) < 0) && prevEdge != null) {
                criticalPath.removeTail(currentActor);
                anchor = prevEdge.getVertexFrom();
                prevEdge = anchor.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE);
            }
            anchor.removeEdge(EdgeDirection.OUTGOING_HORIZONTAL_EDGE);
        }
        Object obj = getGraph().getParentOf(first.getVertexFrom());
        if (obj != currentActor) {
            // fill any gap
            if (anchor.getTs() != first.getVertexFrom().getTs()) {
                anchor = new TmfVertex(first.getVertexFrom());
                criticalPath.append(currentActor, anchor, EdgeType.UNKNOWN);
            }
        }
        // glue body
        TmfEdge prev = null;
        for (TmfEdge link : links) {
            // check connectivity
            if (prev != null && prev.getVertexTo() != link.getVertexFrom()) {
                anchor = copyLink(criticalPath, graph, anchor, prev.getVertexTo(), link.getVertexFrom(),
                        prev.getVertexTo().getTs(), EdgeType.DEFAULT);
            }
            anchor = copyLink(criticalPath, graph, anchor, link.getVertexFrom(), link.getVertexTo(),
                    link.getVertexTo().getTs(), link.getType());
            prev = link;
        }
    }

    private List<TmfEdge> resolveBlockingUnbounded(TmfEdge blocking, TmfVertex bound) {
        List<TmfEdge> subPath = new LinkedList<>();
        TmfVertex junction = findIncoming(blocking.getVertexTo(), EdgeDirection.OUTGOING_HORIZONTAL_EDGE);
        // if wake-up source is not found, return empty list
        if (junction == null) {
            return subPath;
        }
        TmfEdge nextEdge = junction.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE);
        if (nextEdge == null) {
            return subPath;
        }
        subPath.add(nextEdge);
        while (nextEdge != null) {
            TmfVertex node = nextEdge.getVertexFrom();
            if (node.compareTo(bound) <= 0) {
                break;
            }
            // prefer a path that converges
            TmfEdge conv = node.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE);
            if (conv != null) {
                Object parent = getGraph().getParentOf(conv.getVertexFrom());
                Object master = getGraph().getParentOf(bound);
                if (parent == master) {
                    subPath.add(conv);
                    break;
                }
            }
            nextEdge = node.getEdge(EdgeDirection.INCOMING_HORIZONTAL_EDGE);
            if (nextEdge != null) {
                if (nextEdge.getType() == EdgeType.BLOCKED) {
                    subPath.addAll(resolveBlockingUnbounded(nextEdge, bound));
                } else {
                    subPath.add(nextEdge);
                }
            }
        }
        return subPath;
    }

}