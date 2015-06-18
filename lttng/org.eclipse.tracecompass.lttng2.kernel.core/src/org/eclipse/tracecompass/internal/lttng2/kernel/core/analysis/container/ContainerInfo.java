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

/**
 * Small class to hold information.
 *
 * @author Francis Jolivet
 * @author Sébastien Lorrain
 */
public class ContainerInfo {

    private final int fContainerInode;
    private final int fContainerParentTID;
    private final String fContainerHostname;

    /**
     * @param containerInode
     *            The INode of the container (same is the one in
     *            /proc/$PID/ns/pid)
     * @param containerParentTID
     *            The process that spawned the container. It is the parent of
     *            the first process in the container
     * @param containerHostname
     *            The hostname of the container.
     */
    public ContainerInfo(int containerInode, int containerParentTID, String containerHostname) {
        this.fContainerInode = containerInode;
        this.fContainerParentTID = containerParentTID;
        this.fContainerHostname = containerHostname;
    }

    /**
     * Get the container INode
     *
     * @return the container INode
     */
    public int getContainerInode() {
        return fContainerInode;
    }

    /**
     * Get the container parent Tid
     *
     * @return the container parent Tid
     */
    public int getContainerParentTID() {
        return fContainerParentTID;
    }

    /**
     * Get the container hostname
     *
     * @return the container hostname
     */
    public String getContainerHostname() {
        return fContainerHostname;
    }

}
