/*******************************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.analysis.graph.core.criticalpath;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;

/**
 * This algorithm traverse the input graph forward and backward with BFS and
 * returns the intersection of both traversal. The result is connected edges
 * from a given main task.
 *
 * @author Francis Giraldeau
 */
public class ConnectedPathAlgorithm extends AbstractCriticalPathAlgorithm {

    /**
     * Constructor
     *
     * @param main
     *            The graph on which to calculate the critical path
     */
    public ConnectedPathAlgorithm(TmfGraph main) {
        super(main);
    }

    @Override
    public TmfGraph compute(TmfVertex start, @Nullable TmfVertex end) {
        TmfGraph path = new TmfGraph();
        return path;
    }

}
