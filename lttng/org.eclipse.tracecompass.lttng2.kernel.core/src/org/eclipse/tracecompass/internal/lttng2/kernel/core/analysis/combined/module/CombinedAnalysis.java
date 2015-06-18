package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.combined.module;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAbstractAnalysisModule;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.statesystem.*;

import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.container.ContainerAnalysisModule;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.vm.module.VirtualMachineCpuAnalysis;

/**
 * Module for the combined analysis of Virtual Machines
 * and Linux Containers (based on TmfStatisticsModule)
 *
 * @author Louis Pepin
 * @since 2.0
 */
public class CombinedAnalysis extends TmfAbstractAnalysisModule
        implements ITmfAnalysisModuleWithStateSystems {

    /** ID of the Analysis */
    public static final String ID = "org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.combined.module.CombinedAnalysis"; //$NON-NLS-1$

    private final TmfStateSystemAnalysisModule vmModule = new VirtualMachineCpuAnalysis();
    private final TmfStateSystemAnalysisModule containerModule = new ContainerAnalysisModule();

    private final CountDownLatch fInitialized = new CountDownLatch(1);

    /**
     * Constructor
     */
    public CombinedAnalysis() {
        super();
    }

    @Override
    public void dispose() {
        super.dispose();
        vmModule.dispose();
        containerModule.dispose();
    }

    @Override
    public boolean setTrace(ITmfTrace trace) throws TmfAnalysisException {
        if (!super.setTrace(trace)) {
            return false;
        }
        if (!vmModule.setTrace(trace)) {
            return false;
        }
        if (!containerModule.setTrace(trace)) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean executeAnalysis(IProgressMonitor monitor) throws TmfAnalysisException {
        ITmfTrace trace = getTrace();
        if (trace == null) {
            /* This analysis was cancelled in the meantime */
            fInitialized.countDown();
            return false;
        }

        IStatus status1 = vmModule.schedule();
        IStatus status2 = containerModule.schedule();
        if (!(status1.isOK() && status2.isOK())) {
            canceling();
            fInitialized.countDown();
            return false;
        }

        /* Wait until the two modules are initialized */
        waitForInitialization();

        ITmfStateSystem vmModuleSS = vmModule.getStateSystem();
        ITmfStateSystem containerModuleSS = containerModule.getStateSystem();

        if (vmModuleSS == null || containerModuleSS == null) {
            /* This analysis was cancelled in the meantime */
            fInitialized.countDown();
            return false;
        }

        /* Consider this module initialized */
        fInitialized.countDown();

        /*
         * The rest of this "execute" will encompass the "execute" of the two
         * sub-analyzes.
         */
        if (!(vmModule.waitForCompletion(monitor) &&
                containerModule.waitForCompletion(monitor))) {
            return false;
        }
        return true;
    }

    @Override
    protected void canceling() {
        vmModule.cancel();
        containerModule.cancel();
    }

    @Override
    public ITmfStateSystem getStateSystem(String id) {
        switch (id) {
        case VirtualMachineCpuAnalysis.ID:
            return vmModule.getStateSystem();
        case ContainerAnalysisModule.ID:
            return containerModule.getStateSystem();
        default:
            return null;
        }
    }

    @Override
    public Iterable<ITmfStateSystem> getStateSystems() {
        List<ITmfStateSystem> list = new LinkedList<>();
        list.add(vmModule.getStateSystem());
        list.add(containerModule.getStateSystem());
        return list;
    }

    @Override
    public void waitForInitialization() {
        vmModule.waitForInitialization();
        containerModule.waitForInitialization();
    }
}
