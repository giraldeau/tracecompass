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

package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.handlers;

import org.eclipse.tracecompass.analysis.graph.core.building.AbstractTraceEventHandler;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.building.LttngKernelExecGraphProvider.Context;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngInterruptContext;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngStrings;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngSystemModel;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * Event Handler to handle the interrupt context stack of the model
 *
 * @author Francis Giraldeau
 * @author Geneviève Bastien
 */
public class EventContextHandler extends AbstractTraceEventHandler {

    private LttngSystemModel fSystem;

    /**
     * Get the list of events handled by this handler
     *
     * @return The list of event handled by this handler
     */
    public static String[] getHandledEvents() {
        return new String[] { LttngStrings.SOFTIRQ_ENTRY, LttngStrings.SOFTIRQ_EXIT,
                LttngStrings.HRTIMER_EXPIRE_ENTRY, LttngStrings.HRTIMER_EXPIRE_EXIT,
                LttngStrings.IRQ_HANDLER_ENTRY, LttngStrings.IRQ_HANDLER_EXIT };
    }

    /**
     * Constructor
     *
     * @param system
     *            The system model for the system being investigated
     */
    public EventContextHandler(LttngSystemModel system) {
        super();
        fSystem = system;
    }

    @Override
    public void handleEvent(ITmfEvent event) {
        String eventName = event.getType().getName();
        if (LttngStrings.SOFTIRQ_ENTRY.equals(eventName)) {
            handleSoftirqEntry(event);
        } else if (LttngStrings.SOFTIRQ_EXIT.equals(eventName)) {
            handleSoftirqExit(event);
        } else if (LttngStrings.HRTIMER_EXPIRE_ENTRY.equals(eventName)) {
            handleHrtimerExpireEntry(event);
        } else if (LttngStrings.HRTIMER_EXPIRE_EXIT.equals(eventName)) {
            handleHrtimerExpireExit(event);
        } else if (LttngStrings.IRQ_HANDLER_ENTRY.equals(eventName)) {
            handleIrqHandlerEntry(event);
        } else if (LttngStrings.IRQ_HANDLER_EXIT.equals(eventName)) {
            handleIrqHandlerExit(event);
        }
    }

    private void pushInterruptContext(ITmfEvent event, Context ctx) {
        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            throw new NullPointerException();
        }
        Integer cpu = (Integer) cpuObj;

        LttngInterruptContext interruptCtx = new LttngInterruptContext(event, ctx);

        fSystem.pushContextStack(event.getTrace().getHostId(), cpu, interruptCtx);
    }

    private void popInterruptContext(ITmfEvent event, Context ctx) {
        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            throw new NullPointerException();
        }
        Integer cpu = (Integer) cpuObj;

        LttngInterruptContext interruptCtx = fSystem.peekContextStack(event.getTrace().getHostId(), cpu);
        if (interruptCtx.getContext() == ctx) {
            fSystem.popContextStack(event.getTrace().getHostId(), cpu);
        }
    }

    private void handleSoftirqEntry(ITmfEvent event) {
        pushInterruptContext(event, Context.SOFTIRQ);
    }

    private void handleSoftirqExit(ITmfEvent event) {
        popInterruptContext(event, Context.SOFTIRQ);
    }

    private void handleIrqHandlerEntry(ITmfEvent event) {
        pushInterruptContext(event, Context.IRQ);
    }

    private void handleIrqHandlerExit(ITmfEvent event) {
        popInterruptContext(event, Context.IRQ);
    }

    private void handleHrtimerExpireEntry(ITmfEvent event) {
        pushInterruptContext(event, Context.HRTIMER);
    }

    private void handleHrtimerExpireExit(ITmfEvent event) {
        popInterruptContext(event, Context.HRTIMER);
    }

}
