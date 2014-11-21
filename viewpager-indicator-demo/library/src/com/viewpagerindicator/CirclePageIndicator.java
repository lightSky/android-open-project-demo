/*
 * Copyright (C) 2011 Patrik Akerfeldt
 * Copyright (C) 2011 Jake Wharton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viewpagerindicator;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;

/**
 * Draws circles (one for each view). The current view position is filled and
 * others are only stroked.
 */
public class CirclePageIndicator extends View implements PageIndicator {
    private static final int INVALID_POINTER = -1;

    private float mRadius;
    private final Paint mPaintPageFill = new Paint(ANTI_ALIAS_FLAG);
    private final Paint mPaintStroke = new Paint(ANTI_ALIAS_FLAG);
    private final Paint mPaintFill = new Paint(ANTI_ALIAS_FLAG);
    private ViewPager mViewPager;
    private ViewPager.OnPageChangeListener mListener;
    /**
     * 当前界面的索引
     */
    private int mCurrentPage;

    /**
     * 当前界面的索引，和mCurrentPage值一样
     */
    private int mSnapPage;
    /**
     * ViewPager的水平偏移量
     */
    private float mPageOffset;
    private int mScrollState;
    private int mOrientation;
    private boolean mCentered;
    /**
     * circle有2种绘制模式:
     * mSnap = true：circle之间不绘制，只绘制最终的实心点
     * mSnap = false：viewPager滑动过程中，相邻circle之间根据mPageOffset实时绘制circle
     */
    private boolean mSnap;

    /**
     * “Touch slop”是指在用户触摸事件可被识别为移动手势前,移动过的那一段像素距离。
     * Touchslop通常用来预防用户在做一些其他操作时意外地滑动，例如触摸屏幕上的元素时产生的滑动。
     */
    private int mTouchSlop;
    /**
     * 每一次onTouch事件产生时水平位置的最后偏移量
     */
    private float mLastMotionX = -1;

    /**
     * 当前处于活动中pointer的ID
     */
    private int mActivePointerId = INVALID_POINTER;

    /**
     * 用户是否主观的滑动屏幕的标识
     */
     private boolean mIsDragging;


    public CirclePageIndicator(Context context) {
        this(context, null);
    }

