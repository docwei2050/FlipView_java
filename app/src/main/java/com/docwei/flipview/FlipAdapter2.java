package com.docwei.flipview;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class FlipAdapter2 extends BaseAdapter {
	public List<String> mList = new ArrayList<>();

	public FlipAdapter2(List<String> mList) {
		this.mList = mList;
	}

	@Override
	public int getCount() {
		return mList.size();
	}


	@Override
	public Object getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder;
		if (convertView == null) {
			convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.page2, null);
			holder = new ViewHolder(convertView);
			convertView.setTag(holder);
			Log.e("flipPage","创建ViewHOlder");
		} else {
			holder = (ViewHolder) convertView.getTag();
		}
		Glide
				.with(holder.iv)
				.load(mList.get(position))
				.centerCrop()
				.placeholder(R.mipmap.course_pic_holder)
				.into(holder.iv);
		return convertView;
	}

	static class ViewHolder {
		public ImageView iv;
		public ViewHolder(View itemView) {
			iv = itemView.findViewById(R.id.iv);
		}
	}
}
