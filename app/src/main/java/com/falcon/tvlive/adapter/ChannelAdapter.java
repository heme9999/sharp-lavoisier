package com.falcon.tvlive.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.falcon.tvlive.R;
import com.falcon.tvlive.model.Channel;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel, int position);
    }

    private List<Channel> channels = new ArrayList<>();
    private Channel currentPlayingChannel;
    private OnChannelClickListener listener;

    public ChannelAdapter(OnChannelClickListener listener) {
        this.listener = listener;
    }

    public void setChannels(List<Channel> channels) {
        this.channels = channels;
        notifyDataSetChanged();
    }

    public void setCurrentPlayingChannel(Channel channel) {
        this.currentPlayingChannel = channel;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel ch = channels.get(position);
        holder.tvNum.setText(String.format("%02d", ch.getNum()));
        holder.tvName.setText(ch.getName());
        holder.tvSourceCount.setText(ch.getSources().size() + "线");

        if (!TextUtils.isEmpty(ch.getLogo())) {
            Glide.with(holder.itemView.getContext())
                    .load(ch.getLogo())
                    .placeholder(android.R.color.transparent)
                    .error(android.R.color.transparent)
                    .into(holder.ivLogo);
            holder.ivLogo.setVisibility(View.VISIBLE);
        } else {
            holder.ivLogo.setVisibility(View.GONE);
        }

        boolean isPlaying = (currentPlayingChannel != null && currentPlayingChannel.getNum() == ch.getNum());
        holder.itemView.setSelected(isPlaying);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChannelClick(ch, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return channels.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNum;
        ImageView ivLogo;
        TextView tvName;
        TextView tvSourceCount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNum = itemView.findViewById(R.id.tvChannelNum);
            ivLogo = itemView.findViewById(R.id.ivChannelLogo);
            tvName = itemView.findViewById(R.id.tvChannelName);
            tvSourceCount = itemView.findViewById(R.id.tvSourceCount);
        }
    }
}
