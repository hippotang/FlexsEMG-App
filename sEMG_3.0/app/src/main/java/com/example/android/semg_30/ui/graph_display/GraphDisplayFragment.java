package com.example.android.semg_30.ui.graph_display;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.android.semg_30.R;
import com.example.android.semg_30.SEMGData;
import com.example.android.semg_30.databinding.FragmentGraphDisplayBinding;
import com.example.android.semg_30.ui.measure.MeasureFragment;
import com.example.android.semg_30.ui.postprocess.PostprocessFragment;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link GraphDisplayFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class GraphDisplayFragment extends Fragment {
    private static final String TAG = GraphDisplayFragment.class.getSimpleName();
    private static final int MAX_POINTS_PER_SERIES = 8000;
    private static final int MAX_POINTS_PER_LIVE_PLOT_UPDATE = 600;

    private int mDataPointCounter;
    private int mNumChannels = 16;
    private String mParam2;

    private FragmentGraphDisplayBinding binding;
    private GraphView[] mGraphViews;
    private ArrayList<LineGraphSeries<DataPoint>> mSeries;
    private boolean[] mVisibleChannels;

    private ArrayList<Queue<DataPoint>> mTempSeriesStorage;

    private Lock[] mLocks;

    public GraphDisplayFragment() {
        // Required empty public constructor
    }

    // TODO: Rename and change types and number of parameters
    public static GraphDisplayFragment newInstance(String param1, String param2) {
        GraphDisplayFragment fragment = new GraphDisplayFragment();
        return fragment;
    }

    Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread th, Throwable ex) {
            Log.e(TAG, "Uncaught exception: " + ex);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FragmentGraphDisplayBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

//        if (getArguments() != null) {
//            mNumChannels = getArguments().getInt(ARG_PARAM1);
//            mParam2 = getArguments().getString(ARG_PARAM2);
//        }
        mNumChannels = 16;
        mGraphViews = new GraphView[16];
        mVisibleChannels = new boolean[16];
        mSeries = new ArrayList<LineGraphSeries<DataPoint>>(16);
        mLocks = new Lock[16];
        mDataPointCounter = 0;
        Arrays.fill(mVisibleChannels, Boolean.TRUE);

        mTempSeriesStorage = new ArrayList<Queue<DataPoint>>(16);
        for (int i = 0; i<mNumChannels; i++) {
            mLocks[i] = new ReentrantLock();
            mTempSeriesStorage.add(new LinkedList<DataPoint>());
        }
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.buttonSelectChannels.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectChannels();
            }
        });

        mGraphViews[0] = (GraphView) binding.graph00;
        mGraphViews[1] = (GraphView) binding.graph01;
        mGraphViews[2] = (GraphView) binding.graph02;
        mGraphViews[3] = (GraphView) binding.graph03;
        mGraphViews[4] = (GraphView) binding.graph04;
        mGraphViews[5] = (GraphView) binding.graph05;
        mGraphViews[6] = (GraphView) binding.graph06;
        mGraphViews[7] = (GraphView) binding.graph07;
        mGraphViews[8] = (GraphView) binding.graph08;
        mGraphViews[9] = (GraphView) binding.graph09;
        mGraphViews[10] = (GraphView) binding.graph10;
        mGraphViews[11] = (GraphView) binding.graph11;
        mGraphViews[12] = (GraphView) binding.graph12;
        mGraphViews[13] = (GraphView) binding.graph13;
        mGraphViews[14] = (GraphView) binding.graph14;
        mGraphViews[15] = (GraphView) binding.graph15;

        int index = 0;
        for (GraphView gv : mGraphViews) {
            // TODO set starting x-axis width for each viewport
            gv.getViewport().setScalable(true);
            gv.getViewport().setScrollable(true);
            gv.getLegendRenderer().setVisible(false);
            gv.getViewport().setMinX(0);
            gv.getViewport().setMaxX(30);

            mSeries.add(new LineGraphSeries<DataPoint>(new DataPoint[] {
                    new DataPoint(0,0)
            }));
            gv.addSeries(mSeries.get(index));

            index++;
        }
    }

    /*
     * Creates alert dialog that allows user to select when channels can be viewed
     * At end of alertdialog, graph views set to GONE if unselected
     */
    private void selectChannels() {
        String[] channelArray = {"Channel 1", "Channel 2", "Channel 3", "Channel 4",
                "Channel 5", "Channel 6", "Channel 7", "Channel 8",
                "Channel 9", "Channel 10", "Channel 11", "Channel 12",
                "Channel 13", "Channel 14", "Channel 15", "Channel 16"};
        boolean[] selectedChannels = mVisibleChannels;

        AlertDialog.Builder builder = new AlertDialog.Builder(
                getContext()
        );
        // Set title
        builder.setTitle("Select Channels");
        // set non cancelable
        builder.setCancelable(false);

        builder.setMultiChoiceItems(channelArray, selectedChannels, new DialogInterface.OnMultiChoiceClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i, boolean b) {
                // check condition
                if (b) {
                    // when checkbox selected
                    // add position in channel list
                    mVisibleChannels[i] = true;
                    // Collections.sort(mVisibleChannels);
                } else {
                    // when checkbox unselected
                    // remove position from channel list
                    mVisibleChannels[i] = false;
                }
            }
        });

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                updateGraphVisibility();
            }
        });

        builder.show();
    }

    public boolean insertTempDataPoint(double x, double y, int channelIndex) {
        if (channelIndex >= mNumChannels)
            return false;
        try {
            mTempSeriesStorage.get(channelIndex).add(new DataPoint(x,y));
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public void livePlotFromTempData() {
        try {
            for (int i = 0; i<mNumChannels; i++) {
                mLocks[i].lock();

                int tempSize = Math.min(mTempSeriesStorage.get(i).size(), MAX_POINTS_PER_SERIES);
                for (int j = 0; j < tempSize; j++) {
                    DataPoint point = mTempSeriesStorage.get(i).poll();
                    appendToSeries(point, i, true);
                }

                mLocks[i].unlock();
            }
        } catch (Exception e) {

        }
    }

    private void appendToSeries(DataPoint point, int seriesIndex, boolean silent) {
        if (seriesIndex >= 16) {
            Log.e(TAG, String.format("Only 16 graphs exist, attempted to plot at graph # %d", seriesIndex+1));
            return;
        }
        try {
            if (point != null) {
                try {
                    if (mDataPointCounter < MAX_POINTS_PER_SERIES) {
                        mSeries.get(seriesIndex).appendData(point, true, MAX_POINTS_PER_SERIES, silent);
                    } else {
                        mSeries.get(seriesIndex).resetData(new DataPoint[]{point});
                        mDataPointCounter = 0;
                    }
                    //                mSeries.get(seriesIndex).appendData(point, true, MAX_POINTS_PER_SERIES, silent);
                    mDataPointCounter++;
                } catch (Exception e) {
                    //                e.printStackTrace();
                }
            }
        } catch (Exception e) {

        }
    }

    private void matchGraphAxesTo(int channelIndex) {
        // TODO
    }

    private void updateGraphVisibility() {
        try {
            for (int i = 0; i<mVisibleChannels.length; i++) {
                if (mVisibleChannels[i]) {
                    mGraphViews[i].setVisibility(View.VISIBLE);
                } else {
                    mGraphViews[i].setVisibility(View.GONE);
                }
            }
        } catch (ConcurrentModificationException e) {
            e.printStackTrace();
        }
    }

    public void updateGraphPoints(SEMGData s) {
        double[][] processedData = s.getProcessedData();
        if (processedData == null) {
            Log.i(TAG, "processed data is null");
            return;
        }

        int rows = Math.min(processedData.length, 16);
        int cols = processedData[0].length;

        for (int i = 0; i<rows; i++) {
            DataPoint[] pts = new DataPoint[cols];
            for (int j = 0; j<cols; j++) {
                pts[j] = new DataPoint(j*1.0/PostprocessFragment.SAMPLE_RATE, processedData[i][j]);
            }
            LineGraphSeries<DataPoint> series = new LineGraphSeries<>(pts);

            mGraphViews[i].removeAllSeries();
            mGraphViews[i].addSeries(series);
        }
    }

    public void lockViewPorts() {
        for (GraphView gv : mGraphViews) {
            gv.getViewport().setScalable(false);
            gv.getViewport().setScalable(false);
        }
    }

    public void unlockViewPorts() {
        for (GraphView gv : mGraphViews) {
            gv.getViewport().setScalable(true);
            gv.getViewport().setScalable(true);
        }
    }

    public void scrollToEndAll() {
        try {
            for (GraphView gv: mGraphViews) {
                gv.getViewport().scrollToEnd();
            }
        } catch (Exception e) {

        }
    }
    public void graphFFT(SEMGData s, double fs) {
        ArrayList<double[]> fft = s.getFFT();
        if (fft == null) {
            return;
        }

        int rows = Math.min(fft.size(), 16);
        int cols = fft.get(0).length;

        for (int i = 0; i<rows; i++) {
            DataPoint[] pts = new DataPoint[cols];
            for (int j = 0; j<cols; j++) {
                pts[j] = new DataPoint(j*(1.0/cols)*fs, fft.get(i)[j]);
            }
            LineGraphSeries<DataPoint> series = new LineGraphSeries<>(pts);
            series.setTitle("FFT Channel "+(i+1));
            series.setColor(getColor(i));
            mGraphViews[i].removeAllSeries();
            mGraphViews[i].addSeries(series);
        }
    }

    public void clearGraphs() {
        for (int i = 0; i<16; i++) {
            mSeries.get(i).resetData(new DataPoint[] {
                    new DataPoint(-0.01,0.5)
            });
        }
    }

    public static int getColor(int i) {
        int a = i % 16;
        switch (a) {
            case 0: return R.color.color_channel1;
            case 1: return R.color.color_channel2;
            case 2: return R.color.color_channel3;
            case 3: return R.color.color_channel4;
            case 4: return R.color.color_channel5;
            case 5: return R.color.color_channel6;
            case 6: return R.color.color_channel7;
            case 7: return R.color.color_channel8;
            case 8: return R.color.color_channel9;
            case 9: return R.color.color_channel10;
            case 10: return R.color.color_channel11;
            case 11: return R.color.color_channel12;
            case 12: return R.color.color_channel13;
            case 13: return R.color.color_channel14;
            case 14: return R.color.color_channel15;
            case 15: return R.color.color_channel16;
            default: return R.color.color_channel1;
        }
    }

}