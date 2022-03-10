package com.example.android.semg_30.ui.measure;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MeasureViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public MeasureViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is measure fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}