package com.aaronps.aremocam;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * @todo the client is called CameraClient
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

    interface PreviewSender extends SimplePreview.PreviewCallback, CameraClient.Sender {
    }

    public CameraServer(SimplePreview simplePreview, int tcp_port) {
        mSimplePreview = simplePreview;
        mTcpPort = tcp_port;
    }

    @Override
    public void run() {
        Log.d(TAG, "Run Begin");
        final SimpleLoopBarrier loopBarrier = new SimpleLoopBarrier(LOOP_RESTART_MS);
        final Thread            thread      = Thread.currentThread();

        ServerSocketChannel sschannel = null;

        try
        {
            while (!thread.isInterrupted())
            {
                try
                {
                    loopBarrier.check();

                    if (!mSimplePreview.acquire())
                    {
                        continue;
                    }

//                    mSimplePreview.start();

                    if (sschannel == null || !sschannel.isOpen())
                    {
                        sschannel = createServer();
                    }

                    Log.d(TAG, "Waiting for client");
                    commandLoop(new CameraClient(sschannel.accept()));
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
        catch (InterruptedException ie)
        {
            Log.d(TAG, "INTERRUPTED", ie);
            Thread.currentThread().interrupt();
        }
        catch (Exception e)
        {
            Log.d(TAG, "Weird exception", e);
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

    // little risk, numbers can be 16 digits or less only.
    private static final byte[] MSG_PIC = "Pic                 \n".getBytes();

    private void commandLoop(final CameraClient cameraClient) {
        Log.d(TAG, "Client connected");
        try
        {
            final ArrayBlockingQueue<PreviewSender> previewSenders = new ArrayBlockingQueue<>(2);
            for (int n = previewSenders.remainingCapacity(); n > 0; n--)
                previewSenders.add(
                        new PreviewSender() {
                            final ByteBuffer bb = ByteBuffer.allocate(1 * 1024 * 1024);
                            final ByteBufferOutputStream bbos = new ByteBufferOutputStream(bb);

                            byte[] mFrame;
                            Rect mRect = new Rect(0, 0, 0, 0);

                            @Override
                            public void onPreviewFrame(byte[] bytes, int width, int height) {
                                mFrame = bytes;
                                mRect.right = width;
                                mRect.bottom = height;
                                try
                                {
                                    cameraClient.send(this);
                                }
                                catch (InterruptedException e)
                                {
                                    Thread.currentThread().interrupt();
                                    // didn't send it.
                                    mSimplePreview.release(mFrame);
                                }
                            }

                            @Override
                            public ByteBuffer prepareData() throws IOException {
                                bbos.reset();

                                final YuvImage yuv = new YuvImage(mFrame, ImageFormat.NV21, mRect.right, mRect.bottom, null);
                                try
                                {
                                    bbos.write(MSG_PIC);

                                    yuv.compressToJpeg(mRect, 80, bbos);

                                    final int pos    = bb.position();
                                    final int jpglen = pos - MSG_PIC.length;
                                    bb.position(4);
                                    bbos.writeInt(jpglen);
                                    bb.position(pos);
                                }
                                finally
                                {
                                    mSimplePreview.release(mFrame);
                                }

                                bb.flip();
                                return bb;
                            }

                            @Override
                            public void afterSend() throws InterruptedException {
                                previewSenders.put(this);
                            }
                        });

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
//                    Log.d(TAG, "Requested Pic");
                    mSimplePreview.setPreviewCallbackOnce(previewSenders.take());
                }
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            Log.d(TAG, "Interrupted somewhere", e);
        }
        finally
        {
            mSimplePreview.stop();
            cameraClient.close();
        }
    }

}
