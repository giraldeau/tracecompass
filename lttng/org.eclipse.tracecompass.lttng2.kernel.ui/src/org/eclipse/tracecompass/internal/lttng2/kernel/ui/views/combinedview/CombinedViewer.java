package org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.combinedview;


import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.SWT;


//import java.util.Collection;
//import java.util.Set;

/**
 * Viewer for the combined view
 *
 * @author Louis Pepin
 */
public class CombinedViewer {

    Label label;

    /**
     * Default constructor
     *
     * @param parent
     *          The parent containing this viewer
     */
    public CombinedViewer(final Composite parent) {
        label = new Label(parent, SWT.WRAP);
        label.setText("Hello World"); //$NON-NLS-1$
    }
}
