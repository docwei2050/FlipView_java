package com.docwei.flipview.flipview;

import android.util.SparseArray;
import android.view.View;

public class Recycler {
    //回收的View 用于复用
	static class Scrap {
		View view;
		boolean valid;

		public Scrap(View scrap, boolean valid) {
			this.view = scrap;
			this.valid = valid;
		}
	}

	/** Unsorted views that can be used by the adapter as a convert view. */
	private SparseArray<Scrap>[] scraps;
	private SparseArray<Scrap> currentScraps;

	private int viewTypeCount;

	void setViewTypeCount(int viewTypeCount) {
		if (viewTypeCount < 1) {
			throw new IllegalArgumentException("Can't have a viewTypeCount < 1");
		}
		// do nothing if the view type count has not changed.
		if (currentScraps != null && viewTypeCount == scraps.length) {
			return;
		}
		SparseArray<Scrap>[] scrapViews = new SparseArray[viewTypeCount];
		for (int i = 0; i < viewTypeCount; i++) {
			scrapViews[i] = new SparseArray<>();
		}
		this.viewTypeCount = viewTypeCount;
		currentScraps = scrapViews[0];
		this.scraps = scrapViews;
	}

	/** @return A view from the ScrapViews collection. These are unordered. */
	Scrap getScrapView(int position, int viewType) {
		if (viewTypeCount == 1) {
			//只有一种类型，直接从当前scraps中找
			return retrieveFromScrap(currentScraps, position);
		} else if (viewType >= 0 && viewType < scraps.length) {
			//根据类型找
			return retrieveFromScrap(scraps[viewType], position);
		}
		return null;
	}

	void addScrapView(View scrap, int position, int viewType) {
		Scrap item = new Scrap(scrap, true);
		if (viewTypeCount == 1) {
			currentScraps.put(position, item);
		} else {
			scraps[viewType].put(position, item);
		}
	}

	private static Scrap retrieveFromScrap(SparseArray<Scrap> scrapViews, int position) {
		int size = scrapViews.size();
		if (size > 0) {
			// See if we still have a view for this position.
			//是否指定位置有垃圾View可复用
			Scrap result = scrapViews.get(position, null);
			if (result != null) {
				//可以用 就从容器删除它
				scrapViews.remove(position);
				return result;
			}
			//没有就直接从容器中拿
			int index = size - 1;
			result = scrapViews.valueAt(index);
			scrapViews.removeAt(index);
			result.valid = false;
			return result;
		}
		return null;
	}

	void invalidateScraps() {
		//使所有的失效
		for (SparseArray<Scrap> array : scraps) {
			for (int i = 0; i < array.size(); i++) {
				array.valueAt(i).valid = false;
			}
		}
	}

}
