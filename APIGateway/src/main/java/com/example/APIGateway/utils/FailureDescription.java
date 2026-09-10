package com.example.APIGateway.utils;

/**
 * Turns an exception into a log line a human can actually act on.
 *
 * ADDED 2026-09-09 (Track B3, step 1 follow-up).
 *
 * ===========================================================================================
 * WHY THIS EXISTS - THE "null" PROBLEM
 *
 * When the response timeout first fired in testing, the log said this:
 *
 *     Error fetching token version: null
 *     Auth service error: class ...WebClientRequestException - null
 *
 * Correct behaviour (we failed fast in 2 seconds, exactly as designed) but a useless message.
 * "null" tells you nothing about WHY.
 *
 * THE REASON IS NOT A BUG - IT IS A DELIBERATE NETTY DESIGN CHOICE
 *
 * A response timeout throws Netty's ReadTimeoutException. Look at how Netty builds it:
 *
 *     ReadTimeoutException(boolean)  ->  TimeoutException(null, false)
 *                                                        ^^^^
 *                                                    message IS null
 *
 *     TimeoutException.fillInStackTrace()  ->  return this;   // does NOTHING
 *
 * So the exception carries NO message and NO stack trace, on purpose. Timeouts happen a lot
 * under load, and building a stack trace is expensive - so Netty reuses one shared, pre-made
 * instance instead of creating a new one every time.
 *
 * That means calling .getMessage() throws away the ONLY information that exists: the CLASS
 * NAME. And e.printStackTrace() prints nothing useful either, because there is no stack trace
 * to print.
 *
 * THE LESSON WORTH KEEPING
 * "Log the message" is a habit, not a rule. When a message is empty, the exception TYPE and
 * its CAUSE CHAIN are the real information. This class prints all three.
 * ===========================================================================================
 */
public final class FailureDescription {

    /** How many causes deep we are willing to walk. A guard, not a real limit. */
    private static final int MAX_DEPTH = 10;

    private FailureDescription() {
        // utility class - never instantiated
    }

    /**
     * Converts any exception into a user-friendly error message.
     *
     * Two parts:
     * 1. HINT: Recognizes common errors (timeouts) and suggests which setting to change
     * 2. CHAIN: Shows full exception cause chain (outermost → root cause)
     *
     * Output example:
     *   TIMED OUT waiting for a REPLY (see gateway.httpclient.response-timeout-ms)
     *   | WebClientRequestException
     *   <- caused by <- ReadTimeoutException
     *
     * Safe guards: Handles null exceptions, prevents infinite loops, caps depth at 10.
     */
    public static String of(Throwable error) {
        if (error == null) {
            return "unknown failure (the exception itself was null)";
        }

        StringBuilder chain = new StringBuilder();
        String hint = null;

        Throwable current = error;
        int depth = 0;

        while (current != null && depth < MAX_DEPTH) {

            if (chain.length() > 0) {
                chain.append("  <- caused by <-  ");
            }
            chain.append(current.getClass().getSimpleName());

            // THIS is the bit that was missing before. If there is no message, say so out
            // loud instead of printing the word "null", which reads like something went
            // wrong with the logging rather than being the actual, expected situation.
            String message = current.getMessage();
            chain.append(message != null ? ": " + message : " (carries no message)");

            // Take the FIRST hint we recognise while walking down. The outermost exception is
            // usually a generic wrapper; the useful one is further in.
            if (hint == null) {
                hint = hintFor(current.getClass().getSimpleName());
            }

            Throwable cause = current.getCause();
            // An exception whose cause is itself would loop forever. It is rare but it does
            // happen, and a logging helper must never be the thing that hangs the app.
            current = (cause == current) ? null : cause;
            depth++;
        }

        return hint == null ? chain.toString() : hint + "\n  | " + chain;
    }

    /**
     * Maps a timeout exception to the SETTING that caused it.
     *
     * <p>This is the genuinely useful part. All three of our timeouts look similar in a log,
     * but they mean completely different things and are fixed by changing different numbers.
     * Naming the property means you go straight to the right line instead of guessing.</p>
     *
     * <p>Matching on the class NAME rather than the class itself is deliberate. Some of these
     * live in shaded, internal packages inside Reactor Netty, so importing them directly would
     * tie us to internal details that may be renamed. This value is only ever used to build a
     * log message - never to decide what the code does - so a name match is a safe trade.</p>
     */
    private static String hintFor(String simpleName) {
        switch (simpleName) {

            case "ConnectTimeoutException":
                return "TIMED OUT trying to REACH the service - nobody answered the door"
                        + " (see gateway.httpclient.connect-timeout-ms)";

            case "ReadTimeoutException":
                return "TIMED OUT waiting for a REPLY - the service accepted the connection"
                        + " then went quiet (see gateway.httpclient.response-timeout-ms)";

            case "PoolAcquireTimeoutException":
                return "TIMED OUT waiting for a FREE CONNECTION - every connection in the pool"
                        + " is currently busy (see gateway.httpclient.pending-acquire-timeout-ms"
                        + " and gateway.httpclient.max-connections)";

            default:
                // Not a timeout, or not one we recognise. The chain below still gets printed,
                // so nothing is hidden - we just have no friendly one-liner to add.
                return null;
        }
    }
}
