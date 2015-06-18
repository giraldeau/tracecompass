/*******************************************************************************
 * Copyright (c) 2015 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Sébastien Lorrain - Initial API and implementation
 *   Francis Jolivet - Initial API and implementation
 ******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.container;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelAnalysisModule;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.trace.layout.Lttng27EventLayout;
import org.eclipse.tracecompass.lttng2.kernel.core.trace.LttngKernelTrace;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

/**
 * Module for the container CPU analysis. It tracks the status of the CPU across
 * multiple containers on the same host. The analysis is based on LTTng kernel
 * trace. Mostly process statedump and sched_process_fork events.
 *
 * @author Francis Jolivet
 * @author Sébastien Lorrain
 */
public class ContainerAnalysisModule extends TmfStateSystemAnalysisModule {

    /** The ID of this analysis */
    public static final String ID = "org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.container"; //$NON-NLS-1$

    /**
     * Constructor
     */
    public ContainerAnalysisModule() {
        super();
    }

    @Override
    protected ITmfStateProvider createStateProvider() {
        ITmfTrace trace = getTrace();
        Lttng27EventLayout layout;

        if (!(trace instanceof LttngKernelTrace) && !(trace instanceof TmfExperiment)) {
            throw new IllegalStateException();
        }

        layout = Lttng27EventLayout.INSTANCE;
        return new ContainerStateProvider(trace, layout);
    }

    @Override
    protected Iterable<IAnalysisModule> getDependentAnalyses() {
        Set<IAnalysisModule> modules = new HashSet<>();
        /* Depends on the LTTng Kernel analysis modules */
        for (ITmfTrace trace : TmfTraceManager.getTraceSet(getTrace())) {
            for (KernelAnalysisModule module : TmfTraceUtils.getAnalysisModulesOfClass(NonNullUtils.checkNotNull(trace), KernelAnalysisModule.class)) {
                modules.add(module);
            }
        }
        return modules;
    }

}
