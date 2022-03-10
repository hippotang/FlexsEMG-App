package com.example.android.semg_30;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.github.psambit9791.jdsp.filter.Butterworth;
import com.github.psambit9791.jdsp.io.Csv;
import com.github.psambit9791.jdsp.transform.DiscreteFourier;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;

public class SEMGData {
    public static final double INT_TO_VOLTS = 0.569; // is this true idk doesn't matter rn
    private final static String TAG = SEMGData.class.getSimpleName();
    private Context mContext;

    private int[] mRawData;
    private double[][] mRawDataSplit;
    private double[][] mProcessedData;
    private ArrayList<double[]> mFFT;
    private double mFs;
    private int mNumChannels;
    private int mCols;

    private int mFlag = 0;
    private int mIndex = 0;


    // used in PostprocessFragment
    public SEMGData(File srcFile, int numChannels, double fs, Context context) {
        mContext = context;
        mFs = fs;
        mNumChannels = numChannels;

        initializeRawData(srcFile);
        initializeProcessedData();
    }

    // used in MeasureFragment
    public SEMGData(int milliseconds, int numChannels, double fs, Context context) {
        mContext = context;
        mFs = fs;
        mNumChannels = numChannels;

        mRawData = new int[mNumChannels*milliseconds];
        mCols = milliseconds;
        mRawDataSplit = new double[mNumChannels][mCols];
        mProcessedData = new double[mNumChannels][mCols];
        mFFT = new ArrayList(mNumChannels);
    }

    public double[][] getProcessedData() {
        if (mProcessedData == null)
            return null;
        return mProcessedData;
    }

    public ArrayList<double[]> getFFT() {
        if (mFFT == null)
            return null;
        return mFFT;
    }

    private void initializeRawData(File srcFile) {
        String filePath = srcFile.getAbsolutePath();
        mRawData = null;
        try {
            getRawDataFromFile2(filePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Converts integers to actual voltage values
    private void initializeProcessedData() {
        if (mRawData == null) {
            Toast.makeText(mContext, "Error processing data file, please choose another file",
                    Toast.LENGTH_LONG);
            return;
        }
        int index = 0;
        int flag = 0;
        int numSamples = mRawData.length;
        mCols = numSamples/mNumChannels;
        mRawDataSplit = new double[mNumChannels][mCols];
        mProcessedData = new double[mNumChannels][mCols];
        for (int n = 0; n<mNumChannels*mCols; n++) {
            mRawDataSplit[flag][index] = mRawData[n];
            mProcessedData[flag][index] = mRawData[n] * INT_TO_VOLTS;
            flag++;
            if (flag == 16) {
                flag = 0;
                index++;
            }
        }
    }

//    // this works, converts hex to int from txt file into mRawData double[].
//    public void getRawDataFromFile(String filePath) {
//        Csv csv = new Csv('\n');
//        HashMap<String, ArrayList<Object>> hm = new HashMap<>();
//        try {
//            hm = csv.readCSV(filePath,false);
//            ArrayList allData = hm.get("X0");
//            mRawData = new int[allData.size()];
//            int i = 0;
//            for (Object o : allData) {
//                int value = Integer.parseInt((String) o, 16);
//                mRawData[i] = value;
//                i++;
//            }
////            Log.i(TAG, "raw data created, looks like this: ");
////            Log.i(TAG, String.format("Length: %d - %f %f %f %f %f %f ...", mRawData.length,
////                    mRawData[0], mRawData[1], mRawData[2], mRawData[3], mRawData[4], mRawData[5]));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    public void getRawDataFromFile2(String filepath) throws IOException {
        RandomAccessFile file = new RandomAccessFile(filepath, "r");
        int arr_length = (int) (file.length() / 3);
        mRawData = new int[arr_length];
        String str;
        String[] strArr = null;
        int index = 0;
        while ((str = file.readLine()) != null) {
            if (str.length() >= 720) {
                strArr = str.split(" ");
                for (int i = 0; i<239; i+=2) {
                    int value = Integer.parseInt(strArr[i + 1] + strArr[i], 16);
//                    value -= 32768;
//                    value = value > 32767 ? (value - 32768) : value; // unsigned int to signed 16-bit conversion
                    mRawData[index] = value;
                    index++;
                }
            }
        }
        file.close();
    }

    public void insert(int value, int row, int col) {
        if (mIndex >= mNumChannels*mCols || row < 0 || row > 15 || col >= mCols) {
            return;
        }
        mRawData[mIndex] = value;
        mIndex++;
        mProcessedData[row][col] = value*INT_TO_VOLTS;
    }

    /* FILTERS AND TRANSFORMS */

    public void applyBandpass(double low, double high, double fs, int order) {
;       for (int i = 0; i<mNumChannels; i++) {
            Butterworth bw = new Butterworth(mRawDataSplit[i], fs);
            mProcessedData[i] = bw.bandPassFilter(order, low, high);
        }
    }

    public void applyBandstop(double low, double high, double fs, int order) {
        for (int i = 0; i<mNumChannels; i++) {
            Butterworth bw = new Butterworth(mRawDataSplit[i], fs);
            mProcessedData[i] = bw.bandPassFilter(order, low, high);
        }
    }

    public void applyHighpass(double freq, double fs, int order) {
        for (int i = 0; i<mNumChannels; i++) {
            Butterworth bw = new Butterworth(mRawDataSplit[i], fs);
            mProcessedData[i] = bw.highPassFilter(order,freq);
        }
    }

    public void applyLowpass(double freq, double fs, int order) {
        for (int i = 0; i<mNumChannels; i++) {
            Butterworth bw = new Butterworth(mRawDataSplit[i], fs);
            mProcessedData[i] = bw.lowPassFilter(order,freq);
        }
    }

    public void doFFTtoProcessedData() {
        for (int i = 0; i<mNumChannels; i++) {
            DiscreteFourier df = new DiscreteFourier(mProcessedData[i]);
            df.dft();
            mFFT = new ArrayList<double[]>();
            mFFT.add(df.returnAbsolute(true));
        }
    }

}
