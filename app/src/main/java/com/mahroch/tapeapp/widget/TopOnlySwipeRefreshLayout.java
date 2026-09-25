package com.mahroch.tapeapp.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * Swipe-to-refresh that only allows the refresh gesture when the child is
 * genuinely at the top of its scrollable content.
 */
public class TopOnlySwipeRefreshLayout extends SwipeRefreshLayout {

    public TopOnlySwipeRefreshLayout(@NonNull Context context) {
        super(context);
    }

    public TopOnlySwipeRefreshLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean canChildScrollUp() {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        return child != null && child.canScrollVertically(-1);
    }
}
