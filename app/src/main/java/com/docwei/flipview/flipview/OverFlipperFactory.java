package com.docwei.flipview.flipview;


import static com.docwei.flipview.flipview.OverFlipMode.NONE;

public class OverFlipperFactory {


    public static OverFlipper create(FlipView v, OverFlipMode mode) {
        switch (mode) {
            case GLOW:
                return new GlowOverFlipper(v);
            case RUBBER_BAND:
                return new RubberBandOverFlipper();
            case NONE:
                return new NoneOverFlipper();
        }
        return null;
    }

}
