package com.example.android.semg_30.ui.measure;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.android.semg_30.BLEService;
import com.example.android.semg_30.R;
import com.example.android.semg_30.SEMGData;
import com.example.android.semg_30.databinding.FragmentMeasureBinding;
import com.example.android.semg_30.ui.graph_display.GraphDisplayFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

public class MeasureFragment extends Fragment {
    public final int PACKETS_PER_GRAPH_UPDATE = 1; // 120 data points per packet
    private final String TAG = MeasureFragment.class.getSimpleName();
    private final int MAX_MILLISECONDS = 60000; // a minute]

    private final int SAMPLE_RATE = 800;
    private FragmentMeasureBinding binding;

    private BLEService mBLEService;
    private GraphDisplayFragment mGraphDisplayFragment;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothGattCharacteristic mCharacteristic;
    private Handler mHandler;
    private Handler mHandler2;
    private Handler mHandlerGraph;
    private Handler mHandlerTimer;
    private String mFileName;
    private Context mContext;

    private FileOutputStream mOutputStreamWrite;
    private FileOutputStream mOutputStreamAppend;

    private Queue<byte[]> mDataQueue;
    private Queue<String> mDataQueueString;
    private String mTempDataString;
    private FileOutputStream mOutputStream;
    private boolean measurementDone = true;
    private int mNumChannels;

    private int mFlag = 0;
    private int mIndex = 0;
    private int mCounter = 0;
    private int mSeconds = 0;

