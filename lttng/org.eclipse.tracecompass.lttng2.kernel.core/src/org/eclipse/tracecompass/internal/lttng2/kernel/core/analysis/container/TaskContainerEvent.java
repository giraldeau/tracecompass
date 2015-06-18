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

import java.util.LinkedList;
import java.util.List;

/**
 * Represent a task that changed the CPU state at a given time. Also contains
 * information about the PID namespace of that tasks
 *
 * @author Sebastien Lorrain
 * @author Francis Jolivet
 */
public class TaskContainerEvent extends TaskCPUEvent {

    private final List<Integer> fVtids;
    private final int fPpid;
    private final int fNsInode;

    /**
     * Constructor
     *
     * @param ts
     *            The timestamp
     * @param tid
     *            The task tid
     * @param vTIDs
     *            The task VTIDs. The root namespace is stored first at index 0.
     * @param pPID
     *            The task parent pid
     * @param status
     *            The CPU status occasionned by the task
     * @param cpuID
     *            The CPU numerical ID
     * @param nsInode
     *            The INode of the task PID namespace
     */
    public TaskContainerEvent(long ts, int tid, long[] vTIDs, int pPID, int status, int cpuID, int nsInode) {
        super(ts, tid, status, cpuID);
        fVtids = new LinkedList<>();
        for(long vtid : vTIDs) {
            fVtids.add((int) vtid);
        }

        fPpid = pPID;
        fNsInode = nsInode;
    }


    /**
     * @return
     *      The VTID of the TaskEvent
     */
    public List<Integer> getVtids() {
        return fVtids;
    }


    /**
     * @return
     *      The Parent PID of the TaskEvent
     */
    public int getPpid() {
        return fPpid;
    }


    /**
     * @return
     *      The INode of the TaskEvent
     */
    public int getNsInode() {
        return fNsInode;
    }
}