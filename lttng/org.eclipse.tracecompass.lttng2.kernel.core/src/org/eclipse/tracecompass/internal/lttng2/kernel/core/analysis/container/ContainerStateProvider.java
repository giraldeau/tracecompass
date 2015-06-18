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

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.StateValues;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.trace.layout.Lttng27EventLayout;

import com.google.common.collect.ImmutableMap;

/**
 * The stateprovider for the container analysis. It will get the events from the
 * trace and create the statesystem accordingly
 *
 * @author Francis Jolivet
 * @author Sébastien Lorrain
 */
public class ContainerStateProvider extends AbstractTmfStateProvider {

    private static final int VERSION = 1;
    private final Map<String, Integer> fEventNames;
    private final Lttng27EventLayout fLayout;

    private ContainerBuilder containerBuilder;
    @Nullable
    private TaskContainerEvent previousStateDumpTask;

    /**
     * @param trace
     *            The trace to analyse
     * @param layout
     *            Must be LttngEventLayout because this analysis is Lttng
     *            specific
     */
    public ContainerStateProvider(ITmfTrace trace, Lttng27EventLayout layout) {
        super(trace, "Lxc State Provider"); //$NON-NLS-1$
        fLayout = layout;
        fEventNames = buildEventNames(layout);
        containerBuilder = new ContainerBuilder();
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public ContainerStateProvider getNewInstance() {

        ITmfTrace trace = getTrace();
        return new ContainerStateProvider(trace, fLayout);
    }

    @Override
    protected void eventHandle(@Nullable ITmfEvent event) {
        if (event == null) {
            return;
        }

        try {

            final ITmfStateSystemBuilder ssb = checkNotNull(getStateSystemBuilder());

            final String eventName = event.getType().getName();
            final long ts = event.getTimestamp().getValue();

            /*
             * Feed event to the history system if it's known to cause a state
             * transition.
             */
            Integer idx = fEventNames.get(eventName);
            int intval = (idx == null ? -1 : idx.intValue());

            switch (intval) {

            case SCHED_PROCESS_FORK: {
                ITmfEventField content = event.getContent();
                final int parentTid, childTid, childNSInum;
                long[] childVTids = {-1};
                parentTid = ((Long) content.getField(fLayout.fieldParentTid()).getValue()).intValue();
                childTid = ((Long) content.getField(fLayout.fieldChildTid()).getValue()).intValue();
                ITmfEventField field = content.getField(fLayout.fieldChildVTids());
                if (field != null) {
                    childVTids = ((long[]) field.getValue());
                }
                // new version of event!
                field = content.getField(fLayout.fieldChildNSInum());
                if (field != null) {
                    childNSInum = ((Long) field.getValue()).intValue();
                } else {
                    childNSInum = -1;
                }

                Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
                if (cpuObj == null) {
                    /* We couldn't find any CPU information, ignore this event */
                    return;
                }
                Integer cpu = (Integer) cpuObj;

                if (childVTids != null) {
                    TaskContainerEvent t = new TaskContainerEvent(ts, childTid, childVTids, parentTid, ContainerCpuState.CPU_STATUS_IDLE, cpu, childNSInum);
                    containerBuilder.insertTaskContainerEvent(ssb, t);
                }
            }
                break;

            case SCHED_SWITCH: {
                ITmfEventField content = event.getContent();
                Integer prevTid = ((Long) content.getField(fLayout.fieldPrevTid()).getValue()).intValue();
                Integer nextTid = ((Long) content.getField(fLayout.fieldNextTid()).getValue()).intValue();

                Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
                if (cpuObj == null) {
                    /* We couldn't find any CPU information, ignore this event */
                    return;
                }
                Integer cpu = (Integer) cpuObj;

                // Set the CPU in the container that has been sched_switched to
                // IDLE
                TaskCPUEvent outgoingTask = new TaskCPUEvent(ts, prevTid, StateValues.PROCESS_STATUS_WAIT_FOR_CPU, cpu);
                ContainerBuilder.setTaskCPUStatus(ssb, outgoingTask);

                // Set the CPU in the container that has beed sched_switched to
                // RUNNING
                // and propagate changes.
                TaskCPUEvent ongoingTask = new TaskCPUEvent(ts, nextTid, StateValues.PROCESS_STATUS_RUN_USERMODE, cpu);
                ContainerBuilder.setTaskCPUStatus(ssb, ongoingTask);

            }
                break;

            case SCHED_PROCESS_FREE: {
                // Integer tid = ((Long)
                // event.getContent().getField(fLayout.fieldTid()).getValue()).intValue();
                // ContainerManager.removeTask(ssb, ts, tid);
            }
                break;

            case STATEDUMP_PROCESS: {
                ITmfEventField content = event.getContent();
                final int tID, vtID, ppID, nsLevel, status, nsInum;
                tID = ((Long) content.getField(fLayout.fieldTid()).getValue()).intValue();
                vtID = ((Long) content.getField(fLayout.fieldVTid()).getValue()).intValue();
                ppID = ((Long) content.getField(fLayout.fieldPPid()).getValue()).intValue();
                nsLevel = ((Long) content.getField(fLayout.fieldNSLevel()).getValue()).intValue();

                status = ((Long) content.getField(fLayout.fieldStatus()).getValue()).intValue();
                if (content.getField(fLayout.fieldNSInum()) != null) {
                    nsInum = ((Long) content.getField(fLayout.fieldNSInum()).getValue()).intValue();
                } else {
                    nsInum = -1;
                }

                Object cpuObj = TmfTraceUtils.resolveEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
                if (cpuObj == null) {
                    /* We couldn't find any CPU information, ignore this event */
                    return;
                }

                // When we first hit the root container, add his INode info to
                // the container tree.
                if (nsLevel == 0) {
                    ContainerInfo cinfo = new ContainerInfo(nsInum, 0, "TODO_ADD_HOSTNAME"); //$NON-NLS-1$
                    containerBuilder.addRootContainerInfo(ssb, ts, cinfo);
                }

                Integer cpu = (Integer) cpuObj;
                final TaskContainerEvent previousTask = previousStateDumpTask;
                if(previousTask == null)
                {
                    //First statedump ever received, create the "previous task"
                    previousStateDumpTask = new TaskContainerEvent(ts, tID, new long[]{vtID}, ppID, status, cpu, nsInum);
                }
                else if(previousTask.getTid() == tID)
                {
                    /*
                     * We got the statedump corresponding to the next
                     * pid namespace of the same task.
                     * Since statedump receive vtids in "reverse order",
                     * that is from the deepest level of pid_namespcae to
                     * the root, we add the new vtid at the beginning of the list
                     */
                    List<Integer> vtids = previousTask.getVtids();
                    vtids.add(0, vtID);
                }
                else
                {
                    /*
                     * We got the statedump of a new task.
                     * We can dump the previous one to the container builder.
                     * Create a new task to accumulate the vtids...
                     */
                    containerBuilder.insertTaskContainerEvent(ssb, previousTask);
                    previousStateDumpTask = new TaskContainerEvent(ts, tID, new long[]{vtID}, ppID, status, cpu, nsInum);
                }
            }
                break;

            default:
                break;
            }
        } catch (TimeRangeException tre) {
            /*
             * This would happen if the events in the trace aren't ordered
             * chronologically, which should never be the case ...
             */
            // TODO: log me
            System.err.println("TimeRangeExcpetion caught in the state system's event manager."); //$NON-NLS-1$
            System.err.println("Are the events in the trace correctly ordered?"); //$NON-NLS-1$
            tre.printStackTrace();

        } catch (StateValueTypeException sve) {
            /*
             * This would happen if we were trying to push/pop attributes not of
             * type integer. Which, once again, should never happen.
             */
            // TODO: log me
            sve.printStackTrace();
        }

    }

    // ------------------------------------------------------------------------
    // Event names management
    // ------------------------------------------------------------------------

    private static final int SCHED_PROCESS_FORK = 1;
    private static final int SCHED_PROCESS_FREE = 2;
    private static final int SCHED_SWITCH = 3;
    private static final int STATEDUMP_PROCESS = 4;

    private static Map<String, Integer> buildEventNames(IKernelAnalysisEventLayout layout) {
        ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();

        builder.put(layout.eventSchedProcessFork(), SCHED_PROCESS_FORK);
        builder.put(layout.eventSchedProcessFree(), SCHED_PROCESS_FREE);
        builder.put(layout.eventSchedSwitch(), SCHED_SWITCH);
        builder.put(layout.eventStatedumpProcessState(), STATEDUMP_PROCESS);

        return checkNotNull(builder.build());
    }
}
