package com.docwei.flipview.flipview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.Scroller;

import com.docwei.flipview.R;


public class FlipView extends FrameLayout {
    //用于滑到指定页面
    public interface OnFlipListener {
        void onFlippedToPage(FlipView v, int position, long id);
    }

    //滑到边界回调
    public interface OnOverFlipListener {
        void onOverFlip(FlipView v, OverFlipMode mode, boolean overFlippingPrevious, float overFlipDistance, float flipDistancePerPage);
    }

    /**
     * Class to hold a view and its corresponding info
     */
    static class Page {
        View view;
        int position;
        int viewType; //viewType用于复用
        boolean valid; //复用需要用到
    }

    // this will be the postion when there is not data
    private static final int INVALID_PAGE_POSITION = -1;
    // "null" flip distance
    private static final int INVALID_FLIP_DISTANCE = -1;

    private static final int PEAK_ANIM_DURATION = 600;
    private static final int MAX_SINGLE_PAGE_FLIP_ANIM_DURATION = 300;

    // for normalizing width/height
    private static final int FLIP_DISTANCE_PER_PAGE = 180;
    //阴影透明度
    private static final int MAX_SHADOW_ALPHA = 100;// out of 255 180
    private static final int MAX_SHADE_ALPHA = 100;// out of 255  130
    //发光透明度
    private static final int MAX_SHINE_ALPHA = 100;// out of 255


    // constant used by the attributes
    private static final int VERTICAL_FLIP = 0;

    // constant used by the attributes
    private static final int HORIZONTAL_FLIP = 1;

    //预创建View的标志
    private boolean mPreCreateView = false;

    //数据改变监听
    private DataSetObserver dataSetObserver = new DataSetObserver() {

        @Override
        public void onChanged() {
            dataSetChanged();
        }

        @Override
        public void onInvalidated() {
            dataSetInvalidated();
        }

    };

    private Scroller mScroller;
    private final Interpolator flipInterpolator = new DecelerateInterpolator();
    private ValueAnimator mPeakAnim;
    private TimeInterpolator mPeakInterpolator = new AccelerateDecelerateInterpolator();

    private boolean mIsFlippingVertically;
    private boolean mIsFlipping;
    private boolean mIsFlippingEnabled = true;
    private int mTouchSlop;
    private boolean mIsOverFlipping;

    // keep track of pointer
    private float mLastX;
    private float mLastY;
    //多指触控活动的手指
    private static final int INVALID_POINTER = -1;
    private int mActivePointerId = INVALID_POINTER;

    // velocity监听
    private VelocityTracker mVelocityTracker;
    private int mMinimumVelocity;
    private int mMaximumVelocity;

    // views get recycled after they have been pushed out of the active queue
    private Recycler mRecycler = new Recycler();

    //列表数据跟控件桥接用
    private ListAdapter mAdapter;
    private int mPageCount = 0;

    //一般需要缓存3页
    private Page mPreviousPage = new Page();
    private Page mCurrentPage = new Page();
    private Page mNextPage = new Page();
    private View mEmptyView;

    private OnFlipListener mOnFlipListener;
    private OnOverFlipListener mOnOverFlipListener;

    //滑动的距离累加的 并非实际位移，做了处理 整个宽度对应180
    // 那一半的宽度就是90
    private float mFlipDistance = INVALID_FLIP_DISTANCE;
    private int mCurrentPageIndex = INVALID_PAGE_POSITION;
    private int mLastDispatchedPageEventIndex = 0;
    private long mCurrentPageId = 0;

    private OverFlipMode mOverFlipMode;
    private OverFlipper mOverFlipper;

    // clipping rects
    private Rect mTopRect = new Rect();
    private Rect mBottomRect = new Rect();
    private Rect mRightRect = new Rect();
    private Rect mLeftRect = new Rect();

    // used for transforming the canvas
    private Camera mCamera = new Camera();

