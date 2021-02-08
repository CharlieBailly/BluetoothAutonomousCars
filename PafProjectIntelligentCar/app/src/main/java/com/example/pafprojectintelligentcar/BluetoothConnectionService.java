package com.example.pafprojectintelligentcar;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import static javax.net.ssl.SSLEngineResult.Status.BUFFER_OVERFLOW;
import static javax.net.ssl.SSLEngineResult.Status.BUFFER_UNDERFLOW;

public class BluetoothConnectionService {
    //Debugging
    private static final String TAG = "BluetoothConnectionService";

    // Name for the SDP record when creating server socket
    private static final String APP_NAME_SECURE = "BluetoothCommunicationSecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    // Member fields
    private final BluetoothAdapter mBluetoothAdapter;
    Context mContext;

    private AcceptThread mSecureAcceptThread;
    private ConnectThread mConnectThread;
    private BluetoothDevice mmDevice;
    private UUID deviceUUID;
    ProgressDialog mProgressDialog;

    private byte[] peerBTDataArray;
    private byte[] peerAppDataArray;
    private ByteBuffer peerBTData;
    private ByteBuffer peerAppData;

    private byte[] myBTDataArray;
    private byte[] myAppDataArray;
    private ByteBuffer myBTData;
    private ByteBuffer myAppData;

    private ConnectedThread mConnectedThread;

    protected ExecutorService executor = Executors.newSingleThreadExecutor();

