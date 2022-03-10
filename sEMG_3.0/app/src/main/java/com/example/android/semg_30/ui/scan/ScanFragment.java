package com.example.android.semg_30.ui.scan;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.android.semg_30.R;
import com.example.android.semg_30.databinding.FragmentScanBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScanFragment extends Fragment {

    private static final long SCAN_PERIOD = 10000; // 10 seconds
    private static final int REQUEST_ENABLE_BT = 1;
    private static final String TAG = ScanFragment.class.getSimpleName();

    private FragmentScanBinding binding;

    private LinearLayout mDeviceList;
    private ArrayList<BluetoothDevice> mBLEObjectList;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBLEScanner;
    private Handler mHandler;

    private boolean mListEmpty;
    private boolean mScanning;



    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentScanBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // example link to viewmodel
        final TextView textView = binding.fragmentScanTitle;
        // get reference to devicelist
        mDeviceList = binding.deviceList;
        mBLEObjectList = new ArrayList<BluetoothDevice>();

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mHandler = new Handler();
        mListEmpty = true;
        mScanning = false;

        // Tell user if BLE is not supported
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(getContext(), getString(R.string.ble_not_supported),
                    Toast.LENGTH_SHORT).show();
            setButtonsClickable(false);
        }

        // add onclicklisteners
        binding.buttonStartScan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                requestStartScan();
            }
        });
        binding.buttonStopScan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                requestStopScan();
            }
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            // TODO ask to turn on bluetooth and if it is turned on ask to turn it on
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            setButtonsClickable(false);
            Toast.makeText(getContext(), getString(R.string.enable_ble_pls),
                    Toast.LENGTH_SHORT).show();
            return;
        } else if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
            // attempt to get bluetooth adapter again
            mBluetoothAdapter = ((BluetoothManager) getContext().
                    getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    public void onPause() {
        super.onPause();
        scanLeDevice(false);
        clearDeviceList();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

//    private void scanLeDevice(final boolean enable) {
//        if (enable) {
//            // Stops scanning after a pre-defined scan period.
//            mHandler.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    mScanning = false;
//                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
//                }
//            }, SCAN_PERIOD);
//
//            mScanning = true;
//            mBluetoothAdapter.startLeScan(mLeScanCallback);
//        } else {
//            mScanning = false;
//            mBluetoothAdapter.stopLeScan(mLeScanCallback);
//        }
//    }

    private void scanLeDevice(final boolean enable) {
        List<ScanFilter> filters = new ArrayList<ScanFilter>();
        ScanFilter.Builder filterBuilder = new ScanFilter.Builder();

//        // this is not working?
//        ParcelUuid uuid = ParcelUuid.fromString("0000f00d-1212-efde-1523-785fef13d123");
//        filters.add(filterBuilder.setServiceUuid(uuid).build());
        filters.add(filterBuilder.build());

        ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
        ScanSettings settings = settingsBuilder.
                setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();

        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
//                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    mBLEScanner.stopScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
//            mBluetoothAdapter.startLeScan(mLeScanCallback);
            mBLEScanner.startScan(filters, settings, mLeScanCallback);
        } else {
            mScanning = false;
            mBLEScanner.stopScan(mLeScanCallback);
        }
    }

//    // Device scan callback.
//    private BluetoothAdapter.LeScanCallback mLeScanCallback =
//            new BluetoothAdapter.LeScanCallback() {
//
//        @Override
//        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
//            getActivity().runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    addBluetoothDevice(device, rssi);
//                }
//            });
//        }
//    };

    // Device scan callback.
    private ScanCallback mLeScanCallback = new ScanCallback() {
        public void onScanResult(int callbackType, ScanResult result) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addBluetoothDevice(result.getDevice(), result.getRssi());
                }
            });
        }
    };

    public void addBluetoothDevice(BluetoothDevice device, int rssi) {
        if (!mBLEObjectList.contains(device)) {
            int index = mBLEObjectList.size();
            mBLEObjectList.add(device);

            String name = (device.getName() != null) ?
                    device.getName() : "Unknown Device";
            if (name.contains("EMG") || name.contains("emg")) {
                ViewGroup newListItem = generateDeviceView(name,
                        device.getAddress(), index, rssi);
                mDeviceList.addView(newListItem);
            }
        }

        setListEmpty(false);
    }

    private void setListEmpty(boolean b) {
        mListEmpty = b;
//        if (b) {
//            binding.textViewEmptyListMessage.setVisibility(View.INVISIBLE);
//        } else {
//            binding.textViewEmptyListMessage.setVisibility(View.VISIBLE);
//        }
    }


    private ViewGroup generateDeviceView(String deviceName, String deviceAddress, int index, int rssi) {
        LinearLayout viewDeviceItem = new LinearLayout(getContext());
        viewDeviceItem.setOrientation(LinearLayout.VERTICAL);

        TextView textDeviceName = new TextView(getContext());
        TextView textDeviceAddress = new TextView(getContext());
        TextView textDeviceRSSI = new TextView(getContext());

        textDeviceName.setText(deviceName);
        textDeviceName.setTextSize(18);
        textDeviceAddress.setText(String.format("Address: %s", deviceAddress));
        textDeviceRSSI.setText(String.format("RSSI: %d", rssi));

        viewDeviceItem.addView(textDeviceName);
        viewDeviceItem.addView(textDeviceAddress);
        viewDeviceItem.addView(textDeviceRSSI);
        viewDeviceItem.setTag(new Integer(index));

        viewDeviceItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onDeviceItemClicked(view);
            }
        });
        viewDeviceItem.setClickable(true);

        return (ViewGroup) viewDeviceItem;
    }

    // TODO when a device is clicked
    public void onDeviceItemClicked(View v) {
        int deviceIndex = (Integer) v.getTag();
        Log.i(TAG, String.format("clicked device w index %d", deviceIndex));
        // TODO go to SelectCharacteristicFragment
        toSelectCharacteristic(v, mBLEObjectList.get(deviceIndex));
    }

    public void clearDeviceList() {
        mDeviceList.removeAllViewsInLayout();
        mBLEObjectList.clear();
            setListEmpty(true);
        }

        public void requestStartScan() {
        scanLeDevice(true);
    }

    public void requestStopScan() {
        scanLeDevice(false);
    }

    public void setButtonsClickable(boolean value) {
        binding.buttonStartScan.setClickable(value);
        binding.buttonStopScan.setClickable(value);
    }

    public void toSelectCharacteristic(View v, BluetoothDevice device) {
        String name = (device.getName() != null) ?
                device.getName() : getString(R.string.unknown_device);
        String address = (device.getAddress() != null) ?
                device.getAddress() : getString(R.string.unknown_address);
        ScanFragmentDirections.ActionNavigationScanToNavigationSelectDevice action =
                ScanFragmentDirections.actionNavigationScanToNavigationSelectDevice(name, address);
        Navigation.findNavController(v).navigate(action);
    }

    // UNCOMMENT THE FOLLOWING IF U WANNA TEST NAVIGATION WO BLUETOOTH
//    private ScanViewModel scanViewModel;
//    private FragmentScanBinding binding;
//
//    public View onCreateView(@NonNull LayoutInflater inflater,
//                             ViewGroup container, Bundle savedInstanceState) {
//        scanViewModel =
//                new ViewModelProvider(this).get(ScanViewModel.class);
//
//        binding = FragmentScanBinding.inflate(inflater, container, false);
//        View root = binding.getRoot();
//
//        final TextView textView = binding.fragmentScanTitle;
//        scanViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
//            @Override
//            public void onChanged(@Nullable String s) {
//                textView.setText(s);
//            }
//        });
//        return root;
//    }
//
//    @Override
//    public void onDestroyView() {
//        super.onDestroyView();
//        binding = null;
//    }
}