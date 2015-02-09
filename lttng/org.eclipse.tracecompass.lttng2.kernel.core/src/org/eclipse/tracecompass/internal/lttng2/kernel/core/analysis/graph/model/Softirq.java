/*******************************************************************************
 * Copyright (c) 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Francis Giraldeau - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.graph.model;

/**
 * Class containing constants for linux kernel soft IRQs.
 *
 * @author Francis Giraldeau
 */
public final class Softirq {

	/** ?? */
	public static final int HI = 0;
	/** Interrupted because of timer */
	public static final int TIMER = 1;
	/** Interrupted because of network transmission */
	public static final int NET_TX = 2;
	/** Interrupted because of network reception */
	public static final int NET_RX = 3;
	/** Interrupted because of block operation */
	public static final int BLOCK = 4;
	/** Interrupted because of block IO */
	public static final int BLOCK_IOPOLL = 5;
	/** Interrupted because of ?? */
	public static final int TASKLET = 6;
	        /** Interrupted because of the scheduler */
	public static final int SCHED = 7;
	/** Interrupted because of HR timer */
	public static final int HRTIMER = 8;
	/** Interrupted because of RCU */
	public static final int RCU = 9;

	private Softirq() {

	}

}
