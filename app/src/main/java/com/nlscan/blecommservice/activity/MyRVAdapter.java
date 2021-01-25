package com.nlscan.blecommservice.activity;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.nlscan.blecommservice.R;

import java.util.List;

public class MyRVAdapter extends RecyclerView.Adapter<MyRVAdapter.MyTVHolder> {

    private final LayoutInflater mLayoutInflater;
    private final Context mContext;
    private  List<String> mData;

    public MyRVAdapter(Context context, List<String> dataList) {
        mLayoutInflater = LayoutInflater.from(context);
        mContext = context;

        mData = dataList;
    }

    @Override
    public MyRVAdapter.MyTVHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new MyRVAdapter.MyTVHolder(mLayoutInflater.inflate(R.layout.text_item, parent, false));
    }

    @Override
    public void onBindViewHolder(final MyRVAdapter.MyTVHolder holder, int pos) {
        holder.mTextView.setText(mData.get(pos));
        holder.mTextView.setBackgroundColor(pos % 2 == 0 ?mContext.getResources().getColor(R.color.ivory):
                mContext.getResources().getColor(R.color.wheat));
    }

    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.size();
    }

    class MyTVHolder extends RecyclerView.ViewHolder {
        TextView mTextView;

        MyTVHolder(View itemView) {
            super(itemView);
            mTextView = (TextView) itemView.findViewById(R.id.tv_txt);
        }
    }
}