package com.aaronps.aremocam;

/**
 * Created by krom on 2016-05-16.
 */
public final class FastLoopProtection
{
    private long mPreviousMS = 0;
    private final long mMilliseconds;

    public FastLoopProtection(final long milliseconds)
    {
        mMilliseconds = milliseconds;
    }

    public void sleep() throws InterruptedException
    {
        final long msSinceLastLoop = System.currentTimeMillis() - mPreviousMS;

        if (msSinceLastLoop < mMilliseconds)
            Thread.sleep(mMilliseconds - msSinceLastLoop);

        mPreviousMS += msSinceLastLoop; // as to avoid getting the time again...
    }
}
