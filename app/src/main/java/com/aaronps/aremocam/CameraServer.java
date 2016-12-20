package com.aaronps.aremocam;

import android.hardware.Camera;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;

/**
 * @todo the client is called CameraClient
 * @todo When making a client application it's class should be called RemoteCamera
 * <p>
 * <p>
 * C@noteameraServer takes control of the SimplePreview
 * <p>
 * Created by krom on 12/15/16.
 */

public class CameraServer implements Runnable {
    private static final String TAG             = "CameraServer";
    private static final long   LOOP_RESTART_MS = 5000;

    private final SimplePreview mSimplePreview;
    private final int           mTcpPort;

    public CameraServer(SimplePreview simplePreview, int tcp_port) {
        mSimplePreview = simplePreview;
        mTcpPort = tcp_port;
    }

    @Override
    public void run() {
        Log.d(TAG, "Run Begin");
        final FastLoopProtection loopProtection = new FastLoopProtection(LOOP_RESTART_MS);
        final Thread             thread         = Thread.currentThread();

        ServerSocketChannel sschannel = null;

        try
        {
            while (!thread.isInterrupted())
            {
                try
                {
                    loopProtection.sleep();

                    if (!mSimplePreview.acquire())
                    {
                        continue;
                    }

//                    mSimplePreview.start();

                    if (sschannel == null || !sschannel.isOpen())
                    {
                        sschannel = createServer();
                    }

                    commandLoop(new CameraClient(sschannel.accept(), mSimplePreview));
                }
                catch (InterruptedException ie)
                {
                    Log.d(TAG, "INTERRUPTED", ie);
                    Thread.currentThread().interrupt();
                    break;
                }
                catch (RuntimeException re) // at least from acquiring camera
                {
                    Log.d(TAG, "Runtime exception", re);
                    continue;
                }
                catch (IOException ioe)
                {
                    // AHH interesting, ClosedByInterruptException comes here and the loop exits
                    // when "while" checks for interrupted.
                    Log.d(TAG, "IOE", ioe);
                    continue;
                }
            }
        }
        finally
        {
            if (sschannel != null)
            {
                try
                {
                    sschannel.close();
                }
                catch (IOException e)
                { /* ignore */ }
            }
            mSimplePreview.release();
        }

        Log.d(TAG, "Run End");
    }

    private ServerSocketChannel createServer() throws IOException {
        Log.d(TAG, "Creating listening channel");
        final ServerSocketChannel sschannel = ServerSocketChannel.open();

        final ServerSocket ss = sschannel.socket();
        ss.bind(new InetSocketAddress(mTcpPort));
        ss.setReuseAddress(true);

        Log.d(TAG, "Creating listening channel -> OK");
        return sschannel;
    }

    private void commandLoop(final CameraClient cameraClient) {
        try
        {

            Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
                final StringBuilder sb = new StringBuilder(128);
                final ByteBuffer bb = ByteBuffer.allocate(128);
                final byte[] bbarray = bb.array();

                @Override
                public void onPreviewFrame(byte[] bytes, Camera camera) {
                    sb.setLength(0);
                    sb.append("Pic ").append(bytes.length).append('\n');
                    final String headStr = sb.toString(); // @todo could use traction int2string so avoid toString
                    final int headLen = headStr.length();
                    headStr.getBytes(0, headLen, bbarray, 0);
                    bb.position(0);
                    bb.limit(headLen);
                    final ByteBuffer head = bb;

                    // @todo an idea for future avoidance of wrapping would be: SimplePreview finds which pre-made ByteBuffer for this byte[]
                    final ByteBuffer pic  = ByteBuffer.wrap(bytes);

                    cameraClient.send(head);
                    cameraClient.send(pic);
                }
            };

            cameraClient.start();
            while (true)
            {
                final String command = cameraClient.readLine();
                if (command.isEmpty())
                {
                    Log.d(TAG, "Disconnect client");
                    cameraClient.close();
                    return;
                }

                if (command.equals("SizeList"))
                {
                    StringBuilder sb = new StringBuilder(128);
                    sb.append("SizeList");

                    for (Camera.Size size : mSimplePreview.mPreviewSizes)
                    {
                        sb.append(' ').append(size.width).append('x').append(size.height);
                    }

                    sb.append('\n');

                    cameraClient.send(ByteBuffer.wrap(sb.toString().getBytes()));
                    //                cameraClient.send(new byte[][]{sb.toString().getBytes()});
                }
                else if (command.startsWith("BeginVideo "))
                {
                    final String   vsize  = command.split(" ")[1];
                    final String[] parts  = vsize.split("x");
                    final int      width  = Integer.parseInt(parts[0]);
                    final int      height = Integer.parseInt(parts[1]);

                    Log.d(TAG, "Requested BeginVideo size " + vsize);

                    if (mSimplePreview.start(width, height))
                    {
                        StringBuilder sb = new StringBuilder(128);
                        sb.append("Ready ")
                                .append(width).append(' ')
                                .append(height).append(' ')
                                .append(mSimplePreview.getPreviewFormat())
                                .append('\n');

                        cameraClient.send(ByteBuffer.wrap(sb.toString().getBytes()));
                    }
                }
                else if (command.equals("StopVideo"))
                {
                    Log.d(TAG, "Requested StopVideo");
                    mSimplePreview.stop();
                }
                else if (command.equals("Pic"))
                {
                    mSimplePreview.setPreviewCallbackOnce(previewCallback);
//                    Log.d(TAG, "Requested Pic");
                }
            }
        }
        finally
        {
            mSimplePreview.stop();
            cameraClient.close();
        }
    }

}
