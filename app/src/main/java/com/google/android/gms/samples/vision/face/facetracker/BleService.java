package com.google.android.gms.samples.vision.face.facetracker;


import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BleService extends Service {
    private final static String TAG = "BLEtest";//"BLE_NFC";
    private static final String GROVE_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISTIC_TX = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private static final String CHARACTERISTIC_RX = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private static final long SCAN_PERIOD = 2500; // 5 seconds
    private static final String ACK = "A";
    private BluetoothAdapter mBluetoothAdapter;// our local adapter
    private BluetoothGattService mBluetoothGattService; // service// on_mBlueoothGatt
    private BluetoothManager mBluetoothManager;
    private static List<BluetoothDevice> mDevices = new ArrayList<BluetoothDevice>();// discovered_devices_in_range
    private Map<String, BluetoothGatt> connectedDeviceMap;
    private Map<String,TimerAndCounting>actMap = new HashMap<String,TimerAndCounting>();
    private	List<String> DEVICE_NAMES = new ArrayList<>();
    private Handler mHandler;

    private  Handler toastHamdler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            String _msg = (String)msg.obj;
            Toast.makeText(getBaseContext(), _msg, Toast.LENGTH_SHORT).show();
        }
    };

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){
                int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                switch (blueState) {
                    case BluetoothAdapter.STATE_ON:
                        Log.e("MarkBleScanLeDevice", "STATE_ON");
                        toastHamdler.obtainMessage(0,"BluetoothAdapter Is Open").sendToTarget();
                        scanLeDevice();
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        Log.e("MarkBleScanLeDevice", "STATE_OFF");
                        toastHamdler.obtainMessage(0,"Please Open BluetoothAdapter").sendToTarget();
                        mDevices.clear();
                        break;
                    default:
                        break;
                }
            }
        }
    };

    public void setHandler(Handler handler){
        this.mHandler = handler;
    }

    public void setBleDeviceNames(List<String> _list){
        for (int i=0;i<_list.size();i++)
            DEVICE_NAMES.add(_list.get(i));

        for (int i=0;i<_list.size();i++) {
            actMap.put(_list.get(i), new TimerAndCounting());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public class LocalBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        for(int i=0;i<mDevices.size();i++) {
            String name = mDevices.get(i).getName();
            if (connectedDeviceMap.containsKey(name)) {
                BluetoothGatt bluetoothGatt = connectedDeviceMap.get(name);
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                }
            }
        }
        unregisterReceiver(broadcastReceiver);
        connectedDeviceMap.clear();
        mDevices.clear();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onCreate() {
        connectedDeviceMap = new HashMap<String,BluetoothGatt>();
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "BLE not supported on this device", Toast.LENGTH_SHORT).show();
            return;
        }
        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        // Open settings if Bluetooth isn't enabled
        if (!mBluetoothAdapter.isEnabled()) {
            toastHamdler.obtainMessage(0,"Please Open BluetoothAdapter").sendToTarget();
            //Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        }
        searchForDevices();////StartBluetooth
        Log.e("MarkBleScanLeDevice","onCreate");
    }

    private void searchForDevices() {
        scanLeDevice();
    }

    private void scanLeDevice() {
        Log.e("Mark1", "scanLeDevice");
        new Thread() {

            @Override
            public void run() {
                if(!mBluetoothAdapter.isEnabled())return;

                mBluetoothAdapter.startLeScan(mLeScanCallback);
                try {
                    Thread.sleep(SCAN_PERIOD);

                } catch (InterruptedException e) {
                    e.printStackTrace();

                }
                mBluetoothAdapter.stopLeScan(mLeScanCallback);

                findGroveBLE();
            }
        }.start();
    }

    private void findGroveBLE() {
        Log.e("MarkBleScanLeDevice","findGroveBLE");
        if (mDevices.size() != (DEVICE_NAMES.size())) {
            searchForDevices();
            disconnectDevice();
            Log.e("MarkBleScanLeDevice","mDevices size != DEVICE_NAMES size");
        }
        else
            toastHamdler.obtainMessage(0,"BLE Connected Success").sendToTarget();

        Log.e("MarkBleScanLeDevice","mDevices size = "+mDevices.size());
        for (int i = 0; i < mDevices.size(); i++) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mDevices.get(i).getAddress());
            if (device != null) {
                int connectionState = mBluetoothManager.getConnectionState(device, BluetoothProfile.GATT);
                if (connectionState == BluetoothProfile.STATE_DISCONNECTED) {
                    // connect your device
                    device.connectGatt(this, false, mGattCallback);
                } else if (connectionState == BluetoothProfile.STATE_CONNECTED) {
                    // already connected . send Broadcast if needed
                }
            }
        }
    }

    private void disconnectDevice() {
        String disconnectDeviceName = null;
        if(mDevices.size()==0){
            for(int i=0;i<DEVICE_NAMES.size();i++)
                disconnectDeviceName+=DEVICE_NAMES.get(i)+",";
        }
        else {
            for (int i = 0; i < DEVICE_NAMES.size(); i++) {
                String name = DEVICE_NAMES.get(i);
                for (int j = 0; j < mDevices.size(); j++) {
                    if (name.equals(mDevices.get(j).getName()))
                        disconnectDeviceName += name + ",";
                }
            }
        }

        if (disconnectDeviceName != null) {
            disconnectDeviceName = disconnectDeviceName.substring(4, disconnectDeviceName.length() - 1);
            disconnectDeviceName = "Disconnect Device : " + disconnectDeviceName;
            toastHamdler.obtainMessage(0, disconnectDeviceName).sendToTarget();
        }

    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            if (device != null && DEVICE_NAMES.size()!=0) {

                if (mDevices.indexOf(device) == -1)// to avoid duplicate entries
                {
                    for (int i=0;i<DEVICE_NAMES.size();i++) {
                        if (DEVICE_NAMES.get(i).equals(device.getName())) {
                            mDevices.add(device);
                        }
                    }
                }
            }
        }
    };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {

            BluetoothDevice device = gatt.getDevice();
            String name = device.getName();

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!connectedDeviceMap.containsKey(name)) {
                    connectedDeviceMap.put(name, gatt);
                }
                gatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mDevices.remove(device);
                if (connectedDeviceMap.containsKey(name)){
                    BluetoothGatt bluetoothGatt = connectedDeviceMap.get(name);
                    if( bluetoothGatt != null ){
                        bluetoothGatt.close();
                    }
                    connectedDeviceMap.remove(name);
                }
                searchForDevices();
                Log.e("MarkBleScanLeDevice","onConnectionStateChange");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                BluetoothDevice device = gatt.getDevice();
                String name = device.getName();

                if (connectedDeviceMap.containsKey(name)) {
                    BluetoothGatt bluetoothGatt = connectedDeviceMap.get(name);

                    List<BluetoothGattService> gattServices = bluetoothGatt.getServices();

                    for (BluetoothGattService gattService : gattServices) {

                        if (GROVE_SERVICE.equals(gattService.getUuid().toString())) {
                            mBluetoothGattService = gattService;
                        }
                    }
                    BluetoothGattCharacteristic GattCharacteristic_RX = mBluetoothGattService.getCharacteristic(UUID.fromString(CHARACTERISTIC_RX));
                    bluetoothGatt.setCharacteristicNotification(GattCharacteristic_RX, true);
                }
            }
        }
        //read
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            BluetoothDevice device = gatt.getDevice();
            String name = device.getName();
            Log.e("TAG1", "[NFC data]: " + name);
            if (connectedDeviceMap.containsKey(name)) {
                byte[] data = characteristic.getValue();

                try {
                    String msg = new String(data, "UTF-8");
                    if(msg.equals(ACK)) {
                        if (actMap.containsKey(name)) {
                            Log.e("MarkBLETest", "A "+name);
                            TimerAndCounting TAC = actMap.get(name);
                            if (TAC.timer != null) {
                                Log.e("MarkBLETest", "cancel");
                                TAC.Counting = 0;
                                TAC.timer.cancel();
                                TAC.timer = null;
                                actMap.put(name, TAC);
                            }
                        }
                    }
                    else {
                        mHandler.obtainMessage(0, name + "," + msg).sendToTarget();
                        writeCharacteristic(name,ACK);
                    }
                    Log.e("MarkBLETestttttt", "[NFC data]: " + msg);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    //sned
    public void writeCharacteristic(String _DeviceName, String X) {
        Log.e("TAG1", X);
        if (connectedDeviceMap.containsKey(_DeviceName))
        {
            try {
                BluetoothGatt bluetoothGatt = connectedDeviceMap.get(_DeviceName);

                String strCommand = X;
                BluetoothGattCharacteristic characteristicTx =
                        bluetoothGatt.getService(UUID.fromString(GROVE_SERVICE)).getCharacteristic(UUID.fromString(CHARACTERISTIC_TX));

                characteristicTx.setValue(strCommand.getBytes());

                if (bluetoothGatt.writeCharacteristic(characteristicTx)) {
                    //Log.e("MarkBLETest","Send");
                    if (!X.equals(ACK))
                        //ActMethon(_DeviceName, X);
                    Log.i(TAG, "send LED true");
                } else
                    Log.i(TAG, "send LED false");

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else
            Log.e("MarkBLE", "No Device");
    }

    private void ActMethon(final String _DeviceName, final String _msg){
        if(actMap.containsKey(_DeviceName)){
            final TimerAndCounting TAC = actMap.get(_DeviceName);
            if(TAC.timer==null) {
                TAC.timer = new Timer();
                TAC.timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.e("MarkBLETestttttt", "Loop");
                        writeCharacteristic(_DeviceName, _msg);
                        if (++TAC.Counting == 10) {
                            TAC.timer.cancel();
                            TAC.timer = null;
                            TAC.Counting = 0;
                            actMap.put(_DeviceName, TAC);
                        }
                    }
                }, 1000, 1000);
                actMap.put(_DeviceName, TAC);
            }

        }
    }

    public class TimerAndCounting{
        public Timer timer;
        public int Counting = 0;
    }
}
