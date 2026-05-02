const express = require("express");
const router = express.Router();
const db = require("../db");

// ---------- Helpers ----------
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

    const diffMs = startOfExam - startOfToday;
    return Math.max(1, Math.ceil(diffMs / (1000 * 60 * 60 * 24)));
}


// ==========================
//  GENERATE PLAN (READ ONLY)
// ==========================
router.post("/generatePlan", (req, res) => {

    const { firebase_uid, total_daily_hours } = req.body;

    if (!firebase_uid) {
        return res.status(400).json({ success: false, error: "firebase_uid is required" });
    }

    const totalHours = parseHours(total_daily_hours, 6);

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
            return res.status(500).json({ success: false, error: "Database error" });
        }

        if (!rows || rows.length === 0) {
            return res.json({ success: true, plan: [] });
        }

        // ---------- SMART SCORING ----------
        const items = rows.map(r => {

            const difficulty = clampInt(r.difficulty, 1, 3);
            const confidence = clampInt(r.confidence, 0, 100);
            const days_left = toDaysLeft(r.exam_date);

            const total = r.total_topics || 5;
            const completed = r.completed_topics || 0;

            const remaining = Math.max(0.1, (total - completed) / total);
            const progress = completed / total;

            const urgency = Math.exp(-days_left / 7);
            const weakness = Math.pow((100 - confidence) / 100, 1.6);
            const difficultyFactor = difficulty / 3;
            const momentum = progress > 0.3 ? 1.2 : 1.0;

            const score =
                (urgency * urgency * 6) +
                (weakness * 5) +
                (remaining * 4) +
                (difficultyFactor * 2) +
                (momentum * 1.5);

            return {
                subject: r.subject,
                subject_id: r.subject_id,
                difficulty,
                confidence,
                days_left,
                total_topics: total,
                completed_topics: completed,
                remaining,
                score
            };
        });

        items.sort((a, b) => b.score - a.score);

        // ---------- TIME DISTRIBUTION ----------
        const totalScore = items.reduce((sum, it) => sum + it.score, 0);
        const totalMinutesAvailable = Math.round(totalHours * 60);

        const MAX_SUBJECT_RATIO = 0.4;
        const MIN_MINUTES = 25;

        const plan = items.map(it => {

            let proportional = totalScore > 0
                ? (it.score / totalScore) * totalMinutesAvailable
                : (totalMinutesAvailable / items.length);

            proportional = Math.min(proportional, totalMinutesAvailable * MAX_SUBJECT_RATIO);

            const minutes = Math.max(MIN_MINUTES, Math.round(proportional));

            const learning = Math.round(minutes * it.remaining);
            const revision = minutes - learning;

            return {
                subject: it.subject,
                subject_id: it.subject_id,
                time_hours: Number((minutes / 60).toFixed(2)),
                time_minutes: minutes,
                split: {
                    learning_minutes: learning,
                    revision_minutes: revision
                },
                priority_score: it.score.toFixed(2),
                insights: {
                    urgency: it.days_left + " days left",
                    weakness: (100 - it.confidence) + "%",
                    progress: `${it.completed_topics}/${it.total_topics}`,
                    focus: it.remaining > 0.6 ? "New Learning" : "Revision"
                }
            };
        });

        // OPTIONAL: Save history
        db.query("SELECT id FROM users WHERE firebase_uid = ?", [firebase_uid], (err, userRows) => {
            if (!err && userRows.length) {
                const userId = userRows[0].id;

                plan.forEach(p => {
                    db.query(
                        "INSERT INTO plan_history (user_id, subject_id, allocated_minutes, plan_date) VALUES (?, ?, ?, CURDATE())",
                        [userId, p.subject_id, p.time_minutes]
                    );
                });
            }
        });

        res.json({ success: true, plan });
    });
});

module.exports = router;