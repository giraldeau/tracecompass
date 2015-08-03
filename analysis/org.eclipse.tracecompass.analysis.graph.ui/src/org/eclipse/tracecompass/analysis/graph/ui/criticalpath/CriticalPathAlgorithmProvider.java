/*******************************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Geneviève Bastien and Francis Giraldeau - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.graph.ui.criticalpath;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.graph.core.criticalpath.CriticalPathModule;
import org.eclipse.tracecompass.analysis.graph.core.criticalpath.ICriticalPathAlgorithm;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAbstractAnalysisParamProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Parameter provider that saves which critical path algorithm is requested to
 * be run on an execution graph
 *
 * @author Geneviève Bastien
 * @author Francis Giraldeau
 */
public class CriticalPathAlgorithmProvider extends
        TmfAbstractAnalysisParamProvider {

    private static @Nullable CriticalPathAlgorithmProvider fInstance = null;

    private @Nullable Class<?> fAlgorithm = null;

    /**
     * Constructor
     */
    public CriticalPathAlgorithmProvider() {
        super();
        fInstance = this;
    }

    /**
     * Get the instance of this parameter provider
     *
     * @return The instance of this parameter provider
     */
    public static CriticalPathAlgorithmProvider getInstance() {
        CriticalPathAlgorithmProvider provider = fInstance;
        if (provider == null) {
            provider = new CriticalPathAlgorithmProvider();
            fInstance = provider;
        }
        return provider;
    }

    @Override
    public String getName() {
        return "Critical Path algorithm provider"; //$NON-NLS-1$
    }

    @Override
    public @Nullable Object getParameter(@Nullable String name) {
        if (name == null) {
            return null;
        }
        if (name.equals(CriticalPathModule.PARAM_ALGORITHM)) {
            return fAlgorithm;
        }
        return null;
    }

    /**
     * Set the algorithm to use for critical path computation
     *
     * @param algorithmClass
     *            the new algorithm class
     */
    public <T extends ICriticalPathAlgorithm> void setAlgorithm(Class<T> algorithmClass) {
        if (!algorithmClass.equals(fAlgorithm)) {
            fAlgorithm = algorithmClass;
            this.notifyParameterChanged(CriticalPathModule.PARAM_ALGORITHM);
        }
    }

    @Override
    public boolean appliesToTrace(@Nullable ITmfTrace trace) {
        return true;
    }

}
