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

package org.eclipse.tracecompass.internal.analysis.graph.ui.criticalpath.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tracecompass.analysis.graph.core.base.IGraphWorker;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge.EdgeType;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;
import org.eclipse.tracecompass.analysis.graph.core.criticalpath.CriticalPathModule;
import org.eclipse.tracecompass.analysis.graph.core.criticalpath.ICriticalPathAlgorithm;
import org.eclipse.tracecompass.analysis.graph.ui.criticalpath.CriticalPathAlgorithmProvider;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.internal.analysis.graph.core.base.TmfGraphStatistics;
import org.eclipse.tracecompass.internal.analysis.graph.core.base.TmfGraphVisitor;
import org.eclipse.tracecompass.internal.analysis.graph.core.criticalpath.AlgorithmManager;
import org.eclipse.tracecompass.internal.analysis.graph.ui.Activator;
import org.eclipse.tracecompass.internal.analysis.graph.ui.criticalpath.view.CriticalPathPresentationProvider.State;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfStartAnalysisSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.views.timegraph.AbstractTimeGraphView;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphContentProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ILinkEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeLinkEvent;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * The Critical Path view
 *
 * @author Geneviève Bastien
 * @author Francis Giraldeau
 */
public class CriticalPathView extends AbstractTimeGraphView {

    // ------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------

    /** View ID */
    public static final String ID = "org.eclipse.linuxtools.tmf.analysis.graph.ui.criticalpath.view.criticalpathview"; //$NON-NLS-1$

    private static final double NANOINV = 0.000000001;

    private static final String SETTINGS_ALGORITHM_PROPERTY = "AlgorithmID"; //$NON-NLS-1$

    private static final String COLUMN_PROCESS = Messages.getMessage(Messages.CriticalFlowView_columnProcess);
    private static final String COLUMN_ELAPSED = Messages.getMessage(Messages.CriticalFlowView_columnElapsed);
    private static final String COLUMN_PERCENT = Messages.getMessage(Messages.CriticalFlowView_columnPercent);

    private static final String[] COLUMN_NAMES = new String[] {
            COLUMN_PROCESS,
            COLUMN_ELAPSED,
            COLUMN_PERCENT
    };

    private static final String[] FILTER_COLUMN_NAMES = new String[] {
            COLUMN_PROCESS
    };

    private final Table<ITmfTrace, Object, List<ILinkEvent>> fLinks = NonNullUtils.checkNotNull(HashBasedTable.<ITmfTrace, Object, List<ILinkEvent>> create());
    /** The trace to entry list hash map */
    private final Table<ITmfTrace, Object, TmfGraphStatistics> fObjectStatistics = NonNullUtils.checkNotNull(HashBasedTable.<ITmfTrace, Object, TmfGraphStatistics> create());

    private final CriticalPathContentProvider fContentProvider = new CriticalPathContentProvider();

    private TmfGraphStatistics fStats = new TmfGraphStatistics();
    private @Nullable Action fAlgorithmChoice;
    private @Nullable String fAlgorithm;

    private class CriticalPathContentProvider implements ITimeGraphContentProvider {

        private Map<Object, Map<Object, CriticalPathEntry>> workerMaps = new HashMap<>();
        private Map<Object, List<TimeGraphEntry>> workerEntries = new HashMap<>();
        private Map<Object, List<ILinkEvent>> linkMap = new HashMap<>();
        private @Nullable Object fCurrentObject;

        @Override
        public @Nullable ITimeGraphEntry[] getElements(@Nullable Object inputElement) {
            if (inputElement != null) {
                try {
                    List<?> list = (List<?>) inputElement;
                    if (list.size() == 0) {
                        return new ITimeGraphEntry[0];
                    }
                    Object obj = list.get(0);
                    if (!(obj instanceof CriticalPathBaseEntry)) {
                        return new ITimeGraphEntry[0];
                    }
                    IGraphWorker worker = ((CriticalPathBaseEntry) obj).getWorker();
                    fCurrentObject = worker;
                    List<TimeGraphEntry> entries = workerEntries.get(worker);
                    if (entries == null) {
                        buildEntryList(worker);
                        entries = workerEntries.get(worker);
                    }
                    if (entries == null) {
                        return new ITimeGraphEntry[0];
                    }
                    return entries.toArray(new ITimeGraphEntry[entries.size()]);
                } catch (ClassCastException e) {
                }
            }
            return new ITimeGraphEntry[0];
        }

