const express = require("express");
const router = express.Router();
const db = require("../db");

function clampInt(n, min, max) {
    const x = Number.parseInt(n, 10);
    if (Number.isNaN(x)) return min;
    return Math.max(min, Math.min(max, x));
}

function parseHours(n, fallback) {
    const x = Number(n);
    if (!Number.isFinite(x) || x <= 0) return fallback;
    return x;
}

function toDaysLeft(examDate) {
    if (!examDate) return 30;
    const d = new Date(examDate);
    if (Number.isNaN(d.getTime())) return 30;
    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const startOfExam = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    const diffMs = startOfExam.getTime() - startOfToday.getTime();
    return Math.max(1, Math.ceil(diffMs / (1000 * 60 * 60 * 24)));
}

router.post("/generatePlan", (req, res) => {
    const { firebase_uid, total_daily_hours } = req.body;

    if (!firebase_uid) {
        return res.status(400).json({ success: false, error: "firebase_uid is required" });
    }

    const totalHours = parseHours(total_daily_hours, 6);
    const minMinutesPerSubject = 30;

    // Enhanced SQL to count syllabus progress
    const sql = `
        SELECT
            s.id AS subject_id,
            s.name AS subject,
            COALESCE(us.difficulty, 2) AS difficulty,
            COALESCE(us.confidence, 50) AS confidence,
            us.exam_date AS exam_date,
            (SELECT COUNT(*) FROM topics t WHERE t.subject_id = s.id) AS total_topics,
            (SELECT COUNT(*) FROM user_topics ut
             JOIN users u ON u.id = ut.user_id
             WHERE ut.topic_id IN (SELECT id FROM topics WHERE subject_id = s.id)
             AND u.firebase_uid = ? AND ut.is_completed = 1) AS completed_topics
        FROM user_subjects us
        JOIN users u ON u.id = us.user_id
        JOIN subjects s ON s.id = us.subject_id
        WHERE u.firebase_uid = ?
    `;

    db.query(sql, [firebase_uid, firebase_uid], (err, rows) => {
        if (err) {
            console.error("Planner Error:", err);
            return res.status(500).json({ success: false, error: "Database error during plan generation." });
        }

        if (!rows || rows.length === 0) {
            return res.json({ success: true, plan: [] });
        }

        const items = rows.map(r => {
            const difficulty = clampInt(r.difficulty, 1, 3);
            const confidence = clampInt(r.confidence, 0, 100);
            const days_left = toDaysLeft(r.exam_date);
            const total = r.total_topics || 5; // Default to 5 if syllabus not defined
            const completed = r.completed_topics || 0;

            // syllabus_factor: 1.0 if not started, 0.1 if almost done
            const syllabus_remaining_ratio = Math.max(0.1, (total - completed) / total);

            // Calculation logic: Syllabus Progress * (Inverted Confidence + Difficulty + Urgency)
            const score = syllabus_remaining_ratio * (
                ((100 - confidence) / 10) +
                (difficulty * 5) +
                (30 / days_left)
            );

            return {
                subject: r.subject,
                difficulty,
                confidence,
                days_left,
                total_topics: total,
                completed_topics: completed,
                score
            };
        });

        items.sort((a, b) => b.score - a.score);

        const totalScore = items.reduce((sum, it) => sum + it.score, 0);
        const totalMinutesAvailable = Math.round(totalHours * 60);

        const plan = items.map(it => {
            const proportional = totalScore > 0 ? (it.score / totalScore) * totalMinutesAvailable : (totalMinutesAvailable / items.length);
            const minutes = Math.max(minMinutesPerSubject, Math.round(proportional));

            return {
                subject: it.subject,
                time_hours: Number((minutes / 60).toFixed(2)),
                time_minutes: minutes,
                difficulty: it.difficulty,
                confidence: it.confidence,
                progress: `${it.completed_topics}/${it.total_topics} topics done`
            };
        });

        res.json({ success: true, plan });
    });
});

module.exports = router;