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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;

/**
 *
 * This class/builder was designed because we cannot guarantee that we will
 * receive the statedump events in order (parent tasks before children tasks).
 *
 * This builder caches statedump_process_status events on the fly and stores the
 * information in a Task class. If the container holding the task has been added
 * to the statesystem, we can process the task. Otherwise, this builder holds
 * tasks that dosent have their parent container yet.
 *
 * When a new task is processed or a new container is created, every other tasks
 * are re-processed.
 *
 * @author Francis Jolivet
 * @author Sébastien Lorrain
 */
public class ContainerBuilder {

    /** Map of [tid, Task] */
    private final Map<Integer, TaskContainerEvent> fTasks;
    /** List of orphan tasks */
    private List<TaskContainerEvent> unresolvedTasks;

    /**
     * Constructor
     */
    public ContainerBuilder() {
        fTasks = new HashMap<>();
        unresolvedTasks = new LinkedList<>();
    }

    /**
     * Takes the statesystembuilder and a new task to be processed
     *
     * @param ssb
     *            The statesystembuilder
     * @param t
     *            The task to add and process.setTaskCPUStatus If it cannot be processed right
     *            away, it will be cached until it's parent container is found.
     */
    public void insertTaskContainerEvent(ITmfStateSystemBuilder ssb, TaskContainerEvent t) {
        /*
         *  Store the new entry in the hashmapgetContainerInfo
         *  Since we encounter the deepest level first, we can encounter multiple
         *  time the statedump for all levels of the task in the namespaces
         *  Thats why we check first if we alerady added the task
         */
        if (!fTasks.containsKey(t.getTid())) {
            fTasks.put(t.getTid(), t);

            // We wait to have the root container info before processing tasks.
            // This info is added in the stateprovider
            int rootContainerQuark = ContainerHelper.getRootContainerQuark(ssb);
            ContainerInfo rootContainerInfo = ContainerHelper.getContainerInfo(ssb, rootContainerQuark);
            if (rootContainerInfo != null) {

                // If the task is in the root container, only change CPU states
                if (t.getNsInode() == rootContainerInfo.getContainerInode()) {
                    setTaskCPUStatus(ssb, t);
                } else if (isTaskContainerAdded(ssb, t) || isTaskInNewContainer(ssb, t)) {
                    /*
                     * If the task is not in the root container, and we are able
                     * to add it and resolve unprocessed tasks
                     */
                    addTaskAndResolveOrphans(ssb, t);
                } else {
                    // Add task to orphans
                    unresolvedTasks.add(t);
                }
            }
        }
    }

    /*
     * This check the case where a task is in a new container.
     * Return true only if we created the parent container and
     * the child task is in a new container.
     */
    private boolean isTaskInNewContainer(ITmfStateSystemBuilder ssb, TaskContainerEvent t) {
        TaskContainerEvent parent = fTasks.get(t.getPpid());

        return
                parent != null
                && isTaskContainerAdded(ssb, parent)
                && ( parent.getNsInode() != t.getNsInode() );
    }

    /**
     * This method provides the root container information. As long as this info
     * is not set, we cannot manipulate containers (add containers, add tasks to
     * containers, etc). All task added to the builder will be cached until the
     * root container information is added.
     *
     * @param ssb
     *            ITmfStateSystemBuilder
     * @param timestamp
     *            TimeStamp
     * @param cinfo
     *            Container info
     */
    public void addRootContainerInfo(ITmfStateSystemBuilder ssb, long timestamp, ContainerInfo cinfo) {
            ContainerHelper.setRootContainerInfo(ssb, timestamp, cinfo);
    }

    /**
     *  This will process every taskEvents that were cached that can now be added
     *  to the container tree.
     */
    private void addTaskAndResolveOrphans(ITmfStateSystemBuilder ssb, TaskContainerEvent t) {
        // Parent container was added, we can add the task
        processTaskEvent(ssb, t);

        setTaskCPUStatus(ssb, t);

        // We added a task, try to resolve all orphans
        boolean retryUnresolvedTasks = false;
        do {
            retryUnresolvedTasks = false;
            /*
             * Premature Optimisation is the root of all evil. This would be
             * O(n^2) if statedump /task would not arrive in order. BUT this
             * never occurs in practice.
             */
            List<TaskContainerEvent> stillUnresolvedTasks = new LinkedList<>();
            for (TaskContainerEvent orphan : unresolvedTasks)
            {
                if (isTaskContainerAdded(ssb, orphan) || isTaskInNewContainer(ssb, orphan)) {
                    processTaskEvent(ssb, orphan);

                    setTaskCPUStatus(ssb, t);

                    retryUnresolvedTasks = true; // we added a task, try to
                                                 // resolve orphan again
                } else {
                    stillUnresolvedTasks.add(orphan);
                }
            }
            unresolvedTasks = stillUnresolvedTasks;
        } while (retryUnresolvedTasks);
    }

