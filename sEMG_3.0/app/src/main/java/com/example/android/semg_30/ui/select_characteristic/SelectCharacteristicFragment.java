package com.example.android.semg_30.ui.select_characteristic;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.navigation.Navigation;

import com.example.android.semg_30.BLEService;
import com.example.android.semg_30.R;
import com.example.android.semg_30.SampleGattAttributes;
import com.example.android.semg_30.databinding.FragmentSelectCharacteristicBinding;
import com.example.android.semg_30.ui.scan.ScanFragmentDirections;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SelectCharacteristicFragment extends Fragment {

    private static final String TAG = SelectCharacteristicFragment.class.getSimpleName();
    private FragmentSelectCharacteristicBinding binding;

    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    // GATT variables
    private BLEService mBLEService;
    private ArrayList<HashMap<String, String>> mGattServiceData =
            new ArrayList<HashMap<String,String>>();
    private ArrayList<ArrayList<HashMap<String, String>>> mGattCharacteristicData =
            new ArrayList<ArrayList<HashMap<String, String>>>();
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;

    public final String LIST_NAME = "NAME";
    public final String LIST_UUID = "UUID";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = FragmentSelectCharacteristicBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // if arguments sent from ScanFragment, set them
        // arguments from ScanFragment
        Bundle args = getArguments();
        if (args.size() > 0) {
            mDeviceName = args.getString("device_name") != null ?
                    args.getString("device_name") : getString(R.string.unknown_device);
            mDeviceAddress = args.getString("device_address") != null ?
                    args.getString("device_address") : getString(R.string.unknown_address);
        } else {
            mDeviceName = getString(R.string.unknown_device);
            mDeviceAddress = getString(R.string.unknown_address);
        }

        Log.i(TAG, String.format("n: %d, add: %d", mDeviceName.length(), mDeviceAddress.length()));

        binding.textViewDeviceName.setText(String.format("Device Name: %s",mDeviceName));
        binding.textViewDeviceAddress.setText(String.format("Device Address: %s",mDeviceAddress));

        // bind BLEService
        Intent gattServiceIntent = new Intent(getContext(), BLEService.class);
        Log.i(TAG, "going to call bleservice bindservice");
        getContext().bindService(gattServiceIntent,
                mServiceConnection, Context.BIND_AUTO_CREATE);

        // button to manually connect
        binding.buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mNotifyCharacteristic != null) {
                    toMeasureFragment(getView(), mNotifyCharacteristic);
                }
            }
        });

        // expandable list
        binding.gattServicesListView.setOnChildClickListener(servicesListClickListner);

        return root;
    }


    // Code to manage Service lifecycle.
    public final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBLEService = ((BLEService.LocalBinder) service).getService();
            if (!mBLEService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                return;
            }
            // Automatically connects to the device upon successful start-up initialization.
            if (mDeviceAddress != null && mDeviceAddress != getString(R.string.unknown_address)) {
                Log.i(TAG, String.format("BLEService Connected, connecting to address %s",
                        mDeviceAddress));
                mBLEService.connect(mDeviceAddress);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBLEService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BLEService.ACTION_GATT_CONNECTED.equals(action)) {
                binding.textViewState.setText("GATT CONNECTED");
                mConnected = true;
            } else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                binding.textViewState.setText("GATT DISCONNECTED");
                mConnected = false;
            } else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBLEService.getSupportedGattServices());
                binding.textViewState.setText("GATT SERVICES DISCOVERED");
            } else if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) {
                binding.textViewState.setText("DATA AVAILABLE FROM CHARACTERISTIC");
                String dataString = intent.getStringExtra(BLEService.EXTRA_DATA);
                byte[] data = dataString.getBytes();
                displayData(dataString);
            }
        }
    };

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        // called when ACTION_GATT_SERVICES_DISCOVERED
        // oh god TODO
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = "Unknown Service";
        String unknownCharaString = "Unknown Characteristic";

        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available characteristics
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
        mGattServiceData = gattServiceData;
        mGattCharacteristicData = gattCharacteristicData;

        if (gattServiceData == null) { return; }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                getContext(),
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        binding.gattServicesListView.setAdapter(gattServiceAdapter);
    }

    private void displayData(byte[] data) {
        if (data != null) {
            StringBuilder sb = new StringBuilder();
            for (byte b : data) {
                sb.append(String.format("%02X ", b));
            }
//            System.out.println(sb.toString());
            binding.textViewTempData.setText(sb.toString());
        }
    }

    private void displayData(String data) {
        if (data != null) {
//            System.out.println(sb.toString());
            byte[] dataB = data.getBytes(StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (byte b : dataB) {
                sb.append(String.format("%02X ", b));
            }
//            binding.textViewTempData.setText(data);
            binding.textViewTempData.setText(mNotifyCharacteristic.get);
        }
    }

    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
        @Override
        public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                    int childPosition, long id) {
            if (mGattCharacteristics != null) {
                final BluetoothGattCharacteristic characteristic =
                        mGattCharacteristics.get(groupPosition).get(childPosition);
                final int charaProp = characteristic.getProperties();
//                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
//                            // If there is an active notification on a characteristic, clear
//                            // it first so it doesn't update the data field on the user interface.
//                            if (mNotifyCharacteristic != null) {
//                                mBluetoothLeService.setCharacteristicNotification(
//                                        mNotifyCharacteristic, false);
//                                mNotifyCharacteristic = null;
//                                Log.i(TAG, String.format("%d is READ characteristic", charaProp));
//                            }
//                            mBluetoothLeService.readCharacteristic(characteristic);
//                        }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristic;
                    // change descriptors to receive notifications
                    List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
                    if (descriptors.size() > 0) {
                        Log.i(TAG, String.format("%d descriptors obtained", descriptors.size()));
                        for (int i = 0; i < descriptors.size(); i++) {
                            BluetoothGattCharacteristic currentCharacteristic = descriptors.get(i).getCharacteristic();
                            int properties = currentCharacteristic.getProperties();

                            byte[] value;
                            if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                            } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                                value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
                            } else {
                                Log.e(TAG, String.format("ERROR: Characteristic %s does not have notify or indicate property", characteristic.getUuid()));
                                return false;
                            }

                            // send to BLE Service to have GATT write notify service to this characteristic
                            if (value == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) {
                                mBLEService.setDescriptorValue(descriptors.get(i),value);
                            }
                        }
                    } else {
                        Log.i(TAG, "no descriptors for this characteristic");
                    }
                    mBLEService.setCharacteristicNotification(
                            characteristic, true);
                }
                mBLEService.readCharacteristic(characteristic);
                return true;
            }
            return false;
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void toMeasureFragment(View v, BluetoothGattCharacteristic characteristic) {
        String name = mDeviceName;
        String address = mDeviceAddress;

        SelectCharacteristicFragmentDirections.ActionNavigationSelectDeviceToNavigationMeasure action =
                SelectCharacteristicFragmentDirections.actionNavigationSelectDeviceToNavigationMeasure();
        action.setDeviceName(name);
        action.setDeviceAddress(address);
        action.setCharacteristic(mNotifyCharacteristic);
        Navigation.findNavController(v).navigate(action);
    }

    @Override
    public void onResume() {
        super.onResume();
        getContext().registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBLEService != null && mDeviceAddress != null && mDeviceAddress != getString(R.string.unknown_address)) {
            final boolean result = mBLEService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        getContext().unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unbindService(mServiceConnection);
        mBLEService = null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}