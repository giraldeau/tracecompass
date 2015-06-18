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

import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;

/**
 * Represents the valid CPU states used by Container Analysis
 *
 * @author Francis Jolivet
 * @author Sebastien Lorrain
 */
public interface ContainerCpuState {
    /* CPU Status */
    /**
     * CPU is idle value
     */
    int CPU_STATUS_IDLE = 0;
    /**
     * CPU is running
     */
    int CPU_STATUS_RUNNING = 1;
    /**
     * CPU is shared
     */
    int CPU_STATUS_SHARED = 2;

    /**
     * CPU is idle state value
     */
    ITmfStateValue CPU_STATUS_IDLE_VALUE = TmfStateValue.newValueInt(CPU_STATUS_IDLE);
    /**
     * CPU is running state value
     */
    ITmfStateValue CPU_STATUS_RUNNING_VALUE = TmfStateValue.newValueInt(CPU_STATUS_RUNNING);
    /**
     * CPU is shared state value
     */
    ITmfStateValue CPU_STATUS_SHARED_VALUE = TmfStateValue.newValueInt(CPU_STATUS_SHARED);

}