        private void buildEntryList(IGraphWorker worker) {

            final ITmfTrace trace = getTrace();
            if (trace == null) {
                return;
            }
            CriticalPathModule module = null;
            for (CriticalPathModule mod : TmfTraceUtils.getAnalysisModulesOfClass(trace, CriticalPathModule.class)) {
                module = mod;
                break;
            }
            if (module == null) {
                return;
            }
            module.schedule();
            if (!module.waitForCompletion()) {
                return;
            }
            final TmfGraph graph = module.getCriticalPath();

            if (graph == null) {
                return;
            }
            setStartTime(Long.MAX_VALUE);
            setEndTime(Long.MIN_VALUE);

            final HashMap<Object, CriticalPathEntry> rootList = new HashMap<>();
            fLinks.remove(getTrace(), worker);

            TmfVertex vertex = graph.getHead();

            /* Calculate statistics */
            fStats = new TmfGraphStatistics();
            fStats.getGraphStatistics(graph, worker);
            fObjectStatistics.put(getTrace(), worker, fStats);

            // Hosts entries are parent of each worker entries
            final Map<String, CriticalPathEntry> hostEntries = new HashMap<>();

            /* create all interval entries and horizontal links */

            final CriticalPathEntry defaultParent = new CriticalPathEntry("default", trace, getStartTime(), getEndTime(), null); //$NON-NLS-1$
            graph.scanLineTraverse(vertex, new TmfGraphVisitor() {
                @Override
                public void visitHead(TmfVertex node) {
                    /* TODO possible null pointer ? */
                    IGraphWorker owner = graph.getParentOf(node);
                    if (owner == null) {
                        return;
                    }
                    if (rootList.containsKey(owner)) {
                        return;
                    }
                    TmfVertex first = graph.getHead(owner);
                    TmfVertex last = graph.getTail(owner);
                    if (first == null || last == null) {
                        return;
                    }
                    setStartTime(Math.min(getStartTime(), first.getTs()));
                    setEndTime(Math.max(getEndTime(), last.getTs()));
                    // create host entry
                    CriticalPathEntry parent = defaultParent;
                    String host = owner.getHostId();
                    if (!hostEntries.containsKey(host)) {
                        hostEntries.put(host, new CriticalPathEntry(host, trace, getStartTime(), getEndTime(), owner));
                    }
                    parent = hostEntries.get(host);
                    CriticalPathEntry entry = new CriticalPathEntry(NonNullUtils.nullToEmptyString(owner), trace, getStartTime(), getEndTime(), owner);
                    parent.addChild(entry);

                    rootList.put(owner, entry);
                }

                @Override
                public void visit(TmfVertex node) {
                    setStartTime(Math.min(getStartTime(), node.getTs()));
                    setEndTime(Math.max(getEndTime(), node.getTs()));
                }

                @Override
                public void visit(TmfEdge link, boolean horizontal) {
                    if (horizontal) {
                        Object parent = graph.getParentOf(link.getVertexFrom());
                        CriticalPathEntry entry = rootList.get(parent);
                        TimeEvent ev = new TimeEvent(entry, link.getVertexFrom().getTs(), link.getDuration(),
                                getMatchingState(link.getType()).ordinal());
                        entry.addEvent(ev);
                    }
                }
            });

            workerMaps.put(worker, rootList);

            List<TimeGraphEntry> list = new ArrayList<>();
            list.addAll(hostEntries.values());
            if (defaultParent.hasChildren()) {
                list.add(defaultParent);
            }

            for (TimeGraphEntry entry : list) {
                if (entry == null) {
                    throw new NullPointerException();
                }
                buildStatusEvents(trace, (CriticalPathEntry) entry);
            }
            workerEntries.put(worker, list);
        }

