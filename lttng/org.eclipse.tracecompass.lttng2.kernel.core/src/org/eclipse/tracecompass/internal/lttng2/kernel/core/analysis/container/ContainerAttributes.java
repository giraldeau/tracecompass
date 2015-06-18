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
 * This file defines all the attribute names used in the handler. Both the
 * construction and query steps should use them.
 *
 * These should not be externalized! The values here are used as-is in the
 * history file on disk, so they should be kept the same to keep the file format
 * compatible. If a view shows attribute names directly, the localization should
 * be done on the viewer side.
 *
 * @author Francis Jolivet
 * @author Sébastien Lorrain
 */
@SuppressWarnings({ "nls" })
public interface ContainerAttributes {

    /**
     * String representing the path to the root container
     */
    String ROOT = "Container Root";

    /**
     * path /$ROOT/(...)/$CONTAINER_ID Base container ID, concatenated with a
     * unique number in container creation : i.e. Container 26
     */
    String CONTAINER_ID_PREFIX = "Container ";

    /**
     * path /$ROOT/(...)/$CONTAINER_ID/$CONTAINER_CPU Contains the CPUs of the
     * container
     */
    String CONTAINER_CPU = "Container CPU";

    /**
     * path /$ROOT/(...)/$CONTAINER_ID/$CONTAINER_CPU/$CPUID/$RUNNING_TID CPUs
     * are represented by numbers, each containing the following sub-attr :
     */
    String RUNNING_TID = "Running_vtid";

    /**
     * path /$ROOT/(...)/$CONTAINER_ID/$CONTAINER_INFO Contains various static
     * information about the container
     */
    String CONTAINER_INFO = "Container info";

    /**
     * path /$ROOT/(...)/$CONTAINER_ID/$CONTAINER_INFO/$HOSTNAME The string
     * contained in /etc/hostname
     */
    String HOSTNAME = "Hostname";

    /**
     * path /$ROOT/(...)/$CONTAINER_ID/$CONTAINER_INFO/$CONTAINER_INODE the
     * inode of the container as in /proc/$PID/ns/inode
     */
    String CONTAINER_INODE = "ns_INode";

    /**
     * path /$ROOT/(...)/$CONTAINER_ID/$CONTAINER_INFO/$PPID The "real tid" of
     * the parent task that spawned the container
     */
    String PPID = "PPID";

    /**
     * path /$ROOT/(...)/$CONTAINER_ID/$CONTAINER_TASKS Contains some integer
     * representing the tasks/process/thread of the container with their
     * VTID/VPID Note that the root container DOES NOT HAVE this section. We do
     * not keep track of tasks in the root container
     *
     */
    String CONTAINER_TASKS = "Container tasks";

    /**
     * path /$ROOT/(...)/$CONTAINER_ID/$CONTAINER_TASKS/$VTID/$REAL_TID
     * Represent the "real" tid of a task in the container
     */
    String REAL_TID = "0";

    /**
     * path /$ROOT/(...)/$CONTAINER_ID/$CONTAINERS_SECTION Contains other
     * containers ($CONTAINER_ID). This goes recursively.
     */
    String CONTAINERS_SECTION = "Nested Containers";
}
