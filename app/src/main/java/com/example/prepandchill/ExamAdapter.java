package com.example.prepandchill;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ExamAdapter extends RecyclerView.Adapter<ExamAdapter.ExamViewHolder> {

    private List<ExamOption> examList;
    private OnExamClickListener listener;

    public interface OnExamClickListener {
        void onExamClick(int position);
    }

    public ExamAdapter(List<ExamOption> examList, OnExamClickListener listener) {
        this.examList = examList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ExamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_exam_option, parent, false);
        return new ExamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExamViewHolder holder, int position) {
        ExamOption option = examList.get(position);
        holder.tvEmoji.setText(option.getEmoji());
        holder.tvName.setText(option.getName());

        if (option.isSelected()) {
            holder.itemView.setBackgroundResource(R.drawable.bg_option_selected);
            holder.tvName.setTextColor(android.graphics.Color.WHITE);
        } else {
            holder.itemView.setBackgroundResource(R.drawable.bg_option_unselected);
            holder.tvName.setTextColor(android.graphics.Color.parseColor("#CBD5E1"));
        }

        holder.itemView.setOnClickListener(v -> listener.onExamClick(position));
    }

    @Override
    public int getItemCount() { return examList.size(); }

    public static class ExamViewHolder extends RecyclerView.ViewHolder {
        TextView tvEmoji, tvName;

        public ExamViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmoji = itemView.findViewById(R.id.tvExamEmoji);
            tvName = itemView.findViewById(R.id.tvExamName);
        }
    }
}