/*******************************************************************************
 * Copyright (c) 2013 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mohamad Gebai - Initial API and implementation
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.vcpuview;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.core.kernelanalysis.KernelThreadInformationProvider;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.VmAttributes;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.VirtualMachineCpuAnalysis;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.trace.VirtualMachineExperiment;
import org.eclipse.tracecompass.internal.lttng2.kernel.ui.Activator;
import org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.vm.vcpuview.VirtualMachineCommon.Type;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperimentUtils;
import org.eclipse.tracecompass.tmf.ui.views.timegraph.AbstractTimeGraphView;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;

import com.google.common.collect.Multimap;

/**
 * Main implementation for the Virtual Machine view
 *
 * @author Mohamad Gebai
 */
public class VirtualMachineView extends AbstractTimeGraphView {

    /** View ID. */
    public static final String ID = "org.eclipse.tracecompass.lttng2.kernel.ui.views.vm.vcpuview"; //$NON-NLS-1$

    private static final String[] COLUMN_NAMES = new String[] {
            Messages.VmView_stateTypeName
    };

    private static final String[] FILTER_COLUMN_NAMES = new String[] {
            Messages.VmView_stateTypeName
    };

    // Timeout between updates in the build thread in ms
    private static final long BUILD_UPDATE_TIMEOUT = 500;
    private static final int[] DEFAULT_WEIGHT = { 1, 3 };

    private Comparator<ITimeGraphEntry> fComparator = new Comparator<ITimeGraphEntry>() {
        @Override
        public int compare(@Nullable ITimeGraphEntry o1, @Nullable ITimeGraphEntry o2) {
            if (!((o1 instanceof VirtualMachineViewEntry) && (o2 instanceof VirtualMachineViewEntry))) {
                return 0;
            }
            VirtualMachineViewEntry entry1 = (VirtualMachineViewEntry) o1;
            VirtualMachineViewEntry entry2 = (VirtualMachineViewEntry) o2;
            int cmp = entry1.getType().compareTo(entry2.getType());
            if (cmp == 0) {
                cmp = entry1.getName().compareTo(entry2.getName());
            }
            return cmp;
        }
    };

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Default constructor
     */
    public VirtualMachineView() {
        super(ID, new VirtualMachinePresentationProvider());
        setFilterColumns(FILTER_COLUMN_NAMES);
        setTreeColumns(COLUMN_NAMES);
        setTreeLabelProvider(new VmViewTreeLabelProvider());
        setWeight(DEFAULT_WEIGHT);
        setAutoExpandLevel(2);
    }

    @Override
    protected @Nullable String getNextText() {
        return Messages.VmView_nextResourceActionNameText;
    }

    @Override
    protected @Nullable String getNextTooltip() {
        return Messages.VmView_nextResourceActionToolTipText;
    }

    @Override
    protected @Nullable String getPrevText() {
        return Messages.VmView_previousResourceActionNameText;
    }

    @Override
    protected @Nullable String getPrevTooltip() {
        return Messages.VmView_previousResourceActionToolTipText;
    }

    private static class VmViewTreeLabelProvider extends TreeLabelProvider {

        @Override
        public String getColumnText(@Nullable Object element, int columnIndex) {
            if (!(element instanceof VirtualMachineViewEntry)) {
                return ""; //$NON-NLS-1$
            }
            VirtualMachineViewEntry entry = (VirtualMachineViewEntry) element;

            if (COLUMN_NAMES[columnIndex].equals(Messages.VmView_stateTypeName)) {
                String name = entry.getName();
                return (name == null) ? "" : name; //$NON-NLS-1$
            }
            return ""; //$NON-NLS-1$
        }

    }

    // ------------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------------

