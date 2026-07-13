package net.simplr.woosimdp230l;

import static org.junit.Assert.assertTrue;

import com.zebra.sdk.printer.PrinterStatus;

import org.junit.Test;

/**
 * Covers the fault-to-message mapping in {@link MainPresenter#notReadyMessageFor}.
 * These strings are what a warehouse user acts on; a wrong mapping sends them
 * to fix the wrong thing (or reprint into a stalled printer).
 */
public class MainPresenterTest {

    /** Fresh status stub with every fault flag off; tests flip the one they need. */
    private static PrinterStatus status() {
        try {
            return new PrinterStatus(null) {
                @Override
                protected void updateStatus() {
                    // no live connection in a JVM test; fields stay at defaults
                }
            };
        } catch (Exception e) {
            throw new AssertionError("PrinterStatus stub not constructible: " + e, e);
        }
    }

    private static void assertFirstLine(PrinterStatus s, String expectedFirstLine) {
        String msg = MainPresenter.notReadyMessageFor(s);
        assertTrue("expected to start with \"" + expectedFirstLine + "\" but was \"" + msg + "\"",
                msg.startsWith(expectedFirstLine));
    }

    @Test
    public void headOpen() {
        PrinterStatus s = status();
        s.isHeadOpen = true;
        assertFirstLine(s, "Printer cover is open.");
    }

    @Test
    public void paperOut() {
        PrinterStatus s = status();
        s.isPaperOut = true;
        assertFirstLine(s, "Printer is out of labels.");
    }

    @Test
    public void pausedWithEmptyBuffer() {
        PrinterStatus s = status();
        s.isPaused = true;
        assertFirstLine(s, "Printer is paused.");
    }

    @Test
    public void pausedWithQueuedLabelsWarnsAgainstReprinting() {
        PrinterStatus s = status();
        s.isPaused = true;
        s.numberOfFormatsInReceiveBuffer = 2;
        String msg = MainPresenter.notReadyMessageFor(s);
        assertTrue(msg, msg.startsWith("Printer paused, 2 label(s) already waiting."));
        assertTrue(msg, msg.contains("do NOT print again"));
    }

    @Test
    public void ribbonOut() {
        PrinterStatus s = status();
        s.isRibbonOut = true;
        assertFirstLine(s, "Printer ribbon is out.");
    }

    @Test
    public void headTooHot() {
        PrinterStatus s = status();
        s.isHeadTooHot = true;
        assertFirstLine(s, "Printer is too hot.");
    }

    @Test
    public void receiveBufferFull() {
        PrinterStatus s = status();
        s.isReceiveBufferFull = true;
        assertFirstLine(s, "Printer is stuck with pending labels.");
    }

    @Test
    public void noFlagSetMeansStillWaking() {
        assertFirstLine(status(), "Printer not ready (may be waking up).");
    }

    @Test
    public void headOpenOutranksOtherFaults() {
        PrinterStatus s = status();
        s.isHeadOpen = true;
        s.isPaperOut = true;
        s.isPaused = true;
        assertFirstLine(s, "Printer cover is open.");
    }
}
