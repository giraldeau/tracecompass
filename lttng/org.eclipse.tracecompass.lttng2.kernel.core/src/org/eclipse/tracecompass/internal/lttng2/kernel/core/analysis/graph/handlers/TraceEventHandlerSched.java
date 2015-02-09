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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.tracecompass.analysis.graph.core.building.AbstractTmfGraphProvider;
import org.eclipse.tracecompass.analysis.graph.core.building.AbstractTraceEventHandler;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.building.LttngKernelExecGraphProvider.ProcessStatus;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.EventField;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngStrings;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngSystemModel;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngWorker;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTraceWithPreDefinedEvents;
import org.eclipse.tracecompass.tmf.core.trace.TmfEventTypeCollectionHelper;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * Provides the current task running on a CPU according to scheduling events
 *
 * @author Francis Giraldeau
 * @author Geneviève Bastien
 */
public class TraceEventHandlerSched extends AbstractTraceEventHandler {

    private final LttngSystemModel fSystem;
    private ITmfTrace fTrace;
    private Map<ITmfTrace, Boolean> fHasEventSchedTTWU;

    /**
     * Constructor
     *
     * @param provider
     *            Graph provider
     * @param system
     *            The system model
     */
    public TraceEventHandlerSched(AbstractTmfGraphProvider provider, LttngSystemModel system) {
        super();
        fSystem = system;

        fTrace = provider.getTrace();
        fHasEventSchedTTWU = new HashMap<>();

        Collection<ITmfTrace> traceSet = TmfTraceManager.getTraceSet(fTrace);
        for (ITmfTrace traceItem : traceSet) {
            if (traceItem instanceof ITmfTraceWithPreDefinedEvents) {
                Set<String> traceEvents = TmfEventTypeCollectionHelper.getEventNames(((ITmfTraceWithPreDefinedEvents) traceItem).getContainedEventTypes());
                fHasEventSchedTTWU.put(traceItem, traceEvents.contains(LttngStrings.SCHED_TTWU));
            }
        }

    }

    /**
     * Get the list of all events handled by this handler
     *
     * @return A list of event names handled by this class
     */
    public static String[] getHandledEvents() {
        return new String[] { LttngStrings.SCHED_SWITCH, LttngStrings.SCHED_WAKEUP,
                LttngStrings.SCHED_PROCESS_FORK, LttngStrings.SCHED_PROCESS_EXIT,
                LttngStrings.SCHED_PROCESS_EXEC, LttngStrings.SCHED_TTWU,
                LttngStrings.EXIT_SYSCALL, LttngStrings.SCHED_WAKEUP_NEW };
    }

    @Override
    public void handleEvent(ITmfEvent ev) {
        String eventName = ev.getType().getName();

        switch (eventName) {
        case LttngStrings.SCHED_SWITCH:
            handleSchedSwitch(ev);
            break;
        case LttngStrings.SCHED_TTWU:
            if (fHasEventSchedTTWU.get(ev.getTrace())) {
                handleSchedWakeup(ev);
            }
            break;
        case LttngStrings.SCHED_WAKEUP:
            if (!fHasEventSchedTTWU.get(ev.getTrace())) {
                handleSchedWakeup(ev);
            }
            break;
        case LttngStrings.SCHED_WAKEUP_NEW:
            if (!fHasEventSchedTTWU.get(ev.getTrace())) {
                handleSchedWakeup(ev);
            }
            break;
        case LttngStrings.SCHED_PROCESS_FORK:
            handleSchedProcessFork(ev);
            break;
        case LttngStrings.SCHED_PROCESS_EXIT:
            handleSchedProcessExit(ev);
            break;
        case LttngStrings.SCHED_PROCESS_EXEC:
            handleSchedProcessExec(ev);
            break;
        case LttngStrings.EXIT_SYSCALL:
            handleExitSyscall(ev);
            break;
        default:
            break;
        }
    }

    private void handleSchedSwitch(ITmfEvent event) {
        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            throw new NullPointerException();
        }
        Integer cpu = (Integer) cpuObj;

        Integer next = EventField.getInt(event, LttngStrings.NEXT_TID);
        Integer prev = EventField.getInt(event, LttngStrings.PREV_TID);
        long ts = event.getTimestamp().getValue();
        long prev_state = EventField.getLong(event, LttngStrings.PREV_STATE);
        prev_state = (long) ((int) prev_state) & (0x3);
        String host = event.getTrace().getHostId();