        public @Nullable List<ILinkEvent> getLinkList(long startTime, long endTime) {
            Object current = fCurrentObject;
            if (current == null) {
                return null;
            }
            /*
             * Critical path typically has relatively few links, so we calculate
             * and save them all, but just return those in range
             */
            List<ILinkEvent> links = linkMap.get(current);
            if (links != null) {
                return links;
            }
            final ITmfTrace trace = getTrace();
            if (trace == null) {
                return null;
            }
            CriticalPathModule module = null;
            for (CriticalPathModule mod : TmfTraceUtils.getAnalysisModulesOfClass(trace, CriticalPathModule.class)) {
                module = mod;
                break;
            }
            if (module == null) {
                return null;
            }
            final TmfGraph graph = module.getCriticalPath();

            if (graph == null) {
                return null;
            }

            final Map<Object, CriticalPathEntry> entryMap = workerMaps.get(current);
            if (entryMap == null) {
                return null;
            }

            TmfVertex vertex = graph.getHead();

            final List<ILinkEvent> graphLinks = new ArrayList<>();

            /* find vertical links */
            graph.scanLineTraverse(vertex, new TmfGraphVisitor() {
                @Override
                public void visitHead(TmfVertex node) {

                }

                @Override
                public void visit(TmfVertex node) {

                }

                @Override
                public void visit(TmfEdge link, boolean horizontal) {
                    if (!horizontal) {
                        Object parentFrom = graph.getParentOf(link.getVertexFrom());
                        Object parentTo = graph.getParentOf(link.getVertexTo());
                        CriticalPathEntry entryFrom = entryMap.get(parentFrom);
                        CriticalPathEntry entryTo = entryMap.get(parentTo);
                        TimeLinkEvent lk = new TimeLinkEvent(entryFrom, entryTo, link.getVertexFrom().getTs(),
                                link.getVertexTo().getTs() - link.getVertexFrom().getTs(), getMatchingState(link.getType()).ordinal());
                        graphLinks.add(lk);
                    }
                }
            });
            fLinks.put(getTrace(), fCurrentObject, graphLinks);
            links = graphLinks;

            List<ILinkEvent> linksInRange = new ArrayList<>();
            for (ILinkEvent link : links) {
                if (((link.getTime() >= startTime) && (link.getTime() <= endTime)) ||
                        ((link.getTime() + link.getDuration() >= startTime) && (link.getTime() + link.getDuration() <= endTime))) {
                    linksInRange.add(link);
                }
            }
            return linksInRange;
        }

        @Override
        public void dispose() {

        }

        @Override
        public void inputChanged(@Nullable Viewer viewer, @Nullable Object oldInput, @Nullable Object newInput) {
        }

        @Override
        public @Nullable ITimeGraphEntry[] getChildren(@Nullable Object parentElement) {
            if (parentElement instanceof CriticalPathEntry) {
                List<? extends ITimeGraphEntry> children = ((CriticalPathEntry) parentElement).getChildren();
                return children.toArray(new TimeGraphEntry[children.size()]);
            }
            return null;
        }

        @Override
        public @Nullable ITimeGraphEntry getParent(@Nullable Object element) {
            if (element instanceof CriticalPathEntry) {
                return ((CriticalPathEntry) element).getParent();
            }
            return null;
        }

        @Override
        public boolean hasChildren(@Nullable Object element) {
            if (element instanceof CriticalPathEntry) {
                return ((CriticalPathEntry) element).hasChildren();
            }
            return false;
        }

    }

    private class CriticalPathTreeLabelProvider extends TreeLabelProvider {

