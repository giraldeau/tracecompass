package org.eclipse.tracecompass.internal.lttng2.kernel.ui.views.combinedview;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

/**
 * Combine the Virtual Machine View and the Container view into a single class
 *
 * @author Louis Pepin
 */
public class CombinedView extends ViewPart {

    CombinedViewer fViewer;

    /** View ID. */
    //private static final String ID = "org.eclipse.tracecompass.internal.lttng2.kernel.ui.view.combinedview"; //$NON-NLS-1$

    /**
     * Default constructor
     */
    public CombinedView() {
        super();
    }

    @Override
    public void createPartControl(Composite parent) {
        fViewer = new CombinedViewer(parent);
    }

    @Override
    public void setFocus() {
        // TODO Auto-generated method stub

    }

}