    // paints drawn above views when flipping
    private Paint mShadowPaint = new Paint();
    private Paint mShadePaint = new Paint();
    private Paint mShinePaint = new Paint();
    private int mWidth;
    private int mHeight;

    public FlipView(Context context) {
        this(context, null);
    }

    public FlipView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlipView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FlipView);
        mIsFlippingVertically = a.getInt(R.styleable.FlipView_orientation, VERTICAL_FLIP) == VERTICAL_FLIP;
        setOverFlipMode(OverFlipMode.values()[a.getInt(R.styleable.FlipView_overFlipMode, 0)]);
        a.recycle();
        init();
    }

    private void init() {
        final Context context = getContext();
        final ViewConfiguration configuration = ViewConfiguration.get(context);

        mScroller = new Scroller(context, flipInterpolator);
        mTouchSlop = configuration.getScaledPagingTouchSlop();
        //最小的速度50
        mMinimumVelocity = configuration.getScaledMinimumFlingVelocity() * 4;
        //最大的速度8000
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        mShadowPaint.setColor(Color.BLACK);
        mShadowPaint.setStyle(Style.FILL);
        mShadePaint.setColor(Color.BLACK);
        mShadePaint.setStyle(Style.FILL);
        mShinePaint.setColor(Color.WHITE);
        mShinePaint.setStyle(Style.FILL);

    }

    private void dataSetChanged() {
        final int currentPage = mCurrentPageIndex;
        int newPosition = currentPage;

        // if the adapter has stable ids, try to keep the page currently on
        // stable.
        if (mAdapter.hasStableIds() && currentPage != INVALID_PAGE_POSITION) {
            newPosition = getNewPositionOfCurrentPage();
        } else if (currentPage == INVALID_PAGE_POSITION) {
            newPosition = 0;
        }

        // remove all the current views
        //数据改变先移除所有的View
        recycleActiveViews();
        mRecycler.setViewTypeCount(mAdapter.getViewTypeCount());
        mRecycler.invalidateScraps();

        mPageCount = mAdapter.getCount();

        // put the current page within the new adapter range
        newPosition = Math.min(mPageCount - 1, newPosition == INVALID_PAGE_POSITION ? 0 : newPosition);

        if (newPosition != INVALID_PAGE_POSITION) {
            mCurrentPageIndex = INVALID_PAGE_POSITION;
            mFlipDistance = INVALID_FLIP_DISTANCE;
            flipTo(newPosition);
        } else {
            mFlipDistance = INVALID_FLIP_DISTANCE;
            mPageCount = 0;
            setFlipDistance(0);
        }

        updateEmptyStatus();
    }

    private int getNewPositionOfCurrentPage() {
        // check if id is on same position, this is because it will
        // often be that and this way you do not need to iterate the whole
        // dataset. If it is the same position, you are done.
        if (mCurrentPageId == mAdapter.getItemId(mCurrentPageIndex)) {
            return mCurrentPageIndex;
        }

        // iterate the dataset and look for the correct id. If it
        // exists, set that position as the current position.
        for (int i = 0; i < mAdapter.getCount(); i++) {
            if (mCurrentPageId == mAdapter.getItemId(i)) {
                return i;
            }
        }

        // Id no longer is dataset, keep current page
        return mCurrentPageIndex;
    }

    private void dataSetInvalidated() {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(dataSetObserver);
            mAdapter = null;
        }
        mRecycler = new Recycler();
        removeAllViews();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(0, widthMeasureSpec);
        int height = getDefaultSize(0, heightMeasureSpec);
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }


    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                child.layout(0, 0, getWidth(), getHeight());
            }
        }

        //上半边
        mTopRect.top = 0;
        mTopRect.left = 0;
        mTopRect.right = getWidth();
        mTopRect.bottom = getHeight() / 2;

        //下半边
        mBottomRect.top = getHeight() / 2;
        mBottomRect.left = 0;
        mBottomRect.right = getWidth();
        mBottomRect.bottom = getHeight();

        //左半边
        mLeftRect.top = 0;
        mLeftRect.left = 0;
        mLeftRect.right = getWidth() / 2;
        mLeftRect.bottom = getHeight();

        //右半边
        mRightRect.top = 0;
        mRightRect.left = getWidth() / 2;
        mRightRect.right = getWidth();
        mRightRect.bottom = getHeight();
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h != oldh | w != oldw) {
            mCamera.setLocation(0, 0, -3 * h);
            mWidth = w;
            mHeight = h;
        }
    }


    private void setFlipDistance(float flipDistance) {
        if (mPageCount < 1) {
            mFlipDistance = 0;
            mCurrentPageIndex = INVALID_PAGE_POSITION;
            mCurrentPageId = -1;
            recycleActiveViews();
            return;
        }

        if (flipDistance == mFlipDistance) {
            return;
        }
        mFlipDistance = flipDistance;

        final int currentPageIndex = Math.round(mFlipDistance / FLIP_DISTANCE_PER_PAGE);

        //滑到一定距离就要进行页面切换
        if (mCurrentPageIndex != currentPageIndex) {
            mCurrentPageIndex = currentPageIndex;
            mCurrentPageId = mAdapter.getItemId(mCurrentPageIndex);
            // again on the next line.
            recycleActiveViews();
            //保证第一次就预创建3个View
            if (mCurrentPageIndex < mPageCount - 1) {
                if (mCurrentPageIndex + 2 < mPageCount && !mPreCreateView) {
                    fillPageForIndex(mPreviousPage, mCurrentPageIndex + 2);
                    addView(mPreviousPage.view);
                    mPreCreateView = true;
                }
                fillPageForIndex(mNextPage, mCurrentPageIndex + 1);
                addView(mNextPage.view);
            }
            // add the new active views
            if (mCurrentPageIndex > 0) {
                fillPageForIndex(mPreviousPage, mCurrentPageIndex - 1);
                addView(mPreviousPage.view);
            }
            if (mCurrentPageIndex >= 0 && mCurrentPageIndex < mPageCount) {
                fillPageForIndex(mCurrentPage, mCurrentPageIndex);
                addView(mCurrentPage.view);
            }

        }

        invalidate();
    }

    private void fillPageForIndex(Page p, int i) {
        p.position = i;
        p.viewType = mAdapter.getItemViewType(p.position);
        p.view = getView(p.position, p.viewType);
        p.valid = true;
    }

    private void recycleActiveViews() {
        // remove and recycle the currently active views
        if (mPreviousPage.valid) {
            removeView(mPreviousPage.view);
            //添加到了垃圾回收站
            mRecycler.addScrapView(mPreviousPage.view, mPreviousPage.position, mPreviousPage.viewType);
            mPreviousPage.valid = false;
        }
        if (mCurrentPage.valid) {
            removeView(mCurrentPage.view);
            //添加到了垃圾回收站
            mRecycler.addScrapView(mCurrentPage.view, mCurrentPage.position, mCurrentPage.viewType);
            mCurrentPage.valid = false;
        }
        if (mNextPage.valid) {
            removeView(mNextPage.view);
            //添加到了垃圾回收站
            mRecycler.addScrapView(mNextPage.view, mNextPage.position, mNextPage.viewType);
            mNextPage.valid = false;
        }
    }

    private View getView(int index, int viewType) {
        //获取View
        Recycler.Scrap scrap = mRecycler.getScrapView(index, viewType);
        View v;
        if (scrap == null || !scrap.valid) {
            v = mAdapter.getView(index, scrap == null ? null : scrap.view, this);
        } else {
            v = scrap.view;
        }
        return v;
    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mIsFlippingEnabled || mPageCount < 1) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mLastX = ev.getX(ev.findPointerIndex(mActivePointerId));
                mLastY = ev.getY(ev.findPointerIndex(mActivePointerId));
                mIsFlipping = !mScroller.isFinished() | mPeakAnim != null;
                break;
            case MotionEvent.ACTION_MOVE:
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    break;
                }
                final int pointerIndex = ev.findPointerIndex(activePointerId);
                if (pointerIndex == -1) {
                    mActivePointerId = INVALID_POINTER;
                    break;
                }
                final float x = ev.getX(pointerIndex);
                final float dx = x - mLastX;
                final float xDiff = Math.abs(dx);
                final float y = ev.getY(pointerIndex);
                final float dy = y - mLastY;
                final float yDiff = Math.abs(dy);
                if ((mIsFlippingVertically && yDiff > mTouchSlop && yDiff > xDiff) || (!mIsFlippingVertically && xDiff > mTouchSlop && xDiff > yDiff)) {
                    mIsFlipping = true;
                    mLastX = x;
                    mLastY = y;
                }
                break;
            default:
                break;
        }
        return mIsFlipping;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mIsFlippingEnabled || mPageCount < 1) {
            return false;
        }
        final int actionIndex = ev.getActionIndex();
        trackVelocity(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // start flipping immediately if interrupting some sort of animation
                if (endScroll() || endPeak()) {
                    mIsFlipping = true;
                }
                mLastX = ev.getX();
                mLastY = ev.getY();
                mActivePointerId = ev.getPointerId(0);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mIsFlipping) {
                    final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                    if (pointerIndex == -1) {
                        mActivePointerId = INVALID_POINTER;
                        break;
                    }
                    final float x = ev.getX(pointerIndex);
                    final float xDiff = Math.abs(x - mLastX);
                    final float y = ev.getY(pointerIndex);
                    final float yDiff = Math.abs(y - mLastY);
                    if ((mIsFlippingVertically && yDiff > mTouchSlop && yDiff > xDiff) || (!mIsFlippingVertically && xDiff > mTouchSlop && xDiff > yDiff)) {
                        mIsFlipping = true;
                        mLastX = x;
                        mLastY = y;
                    }
                }
                if (mIsFlipping) {
                    // Scroll to follow the motion event
                    final int activePointerIndex = ev.findPointerIndex(mActivePointerId);
                    if (activePointerIndex == -1) {
                        mActivePointerId = INVALID_POINTER;
                        break;
                    }
                    final float x = ev.getX(activePointerIndex);
                    final float deltaX = mLastX - x;
                    final float y = ev.getY(activePointerIndex);
                    final float deltaY = mLastY - y;
                    mLastX = x;
                    mLastY = y;

                    float deltaFlipDistance = 0;
                    if (mIsFlippingVertically) {
                        deltaFlipDistance = deltaY;
                    } else {
                        deltaFlipDistance = deltaX;
                    }

                    deltaFlipDistance /= ((isFlippingVertically() ? getHeight() : getWidth()) / FLIP_DISTANCE_PER_PAGE);
                    setFlipDistance(mFlipDistance + deltaFlipDistance);

                    final int minFlipDistance = 0;
                    final int maxFlipDistance = (mPageCount - 1) * FLIP_DISTANCE_PER_PAGE;
                    final boolean isOverFlipping = mFlipDistance < minFlipDistance || mFlipDistance > maxFlipDistance;
                    if (isOverFlipping) {
                        mIsOverFlipping = true;
                        setFlipDistance(mOverFlipper.calculate(mFlipDistance, minFlipDistance, maxFlipDistance));
                        if (mOnOverFlipListener != null) {
                            float overFlip = mOverFlipper.getTotalOverFlip();
                            mOnOverFlipListener.onOverFlip(this, mOverFlipMode, overFlip < 0, Math.abs(overFlip), FLIP_DISTANCE_PER_PAGE);
                        }
                    } else if (mIsOverFlipping) {
                        mIsOverFlipping = false;
                        if (mOnOverFlipListener != null) {
                            // TODO in the future should only notify flip distance 0
                            // on the correct edge (previous/next)
                            mOnOverFlipListener.onOverFlip(this, mOverFlipMode, false, 0, FLIP_DISTANCE_PER_PAGE);
                            mOnOverFlipListener.onOverFlip(this, mOverFlipMode, true, 0, FLIP_DISTANCE_PER_PAGE);
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mActivePointerId = ev.getPointerId(actionIndex);
                final int pIndex = ev.findPointerIndex(mActivePointerId);
                mLastX = ev.getX(pIndex);
                mLastY = ev.getY(pIndex);
                break;

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                final int newPointerIndex = ev.findPointerIndex(mActivePointerId);
                mLastX = ev.getX(newPointerIndex);
                mLastY = ev.getY(newPointerIndex);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsFlipping) {
                    final VelocityTracker velocityTracker = mVelocityTracker;
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

                    int velocity;
                    if (isFlippingVertically()) {
                        velocity = (int) velocityTracker.getYVelocity(mActivePointerId);
                    } else {
                        velocity = (int) velocityTracker.getXVelocity(mActivePointerId);
                    }
                    smoothFlipTo(getNextPage(velocity));

                    mActivePointerId = INVALID_POINTER;
                    endFlip();

                    mOverFlipper.overFlipEnded();
                }
            default:
                break;
        }
        return true;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mPageCount < 1) {
            return;
        }
        if (mIsFlipping || !mScroller.isFinished() || mPeakAnim != null) {
            drawPreviousHalf(canvas);
            drawNextHalf(canvas);
            drawFlippingHalf(canvas);
        } else {
            endScroll();
            setDrawWithLayer(mCurrentPage.view, false);
            drawChild(canvas, mCurrentPage.view, 0);
            if (mLastDispatchedPageEventIndex != mCurrentPageIndex) {
                mLastDispatchedPageEventIndex = mCurrentPageIndex;
                postFlippedToPage(mCurrentPageIndex);
            }
        }
        if (mOverFlipper.draw(canvas)) {
            invalidate();
        }
    }

    private void drawPreviousHalf(Canvas canvas) {
        canvas.save();
        canvas.clipRect(isFlippingVertically() ? mTopRect : mLeftRect);
        final float degreesFlipped = getDegreesFlipped();
        //在原库这里修改了应该是>=90而不是>90
        final Page p = degreesFlipped >= 90 ? mPreviousPage : mCurrentPage;
        if (p.valid) {
            setDrawWithLayer(p.view, true);
            drawChild(canvas, p.view, 0);
        }
        drawPreviousShadow(canvas);
        canvas.restore();
    }

    private void drawPreviousShadow(Canvas canvas) {
        final float degreesFlipped = getDegreesFlipped();
        if (degreesFlipped > 90) {
            final int alpha = (int) (((degreesFlipped - 90) / 90f) * MAX_SHADOW_ALPHA);
            mShadowPaint.setAlpha(alpha);
            canvas.drawPaint(mShadowPaint);
        }
    }

    private void drawNextHalf(Canvas canvas) {
        canvas.save();
        canvas.clipRect(isFlippingVertically() ? mBottomRect : mRightRect);

        final float degreesFlipped = getDegreesFlipped();
        //在原库这里修改了应该是>=90而不是>90
        final Page p = degreesFlipped >= 90 ? mCurrentPage : mNextPage;
        if (p.valid) {
            setDrawWithLayer(p.view, true);
            drawChild(canvas, p.view, 0);
        }

        drawNextShadow(canvas);
        canvas.restore();
    }

    private void drawNextShadow(Canvas canvas) {
        final float degreesFlipped = getDegreesFlipped();
        if (degreesFlipped <= 90) {
            final int alpha = (int) ((Math.abs(degreesFlipped - 90) / 90f) * MAX_SHADOW_ALPHA);
            mShadowPaint.setAlpha(alpha);
            canvas.drawPaint(mShadowPaint);
        }
    }

    private void drawFlippingHalf(Canvas canvas) {
        canvas.save();
        mCamera.save();
        final float degreesFlipped = getDegreesFlipped();
        if (degreesFlipped > 90) {
            if (mIsFlippingVertically) {
                handleCanvasX(degreesFlipped - 180, canvas);
            } else {
                handleCanvasY(180 - degreesFlipped, canvas);
            }
            canvas.clipRect(isFlippingVertically() ? mTopRect : mLeftRect);
        } else {
            if (mIsFlippingVertically) {
                handleCanvasX(-degreesFlipped, canvas);
            } else {
                handleCanvasY(degreesFlipped, canvas);
            }
            canvas.clipRect(isFlippingVertically() ? mBottomRect : mRightRect);
        }
        setDrawWithLayer(mCurrentPage.view, true);
        drawChild(canvas, mCurrentPage.view, 0);
        drawFlippingShadeShine(canvas);
        mCamera.restore();
        canvas.restore();
    }

    public void handleCanvasY(float degress, Canvas canvas) {
        canvas.translate(mWidth / 2f, 0);
        mCamera.rotateY(degress);
        mCamera.applyToCanvas(canvas);
        canvas.translate(-mWidth / 2f, 0);
    }

    public void handleCanvasX(float degress, Canvas canvas) {
        canvas.translate(0, mHeight / 2f);
        mCamera.rotateX(degress);
        mCamera.applyToCanvas(canvas);
        canvas.translate(0, -mHeight / 2f);
    }

    private void drawFlippingShadeShine(Canvas canvas) {
        final float degreesFlipped = getDegreesFlipped();
        if (degreesFlipped < 90) {
            final int alpha = (int) ((degreesFlipped / 90f) * MAX_SHINE_ALPHA);
            mShinePaint.setAlpha(alpha);
            canvas.drawRect(isFlippingVertically() ? mBottomRect : mRightRect, mShinePaint);
        } else {
            final int alpha = (int) ((Math.abs(degreesFlipped - 180) / 90f) * MAX_SHADE_ALPHA);
            mShadePaint.setAlpha(alpha);
            canvas.drawRect(isFlippingVertically() ? mTopRect : mLeftRect, mShadePaint);
        }
    }

    private void setDrawWithLayer(View v, boolean drawWithLayer) {
        if (isHardwareAccelerated()) {
            if (v.getLayerType() != LAYER_TYPE_HARDWARE && drawWithLayer) {
                v.setLayerType(LAYER_TYPE_HARDWARE, null);
            } else if (v.getLayerType() != LAYER_TYPE_NONE && !drawWithLayer) {
                v.setLayerType(LAYER_TYPE_NONE, null);
            }
        }
    }

    private float getDegreesFlipped() {
        float localFlipDistance = mFlipDistance % FLIP_DISTANCE_PER_PAGE;

        // fix for negative modulo. always want a positive flip degree
        if (localFlipDistance < 0) {
            localFlipDistance += FLIP_DISTANCE_PER_PAGE;
        }


        return (localFlipDistance / FLIP_DISTANCE_PER_PAGE) * 180;
    }

    private void postFlippedToPage(final int page) {
        post(new Runnable() {
            @Override
            public void run() {
                if (mOnFlipListener != null) {
                    mOnFlipListener.onFlippedToPage(FlipView.this, page, mAdapter.getItemId(page));
                }
            }
        });
    }

    private void onSecondaryPointerUp(MotionEvent event) {
        int actionIndex = event.getActionIndex();
        final int pointerId = event.getPointerId(actionIndex);
        if (pointerId == mActivePointerId && event.getPointerCount() > 1) {
            //actionIndex存在补位机制
            int newIndex;
            if (actionIndex == event.getPointerCount() - 1) {
                newIndex = event.getPointerCount() - 2;
            } else {
                newIndex = event.getPointerCount() - 1;
            }
            mActivePointerId = event.getPointerId(newIndex);
            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    private int getFlipDuration(int deltaFlipDistance) {
        float distance = Math.abs(deltaFlipDistance);
        return (int) (MAX_SINGLE_PAGE_FLIP_ANIM_DURATION * Math.sqrt(distance / FLIP_DISTANCE_PER_PAGE));
    }

    private int getNextPage(int velocity) {
        int nextPage;
        if (velocity > mMinimumVelocity) {
            nextPage = getCurrentPageFloor();
        } else if (velocity < -mMinimumVelocity) {
            nextPage = getCurrentPageCeil();
        } else {
            nextPage = getCurrentPageRound();
        }
        return Math.min(Math.max(nextPage, 0), mPageCount - 1);
    }

    private int getCurrentPageRound() {
        return Math.round(mFlipDistance / FLIP_DISTANCE_PER_PAGE);
    }

    private int getCurrentPageFloor() {
        return (int) Math.floor(mFlipDistance / FLIP_DISTANCE_PER_PAGE);
    }

    private int getCurrentPageCeil() {
        return (int) Math.ceil(mFlipDistance / FLIP_DISTANCE_PER_PAGE);
    }

    /**
     * @return true if ended a flip
     */
    private boolean endFlip() {
        final boolean wasflipping = mIsFlipping;
        mIsFlipping = false;

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
        return wasflipping;
    }

    /**
     * @return true if ended a scroll
     */
    private boolean endScroll() {
        final boolean wasScrolling = !mScroller.isFinished();
        mScroller.abortAnimation();
        return wasScrolling;
    }

    /**
     * @return true if ended a peak
     */
    private boolean endPeak() {
        final boolean wasPeaking = mPeakAnim != null;
        if (mPeakAnim != null) {
            mPeakAnim.cancel();
            mPeakAnim = null;
        }
        return wasPeaking;
    }

    private void peak(boolean next, boolean once) {
        final float baseFlipDistance = mCurrentPageIndex * FLIP_DISTANCE_PER_PAGE;
        if (next) {
            mPeakAnim = ValueAnimator.ofFloat(baseFlipDistance, baseFlipDistance + FLIP_DISTANCE_PER_PAGE / 4f);
        } else {
            mPeakAnim = ValueAnimator.ofFloat(baseFlipDistance, baseFlipDistance - FLIP_DISTANCE_PER_PAGE / 4f);
        }
        mPeakAnim.setInterpolator(mPeakInterpolator);
        mPeakAnim.addUpdateListener(new AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setFlipDistance((Float) animation.getAnimatedValue());
            }
        });
        mPeakAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endPeak();
            }
        });
        mPeakAnim.setDuration(PEAK_ANIM_DURATION);
        mPeakAnim.setRepeatMode(ValueAnimator.REVERSE);
        mPeakAnim.setRepeatCount(once ? 1 : ValueAnimator.INFINITE);
        mPeakAnim.start();
    }

    private void trackVelocity(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
    }

    private void updateEmptyStatus() {
        boolean empty = mAdapter == null || mPageCount == 0;

        if (empty) {
            if (mEmptyView != null) {
                mEmptyView.setVisibility(View.VISIBLE);
                setVisibility(View.GONE);
            } else {
                setVisibility(View.VISIBLE);
            }

        } else {
            if (mEmptyView != null) {
                mEmptyView.setVisibility(View.GONE);
            }
            setVisibility(View.VISIBLE);
        }
    }

    /* ---------- API ---------- */

    /**
     * @param adapter a regular ListAdapter, not all methods if the list adapter are
     *                used by the flipview
     */
    public void setAdapter(ListAdapter adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(dataSetObserver);
        }

        // remove all the current views
        removeAllViews();

        mAdapter = adapter;
        mPageCount = adapter == null ? 0 : mAdapter.getCount();

        if (adapter != null) {
            mAdapter.registerDataSetObserver(dataSetObserver);

            mRecycler.setViewTypeCount(mAdapter.getViewTypeCount());
            mRecycler.invalidateScraps();
        }

        // this will be correctly set in setFlipDistance method
        mCurrentPageIndex = INVALID_PAGE_POSITION;
        mFlipDistance = INVALID_FLIP_DISTANCE;
        setFlipDistance(0);

        updateEmptyStatus();
    }

    public ListAdapter getAdapter() {
        return mAdapter;
    }

    public int getPageCount() {
        return mPageCount;
    }

    public int getCurrentPage() {
        return mCurrentPageIndex;
    }

    public void flipTo(int page) {
        if (page < 0 || page > mPageCount - 1) {
            throw new IllegalArgumentException("That page does not exist");
        }
        endFlip();
        setFlipDistance(page * FLIP_DISTANCE_PER_PAGE);
    }

    public void flipBy(int delta) {
        flipTo(mCurrentPageIndex + delta);
    }

    public void smoothFlipTo(int page) {
        if (page < 0 || page > mPageCount - 1) {
            throw new IllegalArgumentException("That page does not exist");
        }
        final int start = (int) mFlipDistance;
        final int delta = page * FLIP_DISTANCE_PER_PAGE - start;

        endFlip();
        if (isFlippingVertically()) {
            mScroller.startScroll(0, start, 0, delta, getFlipDuration(delta));
        } else {
            mScroller.startScroll(start, 0, delta, 0, getFlipDuration(delta));
        }
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            if (isFlippingVertically()) {
                setFlipDistance(mScroller.getCurrY());
            } else {
                setFlipDistance(mScroller.getCurrX());
            }
            postInvalidate();
        }
    }


    public void smoothFlipBy(int delta) {
        smoothFlipTo(mCurrentPageIndex + delta);
    }

    /**
     * Hint that there is a next page will do nothing if there is no next page
     *
     * @param once if true, only peak once. else peak until user interacts with
     *             view
     */
    public void peakNext(boolean once) {
        /*if (mCurrentPageIndex < mPageCount - 1) {
            peak(true, once);
        }*/
    }

    /**
     * Hint that there is a previous page will do nothing if there is no
     * previous page
     *
     * @param once if true, only peak once. else peak until user interacts with
     *             view
     */
    public void peakPrevious(boolean once) {
        if (mCurrentPageIndex > 0) {
            peak(false, once);
        }
    }

    /**
     * @return true if the view is flipping vertically, can only be set via xml
     * attribute "orientation"
     */
    public boolean isFlippingVertically() {
        return mIsFlippingVertically;
    }

    /**
     * The OnFlipListener will notify you when a page has been fully turned.
     *
     * @param onFlipListener
     */
    public void setOnFlipListener(OnFlipListener onFlipListener) {
        mOnFlipListener = onFlipListener;
    }

    /**
     * The OnOverFlipListener will notify of over flipping. This is a great
     * listener to have when implementing pull-to-refresh
     *
     * @param onOverFlipListener
     */
    public void setOnOverFlipListener(OnOverFlipListener onOverFlipListener) {
        this.mOnOverFlipListener = onOverFlipListener;
    }

    /**
     * @return the overflip mode of this flipview. Default is GLOW
     */
    public OverFlipMode getOverFlipMode() {
        return mOverFlipMode;
    }

    /**
     * Set the overflip mode of the flipview. GLOW is the standard seen in all
     * andriod lists. RUBBER_BAND is more like iOS lists which list you flip
     * past the first/last page but adding friction, like a rubber band.
     *
     * @param overFlipMode
     */
    public void setOverFlipMode(OverFlipMode overFlipMode) {
        this.mOverFlipMode = overFlipMode;
        mOverFlipper = OverFlipperFactory.create(this, mOverFlipMode);
    }

    /**
     * @param emptyView The view to show when either no adapter is set or the adapter
     *                  has no items. This should be a view already in the view
     *                  hierarchy which the FlipView will set the visibility of.
     */
    public void setEmptyView(View emptyView) {
        mEmptyView = emptyView;
        updateEmptyStatus();
    }

}
