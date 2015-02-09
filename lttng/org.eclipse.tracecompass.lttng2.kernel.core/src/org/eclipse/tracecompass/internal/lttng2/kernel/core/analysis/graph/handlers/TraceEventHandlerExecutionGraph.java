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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge.EdgeType;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex.EdgeDirection;
import org.eclipse.tracecompass.analysis.graph.core.building.AbstractTmfGraphProvider;
import org.eclipse.tracecompass.analysis.graph.core.building.AbstractTraceEventHandler;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.TcpEventStrings;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.building.LttngKernelExecGraphProvider.Context;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.building.LttngKernelExecGraphProvider.ProcessStatus;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.EventField;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngInterruptContext;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngStrings;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngSystemModel;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.LttngWorker;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model.Softirq;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.event.matching.IMatchProcessingUnit;
import org.eclipse.tracecompass.tmf.core.event.matching.TmfEventDependency;
import org.eclipse.tracecompass.tmf.core.event.matching.TmfEventMatching;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTraceWithPreDefinedEvents;
import org.eclipse.tracecompass.tmf.core.trace.TmfEventTypeCollectionHelper;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Event handler that actually builds the execution graph from the events
 *
 * @author Francis Giraldeau
 * @author Geneviève Bastien
 */
public class TraceEventHandlerExecutionGraph extends AbstractTraceEventHandler {

    private final AbstractTmfGraphProvider fProvider;
    private final LttngSystemModel fSystem;
    private final Table<String, Integer, LttngWorker> fKernel;
    private final IMatchProcessingUnit fMatchProcessing;
    private HashMap<ITmfEvent, TmfVertex> fTcpNodes;
    private TmfEventMatching fTcpMatching;
    private Map<ITmfTrace, Boolean> fHasEventSchedTTWU;

    /**
     * Get the list of events handled by this handler
     *
     * @return The list of events handled by this handler
     */
    public static String[] getHandledEvents() {
        return new String[] { LttngStrings.SCHED_SWITCH, LttngStrings.SCHED_WAKEUP_NEW,
                LttngStrings.SCHED_WAKEUP, LttngStrings.SCHED_TTWU, LttngStrings.SOFTIRQ_ENTRY,
                TcpEventStrings.INET_SOCK_LOCAL_IN, TcpEventStrings.INET_SOCK_LOCAL_OUT,
                TcpEventStrings.NET_DEV_QUEUE, TcpEventStrings.NETIF_RECEIVE_SKB };
    }

    /**
     * Constructor
     *
     * @param provider
     *            The graph provider
     * @param system
     *            The system model associated with the system under
     *            investigation
     */
    public TraceEventHandlerExecutionGraph(AbstractTmfGraphProvider provider, LttngSystemModel system) {
        super();
        fSystem = system;
        fProvider = provider;
        fKernel = NonNullUtils.checkNotNull(HashBasedTable.<String, Integer, LttngWorker> create());

        fTcpNodes = new HashMap<>();
        fMatchProcessing = new IMatchProcessingUnit() {

            @Override
            public void matchingEnded() {
            }

            @Override
            public int countMatches() {
                return 0;
            }

            @Override
            public void addMatch(@Nullable TmfEventDependency match) {
                if (match == null) {
                    return;
                }
                TmfVertex output = fTcpNodes.remove(match.getSourceEvent());
                TmfVertex input = fTcpNodes.remove(match.getDestinationEvent());
                if (output != null && input != null) {
                    output.linkVertical(input).setType(EdgeType.NETWORK);
                }
            }

            @Override
            public void init(@Nullable Collection<ITmfTrace> fTraces) {

            }

        };

        ITmfTrace trace = provider.getTrace();
        fTcpMatching = new TmfEventMatching(Collections.singleton(trace), fMatchProcessing);
        fTcpMatching.initMatching();

        fHasEventSchedTTWU = new HashMap<>();
        for (ITmfTrace traceItem : TmfTraceManager.getTraceSet(trace)) {
            // FIXME: migrate to ITmfTrace
            if (traceItem instanceof ITmfTraceWithPreDefinedEvents) {
                Set<String> traceEvents = TmfEventTypeCollectionHelper.getEventNames(((ITmfTraceWithPreDefinedEvents) traceItem).getContainedEventTypes());
                fHasEventSchedTTWU.put(traceItem, traceEvents.contains(LttngStrings.SCHED_TTWU));
            }
        }
    }

    private LttngWorker getOrCreateKernelWorker(ITmfEvent event, Integer cpu) {
        String host = event.getTrace().getHostId();
        LttngWorker worker = fKernel.get(host, cpu);
        if (worker == null) {
            HostThread ht = new HostThread(host, -1);
            worker = new LttngWorker(ht, "kernel/" + cpu, event.getTimestamp().getValue()); //$NON-NLS-1$
            worker.setStatus(ProcessStatus.RUN);

            fKernel.put(host, cpu, worker);
        }
        return worker;
    }

