package com.example.dexcomg6;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button listen, send, listDevices;
    ListView listView;
    TextView msg_box, status;
    EditText writeMsg;

    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray;

    SendReceive sendReceive;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    private static final int PERMISSION_REQUEST_CODE = 1234;
    String[] appPermissions = {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.INTERNET, Manifest.permission.BLUETOOTH_ADVERTISE};

    int REQUEST_ENABLE_BLUETOOTH = 1;

    Set<BluetoothDevice> bt;
    private static final String APP_NAME = "DexComG6 App";
    private static final UUID MY_UUID = UUID.fromString("8ce255c0-223a-11e0-ac64-0803450c9a66");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewByIds();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(askPermissions()){
            Toast.makeText(MainActivity.this, "Permissions Granted", Toast.LENGTH_LONG).show();
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            List<String> listPermissionsNeeded = new ArrayList<>();
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), PERMISSION_REQUEST_CODE);
                return;
            }
            if(askPermissions())
                startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }
        makeDiscoverable();
        implementListeners();
    }

    @SuppressLint("MissingPermission")
    private void makeDiscoverable() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);
        Log.i("Log", "Your device is Discoverable ");
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                @SuppressLint("MissingPermission") String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                Log.i("Log", "Detected Device: "+ deviceName);
                try {
                    if(deviceName.startsWith("Dexcom")){
                        bt.add(device);
                        createBond(device);
                    }
                }
                catch (Exception e) {
                    Log.i("Log", "Exception occurred during bonding with device: "+ deviceName);
                    throw new RuntimeException(e);
                }

            }
        }
    };

    public boolean createBond(BluetoothDevice btDevice) throws Exception
    {
        Class<?> bond = Class.forName("android.bluetooth.BluetoothDevice");
        Method createBondMethod = bond.getMethod("createBond");
        Boolean returnValue = (Boolean) createBondMethod.invoke(btDevice);
        return Boolean.TRUE.equals(returnValue);
    }

    public boolean askPermissions(){
        List<String> listPermissionsNeeded = new ArrayList<>();
        for(String perm: appPermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(perm);
            }
        }
        if(!listPermissionsNeeded.isEmpty()){
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if( requestCode == PERMISSION_REQUEST_CODE){
            HashMap<String, Integer> permissionResults = new HashMap<>();
            int deniedCount =0;
            for( int i=0; i< grantResults.length; i++){
                if(grantResults[i] == PackageManager.PERMISSION_GRANTED){
                    permissionResults.put(permissions[i], grantResults[i]);
                    deniedCount++;
                }
            }
            if(deniedCount==0){
                Toast.makeText(MainActivity.this, "Permission granted", Toast.LENGTH_LONG);
            }
            else{
                for(Map.Entry<String, Integer> entry : permissionResults.entrySet()){
                    String permName = entry.getKey();
                    int permResult = entry.getValue();

                    if(ActivityCompat.shouldShowRequestPermissionRationale(this, permName)){
                        new AlertDialog.Builder(this)
                                .setTitle("Permission")
                                .setCancelable(false)
                                .setMessage("Please provide the required permissions for using all features")
                                .setPositiveButton("Proceed", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                }).create().show();
                    } else{
                        new AlertDialog.Builder(this)
                                .setTitle("Permission Denied")
                                .setCancelable(false)
                                .setMessage("Permission Denied")
                                .setCancelable(false)
                                .setMessage(" You have denied some Permissions. Allow all permissions at [Settings] > [Permissions]")
                                .setPositiveButton("Go to settings", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", getPackageName(), null));
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        finish();
                                    }
                                }).setNegativeButton("No, Exit App", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                }).create().show();
                        break;
                    }
                }
            }
        }
    }

    private void implementListeners() {

        listDevices.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onClick(View view) {

                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(receiver, filter);
                bluetoothAdapter.startDiscovery();

                // TODO change below line of code: U need to get all devices and not just paired devices
//                Set<BluetoothDevice> bt = bluetoothAdapter.getBondedDevices();


                String[] strings = new String[bt.size()];
                btArray = new BluetoothDevice[bt.size()];
                int index = 0;


                if (bt.size() > 0) {
                    for (BluetoothDevice device : bt) {
                        if ( device.getAddress().startsWith("Dexcom") ) {
                            btArray[index] = device;
                            strings[index] = device.getName();
                            index++;
                        }
                    }
                    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, strings);
                    listView.setAdapter(arrayAdapter);
                }
            }
        });

        listen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ServerClass serverClass = new ServerClass();
                serverClass.start();
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                ClientClass clientClass = new ClientClass(btArray[i]);
                clientClass.start();

                status.setText("Connecting");
            }
        });

//        send.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
////                String string = String.valueOf(writeMsg.getText());
////                sendReceive.write(string.getBytes());
//            }
//        });
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            msg.what = 5;
            switch (msg.what) {
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting");
                    break;
                case STATE_CONNECTED:
                    status.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection Failed");
                    break;
                case STATE_MESSAGE_RECEIVED:
                    // byte[] readBuff = (byte[]) msg.obj;
                    // String tempMsg = new String(readBuff, 0, msg.arg1);
//                    msg_box.setText(tempMsg);
                    msg_box.setText("Glucose reading: "+ Math.random()*100);
                    break;
            }
            return true;
        }
    });

    private void findViewByIds() {
        listen = (Button) findViewById(R.id.listen);
        // send = (Button) findViewById(R.id.send);
        listView = (ListView) findViewById(R.id.listview);
        msg_box = (TextView) findViewById(R.id.msg);
        status = (TextView) findViewById(R.id.status);
        //writeMsg = (EditText) findViewById(R.id.writemsg);
        listDevices = (Button) findViewById(R.id.listDevices);
    }


    private class ServerClass extends Thread {
        private BluetoothServerSocket serverSocket;

        //        @SuppressLint("MissingPermission")
        @SuppressLint("MissingPermission")
        public ServerClass() {
            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run()
        {
            BluetoothSocket socket=null;

            while (socket==null)
            {
                try {
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTING;
                    handler.sendMessage(message);

                    socket=serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }

                if(socket!=null)
                {
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTED;
                    handler.sendMessage(message);

                    sendReceive=new SendReceive(socket);
                    sendReceive.start();

                    break;
                }
            }
        }
    }

    private class ClientClass extends Thread
    {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        @SuppressLint("MissingPermission")
        public ClientClass (BluetoothDevice device1)
        {
            device=device1;

            try {
                socket=device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @SuppressLint("MissingPermission")
        public void run()
        {
            try {
                socket.connect();
                Message message=Message.obtain();
                message.what=STATE_CONNECTED;
                handler.sendMessage(message);

                sendReceive=new SendReceive(socket);
                sendReceive.start();

            } catch (IOException e) {
                e.printStackTrace();
                Message message=Message.obtain();
                message.what=STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }

    private class SendReceive extends Thread
    {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceive (BluetoothSocket socket)
        {
            bluetoothSocket=socket;
            InputStream tempIn=null;
            OutputStream tempOut=null;

            try {
                tempIn=bluetoothSocket.getInputStream();
                tempOut=bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Log.wtf("Log", "InputStream: " + tempIn.toString());
            inputStream=tempIn;
            outputStream=tempOut;
        }

        public void run()
        {
            // Read DexCOM readings every 3 minutes () sleep in between
            byte[] buffer=new byte[1024];
            int bytes;

            while (true)
            {
                try {
                    bytes=inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes)
        {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}