/*******************************************************************************
 * Copyright (c) 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Matthew Khouzam - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.internal.analysis.os.linux.core.kernelanalysis;

import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

/**
 * Kernel event handler interface
 *
 * @since 1.0
 */
public interface IKernelEventHandler {

    /**
     * Handle a specific kernel event
     *
     * @param event
     *            the event
     * @param ss
     *            the state system to write to
     * @throws AttributeNotFoundException
     *             if the attribute is not yet create
     */
    void handleEvent(ITmfEvent event, ITmfStateSystemBuilder ss) throws AttributeNotFoundException;

}