    @Override
    public void handleEvent(ITmfEvent event) {
        String eventName = event.getType().getName();

        switch (eventName) {
        case LttngStrings.SCHED_SWITCH:
            handleSchedSwitch(event);
            break;
        case LttngStrings.SCHED_TTWU:
            if (fHasEventSchedTTWU.get(event.getTrace())) {
                handleSchedWakeup(event);
            }
            break;
        case LttngStrings.SCHED_WAKEUP:
            if (!fHasEventSchedTTWU.get(event.getTrace())) {
                handleSchedWakeup(event);
            }
            break;
        case LttngStrings.SCHED_WAKEUP_NEW:
            if (!fHasEventSchedTTWU.get(event.getTrace())) {
                handleSchedWakeup(event);
            }
            break;
        case LttngStrings.SOFTIRQ_ENTRY:
            handleSoftirqEntry(event);
            break;
        case TcpEventStrings.INET_SOCK_LOCAL_IN:
        case TcpEventStrings.NETIF_RECEIVE_SKB:
            handleInetSockLocalIn(event);
            break;
        case TcpEventStrings.INET_SOCK_LOCAL_OUT:
        case TcpEventStrings.NET_DEV_QUEUE:
            handleInetSockLocalOut(event);
            break;
        default:
            break;
        }
    }

    private TmfVertex stateExtend(LttngWorker task, long ts) {
        TmfGraph graph = NonNullUtils.checkNotNull(fProvider.getAssignedGraph());
        TmfVertex node = new TmfVertex(ts);
        ProcessStatus status = task.getStatus();
        graph.append(task, node, resolveProcessStatus(status));
        return node;
    }

    private TmfVertex stateChange(LttngWorker task, long ts) {
        TmfGraph graph = NonNullUtils.checkNotNull(fProvider.getAssignedGraph());
        TmfVertex node = new TmfVertex(ts);
        ProcessStatus status = task.getOldStatus();
        graph.append(task, node, resolveProcessStatus(status));
        return node;
    }

    private static EdgeType resolveProcessStatus(ProcessStatus status) {
        EdgeType ret = EdgeType.UNKNOWN;
        switch (status) {
        case DEAD:
            break;
        case EXIT:
        case RUN:
            ret = EdgeType.RUNNING;
            break;
        case UNKNOWN:
            ret = EdgeType.UNKNOWN;
            break;
        case WAIT_BLOCKED:
            ret = EdgeType.BLOCKED;
            break;
        case WAIT_CPU:
        case WAIT_FORK:
            ret = EdgeType.PREEMPTED;
            break;
        case ZOMBIE:
            ret = EdgeType.UNKNOWN;
            break;
        default:
            break;
        }
        return ret;
    }

    private void handleSchedSwitch(ITmfEvent event) {
        String host = event.getTrace().getHostId();
        long ts = event.getTimestamp().getValue();
        Integer next = EventField.getInt(event, LttngStrings.NEXT_TID);
        Integer prev = EventField.getInt(event, LttngStrings.PREV_TID);

        LttngWorker nextTask = fSystem.findWorker(new HostThread(host, next));
        LttngWorker prevTask = fSystem.findWorker(new HostThread(host, prev));

        if (prevTask == null || nextTask == null) {
            return;
        }
        stateChange(prevTask, ts);
        stateChange(nextTask, ts);
    }

    private void handleSchedWakeup(ITmfEvent event) {
        TmfGraph graph = NonNullUtils.checkNotNull(fProvider.getAssignedGraph());
        String host = event.getTrace().getHostId();
        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            throw new NullPointerException();
        }
        Integer cpu = (Integer) cpuObj;

        long ts = event.getTimestamp().getValue();
        Integer tid = EventField.getInt(event, LttngStrings.TID);

        LttngWorker target = fSystem.findWorker(new HostThread(host, tid));
        LttngWorker current = fSystem.getWorkerOnCpu(host, cpu);
        if (target == null) {
            return;
        }

