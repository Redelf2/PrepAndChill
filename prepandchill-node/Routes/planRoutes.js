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
    if (!examDate) return 30; // Default to 30 days if no date set

    const d = new Date(examDate);
    if (Number.isNaN(d.getTime())) return 30;

    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const startOfExam = new Date(d.getFullYear(), d.getMonth(), d.getDate());

    const diffMs = startOfExam.getTime() - startOfToday.getTime();
    const days = Math.ceil(diffMs / (1000 * 60 * 60 * 24));

    return Math.max(1, days);
}

router.post("/generatePlan", (req, res) => {
    const { firebase_uid, total_daily_hours } = req.body;

    if (!firebase_uid) {
        return res.status(400).json({ success: false, error: "firebase_uid is required" });
    }

    const totalHours = parseHours(total_daily_hours, 6);
    const minMinutesPerSubject = 30;

    const sql = `
        SELECT
            s.name AS subject,
            COALESCE(us.difficulty, 2) AS difficulty,
            COALESCE(us.confidence, 50) AS confidence,
            us.exam_date AS exam_date
        FROM user_subjects us
        JOIN users u ON u.id = us.user_id
        JOIN subjects s ON s.id = us.subject_id
        WHERE u.firebase_uid = ?
    `;

    db.query(sql, [firebase_uid], (err, rows) => {
        if (err) {
            console.error("Planner Error:", err);
            return res.status(500).json({
                success: false,
                error: `Database Error: ${err.message}. Please ensure user_subjects table has columns: difficulty, confidence, exam_date.`
            });
        }

        if (!rows || rows.length === 0) {
            return res.json({ success: true, plan: [], message: "No subjects found for this user." });
        }

        const items = rows.map(r => {
            const subject = String(r.subject);
            const difficulty = clampInt(r.difficulty, 1, 3);
            const confidence = clampInt(r.confidence, 0, 100);
            const days_left = toDaysLeft(r.exam_date);

            // Calculation logic: prioritize lower confidence, higher difficulty, and closer exam dates
            const score =
                (100 - confidence) / 10 +
                difficulty * 5 +
                (20 / days_left);

            return {
                subject,
                difficulty,
                confidence,
                exam_date: r.exam_date,
                days_left,
                score
            };
        });

        items.sort((a, b) => b.score - a.score);

        const totalScore = items.reduce((sum, it) => sum + it.score, 0);
        const totalMinutes = Math.round(totalHours * 60);

        const planWithMinutes = items.map(it => {
            const proportional = totalScore > 0 ? (it.score / totalScore) * totalMinutes : (totalMinutes / items.length);
            const minutes = Math.max(minMinutesPerSubject, Math.round(proportional));
            return { ...it, minutes };
        });

        const plan = planWithMinutes.map(p => ({
            subject: p.subject,
            time_hours: Number((p.minutes / 60).toFixed(2)),
            time_minutes: p.minutes,
            score: Number(p.score.toFixed(4)),
            difficulty: p.difficulty,
            confidence: p.confidence,
            days_left: p.days_left
        }));

        res.json({ success: true, plan });
    });
});

module.exports = router;