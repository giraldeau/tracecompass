package org.eclipse.tracecompass.internal.lttng2.kernel.ui.ftrace;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.ftrace.LttngFtraceCallStackProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.ui.views.callstack.AbstractCallStackAnalysis;

/**
 * @author francis
 *
 */
public class FtraceAnalysis extends AbstractCallStackAnalysis {

    @Override
    protected @NonNull ITmfStateProvider createStateProvider() {
        return new LttngFtraceCallStackProvider(checkNotNull(getTrace()));
    }

}
