package com.example.android.semg_30.ui.postprocess;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class PostprocessViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public PostprocessViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is postprocess fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}