    public BluetoothConnectionService(Context context){
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        start();
    }

    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        @SuppressLint("LongLogTag")
        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord( APP_NAME_SECURE,
                            MY_UUID_SECURE );
                    Log.d( TAG, "Setting up server using" );
                }
            } catch (IOException e) {
            }
            mmServerSocket = tmp;
        }

        @SuppressLint("LongLogTag")
        public void run (){
            Log.d( TAG, "AcceptThread Running" );

            BluetoothSocket socket = null;

            try{
                //This is a blocking call and will only return on a successful connection or an exception
                Log.d( TAG, "RFComm Server socket start" );

                socket = mmServerSocket.accept();
                Log.d( TAG, "RFComm Server socket accepted connection " );
            }catch (IOException e){
                Log.e( TAG, "IOException :" + e.getMessage() );
            }

            if (socket != null){
                try {
                    connected(socket, mmDevice);
                } catch (IOException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
            }
        }
        @SuppressLint("LongLogTag")
        public void cancel() {
            Log.d(TAG, "Canceling AcceptThread");
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Close of AcceptThread ServerSocket failed" + e.getMessage());
            }
        }
    }


    private class ConnectThread extends Thread{
        private BluetoothSocket mmSocket;

        @SuppressLint("LongLogTag")
        public ConnectThread(BluetoothDevice device, UUID uuid){
            Log.d( TAG, "ConnectThread : started" );
            mmDevice = device;
            deviceUUID = uuid;
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @SuppressLint("LongLogTag")
        public void run (){
            BluetoothSocket tmp = null;

            Method method;

            //Get a BluetoothSocket for a connection with the given BTdevice
            try{
                Log.d(TAG, "Trying to create SecureRfcommSocket using UUID");
                method = mmDevice.getClass().getMethod("createRfcommSocket", new Class[] { int.class } );
                tmp = (BluetoothSocket) method.invoke(mmDevice, 1);
            }catch(NoSuchMethodException | IllegalAccessException | InvocationTargetException e){
                Log.e(TAG, "Could not create SecureRfcommSocket");
            }
            mmSocket = tmp;

            //Always cancel discovery because it will slow down connection
            mBluetoothAdapter.cancelDiscovery();

            //Make a connection to the BluetoothSocket

            try {
                //This is a blocking call and will only return on a successful connection or an exception
                mmSocket.connect();
                Log.e( TAG, "ConnectThread : connected" );
            } catch (IOException e) {
                //close the socket
                try{
                    mmSocket.close();
                    Log.e( TAG, "run: Closed Socket" );
                    Log.e(TAG, e.getMessage());
                    Log.e(TAG, e.getLocalizedMessage());
                }catch (IOException e1){
                    Log.e(TAG, "Unable to close connection in socket" + e1.getMessage());
                }
                Log.e(TAG, "Could not connect to UUID" + MY_UUID_SECURE);
            }

            try {
                connected(mmSocket, mmDevice);
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }

        }

        @SuppressLint("LongLogTag")
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG,  " socket failed", e);
            }
        }
    }

    @SuppressLint("LongLogTag")
    public synchronized void start() {
        Log.d( TAG, "start" );

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
    }

    @SuppressLint("LongLogTag")
    public void connected (BluetoothSocket mmSocket, BluetoothDevice mmDevice) throws IOException {
        Log.d(TAG, "Connected: Starting" );

        //Start the Thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread( mmSocket);
        mConnectedThread.start();
    }

    //AcceptThread starts and waits for a connection.
    //Then ConnectThread starts and attempts to make a connection with the other devices AcceptThread
    @SuppressLint("LongLogTag")
    public void startClient(BluetoothDevice device, UUID uuid){
        Log.d( TAG, "startClient : started" );

        //init Progress Dialog
        mProgressDialog = ProgressDialog.show( mContext, "Connecting Bluetooth", "Please Wait...", true );
        mConnectThread = new ConnectThread( device, uuid);
        mConnectThread.start();
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private SSLEngine ssl;
        private SSLContext ctx;

        @SuppressLint("LongLogTag")
        public ConnectedThread(BluetoothSocket socket) throws IOException {
            Log.d( TAG, "ConnectedThread: Starting" );
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            peerAppDataArray = new byte[1024];
            peerBTDataArray = new byte[1024];

            peerAppData = ByteBuffer.wrap(peerAppDataArray);
            peerBTData = ByteBuffer.wrap(peerBTDataArray);

            myAppDataArray = new byte[1024];
            myBTDataArray = new byte[1024];

            myAppData = ByteBuffer.wrap(myAppDataArray);
            myBTData = ByteBuffer.wrap(myBTDataArray);

            //Dismiss the ProgressDialog when connection is established
            try{
                mProgressDialog.dismiss();
            }catch(NullPointerException e){
                Log.e( TAG, "ProgressDialog couldn't be dismissed", e );
            }


            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e( TAG, "temp sockets not created", e );
            }

            try
            {
                ctx = SSLContext.getInstance("TLSv1");
                ctx.init(null, null, null);
                ssl = ctx.createSSLEngine();
                ssl.setUseClientMode(true);
            }
            catch (NoSuchAlgorithmException | KeyManagementException e)
            {
                Log.e( TAG, "No such algorithm", e );
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

        }

        @SuppressLint("LongLogTag")
        protected boolean doHandshake() throws IOException {

            Log.d(TAG, "About to do handshake...");

            SSLEngineResult result;
            SSLEngineResult.HandshakeStatus handshakeStatus;

            // NioSslPeer's fields myAppData and peerAppData are supposed to be large enough to hold all message data the peer
            // will send and expects to receive from the other peer respectively. Since the messages to be exchanged will usually be less
            // than 16KB long the capacity of these fields should also be smaller. Here we initialize these two local buffers
            // to be used for the handshake, while keeping client's buffers at the same size.
            int appBufferSize = ssl.getSession().getApplicationBufferSize();
            ByteBuffer myAppData = ByteBuffer.allocate(appBufferSize);
            ByteBuffer peerAppData = ByteBuffer.allocate(appBufferSize);
            myBTData.clear();
            peerBTData.clear();

            handshakeStatus = ssl.getHandshakeStatus();
            while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                switch (handshakeStatus) {
                    case NEED_UNWRAP:

                        peerBTDataArray = new byte[1024];

                        if (mmInStream.read(peerBTDataArray) < 0) {

                            if (ssl.isInboundDone() && ssl.isOutboundDone()) {
                                return false;
                            }
                            try {
                                ssl.closeInbound();
                            } catch (SSLException e) {
                                Log.e(TAG,"This engine was forced to close inbound, without having received the proper SSL/TLS close notification message from the peer, due to end of stream.");
                            }
                            ssl.closeOutbound();
                            // After closeOutbound the engine will be set to WRAP state, in order to try to send a close message to the client.
                            handshakeStatus = ssl.getHandshakeStatus();
                            break;
                        }

                        peerBTData.put(peerBTDataArray);

                        peerBTData.flip();
                        try {
                            do {
                                result = ssl.unwrap(peerBTData, peerAppData);
                                Log.d(TAG,"data unwrapped...");
                                Log.d(TAG,"Handskes is: " + result.getHandshakeStatus().toString() +" Current Status: " +result.getStatus() + " Bytes consumed: " + result.bytesConsumed() + " bytes produce: " + result.bytesProduced());
                            } while (peerBTData.hasRemaining() || result.bytesProduced()>0);
                            peerBTData.compact();
                            handshakeStatus = result.getHandshakeStatus();
                        } catch (SSLException sslException) {
                            Log.e(TAG,"A problem was encountered while processing the data that caused the SSLEngine to abort. Will try to properly close connection...");
                            ssl.closeOutbound();
                            handshakeStatus = ssl.getHandshakeStatus();
                            break;
                        }
                        switch (result.getStatus()) {
                            case OK:
                                break;
                            case BUFFER_OVERFLOW:
                                // Will occur when peerAppData's capacity is smaller than the data derived from peerNetData's unwrap.
                                peerAppData = enlargeApplicationBuffer(ssl, peerAppData);
                                break;
                            case BUFFER_UNDERFLOW:
                                // Will occur either when no data was read from the peer or when the peerNetData buffer was too small to hold all peer's data.
                                peerBTData = handleBufferUnderflow(ssl, peerBTData);
                                break;
                            case CLOSED:
                                if (ssl.isOutboundDone()) {
                                    return false;
                                } else {
                                    ssl.closeOutbound();
                                    handshakeStatus = ssl.getHandshakeStatus();
                                    break;
                                }
                            default:
                                throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                        }
                        break;
                    case NEED_WRAP:
                        myBTData.clear();
                        try {
                            result = ssl.wrap(myAppData, myBTData);
                            handshakeStatus = result.getHandshakeStatus();
                        } catch (SSLException sslException) {
                            Log.e(TAG,"A problem was encountered while processing the data that caused the SSLEngine to abort. Will try to properly close connection...");
                            ssl.closeOutbound();
                            handshakeStatus = ssl.getHandshakeStatus();
                            break;
                        }
                        switch (result.getStatus()) {
                            case OK :
                                myBTData.flip();
                                while (myBTData.hasRemaining()) {

                                    myBTDataArray = new byte[myBTData.remaining()];

                                    myBTData.get(myBTDataArray, 0, myBTDataArray.length);

                                    mmOutStream.write(myBTDataArray);
                                }
                                break;
                            case BUFFER_OVERFLOW:
                                // Will occur if there is not enough space in myNetData buffer to write all the data that would be generated by the method wrap.
                                // Since myNetData is set to session's packet size we should not get to this point because SSLEngine is supposed
                                // to produce messages smaller or equal to that, but a general handling would be the following:
                                myBTData = enlargePacketBuffer(ssl, myBTData);
                                break;
                            case BUFFER_UNDERFLOW:
                                throw new SSLException("Buffer underflow occured after a wrap. I don't think we should ever get here.");
                            case CLOSED:
                                try {
                                    myBTData.flip();
                                    while (myBTData.hasRemaining()) {
                                        myBTDataArray = new byte[myBTData.remaining()];

                                        myBTData.get(myBTDataArray, 0, myBTDataArray.length);

                                        mmOutStream.write(myBTDataArray);
                                    }
                                    // At this point the handshake status will probably be NEED_UNWRAP so we make sure that peerNetData is clear to read.
                                    peerBTData.clear();
                                } catch (Exception e) {
                                    Log.e(TAG,"Failed to send server's CLOSE message due to socket channel's failure.");
                                    handshakeStatus = ssl.getHandshakeStatus();
                                }
                                break;
                            default:
                                throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                        }
                        break;
                    case NEED_TASK:
                        Runnable task;
                        while ((task = ssl.getDelegatedTask()) != null) {
                            executor.execute(task);
                        }
                        handshakeStatus = ssl.getHandshakeStatus();
                        break;
                    case FINISHED:
                        break;
                    case NOT_HANDSHAKING:
                        break;
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + handshakeStatus);
                }
            }

            return true;

        }

        @SuppressLint("LongLogTag")
        public void run ()
        {
            byte[] buffer = new byte[1024];

            Log.d(TAG, "Handshake begin");

            try {
                ssl.beginHandshake();
            } catch (SSLException e) {
                e.printStackTrace();
            }
            try {
                if(!doHandshake())
                {
                    Log.e(TAG, "Handshake error");
                }
                else
                {
                    Log.i(TAG, "Handshake successful");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "Handshake ended");

            //bytes return from read
            int bytes;

            //Keep listening to the InputStream until an exception occurs
            /*while(true){

                try{

                    bytes = mmInStream.read(peerBTDataArray);
                    if(bytes > 0)
                    {
                        peerBTData.flip();
                        while (peerBTData.hasRemaining()) {
                            peerAppData.clear();
                            SSLEngineResult result = ssl.unwrap(peerBTData, peerAppData);
                            switch (result.getStatus()) {
                                case OK:
                                    peerAppData.flip();
                                    Log.d(TAG, "Server response: " + new String(peerAppData.array()));
                                    break;
                                default:
                                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                            }
                        }
                    }
                }catch(IOException e){
                    Log.e(TAG, "Error reading Inputstream:" + e.getMessage());
                    break;
                }
            }*/

            while(true)
            {
                try {
                    write("test".getBytes());
                    Thread.sleep(1000);
                } catch (SSLException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        //Call this method from the MainActivity to send data to the remote device
        @SuppressLint("LongLogTag")
        public void write (byte[] bytes) throws SSLException {
           /* String text = new String (bytes, Charset.defaultCharset());
            Log.d(TAG, "Wrinting to Outputstream:" + text);
            try {
                mmOutStream.write(bytes);
            }catch(IOException e){
                Log.e(TAG, "Error wrinting to Outputstream:" + e.getMessage());
            }*/

            Log.d(TAG, "About to write to the server...");

            myAppData.clear();
            myAppData.wrap(bytes);
            myAppData.flip();
            while (myAppData.hasRemaining()) {
                // The loop has a meaning for (outgoing) messages larger than 16KB.
                // Every wrap call will remove 16KB from the original message and send it to the remote peer.
                myBTData.clear();
                SSLEngineResult result = null;
                try {
                    result = ssl.wrap(myAppData, myBTData);
                } catch (SSLException e) {
                    Log.e(TAG, e.getLocalizedMessage());
                }
                switch (result.getStatus()) {
                    case OK:
                        myBTData.flip();
                        while (myBTData.hasRemaining()) {
                            try {
                                myBTDataArray = new byte[myBTData.remaining()];

                                myBTData.get(myBTDataArray, 0, myBTDataArray.length);

                                mmOutStream.write(myBTDataArray);
                            } catch (IOException e) {
                                Log.e(TAG, e.getLocalizedMessage());
                            }
                        }
                        Log.d(TAG, "Message sent to the server: ");
                        break;
                    case BUFFER_OVERFLOW:
                        myBTData = enlargePacketBuffer(ssl, myBTData);
                        break;
                    case BUFFER_UNDERFLOW:
                        throw new SSLException("Buffer underflow occured after a wrap. I don't think we should ever get here.");
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                }
            }
        }

        protected ByteBuffer enlargePacketBuffer(SSLEngine engine, ByteBuffer buffer) {
            return enlargeBuffer(buffer, engine.getSession().getPacketBufferSize());
        }

        protected ByteBuffer enlargeApplicationBuffer(SSLEngine engine, ByteBuffer buffer) {
            return enlargeBuffer(buffer, engine.getSession().getApplicationBufferSize());
        }

        protected ByteBuffer enlargeBuffer(ByteBuffer buffer, int sessionProposedCapacity) {
            if (sessionProposedCapacity > buffer.capacity()) {
                buffer = ByteBuffer.allocate(sessionProposedCapacity);
            } else {
                buffer = ByteBuffer.allocate(buffer.capacity() * 2);
            }
            return buffer;
        }

        protected ByteBuffer handleBufferUnderflow(SSLEngine engine, ByteBuffer buffer) {
            if (engine.getSession().getPacketBufferSize() < buffer.limit()) {
                return buffer;
            } else {
                ByteBuffer replaceBuffer = enlargePacketBuffer(engine, buffer);
                buffer.flip();
                replaceBuffer.put(buffer);
                return replaceBuffer;
            }
        }

        //Call this method from the MainActivity to shutdown the connection
        @SuppressLint("LongLogTag")
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }

        @SuppressLint("LongLogTag")
        public void write2(byte[] out){
            //Create temporary object
            ConnectThread r;

            //Synchronize a copy of the ConnectedThread
            Log.d(TAG, "Write called");
            //perform the write
            mConnectedThread.write2(out);

        }
    }


}
