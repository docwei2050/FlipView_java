package com.docwei.flipview.flipview;

import android.graphics.Canvas;

public interface OverFlipper {

	float calculate(float flipDistance, float minFlipDistance,
                    float maxFlipDistance);
	//是否需要重绘
	boolean draw(Canvas c);

	/**
	 * Triggered from a touch up or cancel event. reset and release state
	 * variables here.
	 */
	void overFlipEnded();

	/**
	 * 
	 * @return the total flip distance the has been over flipped. This is used
	 *         by the onOverFlipListener so make sure to return the correct
	 *         value.
	 */
	float getTotalOverFlip();

}