        @Override
        public String getColumnText(@Nullable Object element, int columnIndex) {
            if (element == null) {
                return ""; //$NON-NLS-1$
            }
            CriticalPathEntry entry = (CriticalPathEntry) element;
            if (columnIndex == 0) {
                return NonNullUtils.nullToEmptyString(entry.getName());
            }
            else if (columnIndex == 1) {
                try {
                    return NonNullUtils.nullToEmptyString(String.format("%.9f", fStats.getSum(entry.getWorker()) * NANOINV)); //$NON-NLS-1$
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else if (columnIndex == 2) {
                return NonNullUtils.nullToEmptyString(String.format("%.2f", fStats.getPercent(entry.getWorker()) * 100)); //$NON-NLS-1$
            }
            return ""; //$NON-NLS-1$
        }

    }

    private class CriticalPathEntryComparator implements Comparator<ITimeGraphEntry> {

        @Override
        public int compare(@Nullable ITimeGraphEntry o1, @Nullable ITimeGraphEntry o2) {

            int result = 0;

            if ((o1 instanceof CriticalPathEntry) && (o2 instanceof CriticalPathEntry)) {
                CriticalPathEntry entry1 = (CriticalPathEntry) o1;
                CriticalPathEntry entry2 = (CriticalPathEntry) o2;
                result = -1 * fStats.getSum(entry1.getWorker()).compareTo(fStats.getSum(entry2.getWorker()));
            }
            return result;
        }
    }

    /**
     * Constructor
     */
    public CriticalPathView() {
        super(ID, new CriticalPathPresentationProvider());
        setTreeColumns(COLUMN_NAMES);
        setFilterColumns(FILTER_COLUMN_NAMES);
        setTreeLabelProvider(new CriticalPathTreeLabelProvider());
        setTimeGraphContentProvider(fContentProvider);
        setEntryComparator(new CriticalPathEntryComparator());
        loadPersistentData();
    }

    // ------------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------------

    private static State getMatchingState(EdgeType type) {
        State state = State.UNKNOWN;
        switch (type) {
        case RUNNING:
            state = State.RUNNING;
            break;
        case PREEMPTED:
            state = State.PREEMPTED;
            break;
        case TIMER:
            state = State.TIMER;
            break;
        case BLOCK_DEVICE:
            state = State.BLOCK_DEVICE;
            break;
        case INTERRUPTED:
            state = State.INTERRUPTED;
            break;
        case NETWORK:
            state = State.NETWORK;
            break;
        case USER_INPUT:
            state = State.USER_INPUT;
            break;
        case EPS:
        case UNKNOWN:
        case DEFAULT:
        case BLOCKED:
            break;
        default:
            break;
        }
        return state;
    }

    private void buildStatusEvents(ITmfTrace trace, CriticalPathEntry entry) {

        long start = trace.getStartTime().getValue();
        long end = trace.getEndTime().getValue() + 1;
        long resolution = Math.max(1, (end - start) / getDisplayWidth());
        List<ITimeEvent> eventList = getEventList(entry, entry.getStartTime(), entry.getEndTime(), resolution, new NullProgressMonitor());

        entry.setZoomedEventList(eventList);

        redraw();

        for (ITimeGraphEntry child : entry.getChildren()) {
            if (child == null) {
                throw new NullPointerException();
            }
            buildStatusEvents(trace, (CriticalPathEntry) child);
        }
    }

    @Override
    protected void buildEventList(@NonNull ITmfTrace trace, @NonNull ITmfTrace parentTrace, @NonNull IProgressMonitor monitor) {
        /** This class uses a content provider instead */
    }

    @Override
    protected @Nullable List<ITimeEvent> getEventList(TimeGraphEntry entry,
            long startTime, long endTime, long resolution,
            IProgressMonitor monitor) {

        final long realStart = Math.max(startTime, entry.getStartTime());
        final long realEnd = Math.min(endTime, entry.getEndTime());
        if (realEnd <= realStart) {
            return null;
        }
        List<ITimeEvent> eventList = null;
        try {
            entry.setZoomedEventList(null);
            Iterator<ITimeEvent> iterator = entry.getTimeEventsIterator();
            eventList = new ArrayList<>();

            while (iterator.hasNext()) {

                ITimeEvent event = iterator.next();
                /* is event visible */
                if (((event.getTime() >= realStart) && (event.getTime() <= realEnd)) ||
                        ((event.getTime() + event.getDuration() > realStart) &&
                        (event.getTime() + event.getDuration() < realEnd))) {
                    eventList.add(event);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return eventList;
    }

    @Override
    protected @Nullable List<ILinkEvent> getLinkList(long startTime, long endTime, long resolution, IProgressMonitor monitor) {
        return fContentProvider.getLinkList(startTime, endTime);
    }

    /**
     * Signal handler for analysis started
     *
     * @param signal
     *            The signal
     */
    @TmfSignalHandler
    public void analysisStarted(TmfStartAnalysisSignal signal) {
        if (!(signal.getAnalysisModule() instanceof CriticalPathModule)) {
            return;
        }
        CriticalPathModule module = (CriticalPathModule) signal.getAnalysisModule();
        Object obj = module.getParameter(CriticalPathModule.PARAM_WORKER);
        if (obj == null) {
            return;
        }
        if (!(obj instanceof IGraphWorker)) {
            throw new IllegalStateException();
        }
        IGraphWorker worker = (IGraphWorker) obj;
        TmfGraphStatistics stats = fObjectStatistics.get(getTrace(), worker);
        if (stats == null) {
            stats = new TmfGraphStatistics();
            fObjectStatistics.put(getTrace(), worker, stats);
        }
        fStats = stats;

        TimeGraphEntry tge = new CriticalPathBaseEntry(worker);
        List<TimeGraphEntry> list = Collections.singletonList(tge);
        putEntryList(getTrace(), list);
        refresh();
    }

    // ------------------------------------------------------------------------
    // Part For Button Action
    // ------------------------------------------------------------------------

    private IDialogSettings getPersistentPropertyStore() {
        IDialogSettings settings = Activator.getDefault().getDialogSettings();
        IDialogSettings section = settings.getSection(getClass().getName());
        if (section == null) {
            section = settings.addNewSection(getClass().getName());
            if (section == null) {
                throw new IllegalStateException();
            }
        }
        return section;
    }

    private void savePersistentData() {
        IDialogSettings settings = getPersistentPropertyStore();
        settings.put(SETTINGS_ALGORITHM_PROPERTY, fAlgorithm);
    }

    private void loadPersistentData() {
        IDialogSettings settings = getPersistentPropertyStore();
        fAlgorithm = settings.get(SETTINGS_ALGORITHM_PROPERTY);
        if (null == fAlgorithm) {
            String algo = AlgorithmManager.getInstance().registeredTypes().keySet().iterator().next();
            if (algo != null) {
                setAlgorithm(algo);
            }
        }
    }

    private void setAlgorithm(String algorithmType) {
        String algorithm = fAlgorithm;
        if (algorithm == null || !algorithm.equals(algorithmType)) {
            fAlgorithm = algorithmType;
            Class<? extends ICriticalPathAlgorithm> type = AlgorithmManager.getInstance().registeredTypes().get(algorithmType);
            if (null != type) {
                CriticalPathAlgorithmProvider.getInstance().setAlgorithm(type);
                savePersistentData();
            } else {
                throw new RuntimeException("Class for algorithm not found: " + fAlgorithm); //$NON-NLS-1$
            }

        }
    }

    @Override
    protected void fillLocalToolBar(@Nullable IToolBarManager manager) {

        Action algorithmChoice = new Action() {
            @Override
            public void run() {
                Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                if (shell == null) {
                    throw new NullPointerException();
                }
                SettingsDialog dialog = new SettingsDialog(shell);
                dialog.create();
                String algorithm = fAlgorithm;
                if (algorithm != null) {
                    dialog.setAlgorithmType(algorithm);
                }
                if (dialog.open() == Window.OK) {
                    String name = dialog.getAlgorithmType();
                    if (name != null) {
                        setAlgorithm(name);
                    }
                }
                Action choice = fAlgorithmChoice;
                if (choice != null) {
                    choice.setChecked(false);
                }
            }
        };
        fAlgorithmChoice = algorithmChoice;
        algorithmChoice.setToolTipText(Messages.CriticalPathView_selectAlgorithm);
        algorithmChoice.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().
                getImageDescriptor(ISharedImages.IMG_OBJ_ELEMENT));
        algorithmChoice.setChecked(false);

        if (manager != null) {
            manager.add(algorithmChoice);
        }
        super.fillLocalToolBar(manager);
    }

}
