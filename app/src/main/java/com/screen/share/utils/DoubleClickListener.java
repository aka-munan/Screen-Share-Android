package com.screen.share.utils;

import android.os.Handler;
import android.os.Looper;
import android.view.View;


public abstract class DoubleClickListener implements View.OnClickListener {

    Handler handler = new Handler(Looper.getMainLooper());
    private long clickedTime = 0L;
    private boolean doubleClicked;

    @Override
    public void onClick(View v) {
        if (System.currentTimeMillis() - clickedTime < 400) {
            doubleClicked=true;
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (doubleClicked) {
                    onDoubleClick(v);
                } else {
                    onSingleClick(v);
                }
                doubleClicked=false;
            }
        }, 400);
        clickedTime=System.currentTimeMillis();
    }

    public abstract void onDoubleClick(View view);

    public abstract void onSingleClick(View view);
}
