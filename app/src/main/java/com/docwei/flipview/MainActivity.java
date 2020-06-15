package com.docwei.flipview;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;


import com.docwei.flipview.flipview.FlipView;
import com.docwei.flipview.flipview.OverFlipMode;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements FlipAdapter.Callback, FlipView.OnFlipListener, FlipView.OnOverFlipListener {


    private FlipView mFlipView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFlipView = (FlipView) findViewById(R.id.flip_view);
        List<String> list = new ArrayList<>();
        list.add("http://pic1.win4000.com/wallpaper/2/5476ea01904b1.jpg");
        list.add("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1592198405744&di=0db325aeea48f66a9d39c6812e1c08ee&imgtype=0&src=http%3A%2F%2Fimg3.imgtn.bdimg.com%2Fit%2Fu%3D2580930663%2C1160291232%26fm%3D214%26gp%3D0.jpg");
        list.add("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1592198246189&di=1f09c03490fedf29186bb16bab620fa9&imgtype=0&src=http%3A%2F%2Fpic1.win4000.com%2Fwallpaper%2F2017-10-13%2F59e0270c6ba4e.jpg");
        list.add("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1592198285159&di=422817270e656f502ddd2df6b51e3bfd&imgtype=0&src=http%3A%2F%2Fpic1.win4000.com%2Fwallpaper%2F1%2F590d97dde6b6e.jpg");
        list.add("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1592198336543&di=eb74e929d0e5251b6422e36d693c3590&imgtype=0&src=http%3A%2F%2Fdik.img.kttpdq.com%2Fpic%2F69%2F47769%2Fca8f48c2a8363dcc.jpg");
        list.add("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1592198409143&di=68844679e7cacb12a7863601dfb17cb5&imgtype=0&src=http%3A%2F%2Finews.gtimg.com%2Fnewsapp_match%2F0%2F11069045374%2F0.jpg");
        list.add("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1592198406817&di=8c371978f5fe9ca0b12c6380d511e743&imgtype=0&src=http%3A%2F%2Fpic1.win4000.com%2Fwallpaper%2F4%2F599e7295c9c94.jpg");
        list.add("https://timgsa.baidu.com/timg?image&quality=80&size=b9999_10000&sec=1592198336543&di=eb74e929d0e5251b6422e36d693c3590&imgtype=0&src=http%3A%2F%2Fdik.img.kttpdq.com%2Fpic%2F69%2F47769%2Fca8f48c2a8363dcc.jpg");
        list.add("http://pic1.win4000.com/wallpaper/e/5477dddb66990.jpg");
        list.add("http://pic1.win4000.com/wallpaper/6/5477d970f202f.jpg");
        list.add("http://pic1.win4000.com/wallpaper/6/5477d9870535e.jpg");
        list.add("http://pic1.win4000.com/wallpaper/2/5476ea0481e4e.jpg");
        list.add("http://pic1.win4000.com/wallpaper/1/5476e5c57f3af.jpg");
        list.add("http://pic1.win4000.com/wallpaper/1/5476e5cb68c19.jpg");
        list.add("http://pic1.win4000.com/wallpaper/1/5476e5d274f4b.jpg");
        list.add("http://pic1.win4000.com/wallpaper/e/5476e58d9d7ad.jpg");
        list.add("http://pic1.win4000.com/wallpaper/e/5476e5964a788.jpg");
        list.add("http://pic1.win4000.com/wallpaper/7/5476e52f2d2d5.jpg");


        //加载文本
       /* FlipAdapter mAdapter = new FlipAdapter(this);
        mFlipView.setAdapter(mAdapter);
        mAdapter.setCallback(this);*/

       //图片
        FlipAdapter2 adapter2 = new FlipAdapter2(list);
        mFlipView.setAdapter(adapter2);
        mFlipView.setOnFlipListener(this);
        mFlipView.peakNext(true);
        mFlipView.setEmptyView(findViewById(R.id.empty_view));
        mFlipView.setOnOverFlipListener(this);


    }


    @Override
    public void onPageRequested(int page) {
        mFlipView.smoothFlipTo(page);
    }

    @Override
    public void onFlippedToPage(FlipView v, int position, long id) {
        Log.i("pageflip", "Page: " + position);

    }

    @Override
    public void onOverFlip(FlipView v, OverFlipMode mode,
                           boolean overFlippingPrevious, float overFlipDistance,
                           float flipDistancePerPage) {
        Log.i("overflip", "overFlipDistance = " + overFlipDistance);
    }

   /* public void addData(View view) {
        List<FlipAdapter.Item> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            FlipAdapter.Item item = new FlipAdapter.Item();
            items.add(item);
        }
        mAdapter.updateData(items);
    }*/
}
