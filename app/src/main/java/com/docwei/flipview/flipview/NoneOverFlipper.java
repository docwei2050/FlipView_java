package com.docwei.flipview.flipview;

import android.graphics.Canvas;
import android.widget.EdgeEffect;

/**
 * Created by liwk on 2020/6/15.
 */
public class NoneOverFlipper implements OverFlipper {


    private float mTotalOverFlip;


    @Override
    public float calculate(float flipDistance, float minFlipDistance, float maxFlipDistance) {
        float deltaOverFlip = flipDistance - (flipDistance < 0 ? minFlipDistance : maxFlipDistance);

        mTotalOverFlip += deltaOverFlip;


        return flipDistance < 0 ? minFlipDistance : maxFlipDistance;
    }

    @Override
    public boolean draw(Canvas c) {
        return false;
    }


    @Override
    public void overFlipEnded() {
        mTotalOverFlip=0;
    }

    @Override
    public float getTotalOverFlip() {
        return mTotalOverFlip;
    }

}
