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
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.lttng2.kernel.core.tests.analysis.graph;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;

/**
 * Various utilities for unit testing: - find traces under directory - make
 * experiments - synchronize traces
 *
 * The aim of this class is to avoid code duplication in the test themselves
 * when working with actual traces.
 *
 * @author Francis Giraldeau <francis.giraldeau@gmail.com>
 */
@SuppressWarnings("javadoc")
@NonNullByDefault
public class Traceset {

    /**
     * List of all traces available in the traceset.
     *
     * Here is a tendinitis saver for updating the list:
     *
     * <pre>
     for i in $(find \
     traces/traceset/localhost/lttng-traceset-20150713/ -maxdepth 1 -type d); \
     do echo "public static final String TRACESET_$(echo "$(basename $i) \
     " | tr '[:lower:]' '[:upper:]' | tr - _) = \"$(basename $i)\";"; done
     * </pre>
     */
    public static final String TRACESET_WK_BOSSTHREAD_U = "wk-bossthread-u";
    public static final String TRACESET_WK_HEARTBEAT_K_U = "wk-heartbeat-k-u";
    public static final String TRACESET_WK_IMBALANCE_K = "wk-imbalance-k";
    public static final String TRACESET_WK_THREADTREE_U = "wk-threadtree-u";
    public static final String TRACESET_WK_MUTEX_K = "wk-mutex-k";
    public static final String TRACESET_WK_PIPETTE_CONS_K = "wk-pipette-cons-k";
    public static final String TRACESET_WK_PULSE_PREEMPT_2X_K = "wk-pulse-preempt-2x-k";
    public static final String TRACESET_WK_IOBURST_512_K = "wk-ioburst-512-k";
    public static final String TRACESET_WK_CPM2_K = "wk-cpm2-k";
    public static final String TRACESET_WK_FUNCTRACE_K_U = "wk-functrace-k-u";
    public static final String TRACESET_DD_100M_K = "dd-100M-k";
    public static final String TRACESET_WK_PIPETTE_PROD_K = "wk-pipette-prod-k";
    public static final String TRACESET_WK_THREADTREE_K_U = "wk-threadtree-k-u";
    public static final String TRACESET_WK_PIPELINE_K = "wk-pipeline-k";
    public static final String TRACESET_WK_IOBURST_512_SYNC_K = "wk-ioburst-512-sync-k";
    public static final String TRACESET_WK_HEARTBEAT_U = "wk-heartbeat-u";
    public static final String TRACESET_SLEEP_1X_1SEC_K = "sleep-1x-1sec-k";
    public static final String TRACESET_NETCAT_UDP_K = "netcat-udp-k";
    public static final String TRACESET_WK_DTHREAD_K = "wk-dthread-k";
    public static final String TRACESET_WK_INCEPTION_3X_100MS_K = "wk-inception-3x-100ms-k";
    public static final String TRACESET_WK_FUNCTRACE_U = "wk-functrace-u";
    public static final String TRACESET_WK_BOSSTHREAD_K_U = "wk-bossthread-k-u";
    public static final String TRACESET_WK_REPARENT_K = "wk-reparent-k";
    public static final String TRACESET_WK_DEADLOCK_K = "wk-deadlock-k";
    public static final String TRACESET_BURNP6_16X_1SEC_K = "burnP6-16x-1sec-k";
    public static final String TRACESET_WK_LOCKFIGHT_K = "wk-lockfight-k";
    public static final String TRACESET_WK_CPM1_K = "wk-cpm1-k";
    public static final String TRACESET_BURNP6_8X_1SEC_K = "burnP6-8x-1sec-k";
    public static final String TRACESET_WK_RPC_100MS_K = "wk-rpc-100ms-k";
    public static final String TRACESET_BURNP6_1X_1SEC_K = "burnP6-1x-1sec-k";
    public static final String TRACESET_BURNP6_3X_1SEC_K = "burnP6-3x-1sec-k";
    public static final String TRACESET_WK_CPM3_K = "wk-cpm3-k";
    public static final String TRACESET_NETCAT_TCP_K = "netcat-tcp-k";

    public static final String TRACESET_PATH = "traces/traceset/localhost/lttng-traceset-20150713";

