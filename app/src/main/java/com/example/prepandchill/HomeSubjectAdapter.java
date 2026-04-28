package com.example.prepandchill;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class HomeSubjectAdapter extends RecyclerView.Adapter<HomeSubjectAdapter.HomeSubjectViewHolder> {

    private List<Subject> subjectList;
    private final Map<String, String> subjectToDuration;

    public HomeSubjectAdapter(List<Subject> subjectList) {
        this.subjectList = subjectList;
        this.subjectToDuration = null;
    }

    public HomeSubjectAdapter(List<Subject> subjectList, Map<String, String> subjectToDuration) {
        this.subjectList = subjectList;
        this.subjectToDuration = subjectToDuration;
    }

    @NonNull
    @Override
    public HomeSubjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_subject, parent, false);
        return new HomeSubjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HomeSubjectViewHolder holder, int position) {
        Subject subject = subjectList.get(position);
        
        String fullName = subject.getName();
        holder.tvFullName.setText(fullName);
        
        if (fullName.length() >= 3) {
            holder.tvShortName.setText(fullName.substring(0, 3).toUpperCase());
        } else {
            holder.tvShortName.setText(fullName.toUpperCase());
        }

        String duration = null;
        if (subjectToDuration != null) {
            duration = subjectToDuration.get(fullName);
            if (duration == null) {
                duration = subjectToDuration.get(fullName.trim());
            }
        }
        holder.tvTime.setText(duration != null ? duration : "2h 00m");
    }

    @Override
    public int getItemCount() {
        return subjectList.size();
    }

    public static class HomeSubjectViewHolder extends RecyclerView.ViewHolder {
        TextView tvShortName, tvFullName, tvTime;

        public HomeSubjectViewHolder(@NonNull View itemView) {
            super(itemView);
            tvShortName = itemView.findViewById(R.id.tvSubjectShortName);
            tvFullName = itemView.findViewById(R.id.tvSubjectFullName);
            tvTime = itemView.findViewById(R.id.tvSubjectTime);
        }
    }
}