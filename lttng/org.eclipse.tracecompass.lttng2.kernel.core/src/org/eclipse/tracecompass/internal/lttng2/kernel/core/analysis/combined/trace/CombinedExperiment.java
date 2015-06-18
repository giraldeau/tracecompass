package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.combined.trace;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.Collections;
import java.util.Set;

import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;

/**
 * Experiment class containing traces from physical machine, the virtual
 * guests and containers running on them.
 *
 * @author Louis Pepin
 */
public class CombinedExperiment extends TmfExperiment {

    /**
     * Default constructor. Needed by the extension point.
     */
    public CombinedExperiment() {
        this("", checkNotNull(Collections.EMPTY_SET)); //$NON-NLS-1$
    }

    /**
     * Constructor with traces and id
     *
     * @param id
     *            The ID of this experiment
     * @param traces
     *            The set of traces that are part of this experiment
     */
    public CombinedExperiment(String id, Set<ITmfTrace> traces) {
        super(CtfTmfEvent.class, id, traces.toArray(new ITmfTrace[traces.size()]), TmfExperiment.DEFAULT_INDEX_PAGE_SIZE, null);
    }
}