    @Override
    protected void buildEventList(ITmfTrace trace, ITmfTrace parentTrace, IProgressMonitor monitor) {
        setStartTime(Long.MAX_VALUE);
        setEndTime(Long.MIN_VALUE);

        if (monitor.isCanceled()) {
            return;
        }
        if (!(parentTrace instanceof VirtualMachineExperiment)) {
            return;
        }
        ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(parentTrace, VirtualMachineCpuAnalysis.ID);
        if (ssq == null) {
            return;
        }
        VirtualMachineExperiment vmExperiment = (VirtualMachineExperiment) parentTrace;
        long startTime = ssq.getStartTime();

        ArrayList<VirtualMachineViewEntry> entryList = new ArrayList<>();

        boolean complete = false;
        while (!complete) {
            if (monitor.isCanceled()) {
                return;
            }
            complete = ssq.waitUntilBuilt(BUILD_UPDATE_TIMEOUT);
            if (ssq.isCancelled()) {
                return;
            }

            /*
             * FIXME: When the analysis is ongoing, the "Thread" entry cannot be
             * expanded, since this list is cleared and the entry at that level
             * is reinitialized to not expanded. The list shouldn't be cleared
             * but added to.
             */
            entryList.clear();
            long endTime = ssq.getCurrentEndTime() + 1;

            VirtualMachineViewEntry groupEntry = new VirtualMachineViewEntry.VmEntryBuilder(vmExperiment.getName(), startTime, endTime, vmExperiment)
                    .build();

            entryList.add(groupEntry);
            setStartTime(Math.min(getStartTime(), startTime));
            setEndTime(Math.max(getEndTime(), endTime));

            try {
                List<Integer> vmQuarks = ssq.getQuarks(VmAttributes.VIRTUAL_MACHINES, "*"); //$NON-NLS-1$
                Set<VirtualMachineViewEntry> vmEntries = new HashSet<>();
                /* For each virtual machine in the analysis */
                for (Integer vmQuark : vmQuarks) {
                    if (vmQuark == null) {
                        throw new IllegalStateException();
                    }

                    /* Display an entry for the machine */
                    String vmHostId = NonNullUtils.checkNotNull(ssq.getAttributeName(vmQuark));
                    ITmfStateInterval vmNameInterval = StateSystemUtils.queryUntilNonNullValue(ssq, vmQuark, startTime, endTime);
                    String vmName = vmHostId;
                    if (vmNameInterval != null) {
                        vmName = vmNameInterval.getStateValue().unboxStr();
                    }

                    VirtualMachineViewEntry vmEntry = new VirtualMachineViewEntry.VmEntryBuilder(vmName, startTime, endTime, vmExperiment)
                            .setId(vmHostId)
                            .setVmName(vmName)
                            .setNumericId(vmQuark)
                            .setType(Type.VM)
                            .build();
                    vmEntry.sortChildren(fComparator);

                    groupEntry.addChild(vmEntry);
                    vmEntries.add(vmEntry);

                    /* Display an entry for each of its CPUs */
                    for (Integer vCpuQuark : ssq.getSubAttributes(vmQuark, false)) {
                        if (vCpuQuark == null) {
                            throw new IllegalStateException();
                        }
                        String vcpuId = ssq.getAttributeName(vCpuQuark);
                        vcpuId = "CPU unknown"; //$NON-NLS-1$

                        VirtualMachineViewEntry vcpuEntry = new VirtualMachineViewEntry.VmEntryBuilder(vcpuId, startTime, endTime, vmExperiment)
                                .setId(vcpuId)
                                .setVmName(vmName)
                                .setNumericId(vCpuQuark)
                                .setType(Type.VCPU)
                                .build();

                        vmEntry.addChild(vcpuEntry);

                    }

                    /*
                     * Get the LTTng Kernel analysis module from the
                     * corresponding trace
                     */
                    VirtualMachineViewEntry threadEntry = new VirtualMachineViewEntry.VmEntryBuilder(NonNullUtils.nullToEmptyString(Messages.VmView_threads), startTime, endTime, vmExperiment).build();
                    vmEntry.addChild(threadEntry);

                    KernelAnalysisModule kernelModule = TmfExperimentUtils.getAnalysisModuleOfClassForHost(vmExperiment, vmHostId, KernelAnalysisModule.class);
                    if (kernelModule == null) {
                        continue;
                    }

                    /*
                     * Display an entry for each thread.
                     *
                     * For each interval that is in a running status, intersect
                     * with the status of the virtual CPU it is currently
                     * running on
                     */
                    Collection<Integer> threadIds = KernelThreadInformationProvider.getThreadIds(kernelModule);
                    for (Integer threadId : threadIds) {
                        if (threadId == null) {
                            throw new IllegalStateException();
                        }
                        String threadName = KernelThreadInformationProvider.getExecutableName(kernelModule, threadId);
                        String tidString = NonNullUtils.checkNotNull(threadId.toString());
                        threadName = (threadName != null) ? tidString + ':' + ' ' + threadName : tidString;
                        VirtualMachineViewEntry oneThreadEntry = new VirtualMachineViewEntry.VmEntryBuilder(threadName, startTime, endTime, vmExperiment)
                                .setId(threadName)
                                .setVmName(vmName)
                                .setNumericId(threadId)
                                .setType(Type.THREAD)
                                .build();

                        threadEntry.addChild(oneThreadEntry);
                    }

                }

            } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
                Activator.getDefault().logError("VirtualMachineView: error building event list", e); //$NON-NLS-1$
            }

            putEntryList(parentTrace, new ArrayList<TimeGraphEntry>(entryList));

            if (parentTrace.equals(getTrace())) {
                refresh();
            }

            for (VirtualMachineViewEntry entry : entryList) {
                if (entry == null) {
                    continue;
                }
                if (monitor.isCanceled()) {
                    return;
                }
                buildEntryEventList(entry, ssq, startTime, endTime, monitor);
            }
        }
    }

    private void buildEntryEventList(TimeGraphEntry entry, ITmfStateSystem ssq, long start, long end, IProgressMonitor monitor) {
        if (start < entry.getEndTime() && end > entry.getStartTime()) {

            long startTime = Math.max(start, entry.getStartTime());
            long endTime = Math.min(end + 1, entry.getEndTime());
            long resolution = Math.max(1, (end - ssq.getStartTime()) / getDisplayWidth());
            List<ITimeEvent> eventList = getEventList(entry, startTime, endTime, resolution, monitor);
            entry.setEventList(eventList);
            redraw();
            for (ITimeGraphEntry child : entry.getChildren()) {
                if (!(child instanceof TimeGraphEntry)) {
                    continue;
                }
                if (monitor.isCanceled()) {
                    return;
                }
                buildEntryEventList((TimeGraphEntry) child, ssq, start, end, monitor);
            }
        }

    }

    @Override
    protected @Nullable List<ITimeEvent> getEventList(TimeGraphEntry entry,
            long startTime, long endTime, long resolution,
            IProgressMonitor monitor) {
        if (!(entry instanceof VirtualMachineViewEntry)) {
            return null;
        }
        if (monitor.isCanceled()) {
            return null;
        }

        VirtualMachineViewEntry vmEntry = (VirtualMachineViewEntry) entry;
        List<ITimeEvent> eventList = null;
        int quark = vmEntry.getNumericId();

        try {
            switch (vmEntry.getType()) {
            case THREAD: {
                /*
                 * The parent VM entry will contain the thread intervals for all
                 * thread. Just take the list from there
                 */
                Collection<ITmfStateInterval> threadIntervals = null;
                ITimeGraphEntry parent = vmEntry.getParent();
                while (threadIntervals == null && parent != null) {
                    if (parent instanceof VirtualMachineViewEntry) {
                        threadIntervals = ((VirtualMachineViewEntry) parent).getThreadIntervals(vmEntry.getNumericId());
                    }
                    if (parent instanceof TimeGraphEntry) {
                        parent = ((TimeGraphEntry) parent).getParent();
                    }
                }
                if (threadIntervals != null) {
                    eventList = new ArrayList<>(threadIntervals.size());
                    long lastEndTime = -1;
                    for (ITmfStateInterval interval : threadIntervals) {
                        if (monitor.isCanceled()) {
                            return null;
                        }
                        long time = interval.getStartTime();
                        long duration = interval.getEndTime() - time + 1;
                        /*
                         * FIXME: I think the key for the alpha bug when alpha
                         * overlaps multiple events is around here
                         */
                        if (!interval.getStateValue().isNull()) {
                            int status = interval.getStateValue().unboxInt();
                            if (lastEndTime < time && lastEndTime != -1) {
                                /*
                                 * Add a time event to fill the blanks between
                                 * intervals
                                 */
                                eventList.add(new TimeEvent(entry, lastEndTime, time - lastEndTime));
                            }
                            eventList.add(new TimeEvent(entry, time, duration, status));
                        } else if (lastEndTime == -1) {
                            eventList.add(new NullTimeEvent(entry, time, duration));
                        }
                        lastEndTime = time + duration;
                    }
                }
            }
                break;
            case VCPU: {
                ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(vmEntry.getExperiment(), VirtualMachineCpuAnalysis.ID);
                if (ssq == null) {
                    return Collections.EMPTY_LIST;
                }
                final long realStart = Math.max(startTime, ssq.getStartTime());
                final long realEnd = Math.min(endTime, ssq.getCurrentEndTime() + 1);
                if (realEnd <= realStart) {
                    return Collections.EMPTY_LIST;
                }
                quark = ssq.getQuarkRelative(quark, VmAttributes.STATUS);
                List<ITmfStateInterval> statusIntervals = StateSystemUtils.queryHistoryRange(ssq, quark, realStart, realEnd - 1, resolution, monitor);
                eventList = new ArrayList<>(statusIntervals.size());
                long lastEndTime = -1;
                for (ITmfStateInterval statusInterval : statusIntervals) {
                    if (monitor.isCanceled()) {
                        return null;
                    }

                    long time = statusInterval.getStartTime();
                    long duration = statusInterval.getEndTime() - time + 1;
                    if (!statusInterval.getStateValue().isNull()) {
                        int status = statusInterval.getStateValue().unboxInt();
                        if (lastEndTime != time && lastEndTime != -1) {
                            eventList.add(new TimeEvent(entry, lastEndTime, time - lastEndTime));
                        }
                        eventList.add(new TimeEvent(entry, time, duration, status));
                    } else if (lastEndTime == -1 || time + duration >= endTime) {
                        // add null event if it intersects the start or end time
                        eventList.add(new NullTimeEvent(entry, time, duration));
                    }
                    lastEndTime = time + duration;
                }
            }
                break;
            case VM: {
                VirtualMachineExperiment experiment = vmEntry.getExperiment();
                VirtualMachineCpuAnalysis vmAnalysis = null;
                for (VirtualMachineCpuAnalysis module : TmfTraceUtils.getAnalysisModulesOfClass(experiment, VirtualMachineCpuAnalysis.class)) {
                    vmAnalysis = module;
                    break;
                }
                if (vmAnalysis == null) {
                    break;
                }
                Multimap<Integer, ITmfStateInterval> updatedThreadIntervals = vmAnalysis.getUpdatedThreadIntervals(vmEntry.getNumericId(), startTime, endTime, resolution, monitor);
                vmEntry.setThreadIntervals(updatedThreadIntervals);
            }
                break;
            case NULL:
                /* These entry types are not used in this view */
                break;
            default:
                break;

            }

        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
            Activator.getDefault().logError("Error getting event list", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }
        return eventList;
    }

    @Override
    protected Iterable<ITmfTrace> getTracesToBuild(@Nullable ITmfTrace trace) {
        if (trace == null) {
            return NonNullUtils.checkNotNull(Collections.EMPTY_SET);
        }
        return NonNullUtils.checkNotNull(Collections.singleton(trace));
    }

}
