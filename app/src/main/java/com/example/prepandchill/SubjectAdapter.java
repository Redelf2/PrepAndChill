package com.example.prepandchill;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SubjectAdapter extends RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder> {

    private List<Subject> subjectList;
    private OnSubjectClickListener listener;

    public interface OnSubjectClickListener {
        void onSubjectClick(int position);
        void onCalendarClick(int position);
        void onDeleteClick(int position);
    }

    public SubjectAdapter(List<Subject> subjectList, OnSubjectClickListener listener) {
        this.subjectList = subjectList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subject, parent, false);
        return new SubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SubjectViewHolder holder, int position) {
        Subject subject = subjectList.get(position);
        holder.tvSubjectName.setText(subject.getName());
        holder.tvExamDate.setText(subject.getExamDate());

        if (subject.isSelected()) {
            holder.checkbox.setBackgroundResource(R.drawable.bg_checkbox_checked);
            holder.itemView.setAlpha(1.0f);
        } else {
            holder.checkbox.setBackgroundResource(R.drawable.bg_checkbox_unchecked);
            holder.itemView.setAlpha(0.7f);
        }

        holder.itemView.setOnClickListener(v -> listener.onSubjectClick(position));
        holder.btnCalendar.setOnClickListener(v -> listener.onCalendarClick(position));
        holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(position));
    }

    @Override
    public int getItemCount() {
        return subjectList.size();
    }

    public static class SubjectViewHolder extends RecyclerView.ViewHolder {
        TextView tvSubjectName, tvExamDate;
        View checkbox;
        View btnCalendar;
        ImageView btnDelete;

        public SubjectViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubjectName = itemView.findViewById(R.id.tvSubjectName);
            tvExamDate = itemView.findViewById(R.id.tvExamDate);
            checkbox = itemView.findViewById(R.id.checkbox);
            btnCalendar = itemView.findViewById(R.id.btnCalendar);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
