package org.eclipse.tracecompass.internal.lttng2.kernel.ui.symbols;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.symbols.DefaultSymbolProvider;

/**
 * @author francis
 *
 */

public class KallsymsSymbolProvider extends DefaultSymbolProvider {

    public KallsymsSymbolProvider(@NonNull ITmfTrace trace) {
        super(trace);
    }



}
