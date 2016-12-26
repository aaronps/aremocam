package com.aaronps.aremocam;

/**
 * Helper class intended to be used on infinite loops to protect from very fast
 * restart.
 * <p>
 * <b>Usage</b>
 * <p>
 * Pass the minimum number of milliseconds that has to happen between restarts
 * to the constructor. Then at the beginning of the loop add a call to "check".
 * <p>
 * <b>Example</b>
 *
 * <blockquote><pre>{@code
  final SimpleLoopBarrier loopBarrier = new SimpleLoopBarrier(1000);
  try
  {
      while (not_finished && ! Thread.currentThread.isInterrupted())
      {
          loopBarrier.check();
          // do something
      }
  }
  catch ( InterruptedException ie )
  {
      Thread.currentThread().interrupt();
  }
 }
 * </pre></blockquote>
 *
 * <p>
 * <b>note:</b> whether to catch the <code>InterruptedException</code> within
 * the loop or not, depends on the meaning you give to interruptions.
 */
public final class SimpleLoopBarrier {

    /**
     * Last execution time.
     */
    private long mPreviousMS = 0;
    
    /**
     * Minimum elapsed time between checks.
     */
    private final long mMilliseconds;

    /**
     * Creates the loop barrier
     *
     * @param milliseconds Minimum number of milliseconds between restarts.
     */
    public SimpleLoopBarrier(final long milliseconds) {
        mMilliseconds = milliseconds;
    }

    /**
     * Ensures the required elapsed time (set on construction) between calls to
     * <code>check</code> has passed.
     *
     * @throws InterruptedException when interrupted doing its thing.
     */
    public void check() throws InterruptedException {
        final long now = System.currentTimeMillis();
        final long msSinceLastLoop = now - mPreviousMS;

        if (msSinceLastLoop < mMilliseconds)
        {
            final long toSleep = mMilliseconds - msSinceLastLoop;
            Thread.sleep(toSleep);
            mPreviousMS = now + toSleep;
        }
        else
        {
            mPreviousMS = now;
        }
    }
}