        ProcessStatus status = target.getOldStatus();
        switch (status) {
        case WAIT_FORK:
            if (current != null) {
                TmfVertex n0 = stateExtend(current, ts);
                TmfVertex n1 = stateChange(target, ts);
                graph.link(n0, n1);
            } else {
                stateChange(target, ts);
            }
            break;
        case WAIT_BLOCKED:
            LttngInterruptContext context = fSystem.peekContextStack(host, cpu);
            switch (context.getContext()) {
            case HRTIMER:
                // shortcut of appendTaskNode: resolve blocking source in situ
                graph.append(target, new TmfVertex(ts), EdgeType.TIMER);
                break;
            case IRQ:
                TmfEdge l3 = graph.append(target, new TmfVertex(ts));
                if (l3 != null) {
                    l3.setType(resolveIRQ(context.getEvent()));
                }
                break;
            case SOFTIRQ:
                TmfVertex wup = new TmfVertex(ts);
                TmfEdge l2 = graph.append(target, wup);
                if (l2 != null) {
                    l2.setType(resolveSoftirq(context.getEvent()));
                }
                // special case for network related softirq
                Long vec = EventField.getLong(context.getEvent(), LttngStrings.VEC);
                if (vec == Softirq.NET_RX || vec == Softirq.NET_TX) {
                    // create edge if wake up is caused by incoming packet
                    LttngWorker k = getOrCreateKernelWorker(event, cpu);
                    TmfVertex tail = graph.getTail(k);
                    if (tail == null) {
                        break;
                    }
                    if (tail.getEdge(EdgeDirection.INCOMING_VERTICAL_EDGE) != null) {
                        TmfVertex kwup = stateExtend(k, event.getTimestamp().getValue());
                        kwup.linkVertical(wup);
                    }
                }
                break;
            case NONE:
                // task context wakeup
                if (current != null) {
                    TmfVertex n0 = stateExtend(current, ts);
                    TmfVertex n1 = stateChange(target, ts);
                    n0.linkVertical(n1);
                } else {
                    stateChange(target, ts);
                }
                break;
            default:
                break;
            }
            break;
        case DEAD:
        case EXIT:
        case RUN:
        case UNKNOWN:
        case WAIT_CPU:
        case ZOMBIE:
            break;
        default:
            break;
        }
    }

    private static EdgeType resolveIRQ(ITmfEvent event) {
        int vec = EventField.getLong(event, LttngStrings.IRQ).intValue();
        EdgeType ret = EdgeType.UNKNOWN;
        switch (vec) {
        case 0: // resched
            ret = EdgeType.INTERRUPTED;
            break;
        case 19: // ehci_hcd:usb
        case 23:
            ret = EdgeType.USER_INPUT;
            break;
        default:
            ret = EdgeType.UNKNOWN;
            break;
        }
        return ret;
    }

    private static EdgeType resolveSoftirq(ITmfEvent event) {
        int vec = EventField.getLong(event, LttngStrings.VEC).intValue();
        EdgeType ret = EdgeType.UNKNOWN;
        switch (vec) {
        case Softirq.HRTIMER:
        case Softirq.TIMER:
            ret = EdgeType.TIMER;
            break;
        case Softirq.BLOCK:
        case Softirq.BLOCK_IOPOLL:
            ret = EdgeType.BLOCK_DEVICE;
            break;
        case Softirq.NET_RX:
        case Softirq.NET_TX:
            ret = EdgeType.NETWORK;
            break;
        case Softirq.SCHED:
            ret = EdgeType.INTERRUPTED;
            break;
        default:
            ret = EdgeType.UNKNOWN;
            break;
        }
        return ret;
    }

    private void handleInetSockLocalIn(ITmfEvent event) {
        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            throw new NullPointerException();
        }
        Integer cpu = (Integer) cpuObj;
        String host = event.getTrace().getHostId();
        LttngInterruptContext intCtx = fSystem.peekContextStack(host, cpu);
        Context context = intCtx.getContext();
        if (context == Context.SOFTIRQ) {
            LttngWorker k = getOrCreateKernelWorker(event, cpu);
            TmfVertex endpoint = stateExtend(k, event.getTimestamp().getValue());
            fTcpNodes.put(event, endpoint);
            // TODO add actual progress monitor
            fTcpMatching.matchEvent(event, event.getTrace(), new NullProgressMonitor());
        }
    }

    private void handleInetSockLocalOut(ITmfEvent event) {
        Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            throw new NullPointerException();
        }
        Integer cpu = (Integer) cpuObj;
        String host = event.getTrace().getHostId();

        LttngInterruptContext intCtx = fSystem.peekContextStack(host, cpu);
        Context context = intCtx.getContext();

        LttngWorker sender = null;
        if (context == Context.NONE) {
            sender = fSystem.getWorkerOnCpu(event.getTrace().getHostId(), cpu);
        } else if (context == Context.SOFTIRQ) {
            sender = getOrCreateKernelWorker(event, cpu);
        }
        if (sender == null) {
            return;
        }
        TmfVertex endpoint = stateExtend(sender, event.getTimestamp().getValue());
        fTcpNodes.put(event, endpoint);
        // TODO, add actual progress monitor
        fTcpMatching.matchEvent(event, event.getTrace(), new NullProgressMonitor());
    }

    private void handleSoftirqEntry(ITmfEvent event) {
        TmfGraph graph = NonNullUtils.checkNotNull(fProvider.getAssignedGraph());
        Long vec = EventField.getLong(event, LttngStrings.VEC);
        if (vec == Softirq.NET_RX || vec == Softirq.NET_TX) {
            Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
            if (cpuObj == null) {
                throw new NullPointerException();
            }
            Integer cpu = (Integer) cpuObj;
            LttngWorker k = getOrCreateKernelWorker(event, cpu);
            graph.add(k, new TmfVertex(event.getTimestamp().getValue()));
        }
    }

}
