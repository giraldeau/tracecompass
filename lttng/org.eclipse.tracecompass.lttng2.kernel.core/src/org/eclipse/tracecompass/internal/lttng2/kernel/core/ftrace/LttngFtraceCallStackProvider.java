/*******************************************************************************
 * Copyright (c) 2013, 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Alexandre Montplaisir - Initial API and implementation
 *   Patrick Tasse - Add support for thread id
 *******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.core.ftrace;

import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.event.aspect.LinuxTidAspect;
import org.eclipse.tracecompass.tmf.core.callstack.CallStackStateProvider;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;

import com.google.common.collect.ImmutableSet;

/**
 * Callstack provider for LTTng-UST traces.
 *
 * If the traces contains 'func_entry' and 'func_exit' event (see the
 * lttng-ust-cyg-profile manpage), AND contains vtid and procname contexts, we
 * can use this information to populate the TMF Callstack View.
 *
 * Granted, most UST traces will not contain this information. In this case,
 * this will simply build an empty state system, and the view will remain
 * unavailable.
 *
 * @author Alexandre Montplaisir
 */
public class LttngFtraceCallStackProvider extends CallStackStateProvider {

    /**
     * Version number of this state provider. Please bump this if you modify
     * the contents of the generated state history in some way.
     */
    private static final int VERSION = 1;

    /** Event names indicating function entry */
    private final Set<String> funcEntryEvents;

    /** Event names indicating function exit */
    private final Set<String> funcExitEvents;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor
     *
     * @param trace
     *            The UST trace
     */
    public LttngFtraceCallStackProvider(ITmfTrace trace) {
        super(trace);
        funcEntryEvents = ImmutableSet.of("fgraph_entry"); //$NON-NLS-1$
        funcExitEvents = ImmutableSet.of("fgraph_return"); //$NON-NLS-1$
    }

    // ------------------------------------------------------------------------
    // Methods from AbstractTmfStateProvider
    // ------------------------------------------------------------------------

    @Override
    public LttngFtraceCallStackProvider getNewInstance() {
        return new LttngFtraceCallStackProvider(getTrace());
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    // ------------------------------------------------------------------------
    // Methods from CallStackStateProvider
    // ------------------------------------------------------------------------

    /**
     * Check that this event contains the required information we need to be
     * used in the call stack view.
     */
    @Override
    protected boolean considerEvent(@Nullable ITmfEvent event) {
        if (!(event instanceof CtfTmfEvent)) {
            return false;
        }
        String name = event.getName();
        if (!funcEntryEvents.contains(name) && !funcExitEvents.contains(name)) {
            return false;
        }
        return true;
    }

    @Override
    public String functionEntry(ITmfEvent event) {
        String eventName = event.getName();
        if (!funcEntryEvents.contains(eventName)) {
            return null;
        }
        Long address = (Long) event.getContent().getField("ip").getValue(); //$NON-NLS-1$
        return Long.toHexString(address);
    }

    @Override
    public String functionExit(ITmfEvent event) {
        return CallStackStateProvider.UNDEFINED;
    }

    @Override
    public String getThreadName(ITmfEvent event) {
        Integer tid = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), LinuxTidAspect.class, event);
        if (tid == null) {
            return "unknown"; //$NON-NLS-1$
        }

        return "tid-" + tid.toString(); //$NON-NLS-1$
    }

    @Override
    protected Long getThreadId(ITmfEvent event) {
        Integer tid = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), LinuxTidAspect.class, event);
        if (tid == null) {
            return 0L;
        }
        return tid.longValue();
    }
}