    /**
     * Find directories that contains a signature file. It returns the
     * directories containing a file matching the glob pattern.
     *
     * By default, returns directories containing a metadata file. This is
     * likely a CTF trace directory.
     *
     * @author Francis Giraldeau <francis.giraldeau@gmail.com>
     *
     */
    public static final String GLOB_METADATA = "glob:metadata";

    private static class DirectoryFinder extends SimpleFileVisitor<Path> {

        private final PathMatcher fMatcher;
        private final List<Path> fResults;

        DirectoryFinder(String glob) {
            fMatcher = FileSystems.getDefault().getPathMatcher(glob);
            fResults = new LinkedList<>();
        }

        @Override
        public FileVisitResult visitFile(@Nullable Path path, @Nullable BasicFileAttributes attrs) {
            if (path != null) {
                Path name = path.getFileName();
                if (name != null && fMatcher.matches(name)) {
                    fResults.add(path.getParent());
                }
            }
            return FileVisitResult.CONTINUE;
        }

        /**
         * Return the list of directories containing a metadata file.
         *
         * @return results
         */
        public List<Path> getResults() {
            return fResults;
        }

    }

    /**
     * Compare java.nio.Path based on creation time.
     *
     * Useful to process traces in the same order they were created.
     *
     * @author Francis Giraldeau <francis.giraldeau@gmail.com>
     *
     */
    public static class PathCreationTimeComparator implements Comparator<Path> {
        @Override
        public int compare(@Nullable Path p0, @Nullable Path p1) {
            try {
                FileTime t0 = Files.readAttributes(p0, BasicFileAttributes.class).creationTime();
                FileTime t1 = Files.readAttributes(p1, BasicFileAttributes.class).creationTime();
                return t0.compareTo(t1);
            } catch (IOException e) {
                /*
                 * DISCUSS: handle exception in a comparator for use outside
                 * unit testing.
                 */
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    /**
     * Utility method to walk directories and find directories that containes a
     * a file matching the glob pattern.
     *
     * @param base
     *            base directory
     * @return list of trace paths
     */
    public static List<Path> findDirectories(Path base, String glob) {
        DirectoryFinder finder = new DirectoryFinder(glob);
        try {
            Files.walkFileTree(base, finder);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }

        return finder.getResults();
    }

    /**
     * Returns a list of sub-directories (excluding files).
     *
     * @param dir
     *            parent directory
     * @return list of paths
     */
    public static List<Path> listSubDirectories(Path dir) {
        List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path item : stream) {
                BasicFileAttributes attrs = Files.readAttributes(item, BasicFileAttributes.class);
                if (attrs.isDirectory()) {
                    paths.add(item);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }

        return paths;
    }

    /**
     * Make a trace collection from a set of paths.
     *
     * @param paths
     *            directory of traces
     * @param traceKlass
     *            the trace type
     * @param evKlass
     *            the event type
     * @return experiment
     */
    // The main object is necessarily non null, otherwise an exception occurs.
    // However, the compiler insists for
    // @Nullable annotation, which is obviously wrong, isn't?
    public static @Nullable ITmfTrace makeTraceCollectionGeneric(List<Path> paths, Class<? extends ITmfTrace> traceKlass, Class<? extends ITmfEvent> evKlass) {
        try {
            ITmfTrace main = traceKlass.newInstance();
            for (Path p : paths) {
                ITmfTrace child = traceKlass.newInstance();
                child.initTrace(null, p.toString(), evKlass);
                main.addChild(child);
            }
            return main;
        } catch (InstantiationException | IllegalAccessException | TmfTraceException e) {
            System.err.println(e.getClass());
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Make a CTF trace collection from a set of paths.
     *
     * @param paths
     *            directory of traces
     * @return experiment
     */
    public static @Nullable ITmfTrace makeTraceCollectionCTF(List<Path> loc) {
        return makeTraceCollectionGeneric(loc, CtfTmfTrace.class, CtfTmfEvent.class);
    }

    /**
     * Load a specific trace from the traceset.
     */
    public static @Nullable ITmfTrace load(String name) {
        List<Path> dirs = findDirectories(Paths.get(TRACESET_PATH, name), GLOB_METADATA);
        return makeTraceCollectionCTF(dirs);
    }

    /**
     * Send TmfTraceOpenSignal (required for analysis modules).
     */
    public static void open(ITmfTrace trace) {
        TmfTraceOpenedSignal signal = new TmfTraceOpenedSignal(null, trace, null);
        ((TmfTrace) trace).traceOpened(signal);
    }
}
