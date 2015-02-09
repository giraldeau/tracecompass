/*******************************************************************************
 * Copyright (c) 2012, 2013 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Alexandre Montplaisir - Initial API and implementation
 ******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model;

/**
 * This file defines all the known event and field names for LTTng 2.0 kernel
 * traces.
 *
 * Once again, these should not be externalized, since they need to match
 * exactly what the tracer outputs. If you want to localize them in a view, you
 * should do a mapping in the viewer itself.
 *
 * TODO: This has been revived from back in the days before the
 * analysis.os.linux event layout. The LTTng kernel execution graph analysis
 * should eventually move to the os.linux plugin and use whatever mechanism will
 * exist to express the events and this file should be removed for good. We keep
 * it for now, though.
 *
 * @author alexmont
 */
@SuppressWarnings({ "javadoc", "nls" })
public interface LttngStrings {

    /* Event names */
    static final String EXIT_SYSCALL = "exit_syscall";
    static final String HRTIMER_CANCEL = "hrtimer_cancel";
    static final String HRTIMER_EXPIRE_ENTRY = "hrtimer_expire_entry";
    static final String HRTIMER_EXPIRE_EXIT = "hrtimer_expire_exit";
    static final String HRTIMER_INIT = "hrtimer_init";
    static final String HRTIMER_START = "hrtimer_start";
    static final String IRQ_HANDLER_ENTRY = "irq_handler_entry";
    static final String IRQ_HANDLER_EXIT = "irq_handler_exit";
    static final String SOFTIRQ_ENTRY = "softirq_entry";
    static final String SOFTIRQ_EXIT = "softirq_exit";
    static final String SOFTIRQ_RAISE = "softirq_raise";
    static final String SCHED_SWITCH = "sched_switch";
    static final String SCHED_TTWU = "sched_ttwu";
    static final String SCHED_WAKEUP = "sched_wakeup";
    static final String SCHED_WAKEUP_NEW = "sched_wakeup_new";
    static final String SCHED_PROCESS_FORK = "sched_process_fork";
    static final String SCHED_PROCESS_EXEC = "sched_process_exec";
    static final String SCHED_PROCESS_EXIT = "sched_process_exit";
    static final String SCHED_PROCESS_FREE = "sched_process_free";
    static final String STATEDUMP_PROCESS_STATE = "lttng_statedump_process_state";
    static final String STATEDUMP_START = "lttng_statedump_start";
    static final String STATEDUMP_END = "lttng_statedump_end";
    static final String STATEDUMP_FD = "lttng_statedump_file_descriptor";
    static final String STATEDUMP_INET_SOCK = "lttng_statedump_inet_sock";
    static final String SYS_CLOSE = "sys_close";
    static final String SYS_DUP2 = "sys_dup2";
    static final String SYS_OPEN = "sys_open";

    /* System call names */
    static final String SYSCALL_PREFIX = "sys_";
    static final String COMPAT_SYSCALL_PREFIX = "compat_sys_";
    static final String SYS_CLONE = "sys_clone";

    /* Field names */
    static final String IRQ = "irq";
    static final String COMM = "comm";
    static final String NAME = "name";
    static final String TID = "tid";
    static final String PID = "pid";
    static final String PPID = "ppid";
    static final String STATUS = "status";
    static final String VEC = "vec";
    static final String PREV_COMM = "prev_comm";
    static final String PREV_TID = "prev_tid";
    static final String PREV_STATE = "prev_state";
    static final String NEXT_COMM = "next_comm";
    static final String NEXT_TID = "next_tid";
    static final String PARENT_TID = "parent_tid";
    static final String CHILD_COMM = "child_comm";
    static final String CHILD_TID = "child_tid";
    static final String FILENAME = "filename";
    static final String FD = "fd";
    static final String TYPE = "type";
    static final String MODE = "mode";
    static final String SUBMODE = "submode";
    static final String UNKNOWN = "unknown";
    static final String SEQ = "seq";
    static final String SK = "sk";
    static final String HRTIMER = "hrtimer";

}
