package com.invapp.view;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;

/**
 * A combination of {@link GestureDetector} and {@link ScaleGestureDetector},
 * plus a two-finger downward swipe recognizer for opening the Tools drawer.
 */
final class GestureAndScaleRecognizer {

    public interface Listener {
        boolean onSingleTapUp(MotionEvent e);

        boolean onDoubleTap(MotionEvent e);

        boolean onScroll(MotionEvent e2, float dx, float dy);

        boolean onFling(MotionEvent e, float velocityX, float velocityY);

        boolean onScale(float focusX, float focusY, float scale);

        boolean onDown(float x, float y);

        boolean onUp(MotionEvent e);

        void onLongPress(MotionEvent e);

        /** Two fingers moved mostly downward with little pinch. */
        void onTwoFingerSwipeDown();
    }

    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleDetector;
    final Listener mListener;
    boolean isAfterLongPress;

    private final float mTouchSlop;
    private final float mMinSwipeDistance;
    private boolean mTwoFingerTracking;
    private boolean mTwoFingerFired;
    private boolean mTwoFingerPinched;
    private float mDownY0;
    private float mDownY1;
    private float mDownX0;
    private float mDownX1;
    private float mDownSpan;

    public GestureAndScaleRecognizer(Context context, Listener listener) {
        mListener = listener;
        ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mMinSwipeDistance = mTouchSlop * 8f;

        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                return mListener.onScroll(e2, dx, dy);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return mListener.onFling(e2, velocityX, velocityY);
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return mListener.onDown(e.getX(), e.getY());
            }

            @Override
            public void onLongPress(MotionEvent e) {
                mListener.onLongPress(e);
                isAfterLongPress = true;
            }
        }, null, true /* ignoreMultitouch */);

        mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return mListener.onSingleTapUp(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return mListener.onDoubleTap(e);
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return true;
            }
        });

        mScaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (mTwoFingerTracking) {
                    float span = detector.getCurrentSpan();
                    if (mDownSpan > 0f
                        && Math.abs(span - mDownSpan) > mTouchSlop * 3f) {
                        mTwoFingerPinched = true;
                    }
                }
                return mListener.onScale(detector.getFocusX(), detector.getFocusY(), detector.getScaleFactor());
            }
        });
        mScaleDetector.setQuickScaleEnabled(false);
    }

    public void onTouchEvent(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        mScaleDetector.onTouchEvent(event);
        trackTwoFingerSwipe(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                isAfterLongPress = false;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!isAfterLongPress && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    // This behaviour is desired when in e.g. vim with mouse events, where we do not
                    // want to move the cursor when lifting finger after a long press.
                    mListener.onUp(event);
                }
                break;
        }
    }

    private void trackTwoFingerSwipe(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                resetTwoFinger();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2 && !mTwoFingerFired) {
                    mTwoFingerTracking = true;
                    mTwoFingerPinched = false;
                    mDownX0 = event.getX(0);
                    mDownY0 = event.getY(0);
                    mDownX1 = event.getX(1);
                    mDownY1 = event.getY(1);
                    float dx = mDownX1 - mDownX0;
                    float dy = mDownY1 - mDownY0;
                    mDownSpan = (float) Math.hypot(dx, dy);
                } else if (event.getPointerCount() > 2) {
                    resetTwoFinger();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!mTwoFingerTracking || mTwoFingerFired || mTwoFingerPinched
                    || event.getPointerCount() != 2) {
                    break;
                }
                float y0 = event.getY(0);
                float y1 = event.getY(1);
                float x0 = event.getX(0);
                float x1 = event.getX(1);
                float dy0 = y0 - mDownY0;
                float dy1 = y1 - mDownY1;
                float dx0 = x0 - mDownX0;
                float dx1 = x1 - mDownX1;
                float avgDy = (dy0 + dy1) / 2f;
                float avgAbsDx = (Math.abs(dx0) + Math.abs(dx1)) / 2f;
                // Both fingers move down, mostly vertical, past threshold.
                if (dy0 > mMinSwipeDistance && dy1 > mMinSwipeDistance
                    && avgDy > mMinSwipeDistance
                    && avgAbsDx < avgDy * 0.55f
                    && !mScaleDetector.isInProgress()) {
                    mTwoFingerFired = true;
                    mTwoFingerTracking = false;
                    mListener.onTwoFingerSwipeDown();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resetTwoFinger();
                break;
            default:
                break;
        }
    }

    private void resetTwoFinger() {
        mTwoFingerTracking = false;
        mTwoFingerFired = false;
        mTwoFingerPinched = false;
        mDownSpan = 0f;
    }

    public boolean isInProgress() {
        return mScaleDetector.isInProgress();
    }

}