    public CirclePageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.vpiCirclePageIndicatorStyle);
    }

    public CirclePageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (isInEditMode()) return;

        //Load defaults from resources
        final Resources res = getResources();
        final int defaultPageColor = res.getColor(R.color.default_circle_indicator_page_color);
        final int defaultFillColor = res.getColor(R.color.default_circle_indicator_fill_color);
        final int defaultOrientation = res.getInteger(R.integer.default_circle_indicator_orientation);
        final int defaultStrokeColor = res.getColor(R.color.default_circle_indicator_stroke_color);
        final float defaultStrokeWidth = res.getDimension(R.dimen.default_circle_indicator_stroke_width);
        final float defaultRadius = res.getDimension(R.dimen.default_circle_indicator_radius);
        final boolean defaultCentered = res.getBoolean(R.bool.default_circle_indicator_centered);
        final boolean defaultSnap = res.getBoolean(R.bool.default_circle_indicator_snap);

        //Retrieve styles attributes
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CirclePageIndicator, defStyle, 0);

        mCentered = a.getBoolean(R.styleable.CirclePageIndicator_centered, defaultCentered);
        mOrientation = a.getInt(R.styleable.CirclePageIndicator_android_orientation, defaultOrientation);
        mPaintPageFill.setStyle(Style.FILL);
        mPaintPageFill.setColor(a.getColor(R.styleable.CirclePageIndicator_pageColor, defaultPageColor));
        mPaintStroke.setStyle(Style.STROKE);
        mPaintStroke.setColor(a.getColor(R.styleable.CirclePageIndicator_strokeColor, defaultStrokeColor));
        mPaintStroke.setStrokeWidth(a.getDimension(R.styleable.CirclePageIndicator_strokeWidth, defaultStrokeWidth));
        mPaintFill.setStyle(Style.FILL);
        mPaintFill.setColor(a.getColor(R.styleable.CirclePageIndicator_fillColor, defaultFillColor));
        mRadius = a.getDimension(R.styleable.CirclePageIndicator_radius, defaultRadius);
        mSnap = a.getBoolean(R.styleable.CirclePageIndicator_snap, defaultSnap);

        Drawable background = a.getDrawable(R.styleable.CirclePageIndicator_android_background);
        if (background != null) {
          setBackgroundDrawable(background);
        }

        a.recycle();

        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);
    }


    public void setCentered(boolean centered) {
        mCentered = centered;
        invalidate();
    }

    public boolean isCentered() {
        return mCentered;
    }

    public void setPageColor(int pageColor) {
        mPaintPageFill.setColor(pageColor);
        invalidate();
    }

    public int getPageColor() {
        return mPaintPageFill.getColor();
    }

    public void setFillColor(int fillColor) {
        mPaintFill.setColor(fillColor);
        invalidate();
    }

    public int getFillColor() {
        return mPaintFill.getColor();
    }

    public void setOrientation(int orientation) {
        switch (orientation) {
            case HORIZONTAL:
            case VERTICAL:
                mOrientation = orientation;
                requestLayout();
                break;

            default:
                throw new IllegalArgumentException("Orientation must be either HORIZONTAL or VERTICAL.");
        }
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void setStrokeColor(int strokeColor) {
        mPaintStroke.setColor(strokeColor);
        invalidate();
    }

    public int getStrokeColor() {
        return mPaintStroke.getColor();
    }

    public void setStrokeWidth(float strokeWidth) {
        mPaintStroke.setStrokeWidth(strokeWidth);
        invalidate();
    }

    public float getStrokeWidth() {
        return mPaintStroke.getStrokeWidth();
    }

    public void setRadius(float radius) {
        mRadius = radius;
        invalidate();
    }

    public float getRadius() {
        return mRadius;
    }

    public void setSnap(boolean snap) {
        mSnap = snap;
        invalidate();
    }

    public boolean isSnap() {
        return mSnap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mViewPager == null) {
            return;
        }
        final int count = mViewPager.getAdapter().getCount();
        if (count == 0) {
            return;
        }

        if (mCurrentPage >= count) {
            setCurrentItem(count - 1);
            return;
        }

        /**
         * CirclePageIndicator 分为水平和竖直放置两种模式
         */

        //TODO 这里给出图，更好。。。。。。。。。
        int longSize;	/** 当前方向的Indicator宽度*/
        int longPaddingBefore;/** 当前方向的Indicator起始位置 */
        int longPaddingAfter;	/** 当前方向的Indicator结束位置 */
        int shortPaddingBefore;	/** 如果Indicator是水平方向，则取：padding Top ，竖直方向则取：padding Left */
        if (mOrientation == HORIZONTAL) {//水平方向，则由上、右、左三个方向来确定绘制范围
            longSize = getWidth();
            longPaddingBefore = getPaddingLeft();
            longPaddingAfter = getPaddingRight();
            shortPaddingBefore = getPaddingTop();
        } else {//垂直方向，则由左、上、下、来确定绘制的范围。
            longSize = getHeight();
            longPaddingBefore = getPaddingTop();
            longPaddingAfter = getPaddingBottom();
            shortPaddingBefore = getPaddingLeft();
        }


        final float threeRadius = mRadius * 3;//两相邻circle的间距
        final float shortOffset = shortPaddingBefore + mRadius;//当前方向的垂直方向的圆心坐标位置
        float longOffset = longPaddingBefore + mRadius;//当前方向的圆心位置
        if (mCentered) {
            longOffset += ((longSize - longPaddingBefore - longPaddingAfter) / 2.0f) - ((count * threeRadius) / 2.0f);
        }

        float dX;
        float dY;

        float pageFillRadius = mRadius;
        if (mPaintStroke.getStrokeWidth() > 0) {
            pageFillRadius -= mPaintStroke.getStrokeWidth() / 2.0f;
        }

        //循环的 draw circle
        for (int iLoop = 0; iLoop < count; iLoop++) {
            float drawLong = longOffset + (iLoop * threeRadius);//计算当前方向的每个circle偏移量
            if (mOrientation == HORIZONTAL) {
                dX = drawLong;
                dY = shortOffset;
            } else {
                dX = shortOffset;
                dY = drawLong;
            }

            //只绘制透明度 > 0的circle
            if (mPaintPageFill.getAlpha() > 0) {
                canvas.drawCircle(dX, dY, pageFillRadius, mPaintPageFill);
            }

            // Only paint stroke if a stroke width was non-zero
            //有pageFillRadius时才绘制
            if (pageFillRadius != mRadius) {
                canvas.drawCircle(dX, dY, mRadius, mPaintStroke);
            }
        }

        //Draw the filled circle according to the current scroll
        //根据滑动的位置画出实心的点
        float cx = (mSnap ? mSnapPage : mCurrentPage) * threeRadius;//计算实心点的目标位置
        if (!mSnap) {//不是跳跃模式，则根据当前界面的偏移量平滑地绘制
            cx += mPageOffset * threeRadius;
        }
        if (mOrientation == HORIZONTAL) {//计算实心圆的坐标
            dX = longOffset + cx;
            dY = shortOffset;
        } else {
            dX = shortOffset;
            dY = longOffset + cx;
        }
        canvas.drawCircle(dX, dY, mRadius, mPaintFill);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (super.onTouchEvent(ev)) {
            return true;
        }
        if ((mViewPager == null) || (mViewPager.getAdapter().getCount() == 0)) {//无效的ViewPager，啥也不做
            return false;
        }

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);//记录第一触摸点的ID
                mLastMotionX = ev.getX();//获取当前水平移动距离
                break;

            case MotionEvent.ACTION_MOVE: {
                final int activePointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);//获取第一点的索引
                final float x = MotionEventCompat.getX(ev, activePointerIndex);//根据第一点的索引获取其X坐标
                final float deltaX = x - mLastMotionX;//计算X方向的偏移

                if (!mIsDragging) {
                    if (Math.abs(deltaX) > mTouchSlop) {//如果用户是主观的滑动屏幕，则设置标识为 mIsDragging = true
                        mIsDragging = true;
                    }
                }

                if (mIsDragging) {//如果用户拖拽了屏幕，处理ViewPager移动相应的偏移量
                    mLastMotionX = x;//重新赋值当前的X坐标，以便下次重新计算偏移量
                    if (mViewPager.isFakeDragging() || mViewPager.beginFakeDrag()) {
                        mViewPager.fakeDragBy(deltaX);
                    }
                }

                break;
            }

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (!mIsDragging) {
                    final int count = mViewPager.getAdapter().getCount();
                    final int width = getWidth();
                    final float halfWidth = width / 2f;
                    final float sixthWidth = width / 6f;

                    if ((mCurrentPage > 0) && (ev.getX() < halfWidth - sixthWidth)) {// 向后滑动，回退，以1/3屏幕宽度 为分界线。。。。。。。。。因为布局文件设置为fill_parent????????
                        if (action != MotionEvent.ACTION_CANCEL) {
                            mViewPager.setCurrentItem(mCurrentPage - 1);
                        }
                        return true;
                    } else if ((mCurrentPage < count - 1) && (ev.getX() > halfWidth + sixthWidth)) {//向前滑动
                        if (action != MotionEvent.ACTION_CANCEL) {
                            mViewPager.setCurrentItem(mCurrentPage + 1);
                        }
                        return true;
                    }
                }

                mIsDragging = false;//设置ViewPager滑动标识为false.
                mActivePointerId = INVALID_POINTER;//设置第一个触摸点的ID为invalid
                if (mViewPager.isFakeDragging()) mViewPager.endFakeDrag();//结束ViewPager的临时滑动
                break;

            case MotionEventCompat.ACTION_POINTER_DOWN: {//除最初点外的第一个外出现在屏幕上的点
                final int index = MotionEventCompat.getActionIndex(ev);
                mLastMotionX = MotionEventCompat.getX(ev, index);
                mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP://当非第一点离开屏幕时
                final int pointerIndex = MotionEventCompat.getActionIndex(ev);//获取抬起手指的索引
                final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);//获取抬起手指的ID
                if (pointerId == mActivePointerId) {//如果之前跟踪的mActivePointerId是当前抬起的手指ID，那么就重新为mActivePointerId 赋值另一个活动中的pointerId
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
                }
                mLastMotionX = MotionEventCompat.getX(ev, MotionEventCompat.findPointerIndex(ev, mActivePointerId));//获取仍活动在屏幕上pointer的X坐标值
                break;
        }

        return true;
    }

    @Override
    public void setViewPager(ViewPager view) {
        if (mViewPager == view) {
            return;
        }
        if (mViewPager != null) {
            mViewPager.setOnPageChangeListener(null);
        }
        if (view.getAdapter() == null) {
            throw new IllegalStateException("ViewPager does not have adapter instance.");
        }
        mViewPager = view;
        mViewPager.setOnPageChangeListener(this);
        invalidate();
    }

    @Override
    public void setViewPager(ViewPager view, int initialPosition) {
        setViewPager(view);
        setCurrentItem(initialPosition);
    }

    @Override
    public void setCurrentItem(int item) {
        if (mViewPager == null) {
            throw new IllegalStateException("ViewPager has not been bound.");
        }
        mViewPager.setCurrentItem(item);
        mCurrentPage = item;
        invalidate();
    }

    @Override
    public void notifyDataSetChanged() {
        invalidate();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        mScrollState = state;

        if (mListener != null) {
            mListener.onPageScrollStateChanged(state);
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        mCurrentPage = position;
        mPageOffset = positionOffset;
        invalidate();

        if (mListener != null) {
            mListener.onPageScrolled(position, positionOffset, positionOffsetPixels);
        }
    }

    @Override
    public void onPageSelected(int position) {
        if (mSnap || mScrollState == ViewPager.SCROLL_STATE_IDLE) {
            mCurrentPage = position;
            mSnapPage = position;
            invalidate();
        }

        if (mListener != null) {
            mListener.onPageSelected(position);
        }
    }

    @Override
    public void setOnPageChangeListener(ViewPager.OnPageChangeListener listener) {
        mListener = listener;
    }

    /*
     * (non-Javadoc)
     *
     * @see android.view.View#onMeasure(int, int)
     *
     * MeasureSpc类封装了父View传递给子View的布局(layout)要求。每个MeasureSpc实例代表宽度或者高度(只能是其一)要求，它有三种模式：
     *  ①、UNSPECIFIED(未指定)，父元素不对子元素施加任何束缚，子元素可以得到任意想要的大小；
     *  ②、EXACTLY(完全)，父元素决定自元素的确切大小，子元素将被限定在给定的边界里而忽略它本身大小；相对应的是 FILL_PARENT
     *  ③、AT_MOST(至多)，子元素至多达到指定大小的值。相对应的是 WRAP_CONTENT
     *
     * View在测量阶段的最终大小的设定是由setMeasuredDimension()方法决定的,也是必须要调用的方法，否则会报异常，
     * 这里就直接调用了setMeasuredDimension()方法设置值了。
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        if (mOrientation == HORIZONTAL) {
            setMeasuredDimension(measureLong(widthMeasureSpec), measureShort(heightMeasureSpec));
        } else {
            setMeasuredDimension(measureShort(widthMeasureSpec), measureLong(heightMeasureSpec));
        }
    }

    /**
     * 决定View的宽度
     *
     * Determines the width of this view
     *
     * @param measureSpec
     *            A measureSpec packed into an int
     * @return The width of the view, honoring constraints from measureSpec
     *
     * 测量的步骤分为获取MeasureSpec模式，获取系统建议的值、自己计算height和width（需要考虑自身的padding）
     */
    private int measureLong(int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);//获取MeasureSpec模式
        int specSize = MeasureSpec.getSize(measureSpec);//获取系统建议的值

        if ((specMode == MeasureSpec.EXACTLY) || (mViewPager == null)) {//用户指定了具体的值
            //We were told how big to be
            result = specSize;
        } else {
            //UNSPECIFIED 或者 AT_MOST 模式，则根据ViewPager的page数量计算宽度
            final int count = mViewPager.getAdapter().getCount();//
            result = (int)(getPaddingLeft() + getPaddingRight()
                    + (count * 2 * mRadius) + (count - 1) * mRadius + 1);
            //Respect AT_MOST value if that was what is called for by measureSpec
            //AT_MOST 特殊处理，从系统建议值和自己计算值中取一个较小值
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }
        return result;
    }

    /**
     * 决定View的高度
     *
     * @param measureSpec
     *            A measureSpec packed into an int
     * @return The height of the view, honoring constraints from measureSpec
     */
    private int measureShort(int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            //We were told how big to be
            result = specSize;
        } else {
            //Measure the height
            result = (int)(2 * mRadius + getPaddingTop() + getPaddingBottom() + 1);
            //Respect AT_MOST value if that was what is called for by measureSpec
            if (specMode == MeasureSpec.AT_MOST) {//获取一个合适的值
                result = Math.min(result, specSize);
            }
        }
        return result;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState savedState = (SavedState)state;
        super.onRestoreInstanceState(savedState.getSuperState());
        mCurrentPage = savedState.currentPage;
        mSnapPage = savedState.currentPage;
        requestLayout();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.currentPage = mCurrentPage;
        return savedState;
    }

    static class SavedState extends BaseSavedState {
        int currentPage;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            currentPage = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(currentPage);
        }

        @SuppressWarnings("UnusedDeclaration")
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
