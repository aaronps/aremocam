package com.aaronps.aremocam;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by krom on 12/15/16.
 */

public final class CameraClient implements Runnable, Closeable {
    private static final String TAG = "CameraClient";

    private static final int QUEUE_LIMIT      = 5;
    private static final int RECV_BUFFER_SIZE = 128;

    private final Thread                             mThread;
    private final SocketChannel                      mSocketChannel;
    private final ArrayBlockingQueue<Sender>         mSendQueue;
    private final ArrayBlockingQueue<InternalSender> mFreeSenders;
    private final ByteBuffer                         mReceiveBuffer;

    public interface Sender {
        ByteBuffer prepareData() throws IOException;

        void afterSend() throws InterruptedException;
    }

    private class InternalSender implements Sender {

        ByteBuffer mBuffer;

        public void setData(ByteBuffer buffer) {
            mBuffer = buffer;
        }

        @Override
        public ByteBuffer prepareData() {
            return mBuffer;
        }

        @Override
        public void afterSend() throws InterruptedException {
            mFreeSenders.put(this);
        }
    }


    public CameraClient(final SocketChannel socket) {
        mSendQueue = new ArrayBlockingQueue<>(QUEUE_LIMIT);
        mFreeSenders = new ArrayBlockingQueue<>(QUEUE_LIMIT);

        mReceiveBuffer = ByteBuffer.allocate(RECV_BUFFER_SIZE);
        mSocketChannel = socket;
        mThread = new Thread(this);

        for (int n = 0; n < QUEUE_LIMIT; n++)
        {
            mFreeSenders.add(new InternalSender());
        }
    }

    public synchronized void start() {
        mThread.start();
    }

    public synchronized void close() {
        Log.d(TAG, "Close beg");
        if (mThread.isAlive())
        {
            try
            {
                Log.d(TAG, "Close try");
                mThread.interrupt();
                mThread.join();
            }
            catch (InterruptedException e)
            {
                // this will happen even if I was already interrupted before join
                Thread.currentThread().interrupt();
            }
        }

        Log.d(TAG, "Close close");

        closeSocket();
        Log.d(TAG, "Close clear");
        mSendQueue.clear();
        Log.d(TAG, "Close end");
    }

    // @todo unsynchronize closeSocket
    private synchronized void closeSocket() {
        Log.d(TAG, "Close socket beg");
        if (mSocketChannel.isOpen())
        {
            try
            {
                Log.d(TAG, "Close socket try");
                mSocketChannel.close();
            }
            catch (IOException ex)
            {
                // ignore
            }
        }
        Log.d(TAG, "Close socket end");
    }

    /**
     * The passed data gets owned by the camera client.
     *
     * @param data
     * @return
     */
    public void send(final ByteBuffer data) throws InterruptedException {
        final InternalSender s = mFreeSenders.take();
        s.setData(data);
        mSendQueue.put(s);
    }

    public void send(final Sender sender) throws InterruptedException {
        mSendQueue.put(sender);
    }

    @Override
    public void run() {
        Log.d(TAG, "Thread starts");
        final Thread thread = Thread.currentThread();

        try
        {
            while (!thread.isInterrupted())
            {
                final Sender sender = mSendQueue.take();
                mSocketChannel.write(sender.prepareData());
                sender.afterSend();
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
            // @todo closesocket is synchronized and may cause deadlocks if in finally...
            // so we close here...
            closeSocket();
        }
        finally
        {
            Log.d(TAG, "Thread ends");
        }
    }

    /**
     * Reads a text line from the client socket
     *
     * @todo don't read lines as protocol, the "new string" makes garbage, maybe use binary.
     *
     * @return The received line | empty line if closed
     */
    public String readLine() {
//        Log.d(TAG, "Reading line");

        final SocketChannel socketChannel = mSocketChannel;
        final ByteBuffer    receiveBuffer = mReceiveBuffer;
        final byte[]        receiveArray  = receiveBuffer.array();
        final Charset       utf8          = Charset.forName("UTF-8");

        int linePos = 0;

        try
        {

            // reading a line always starts at the beginning of the buffer
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
                    final String result = new String(receiveArray, 0, linePos, utf8);
                    // compact the buffer

                    if (receiveBuffer.position() == linePos + 1)
                    {
                        receiveBuffer.clear();
                    }
                    else
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
