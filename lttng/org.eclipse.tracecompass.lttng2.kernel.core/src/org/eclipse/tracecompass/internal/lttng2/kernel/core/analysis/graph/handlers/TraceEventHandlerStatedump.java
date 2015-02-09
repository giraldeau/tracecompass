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

package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.handlers;

import org.eclipse.tracecompass.analysis.graph.core.building.AbstractTraceEventHandler;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.building.LttngKernelExecGraphProvider.ProcessStatus;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.EventField;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngStrings;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngSystemModel;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngWorker;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

/**
 * Handles the LTTng statedump events necessary for the initialization of the
 * system model
 *
 * @author Francis Giraldeau
 */
public class TraceEventHandlerStatedump extends AbstractTraceEventHandler {

    private LttngSystemModel fSystem;

    /**
     * Get the list of events handled by this handler
     *
     * @return The list of event handled by this handler
     */
    public static String[] getHandledEvents() {
        return new String[] { LttngStrings.STATEDUMP_PROCESS_STATE };
    }

    /**
     * Constructor
     *
     * @param system
     *            The system model for the system being investigated
     */
    public TraceEventHandlerStatedump(LttngSystemModel system) {
        super();
        fSystem = system;
    }

    @Override
    public void handleEvent(ITmfEvent event) {
        String eventName = event.getType().getName();
        if (!LttngStrings.STATEDUMP_PROCESS_STATE.equals(eventName)) {
            return;
        }

        Integer tid = EventField.getInt(event, LttngStrings.TID);
        String name = EventField.getOrDefault(event, LttngStrings.NAME, LttngStrings.UNKNOWN);
        Integer status = EventField.getInt(event, LttngStrings.STATUS);

        String host = event.getTrace().getHostId();
        long ts = event.getTimestamp().getValue();

        HostThread ht = new HostThread(host, tid);
        LttngWorker task = fSystem.findWorker(ht);
        if (task == null) {
            task = new LttngWorker(ht, name, ts);
            fSystem.addWorker(task);
        } else {
            task.setName(name);
        }

        task.setStatus(ProcessStatus.getStatus(status));
    }

}
