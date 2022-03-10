package com.example.android.semg_30.ui.postprocess;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.example.android.semg_30.R;
import com.example.android.semg_30.SEMGData;
import com.example.android.semg_30.databinding.FragmentPostprocessBinding;
import com.example.android.semg_30.ui.graph_display.GraphDisplayFragment;
import com.jjoe64.graphview.GraphView;

import java.io.File;
import java.io.FileOutputStream;

public class PostprocessFragment extends Fragment {
    public static final int SAMPLE_RATE = 800; // Hz
    private static final String TAG = PostprocessFragment.class.getSimpleName();
    private static final int CHOOSE_FILE_REQUESTCODE = 8777;
    private static final int PICKFILE_RESULT_CODE = 8778;
    private static final int NOTCH_WIDTH = 4;
    private FragmentPostprocessBinding binding;
    private String mCurrentFileName;
    private String mCurrentFilePath;
    private SEMGData mSEMGData;
    private GraphDisplayFragment mGraphDisplayFragment;

    private boolean mDoNotch1;
    private boolean mDoNotch2;
    private boolean mDoHigh;
    private boolean mDoLow;
    private boolean mViewFFT;
    private int mNotch1;
    private int mNotch2;
    private int mBandpassLow;
    private int mBandpassHigh;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentPostprocessBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // get arguments
        Bundle args = getArguments();
        mCurrentFileName = PostprocessFragmentArgs.fromBundle(args).getFileName();
        if (mCurrentFileName == null || mCurrentFileName == "default") {
            chooseFileManually();
        } else {
            mCurrentFilePath = Environment.getExternalStorageDirectory() + File.separator
                    + "sEMG" + File.separator + mCurrentFileName;
            loadCurrentMeasurement();
        }

        mSEMGData = null;
        mDoNotch1 = false;
        mDoNotch2 = false;
        mDoHigh = false;
        mDoLow = false;
        mViewFFT = false;
        mNotch1 = -1;
        mNotch2 = -1;
        mBandpassHigh = -1;
        mBandpassLow = -1;

        binding.buttonSelectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseFileManually();
            }
        });
        binding.buttonUpdateGraphs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mNotch1 = getNumber(binding.editTextNotch1);
                mNotch2 = getNumber(binding.editTextNotch2);
                mBandpassHigh = getNumber(binding.editTextBandpassHigh);
                mBandpassLow = getNumber(binding.editTextBandpassLow);

                if (mGraphDisplayFragment != null && mSEMGData != null) {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    if (mDoNotch1) {
                        mSEMGData.applyBandstop(mNotch1-(NOTCH_WIDTH/2),
                                mNotch1+(NOTCH_WIDTH/2), SAMPLE_RATE, 8);
                    }
                    if (mDoNotch2) {
                        mSEMGData.applyBandstop(mNotch1-(NOTCH_WIDTH/2),
                                mNotch2+(NOTCH_WIDTH/2), SAMPLE_RATE, 8);
                    }
                    if (mDoHigh && mDoLow && (mBandpassHigh > mBandpassLow)) {
                        mSEMGData.applyBandpass(mBandpassLow, mBandpassHigh, SAMPLE_RATE, 5);
                    } else if (mDoHigh && !mDoLow && (mBandpassHigh>=0)) {
                        mSEMGData.applyLowpass(mBandpassHigh, SAMPLE_RATE, 5);
                    } else if (!mDoHigh && mDoLow && (mBandpassLow>=0)) {
                        mSEMGData.applyHighpass(mBandpassHigh, SAMPLE_RATE, 5);
                    }
                    if (mViewFFT) {
//                        mSEMGData.doFFTtoProcessedData();
//                        mGraphDisplayFragment.graphFFT(mSEMGData, SAMPLE_RATE);
//                        return;
                    }
                    mGraphDisplayFragment.updateGraphPoints(mSEMGData);
                    binding.progressBar.setVisibility(View.GONE);
                }
            }
        });
        binding.checkBoxNotch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                    mDoNotch1 = true;
                else
                    mDoNotch1 = false;
            }
        });
        binding.checkBoxNotch2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                    mDoNotch2 = true;
                else
                    mDoNotch2 = false;
            }
        });
        binding.checkBoxBandpassHigh.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                    mDoHigh = true;
                else
                    mDoHigh = false;
            }
        });
        binding.checkBoxBandpassLow.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                    mDoLow = true;
                else
                    mDoLow = false;
            }
        });
        binding.checkBoxViewFFT.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                    mViewFFT = true;
                else
                    mViewFFT = false;
            }
        });

        return root;
    }

    private int getNumber(EditText et) {
        int value = -1;
        try {
            value = Integer.parseInt(et.getText().toString());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return value;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mGraphDisplayFragment = (GraphDisplayFragment) getChildFragmentManager().
                findFragmentById(R.id.graphDisplayFragmentPostprocess);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICKFILE_RESULT_CODE && resultCode == Activity.RESULT_OK){
            Uri content_describer = data.getData();
            String path = content_describer.getPath();
            File temp = new File(path);
            String filename = temp.getName();
            File source = new File(Environment.getExternalStorageDirectory(), "sEMG");
            source = new File(source, filename);

            Log.d(TAG, String.format("FileName exists?: %b", source.exists()));
            binding.textView.setText(filename + " chosen");

            mCurrentFileName = filename;
            mCurrentFilePath = source.getPath();

            mSEMGData = new SEMGData(source, 16, SAMPLE_RATE, getContext());

            // initialize plots
            mGraphDisplayFragment.updateGraphPoints(mSEMGData);
        }
    }

    private void chooseFileManually() {
        // intent
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        String path = Environment.getExternalStorageDirectory() + File.separator + "sEMG" +
                File.separator;
        Uri uri = Uri.parse(path);
        chooseFile.setDataAndType(uri, "*/*");
        startActivityForResult(
                Intent.createChooser(chooseFile, "Choose a file"),
                PICKFILE_RESULT_CODE
        );
    }

    private void loadCurrentMeasurement() {
        File source = new File(mCurrentFilePath);
        if (source.exists()) {
            binding.textView.setText(mCurrentFileName + " chosen");
            mSEMGData = new SEMGData(source, 16, SAMPLE_RATE, getContext());
        } else {
            binding.textView.setText("Failed to load current measurement file");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}