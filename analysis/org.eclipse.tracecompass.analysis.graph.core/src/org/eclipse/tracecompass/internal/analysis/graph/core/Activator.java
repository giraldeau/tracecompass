/*******************************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Geneviève Bastien - Initial implementation and API
 *******************************************************************************/

package org.eclipse.tracecompass.internal.analysis.graph.core;

import org.eclipse.tracecompass.common.core.TraceCompassActivator;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends TraceCompassActivator {

    /** The plug-in ID */
    public static final String PLUGIN_ID = "org.eclipse.tracecompass.analysis.graph.core"; //$NON-NLS-1$

    /**
     * The constructor
     */
    public Activator() {
        super(PLUGIN_ID);
    }

    /**
     * Returns the instance of this plug-in
     *
     * @return The plugin instance
     */
    public static TraceCompassActivator getInstance() {
        return TraceCompassActivator.getInstance(PLUGIN_ID);
    }

    @Override
    protected void startActions() {

    }

    @Override
    protected void stopActions() {

    }

}