    /**
     * This will set the proper state of the container CPUs given the
     * taskCpuEvent. If the taskCpuEvent is a running task, it will also set the
     * running TID of the CPU.
     *
     * @param ssb
     *            ITmfStateSystemBuilder
     * @param taskCpuEvent
     *            The task CPU event containing the timestamp, TID and CPU id
     *            and state./*
     */
    public static void setTaskCPUStatus(ITmfStateSystemBuilder ssb, TaskCPUEvent taskCpuEvent) {
        int containerQuark = ContainerHelper.getContainerFromTaskTid(ssb, taskCpuEvent.getTid());

        if(containerQuark == ContainerHelper.CONTAINER_QUARK_NOT_FOUND) {
            containerQuark = ContainerHelper.getRootContainerQuark(ssb);
        }

        /*
         *  If the task is set to a "running" state, update the CPU quark to the
         *  current task's TID
         */
        if (taskCpuEvent.getCpuState().unboxInt() != ContainerCpuState.CPU_STATUS_IDLE) {
            ContainerHelper.setCpuCurrentlyRunningTask(ssb, containerQuark, taskCpuEvent.getTs(), taskCpuEvent.getCpuId(), taskCpuEvent.getTid());
        }
        // Change the state of the CPU to reflect the task's state
        ContainerHelper.updateContainerCPUState(ssb, containerQuark, taskCpuEvent.getTs(), taskCpuEvent.getCpuId(), taskCpuEvent.getCpuState());
    }

    /*
     * This function will try to find the parent container of the task. If
     * found, return true. Return false otherwise. This method is used to
     * determine if we can process the task or not. If we can, that means that
     * the container of the PARENT task was added (the parent container can be
     * root container or not).
     */
    private static boolean isTaskContainerAdded(ITmfStateSystemBuilder ssb, TaskContainerEvent task) {
        /*
         * The only time that a parent container is not yet instanciated is when
         * a task got a parent container that is NOT the root container and that
         * this parent container is not created yet.
         */

        /*
         * If the root container is not set,
         * there is no way the parent container is added
         */
        int rootContainerQuark = ContainerHelper.getRootContainerQuark(ssb);
        ContainerInfo rootContainerInfo = ContainerHelper.getContainerInfo(ssb, rootContainerQuark);
        if (rootContainerInfo == null) {
            return false;
        }

        /*
         * Is the container containing the tasks is already created ?
         * That also check if the task belong to the root container.
         */
        int containerQuark = ContainerHelper.findContainerQuarkWithInode(ssb, task.getNsInode());
        if(containerQuark != ContainerHelper.CONTAINER_QUARK_NOT_FOUND) {
            return true;
        }

        return false;

    }

    private void processTaskEvent(ITmfStateSystemBuilder ssb, TaskContainerEvent t) {
        // This function takes for granted that task NS != root
        int rootContainerQuark = ContainerHelper.getRootContainerQuark(ssb);
        ContainerInfo rootContainerInfo = ContainerHelper.getContainerInfo(ssb, rootContainerQuark);
        if (rootContainerInfo == null) {
            /*
             * If you are here, there is something wrong.
             * You try to process a trask but you didnt set the root container.
             * Root container info should have been added BEFORE calling this function.
             * This would cause unexcepected behaviour on this function.
             */
            throw new IllegalStateException("Error, root container seems added but container info is unavailable!"); //$NON-NLS-1$
        }

        TaskContainerEvent parent = fTasks.get(t.getPpid());

        /*
         *  If the task is in the root container, do not process it.
         *  We dont keep track of the task associated with the root container.
         */
        if (parent != null && t.getNsInode() != rootContainerInfo.getContainerInode()) {
            // We found the task container, add it to it.
            int taskContainerQuark = ContainerHelper.findContainerQuarkWithInode(ssb, t.getNsInode());
            if (taskContainerQuark != ContainerHelper.CONTAINER_QUARK_NOT_FOUND) {
                ContainerHelper.addTaskToContainer(ssb, taskContainerQuark, t.getTs(), t.getVtids());
            } else {
                //The container does not exist. Create it
                if (t.getNsInode() != parent.getNsInode()) {
                    /*
                     *  This function takes for granted that the parent container of
                     *  the task exists
                     */
                    if (!isTaskContainerAdded(ssb, t) && !isTaskInNewContainer(ssb, t)) {
                        throw new IllegalStateException("The container of the task does not exist!"); //$NON-NLS-1$
                    }

                    // Sometimes, we get wierd events and the inode is 0...do not add that task!
                    if (t.getNsInode() != 0) {
                        ContainerInfo cInfo = new ContainerInfo(t.getNsInode(), parent.getTid(), "TODO_ADD_HOSTNAME"); //$NON-NLS-1$
                        ContainerHelper.addContainerAndTask(ssb, t.getTs(), parent.getNsInode(), t.getVtids(), cInfo);
                    }
                }
            }
        }
    }

}
