package org.eclipse.tracecompass.lttng2.kernel.ui.swtbot.tests;

import java.io.IOException;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.tracecompass.internal.lttng2.kernel.ui.symbols.KallsymsSymbolProvider;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ctf.core.tests.stubs.CtfTmfTraceStub;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.eclipse.tracecompass.tmf.ui.symbols.ISymbolProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * FIXME: This class is not in the right package, it would belong to
 * org.eclipse.tracecompass.lttn2.kernel.ui.tests, but this plug-in does not
 * exists.
 *
 * @author francis
 *
 */
public class TestKallsyms {

    private CtfTmfTrace fTrace;

    /**
     * @throws IOException exception
     * @throws TmfTraceException exception
     */
    @Before
    public void loadTrace() throws IOException, TmfTraceException {
        CtfTmfTrace trace = new CtfTmfTraceStub();
        URL url = this.getClass().getResource("/traces/fgraph-02/snap-20160428-142057-0/kernel");
        String path = FileLocator.toFileURL(url).getPath();
        trace.initTrace(null, path, CtfTmfEvent.class);
        fTrace = trace;
    }

    /**
     *
     */
    @After
    public void disposeTrace() {
        if (fTrace != null) {
            fTrace.dispose();
        }
    }

    /*
     * Examples from the actual kallsyms file
     * ffffffff81020aa0 T arch_cpu_idle_enter
     * ffffffff81020ad0 T arch_cpu_idle_exit
     * ffffffff81020b00 T arch_cpu_idle_dead
     * ffffffffc0015848 b do_floppy    [floppy]
     */
    enum TestData {
        SYM1(0xffffffff81020aa0L, "arch_cpu_idle_enter"),
        SYM2(0xffffffff81020ad0L, "arch_cpu_idle_exit"),
        SYM3(0xffffffff81020b00L, "arch_cpu_idle_dead"),
        SYM4(0xffffffffc0015848L, "do_floppy");

        private final Long fAddress;
        private final String fName;
        TestData(Long address, String name) {
            fAddress = address;
            fName = name;
        }
        Long getAddress() { return fAddress; }
        String getName() { return fName; }
    }

    /**
     *
     */
    @Test
    public void testLoadKallsyms() {
        ISymbolProvider provider = new KallsymsSymbolProvider(fTrace);
        provider.loadConfiguration(new NullProgressMonitor());
        for (TestData item: TestData.values()) {
            System.out.println(provider.getSymbolText(item.getAddress()));
        }
    }

}
