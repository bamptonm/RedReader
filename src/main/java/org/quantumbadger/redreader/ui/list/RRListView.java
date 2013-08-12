/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader.ui.list;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import org.quantumbadger.redreader.ui.RRFragmentContext;

import java.util.LinkedList;

public final class RRListView extends SurfaceView implements SurfaceHolder.Callback {

	private final RRListViewContents contents = new RRListViewContents(this);
	private volatile RRListViewContents.RRListViewFlattenedContents flattenedContents;

	private final RRListViewCacheManager cacheManager = new RRListViewCacheManager();

	private volatile int width, height;

	private int firstVisibleItemPos = 0, lastVisibleItemPos = -1;
	private int pxInFirstVisibleItem = 0;

	private int oldWidth = -1;

	private volatile SurfaceThread surfaceThread;
	private volatile RenderThread renderThread;
	private volatile boolean isPaused = true;

	private final RRFragmentContext context;

	private final Paint backgroundPaint = new Paint();

	public RRListView(RRFragmentContext context) {
		super(context.activity);
		this.context = context;
		setWillNotDraw(false);
		getHolder().addCallback(this);
		backgroundPaint.setColor(Color.BLUE);
	}

	public void onChildAppended() {

		flattenedContents = contents.getFlattenedContents();
		if(flattenedContents.itemCount - 2 == lastVisibleItemPos) {
			recalculateLastVisibleItem();
			postInvalidate();
		}
	}

	public void onChildInserted() {
		// TODO invalidate
		throw new UnsupportedOperationException();
	}

	public void onChildrenRecomputed() {
		// TODO invalidate. If previous top child is now invisible, go to the next one visible one after it in the list
		flattenedContents = contents.getFlattenedContents();
		recalculateLastVisibleItem();
		postInvalidate();
	}

	public RRListViewContents getContents() {
		return contents;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		width = MeasureSpec.getSize(widthMeasureSpec);
		height = MeasureSpec.getSize(heightMeasureSpec);

		setMeasuredDimension(width, height);

		if(width != oldWidth) {
			recalculateLastVisibleItem();
			oldWidth = width;
		}
	}

	public void scrollBy(int px) {

		pxInFirstVisibleItem += px;

		while(pxInFirstVisibleItem < 0) {

			if(firstVisibleItemPos == 0) {
				pxInFirstVisibleItem = 0;
			} else {
				firstVisibleItemPos--;
				pxInFirstVisibleItem += flattenedContents.items[firstVisibleItemPos].height;
			}
		}

		while(pxInFirstVisibleItem >= flattenedContents.items[firstVisibleItemPos].height) {
			pxInFirstVisibleItem -= flattenedContents.items[firstVisibleItemPos].height;
			firstVisibleItemPos++;
		}

		recalculateLastVisibleItem();
	}

	public void recalculateLastVisibleItem() {

		final int width = this.width;

		final RRListViewContents.RRListViewFlattenedContents fc = flattenedContents;
		int pos = (int) (fc.items[firstVisibleItemPos].measureHeight(width) - pxInFirstVisibleItem);
		int lastVisibleItemPos = firstVisibleItemPos;

		while(pos <= height && lastVisibleItemPos < fc.itemCount - 1) {
			lastVisibleItemPos++;
			pos += fc.items[lastVisibleItemPos].measureHeight(width);
		}

		this.lastVisibleItemPos = lastVisibleItemPos;

		cacheManager.update(fc, firstVisibleItemPos, lastVisibleItemPos, 10, renderThread);
	}

	private int downId = -1;
	private int lastYPos = -1;

	@Override
	public boolean onTouchEvent(MotionEvent ev) {

		final int action = ev.getAction() & MotionEvent.ACTION_MASK;

		if(action == MotionEvent.ACTION_DOWN) {

			lastYPos = Math.round(ev.getY());
			downId = ev.getPointerId(0);
			return true;

		} else if(action == MotionEvent.ACTION_UP
				|| action == MotionEvent.ACTION_OUTSIDE
				|| action == MotionEvent.ACTION_CANCEL
				|| action == MotionEvent.ACTION_POINTER_UP) {

			if(ev.getPointerId(ev.getActionIndex()) != downId) return false;
			downId = -1;

			return false;

		} else if(action == MotionEvent.ACTION_MOVE) {

			if(ev.getPointerId(ev.getActionIndex()) != downId) return false;

			final int yDelta = Math.round(ev.getY() - lastYPos);
			lastYPos = Math.round(ev.getY());

			scrollBy(-yDelta);
			//invalidate();

			return true;

		} else return false;
	}

	public void resume() {
		isPaused = false;
		renderThread = new RenderThread(); // TODO this is unsafe - two threads may run at once
		renderThread.start();
	}

	public void pause() {
		isPaused = true;
		renderThread.interrupt();
	}

	private void doDraw(final Canvas canvas) {

		if(flattenedContents == null) return;

		canvas.drawRect(0, 0, width, height, backgroundPaint);

		final RRListViewContents.RRListViewFlattenedContents fc = flattenedContents;

		canvas.translate(0, -pxInFirstVisibleItem);

		for(int i = firstVisibleItemPos; i <= lastVisibleItemPos; i++) {
			fc.items[i].draw(canvas, width);
			canvas.translate(0, fc.items[i].height);
		}
	}

	public void surfaceCreated(SurfaceHolder holder) {
		surfaceThread = new SurfaceThread();
		surfaceThread.start();
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		surfaceThread.surfaceHolder = holder;
	}

	public void surfaceDestroyed(SurfaceHolder holder) {

		surfaceThread.running = false;

		try {
			surfaceThread.join();
		} catch(InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	// TODO low priority
	protected final class RenderThread extends Thread {

		// TODO optimise
		private final LinkedList<RRListViewItem> toRender = new LinkedList<RRListViewItem>();

		public void add(RRListViewItem item) {
			synchronized(toRender) {
				toRender.add(item);
				toRender.notify();
			}
		}

		@Override
		public void run() {

			synchronized(toRender) {
				while(!isPaused) {

					if(toRender.isEmpty()) {
						try {
							toRender.wait();
						} catch(InterruptedException e) {}
					}

					if(!toRender.isEmpty()) {
						toRender.removeFirst().doCacheRender(width, false);
					}
				}
			}
		}
	}

	protected final class SurfaceThread extends Thread {

		public volatile boolean running = true;
		SurfaceHolder surfaceHolder;

		public SurfaceThread() {
			surfaceHolder = getHolder();
		}

		@Override
		public void run() {

			while(running) {

				final Canvas canvas = surfaceHolder.lockCanvas();
				if(canvas != null) {
					doDraw(canvas);
					surfaceHolder.unlockCanvasAndPost(canvas);
				}
			}
		}
	}
}