    private boolean mDoLivePlot = false;

    Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread th, Throwable ex) {
            Log.e(TAG, "Uncaught exception: " + ex);
        }
    };

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMeasureBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        Bundle args = getArguments();
        if (args != null && args.size() > 0) {
            mDeviceName = MeasureFragmentArgs.fromBundle(getArguments()).getDeviceName();
            mDeviceAddress = MeasureFragmentArgs.fromBundle(getArguments()).getDeviceAddress();
            mCharacteristic = MeasureFragmentArgs.fromBundle(getArguments()).getCharacteristic();
        }

        mHandler = new Handler();
        mHandler2 = new Handler();
        mHandlerGraph = new Handler();
        mHandlerTimer = new Handler();
        mOutputStream = null;
        mTempDataString = "";
        mDataQueue = new LinkedList<byte[]>();
        mDataQueueString = new LinkedList<String>();
        mNumChannels = 16;
        mContext = getContext();

        // bind BLEService
        Intent gattServiceIntent = new Intent(getContext(), BLEService.class);
        getContext().bindService(gattServiceIntent,
                mServiceConnection, Context.BIND_AUTO_CREATE);

        binding.buttonStartMeasurement.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMeasurement();
            }
        });
        binding.buttonStopMeasurement.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopMeasurement();
            }
        });
        binding.buttonToPostprocess.setClickable(false);
        binding.buttonToPostprocess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MeasureFragmentDirections.ActionNavigationMeasureToNavigationPostprocess action =
                        MeasureFragmentDirections.actionNavigationMeasureToNavigationPostprocess();
                action.setFileName(mFileName);
                Navigation.findNavController(getView()).navigate(action);
            }
        });
        binding.checkBoxLivePlot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                    mDoLivePlot = true;
                else
                    mDoLivePlot = false;
            }
        });

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mGraphDisplayFragment =
                (GraphDisplayFragment) getChildFragmentManager().
                        findFragmentById(R.id.graphDisplayFragmentMeasure);
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
                try {
                    mBLEService.connect(mDeviceAddress);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Cannot connect to device, " +
                            "please try scanning for device again", Toast.LENGTH_LONG);
                }
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
                binding.textViewConnectionState.setText("GATT CONNECTED");
            } else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                binding.textViewConnectionState.setText("GATT DISCONNECTED");
                stopMeasurement();
            } else if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) {
                binding.textViewConnectionState.setText("DATA AVAILABLE FROM CHARACTERISTIC");
                if (!measurementDone) {
                    String dataString = intent.getStringExtra(BLEService.EXTRA_DATA);
//                    byte[] data = dataString.getBytes();
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            handleData2(dataString);
                        }
                    });
                }
            }
        }
    };

    private void handleData(byte[] data) {
        // if we format the data such that we read every single byte, it causes disconnect
        // simply append to file and postprocess the data later
        mDataQueue.add(data);

        // t1: iterate over bytes to populate plot and store in temporary string
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
            byte[] currentData = mDataQueue.poll();
            if (currentData != null) {
                for (byte b : currentData) {
                    if (mFlag >= mNumChannels) {
                        mFlag = 0;
                        mIndex++;
                    }
//                        mTempDataString.concat(String.format("%02X\n", b));
                    createUpdateFile(mFileName, String.format("%02X\n", b), true);

                    if (mDoLivePlot && mIndex % 100 == 0) {
                        int value = b & 0xFF;
                        try {
                            mGraphDisplayFragment.insertTempDataPoint(
                                    ((double)(mIndex + 1) * (1.0 / SAMPLE_RATE)), value, mFlag);
                        } catch (ConcurrentModificationException e) {

                        }
                    }
                    mFlag++;
                }
            }
            }
        });
        t1.setUncaughtExceptionHandler(h);
        t1.start();

        if (mDoLivePlot && mCounter >= PACKETS_PER_GRAPH_UPDATE) {
            mCounter = 0;
            // t2: live plot
            Thread t2 = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mGraphDisplayFragment.livePlotFromTempData();
                        mGraphDisplayFragment.scrollToEndAll();
                    } catch (ConcurrentModificationException e) {

                    }
                }
            });
            t2.setPriority(Thread.MIN_PRIORITY);
            t2.setUncaughtExceptionHandler(h);
            getActivity().runOnUiThread(t2);

        } else {
            mCounter++;
        }
    }

    private void handleData(String data) {
        mDataQueueString.add(data);
        String currentData = mDataQueueString.poll();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
            if (currentData != null) {
                createUpdateFile(mFileName, data + "\n", true);
            }
            }
        });

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
            if (currentData != null) {
                String trimData = currentData.trim();
                String[] dataArr = currentData.split(" ");
                int dataLength = dataArr.length - 1;
                for (int i = 0; i<dataLength; i+=2) {
                    String b = dataArr[i+1] + dataArr[i];
                    if (mFlag >= mNumChannels) {
                        mFlag = 0;
                        mIndex++;
                    }

                    if (mIndex % 5 == 0) {
//                            int value = Byte.valueOf(b, 16);
                        int value = Integer.parseInt(b, 16);
                        try {
                            mGraphDisplayFragment.insertTempDataPoint(
                                    ((double)(mIndex + 1) * (1.0 / SAMPLE_RATE)), value, mFlag);
                        } catch (ConcurrentModificationException e) {

                        }
                    }
                    mFlag++;
                }
            }

            if (mDoLivePlot && mCounter >= PACKETS_PER_GRAPH_UPDATE) {
                mCounter = 0;
                // t3: live plot
                Thread t3 = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mGraphDisplayFragment.livePlotFromTempData();
                            mGraphDisplayFragment.scrollToEndAll();
                        } catch (ConcurrentModificationException e) {

                        }
                    }
                });
                t3.setPriority(Thread.MIN_PRIORITY);
                t3.setUncaughtExceptionHandler(h);
                t3.start();
            } else {
                mCounter++;
            }
            }
        });

        t1.start();
        if (mDoLivePlot) {
            t2.start();
        }
    }

    public void handleData2(String data) {
        mDataQueueString.add(data);
        String currentData = mDataQueueString.poll();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
            if (currentData != null) {
                createUpdateFile(mFileName, data + "\n", true);
            }
            }
        });
        t1.start();
    }

    // TODO add parameters as needed
    private void startNewFile(int sample_time_milliseconds) {
        mFileName = generateFileName(sample_time_milliseconds);
        try {
            mOutputStreamWrite = getContext().openFileOutput(mFileName, Context.MODE_PRIVATE);
            mOutputStreamAppend = getContext().openFileOutput(mFileName, Context.MODE_APPEND);
        } catch (Exception e) {
            e.printStackTrace();
        }
        createUpdateFile(mFileName, "\n", false);
    }

    private void createUpdateFile(String fileName, String content, boolean update) {
        FileOutputStream outputStream;

        try {
            if (update) {
                outputStream = mContext.openFileOutput(fileName, Context.MODE_APPEND);
            } else {
                outputStream = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
            }
            outputStream.write(content.getBytes());
//            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {

        }
    }


    private void moveCurrentFileToExternal() {
        Log.i(TAG, "creating file in external folder");
        //external storage availability check
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            Log.i(TAG, "external storage not mounted");
            return;
        }

        copyInteralExternal(mFileName, mFileName);
    }

    private File copyInteralExternal(String srcFileName, String dstFileName) {
        Log.i(TAG, "attempting to copy files");
        String dstFolderPath = Environment.getExternalStorageDirectory() + File.separator
                + "sEMG";
        File dst = new File(dstFolderPath);

        //if folder does not exist
        if (!dst.exists()) {
            if (!dst.mkdir()) {
                Log.i(TAG, "external folder \"sEMG\" cannot be created");
                return null;
            }
        }

        FileChannel inChannel = null;
        FileChannel outChannel = null;
        File src = new File(getContext().getFilesDir(), mFileName);
        File expFile = new File(dstFolderPath, dstFileName);
        try {
            expFile.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.i(TAG, String.format("src file exists?: %b", src.exists()));
        Log.i(TAG, String.format("dst file exists?: %b", expFile.exists()));

        try {
            inChannel = new FileInputStream(src).getChannel();
            outChannel = new FileOutputStream(expFile).getChannel();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (inChannel != null)
                    inChannel.close();
                if (outChannel != null)
                    outChannel.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Log.i(TAG, "internal file copied to external file in Documents/sEMG");
        // TODO delete local file after copying to external storage
        return expFile;
    }

    private void shareCurrentFile() {
        // TODO after you save to public storage
    }

    private String generateFileName(int sampleTimeMilliseconds) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
        LocalDateTime date = LocalDateTime.now();
        return String.format("%s_%d_ms.txt", date.format(formatter), sampleTimeMilliseconds);
    }

    private void startMeasurement() {
        if (mCharacteristic == null || mDeviceAddress == null) {
            return;
        }

        if (measurementDone) {
            mFlag = 0;
            mIndex = 0;
            mSeconds = 0;

            int milliseconds = 0;
            String time = binding.editTextSampleTime.getText().toString();
            if (time == null || time.isEmpty()) {
                milliseconds = MAX_MILLISECONDS;
            } else {
                milliseconds = (int) (Float.parseFloat(time) * 1000);
            }

            mGraphDisplayFragment.clearGraphs();
            mGraphDisplayFragment.lockViewPorts();

            measurementDone = false;
            startNewFile(milliseconds);
            Log.i(TAG, String.format("measurement of %d ms started", milliseconds));

            runTimer();

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopMeasurement();
                }
            }, milliseconds);

            mBLEService.connect(mDeviceAddress);
            mBLEService.setCharacteristicNotification(
                    mCharacteristic, true);
        }

    }

    private void stopMeasurement() {
        if (!measurementDone) {
            mIndex = 0;
            mCounter = 0;
            measurementDone = true;
            if (mBLEService != null)
                mBLEService.disconnect();

            try {
                mOutputStream.close();
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage());
            } finally {
                Log.i(TAG, "measurement stopped");
            }

            moveCurrentFileToExternal();
            binding.buttonToPostprocess.setClickable(true);
            mGraphDisplayFragment.unlockViewPorts();

            mBLEService.disconnect();
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        getContext().registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
//        if (mBLEService != null && mDeviceAddress != null && mDeviceAddress != getString(R.string.unknown_address)) {
//            final boolean result = mBLEService.connect(mDeviceAddress);
//            Log.d(TAG, "Connect request result=" + result);
//        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onPause() {
        super.onPause();
        getContext().unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMeasurement();
        getContext().unbindService(mServiceConnection);
        mBLEService = null;
    }

    @Override
    public void onDestroyView() {
        stopMeasurement();
        super.onDestroyView();
        binding = null;
    }

    // Sets the NUmber of seconds on the timer.
    // The runTimer() method uses a Handler
    // to increment the seconds and
    // update the text view.
    private void runTimer() {
        final TextView timeView = binding.textViewStopwatch;

        // Call the post() method,
        // passing in a new Runnable.
        // The post() method processes
        // code without a delay,
        // so the code in the Runnable
        // will run almost immediately.
        mHandlerTimer.post(new Runnable() {
            @Override

            public void run() {
                int minutes = (mSeconds % 3600) / 60;
                int secs = mSeconds % 60;

                String time
                        = String
                        .format(Locale.getDefault(),
                                "%02dm:%02ds",
                                minutes, secs);

                timeView.setText(time);

                if (measurementDone) {
                    return;
                } else {
                    mSeconds++;
                    mHandlerTimer.postDelayed(this, 1000);
                }
            }
        });
    }

}
