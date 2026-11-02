package dev.nico.folio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Cuadrícula de páginas. Mantén pulsada una miniatura para levantarla y arrástrala
 * a su nuevo sitio; las demás se apartan con una animación.
 */
final class PageGrid extends ViewGroup {
    interface Listener {
        void onCellTap(PageCell cell);

        void onCellRemove(PageCell cell);

        void onOrderChanged();

        void onDragState(boolean dragging);
    }

    private static final float RATIO = 4f / 3f;
    private static final Interpolator DECEL = new DecelerateInterpolator(2f);

    private final ArrayList<PageCell> cells = new ArrayList<>();
    private final HashMap<PageCell, float[]> snapshot = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int touchSlop;
    private final int longPressTimeout;
    private final int gap;
    private final int[] loc = new int[2];

    private Listener listener;
    private int columns = 3;
    private int cellW;
    private int cellH;
    private int bottomOverlay;

    private PageCell downCell;
    private PageCell dragCell;
    private float downX, downY, rawX, rawY, offX, offY;
    private boolean pending;
    private boolean dragging;
    private int dragStartIndex;

    PageGrid(Context c) {
        super(c);
        ViewConfiguration vc = ViewConfiguration.get(c);
        touchSlop = vc.getScaledTouchSlop();
        longPressTimeout = Math.max(320, ViewConfiguration.getLongPressTimeout());
        gap = Ui.dp(12);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** Alto (en píxeles) de la parte inferior del ScrollView tapada por los botones flotantes. */
    void setBottomOverlay(int px) {
        bottomOverlay = px;
    }

    int count() {
        return cells.size();
    }

    boolean isDragging() {
        return dragging;
    }

    List<Page> pages() {
        ArrayList<Page> l = new ArrayList<>(cells.size());
        for (PageCell c : cells) l.add(c.page);
        return l;
    }

    int indexOf(PageCell c) {
        return cells.indexOf(c);
    }

    PageCell cellFor(Page p) {
        for (PageCell c : cells) if (c.page == p) return c;
        return null;
    }

    // ---------------------------------------------------------------- cambios

    void add(Page p, int stagger) {
        insert(p, cells.size(), stagger);
    }

    void insert(Page p, int index, int stagger) {
        snapshot();
        PageCell c = new PageCell(getContext(), p, true);
        index = Math.max(0, Math.min(index, cells.size()));
        cells.add(index, c);
        addView(c);
        settle();
        c.setAlpha(0f);
        c.setScaleX(0.9f);
        c.setScaleY(0.9f);
        c.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(Math.min(stagger, 12) * 35L)
                .setDuration(280).setInterpolator(DECEL).start();
    }

    void remove(final PageCell c) {
        int idx = cells.indexOf(c);
        if (idx < 0) return;
        snapshot();
        cells.remove(idx);
        fadeOut(c);
        settle();
    }

    void clear() {
        for (PageCell c : new ArrayList<>(cells)) fadeOut(c);
        cells.clear();
        snapshot.clear();
        requestLayout();
    }

    void move(PageCell c, int to) {
        int from = cells.indexOf(c);
        if (from < 0) return;
        to = Math.max(0, Math.min(to, cells.size() - 1));
        if (from == to) return;
        snapshot();
        cells.remove(from);
        cells.add(to, c);
        settle();
    }

    void reverse() {
        snapshot();
        Collections.reverse(cells);
        settle();
    }

    void sort(final Comparator<Page> cmp) {
        snapshot();
        Collections.sort(cells, new Comparator<PageCell>() {
            @Override
            public int compare(PageCell a, PageCell b) {
                return cmp.compare(a.page, b.page);
            }
        });
        settle();
    }

    private void fadeOut(final PageCell c) {
        c.animate().cancel();
        final ViewGroupOverlay overlay = getOverlay();
        overlay.add(c);
        c.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setStartDelay(0)
                .setDuration(180).setInterpolator(DECEL)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        overlay.remove(c);
                    }
                }).start();
    }

    // ------------------------------------------------------- animación FLIP

    private void snapshot() {
        snapshot.clear();
        if (cellW <= 0) return;
        for (PageCell c : cells) {
            snapshot.put(c, new float[]{c.getLeft() + c.getTranslationX(), c.getTop() + c.getTranslationY()});
        }
    }

    /** Recoloca las celdas en su hueco nuevo y las anima desde donde estaban. */
    private void settle() {
        for (int i = 0; i < cells.size(); i++) cells.get(i).setNumber(i + 1);
        layoutCells();
        for (PageCell c : cells) {
            if (c == dragCell) continue;
            float[] old = snapshot.get(c);
            if (old == null) continue;
            float dx = old[0] - c.getLeft(), dy = old[1] - c.getTop();
            if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) continue;
            c.setTranslationX(dx);
            c.setTranslationY(dy);
            c.animate().translationX(0f).translationY(0f).setStartDelay(0)
                    .setDuration(260).setInterpolator(DECEL).start();
        }
        snapshot.clear();
        requestLayout();
    }

    // ---------------------------------------------------------- medida

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int avail = width - getPaddingLeft() - getPaddingRight();
        int minCell = Ui.dp(104);
        columns = Math.max(2, Math.min(8, (avail + gap) / (minCell + gap)));
        cellW = Math.max(0, (avail - gap * (columns - 1)) / columns);
        cellH = Math.round(cellW * RATIO);
        int rows = (cells.size() + columns - 1) / columns;
        int height = getPaddingTop() + getPaddingBottom() + (rows > 0 ? rows * cellH + (rows - 1) * gap : 0);
        int ws = MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY);
        int hs = MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY);
        for (PageCell c : cells) c.measure(ws, hs);
        setMeasuredDimension(width, Math.max(height, getSuggestedMinimumHeight()));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutCells();
        if (dragging) positionDragCell();
    }

    private void layoutCells() {
        if (cellW <= 0) return;
        for (int i = 0; i < cells.size(); i++) {
            PageCell c = cells.get(i);
            int left = getPaddingLeft() + (i % columns) * (cellW + gap);
            int top = getPaddingTop() + (i / columns) * (cellH + gap);
            if (c.getLeft() != left || c.getTop() != top || c.getWidth() != cellW || c.getHeight() != cellH) {
                c.layout(left, top, left + cellW, top + cellH);
            }
        }
    }

    // ---------------------------------------------------------- toques

    private PageCell cellAt(float x, float y) {
        for (int i = cells.size() - 1; i >= 0; i--) {
            PageCell c = cells.get(i);
            float l = c.getLeft() + c.getTranslationX(), t = c.getTop() + c.getTranslationY();
            if (x >= l && x < l + c.getWidth() && y >= t && y < t + c.getHeight()) return c;
        }
        return null;
    }

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (pending && downCell != null) startDrag();
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downCell = cellAt(x, y);
                if (downCell == null) return false;
                downX = x;
                downY = y;
                rawX = e.getRawX();
                rawY = e.getRawY();
                pending = true;
                downCell.animate().scaleX(0.96f).scaleY(0.96f).setStartDelay(0)
                        .setDuration(140).setInterpolator(DECEL).start();
                handler.postDelayed(longPress, longPressTimeout);
                return true;
            case MotionEvent.ACTION_MOVE:
                rawX = e.getRawX();
                rawY = e.getRawY();
                if (dragging) {
                    updateDrag();
                } else if (pending && (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop)) {
                    cancelPending();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    endDrag();
                } else if (pending) {
                    PageCell c = downCell;
                    cancelPending();
                    if (c != null && listener != null) {
                        float cx = x - c.getLeft() - c.getTranslationX();
                        float cy = y - c.getTop() - c.getTranslationY();
                        playSoundEffect(SoundEffectConstants.CLICK);
                        if (c.hitRemove(cx, cy)) listener.onCellRemove(c);
                        else listener.onCellTap(c);
                    }
                }
                downCell = null;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) endDrag();
                else cancelPending();
                downCell = null;
                return true;
            default:
                return true;
        }
    }

    private void cancelPending() {
        pending = false;
        handler.removeCallbacks(longPress);
        if (downCell != null && downCell != dragCell) {
            downCell.animate().scaleX(1f).scaleY(1f).setStartDelay(0)
                    .setDuration(160).setInterpolator(DECEL).start();
        }
    }

    private void startDrag() {
        pending = false;
        dragging = true;
        dragCell = downCell;
        dragStartIndex = cells.indexOf(dragCell);
        ViewParent p = getParent();
        if (p != null) p.requestDisallowInterceptTouchEvent(true);
        dragCell.animate().cancel();
        dragCell.setAlpha(1f);
        getLocationOnScreen(loc);
        float gx = rawX - loc[0], gy = rawY - loc[1];
        offX = gx - (dragCell.getLeft() + dragCell.getTranslationX());
        offY = gy - (dragCell.getTop() + dragCell.getTranslationY());
        dragCell.setLifted(true);
        dragCell.animate().scaleX(1.07f).scaleY(1.07f).translationZ(Ui.dp(14)).setStartDelay(0)
                .setDuration(180).setInterpolator(DECEL).start();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (listener != null) listener.onDragState(true);
        postOnAnimation(autoScroll);
    }

    private void updateDrag() {
        if (dragCell == null) return;
        getLocationOnScreen(loc);
        float gx = rawX - loc[0], gy = rawY - loc[1];
        int target = slotAt(gx, gy);
        int current = cells.indexOf(dragCell);
        if (target >= 0 && current >= 0 && target != current) {
            snapshot();
            cells.remove(current);
            cells.add(target, dragCell);
            settle();
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        }
        positionDragCell();
    }

    /** Deja la celda levantada justo bajo el dedo. */
    private void positionDragCell() {
        if (dragCell == null) return;
        getLocationOnScreen(loc);
        dragCell.setTranslationX(rawX - loc[0] - offX - dragCell.getLeft());
        dragCell.setTranslationY(rawY - loc[1] - offY - dragCell.getTop());
    }

    private int slotAt(float gx, float gy) {
        int n = cells.size();
        if (n == 0 || cellW <= 0) return -1;
        int rows = (n + columns - 1) / columns;
        int col = (int) Math.floor((gx - getPaddingLeft() + gap / 2f) / (cellW + gap));
        int row = (int) Math.floor((gy - getPaddingTop() + gap / 2f) / (cellH + gap));
        col = Math.max(0, Math.min(columns - 1, col));
        row = Math.max(0, Math.min(rows - 1, row));
        return Math.min(row * columns + col, n - 1);
    }

    private void endDrag() {
        dragging = false;
        removeCallbacks(autoScroll);
        PageCell c = dragCell;
        dragCell = null;
        if (c != null) {
            c.setLifted(false);
            c.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).translationZ(0f)
                    .setStartDelay(0).setDuration(240).setInterpolator(DECEL).start();
        }
        if (listener != null) {
            listener.onDragState(false);
            if (c != null && cells.indexOf(c) != dragStartIndex) listener.onOrderChanged();
        }
    }

    /** Desplaza la lista sola cuando arrastras cerca del borde de arriba o de abajo. */
    private final Runnable autoScroll = new Runnable() {
        @Override
        public void run() {
            if (!dragging) return;
            ViewParent p = getParent();
            if (p instanceof ScrollView) {
                ScrollView sv = (ScrollView) p;
                sv.getLocationOnScreen(loc);
                float y = rawY - loc[1];
                float edge = Ui.dp(80);
                float max = Ui.dp(16);
                float bottomEdge = sv.getHeight() - bottomOverlay - edge;
                int dy = 0;
                if (y < edge) {
                    dy = -(int) Math.ceil(max * Math.min(1f, (edge - y) / edge));
                } else if (y > bottomEdge) {
                    dy = (int) Math.ceil(max * Math.min(1f, (y - bottomEdge) / edge));
                }
                if (dy != 0 && sv.canScrollVertically(dy > 0 ? 1 : -1)) {
                    sv.scrollBy(0, dy);
                    updateDrag();
                }
            }
            postOnAnimation(this);
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(longPress);
        removeCallbacks(autoScroll);
    }
}