        fSystem.cacheTidOnCpu(cpu, new HostThread(event.getTrace().getHostId(), next));

        HostThread nextHt = new HostThread(host, next);
        LttngWorker nextTask = fSystem.findWorker(nextHt);
        if (nextTask == null) {
            String name = EventField.getOrDefault(event, LttngStrings.NEXT_COMM, LttngStrings.UNKNOWN);
            nextTask = new LttngWorker(nextHt, name, ts);
            fSystem.addWorker(nextTask);
        }
        nextTask.setStatus(ProcessStatus.RUN);

        HostThread prevHt = new HostThread(host, prev);
        LttngWorker prevTask = fSystem.findWorker(prevHt);
        if (prevTask == null) {
            String name = EventField.getOrDefault(event, LttngStrings.PREV_COMM, LttngStrings.UNKNOWN);
            prevTask = new LttngWorker(prevHt, name, ts);
            fSystem.addWorker(prevTask);
        }
        /* prev_state == 0 means runnable, thus waits for cpu */
        if (prev_state == 0) {
            prevTask.setStatus(ProcessStatus.WAIT_CPU);
        } else {
            prevTask.setStatus(ProcessStatus.WAIT_BLOCKED);
        }
    }

    private void handleSchedProcessFork(ITmfEvent event) {
        String host = event.getTrace().getHostId();

        Integer childTid = EventField.getInt(event, LttngStrings.CHILD_TID);
        String name = EventField.getString(event, LttngStrings.CHILD_COMM);
        long ts = event.getTimestamp().getValue();

        HostThread childHt = new HostThread(host, childTid);

        LttngWorker childTask = fSystem.findWorker(childHt);
        if (childTask == null) {
            childTask = new LttngWorker(childHt, name, ts);
            fSystem.addWorker(childTask);
        }

        childTask.setStatus(ProcessStatus.WAIT_FORK);
    }

    private void handleSchedWakeup(ITmfEvent event) {
        String host = event.getTrace().getHostId();
        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            throw new NullPointerException();
        }
        Integer cpu = (Integer) cpuObj;

        Integer tid = EventField.getInt(event, LttngStrings.TID);
        HostThread targetHt = new HostThread(host, tid);

        LttngWorker target = fSystem.findWorker(targetHt);
        LttngWorker current = fSystem.getWorkerOnCpu(host, cpu);
        if (target == null) {
            String name = EventField.getOrDefault(event, LttngStrings.COMM, LttngStrings.UNKNOWN);
            target = new LttngWorker(targetHt, name, event.getTimestamp().getValue());
            fSystem.addWorker(target);
            target.setStatus(ProcessStatus.WAIT_BLOCKED);
        }
        // spurious wakeup
        ProcessStatus status = target.getStatus();
        if ((current != null && target.getHostThread().equals(current.getHostThread())) ||
                status == ProcessStatus.WAIT_CPU) {
            return;
        }
        if (status == ProcessStatus.WAIT_BLOCKED ||
                status == ProcessStatus.WAIT_FORK ||
                status == ProcessStatus.UNKNOWN) {
            target.setStatus(ProcessStatus.WAIT_CPU);
            return;
        }
    }

    private void handleSchedProcessExit(ITmfEvent event) {
        String host = event.getTrace().getHostId();

        Integer tid = EventField.getInt(event, LttngStrings.TID);
        LttngWorker task = fSystem.findWorker(new HostThread(host, tid));
        if (task == null) {
            return;
        }
        task.setStatus(ProcessStatus.EXIT);
    }

    private void handleSchedProcessExec(ITmfEvent event) {
        String host = event.getTrace().getHostId();
        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            throw new NullPointerException();
        }
        Integer cpu = (Integer) cpuObj;

        String filename = EventField.getString(event, LttngStrings.FILENAME);
        LttngWorker task = fSystem.getWorkerOnCpu(host, cpu);
        if (task == null) {
            return;
        }
        task.setName(filename);
    }

    private void handleExitSyscall(ITmfEvent event) {
        String host = event.getTrace().getHostId();
        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            throw new NullPointerException();
        }
        Integer cpu = (Integer) cpuObj;

        LttngWorker task = fSystem.getWorkerOnCpu(host, cpu);
        if (task == null) {
            return;
        }
    }

}
