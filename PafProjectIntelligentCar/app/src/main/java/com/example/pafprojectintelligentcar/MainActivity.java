package com.example.pafprojectintelligentcar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    private static final int REQUEST_ENABLE_BT = 0;
    private static final int REQUEST_DISCOVER_BT = 1;
    private static final String TAG = "MainActivity";

    TextView mStatusBlueTv, get_response_text, post_response_text;
    ImageView mBlueIv;
    Button mOnOffBtn, mEnableDiscoverableBtn, mDiscoverBtn, mStartConnectionBtn, get_request_button, post_request_button;
    BluetoothAdapter mAdapter;

    BluetoothConnectionService mBluetoothConnection;
    BluetoothDevice mBTDevice;
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public ArrayList<BluetoothDevice> mBTDevices = new ArrayList<>();
    public DeviceListAdapter mDeviceListAdapter;
    ListView lvNewDevices;

    // Create a BroadcastReceiver1 for ACTION_STATE_CHANGED.
    private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals( mAdapter.ACTION_STATE_CHANGED )) {
                final int state = intent.getIntExtra( BluetoothAdapter.EXTRA_STATE, mAdapter.ERROR );

                switch(state){
                    case BluetoothAdapter.STATE_OFF:
                        Log.d( TAG, "STATE_OFF" );
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d( TAG, "STATE_TURNING_OFF" );
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d( TAG, "STATE_ON" );
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d( TAG, "STATE_TURNING_ON" );
                        break;
                }

            }
        }
    };

    // Create a BroadcastReceiver2 for ACTION_SCAN_MODE_CHANGED.
    private final BroadcastReceiver mBroadcastReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals( mAdapter.ACTION_SCAN_MODE_CHANGED )) {
                int mode = intent.getIntExtra( BluetoothAdapter.EXTRA_SCAN_MODE, mAdapter.ERROR );

                switch(mode){
                    //Device is in Discoverable Mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d( TAG, "Discoverability is enabled" );
                        break;
                    //Device not in discoverable mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d( TAG, "Discoverability is disabled. Able to receive connections" );
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d( TAG, "Discoverability is disabled. Not able to receive connections" );
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d( TAG, "Connecting..." );
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d( TAG, "Connected" );
                        break;
                }

            }
        }
    };

    //Create a BroadcastReceiver3 for ACTION_FOUND
    private final BroadcastReceiver mBroadcastReceiver3 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d( TAG, "ACTION_FOUND" );

            if(action.equals( BluetoothDevice.ACTION_FOUND )){
                BluetoothDevice device = intent.getParcelableExtra( BluetoothDevice.EXTRA_DEVICE );
                mBTDevices.add(device);
                Log.d( TAG, device.getName() + ":" + device.getAddress() );
                mDeviceListAdapter = new DeviceListAdapter( context, R.layout.device_adapter_view, mBTDevices );
                lvNewDevices.setAdapter( mDeviceListAdapter );
            }
        }
    };

    //Create BroadcastReceiver4
    private final BroadcastReceiver mBroadcastReceiver4 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(action.equals( BluetoothDevice.ACTION_BOND_STATE_CHANGED )){
                BluetoothDevice mDevice = intent.getParcelableExtra( BluetoothDevice.EXTRA_DEVICE );
                //3 cases
                //case1 : bonded already
                if(mDevice.getBondState() ==  BluetoothDevice.BOND_BONDED){
                    Log.d(TAG, "BOND_BONDED");
                    mBTDevice = mDevice;
                }
                //case2 : creating a bond
                if(mDevice.getBondState() ==  BluetoothDevice.BOND_BONDING){
                    Log.d(TAG, "BOND_BONDING");
                }
                //case3 : breaking a bond
                if(mDevice.getBondState() ==  BluetoothDevice.BOND_NONE){
                    Log.d(TAG, "BOND_NONE");
                }
            }
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver( mBroadcastReceiver1 );
        unregisterReceiver( mBroadcastReceiver2 );
        unregisterReceiver( mBroadcastReceiver3 );
        unregisterReceiver( mBroadcastReceiver4 );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        mEnableDiscoverableBtn = findViewById( R.id.enableDisableDiscoverableBtn );
        mDiscoverBtn        = findViewById( R.id.findUnpairedDevicesBtn );
        mStartConnectionBtn = findViewById( R.id.startConnectionBtn );
        mOnOffBtn        = findViewById( R.id.OnOffBtn );
        mStatusBlueTv    = findViewById(R.id.statusBluetoothTv );
        mBlueIv          = findViewById( R.id.bluetoothIv );

        mAdapter         = BluetoothAdapter.getDefaultAdapter();

        lvNewDevices     = (ListView) findViewById( R.id.lvNewDevices );
        mBTDevices       = new ArrayList<>();

        lvNewDevices.setOnItemClickListener( MainActivity.this );

        //Broadcast when bond state changes(pairing)
        IntentFilter filter = new IntentFilter( BluetoothDevice.ACTION_BOND_STATE_CHANGED );
        registerReceiver( mBroadcastReceiver4, filter );

        //http buttons
        get_request_button = findViewById( R.id.get_data );
        post_request_button = findViewById( R.id.post_data );

        get_response_text = findViewById(R.id.get_response_data);
        post_response_text = findViewById(R.id.post_response_data);

        //Get_request button click
        get_request_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new SendGetRequest().execute("https://paf-communications-bluetooth.online/messages/cam/");
            }
        });


        //Post_request Button click
        post_request_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JSONObject postData = new JSONObject();
                try {
                    //postData.put("data", post_response_text.getText().toString());
                    postData.put("data", "ffffffffffff00155deffee6894711001a0120500080002d0100800000155deffee618f6c5781ceb7910016979c207cc06110000000007d10000020200000001c5b7005a520c420d966978dffffffc23b7743e0000012000003fe1ed0403ffe3fff400");
                    new SendDeviceDetails().execute("https://paf-communications-bluetooth.online/messages/cam/",
                            postData.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        //check if bluetooth is available or not
        if (mAdapter == null) {
            mStatusBlueTv.setText( "Bluetooth is not available" );
        } else {
            mStatusBlueTv.setText( "Bluetooth is available" );
        }

        //set image according to bluetooth status (On/Off)
        if (mAdapter.isEnabled()) {
            mBlueIv.setImageResource( R.drawable.ic_action_on );
        } else {
            mBlueIv.setImageResource( R.drawable.ic_action_off );
        }

        //ON/OFF Button click
        mOnOffBtn.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableDisableBluetooth();
            }
        } );

        //Enable Discoverable Button click
        mEnableDiscoverableBtn.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableDisableDiscoverable();
            }
        } );

        //Discover Devices Button click
        mDiscoverBtn.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Looking for unpaired devices" );
                showToast( "Looking for unpaired devices" );
                if(mAdapter.isDiscovering()){
                    mAdapter.cancelDiscovery();
                    Log.d( TAG, "Canceling discovery" );
                }

                checkBTPermissions();
                mAdapter.startDiscovery();
                IntentFilter discoverDevicesIntent = new IntentFilter( BluetoothDevice.ACTION_FOUND );
                registerReceiver( mBroadcastReceiver3, discoverDevicesIntent );

            }
        } );

        //Start Connection Button click
        mStartConnectionBtn.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startConnection();
            }
        } );

    }

    //Create method for starting connection
    public void startConnection(){
        startBTConnection( mBTDevice, MY_UUID_SECURE );
    }

    //Start BT connection
    public void startBTConnection(BluetoothDevice device, UUID uuid){
        Log.d( TAG, "Initializing RFCOMM Bluetooth Connection" );

        mBluetoothConnection.startClient( device, uuid );
    }

    //ON/OFF Bluetooth
    public void enableDisableBluetooth() {
        //On Bluetooth click
        if (!mAdapter.isEnabled()){
            showToast( "Turning on Bluetooth" );
            Intent enableBTIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
            startActivityForResult(enableBTIntent, REQUEST_ENABLE_BT );
            mBlueIv.setImageResource( R.drawable.ic_action_on );

            IntentFilter BTIntent = new IntentFilter( BluetoothAdapter.ACTION_STATE_CHANGED );
            registerReceiver( mBroadcastReceiver1, BTIntent );

        }
        //Off Bluetooth click
        if(mAdapter.isEnabled()){
            mAdapter.disable();
            showToast( "Turning off Bluetooth" );
            mBlueIv.setImageResource( R.drawable.ic_action_off );

            IntentFilter BTIntent = new IntentFilter( BluetoothAdapter.ACTION_STATE_CHANGED );
            registerReceiver( mBroadcastReceiver1, BTIntent );
        }
    }


    //Making device discoverable
    public void enableDisableDiscoverable(){
        showToast( "Making device discoverable for 300 seconds " );

        Intent discoverableIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE );
        discoverableIntent.putExtra( BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300 );
        startActivityForResult( discoverableIntent, REQUEST_DISCOVER_BT );

        IntentFilter intentFilter = new IntentFilter( BluetoothAdapter.ACTION_SCAN_MODE_CHANGED );
        registerReceiver( mBroadcastReceiver2, intentFilter );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    //Bluetooth is on
                    mBlueIv.setImageResource(R.drawable.ic_action_on);
                    showToast("Bluetooth is on");
                } else {
                    //user denied to turn bluetooth on
                    mBlueIv.setImageResource(R.drawable.ic_action_off);
                    showToast("Couldn't on Bluetooth");
                }
                break;

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    //Check Bluetooth permissions
    /**
     * This method is required for all devices running API23+
     * Android must programmatically check the permissions for bluetooth. Putting the proper permissions
     * in the manifest is not enough.
     *
     * NOTE: This will only execute on versions > LOLLIPOP because it is not needed otherwise.
     */
    private void checkBTPermissions() {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
            int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            if (permissionCheck != 0) {

                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number
            }
        }else{
            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.");
        }
    }


    //Toast message method
    private void showToast( String message ) {
        Toast.makeText( this, message, Toast.LENGTH_SHORT ).show();
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        //Cancel the discovery first because it's very memory intensive
        mAdapter.cancelDiscovery();

        Log.d(TAG,"You clicked on a device");
        String deviceName = mBTDevices.get(position).getName();
        String deviceAddress = mBTDevices.get(position).getAddress();

        Log.d(TAG,"deviceName" + deviceName);
        Log.d(TAG,"deviceAddress" + deviceAddress);

        //Create the bond
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2){
            Log.d(TAG,"Trying to pair with" + deviceName);
            mBTDevices.get(position).createBond();
            mBTDevice = mBTDevices.get(position);
            mBluetoothConnection = new BluetoothConnectionService( MainActivity.this );
        }

    }

    //http request
    private class SendGetRequest extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            HttpsWithCertificate httpsWithCertificate = new HttpsWithCertificate(getApplicationContext(), R.raw.client_certificate, "PAF2020");
            try {
                final String messagesCAM = httpsWithCertificate.httpGet("https://paf-communications-bluetooth.online/messages/cam/");
                System.out.println(messagesCAM);

                final String messagesDENM = httpsWithCertificate.httpGet("https://paf-communications-bluetooth.online/messages/denm/");
                System.out.println(messagesDENM);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        get_response_text.setText("Data :" + messagesCAM + "\r\n" + messagesDENM);
                    }
                });
                return messagesCAM + "\r\n" + messagesDENM;
            } catch (Exception e) {
                e.printStackTrace();
                return "Error in GET request";
            }
        }
    }

    private class SendDeviceDetails extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            HttpsWithCertificate httpsWithCertificate = new HttpsWithCertificate(getApplicationContext(), R.raw.client_certificate, "PAF2020");
            try {
                httpsWithCertificate.httpPost(params[0], params[1]);
                System.out.println("POST : " + params[1]);
                return "POST successful";
            } catch (Exception e) {
                e.printStackTrace();
                return "Error in POST request";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            Log.e("TAG", result); // this is expecting a response code to be sent from your server upon receiving the POST data
        }
    }
}
