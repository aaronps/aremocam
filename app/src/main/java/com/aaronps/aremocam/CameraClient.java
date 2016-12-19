package com.aaronps.aremocam;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by krom on 12/15/16.
 */

public final class CameraClient implements Runnable, Closeable {
    private static final String TAG = "CameraClient";

    private static final int RECV_BUFFER_SIZE = 128;

    private final Thread mThread;
    private final SocketChannel                     mSocketChannel;
    private final LinkedBlockingQueue<ByteBuffer[]> mSendQueue;
    private final ByteBuffer                        mReceiveBuffer;


    public CameraClient(final SocketChannel socket) {
        mSendQueue = new LinkedBlockingQueue<>();
        mReceiveBuffer = ByteBuffer.allocate(RECV_BUFFER_SIZE);
        mSocketChannel = socket;
        mThread = new Thread(this);
    }

    public synchronized void start() {
        mThread.start();
    }

    public synchronized void close() {
        if ( mThread.isAlive() )
        {
            try
            {
                mThread.interrupt();
                mThread.join();
            }
            catch (InterruptedException e)
            {
                // this will happen even if I was already interrupted before join
                Thread.currentThread().interrupt();
            }
        }

        closeSocket();
        mSendQueue.clear();
    }

    private synchronized void closeSocket() {
        if (mSocketChannel.isOpen())
        {
            try
            {
                mSocketChannel.close();
            }
            catch (IOException ex)
            {
                // ignore
            }
        }
    }

    /**
     * The passed data gets owned by the camera client.
     *
     * @param data
     * @return
     */
    public boolean send(final ByteBuffer[] data) {
        return mSendQueue.offer(data);
    }

    public boolean send(final ByteBuffer data) {
        return send(new ByteBuffer[]{data});
    }


    @Override
    public void run() {
        Log.d(TAG, "Thread starts");
        final Thread thread = Thread.currentThread();

        try
        {
            while (!thread.isInterrupted())
            {
                // @question being blocking socket, will it always write everything?
                mSocketChannel.write(mSendQueue.take());
            }
        }
        catch (InterruptedException e)
        {
            Log.d(TAG, "Thread Interrupted");
            thread.interrupt();
        }
        catch (IOException e)
        {
            Log.d(TAG, "Thread IOException", e);
        }
        finally
        {
            Log.d(TAG, "Thread ends");
            // @todo shall I close mSocketChannel?
//            closeNoThrow(os); // ensure that at some point will fail when reading because its closed
        }
    }

    /**
     * Reads a text line from the client socket
     *
     * @return The received line | empty line if closed
     */
    public String readLine() {
//        Log.d(TAG, "Reading line");

        final SocketChannel socketChannel = mSocketChannel;
        final ByteBuffer    receiveBuffer = mReceiveBuffer;
        final byte[]        receiveArray  = receiveBuffer.array();

        int linePos = 0;

        try
        {

            // reading a line always starts at the beginning
            do
            {
                if (receiveBuffer.position() == linePos)
                {
                    // blocking read
                    if (socketChannel.read(receiveBuffer) <= 0)
                    {
                        return ""; // closed, my owner should close me.
                    }
                }

                if (receiveArray[linePos] == 10)
                {
                    final String result = new String(receiveArray, 0, linePos, Charset.forName("UTF-8"));
                    // compact the buffer

                    if (receiveBuffer.position() == linePos + 1)
                    {
                        receiveBuffer.clear();
                    } else
                    {
                        // position can only be > bpos
                        receiveBuffer.position(linePos + 1);
                        receiveBuffer.compact();
                    }

                    return result;
                }

            } while (++linePos < receiveArray.length);

            // if arrive here it means the buffer became full and the newline was never seend
            // it means buffer size should be increased, what to do know?

            Log.d(TAG, "Buffer full and no new line, disconnecting client, maybe increase buffer");
            return "";
        }
        catch (IOException ex)
        {
            Log.d(TAG, "IOException when reading line", ex);
            // assume close it
            closeSocket();
        }
        catch (Exception e)
        {
            Log.d(TAG, "Other Exception when reading line", e);
        }

        return "";
    }
}
