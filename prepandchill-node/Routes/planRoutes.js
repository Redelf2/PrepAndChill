const express = require("express");
const router = express.Router();
const db = require("../db");
const util = require("util");

const queryAsync = util.promisify(db.query).bind(db);

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

// ---------- Route ----------
router.post("/generatePlan", async (req, res) => {

    const { firebase_uid, total_daily_hours, setup_data } = req.body;

    if (!firebase_uid) {
        return res.status(400).json({ success: false, error: "firebase_uid is required" });
    }

    // --- TRANSACTIONAL SETUP (Only runs if setup_data is provided) ---
    if (setup_data && Array.isArray(setup_data)) {
        try {
            await queryAsync("START TRANSACTION");

            const userRows = await queryAsync("SELECT id FROM users WHERE firebase_uid = ?", [firebase_uid]);
            if (!userRows.length) throw new Error("User not found");
            const userId = userRows[0].id;

            for (const sub of setup_data) {
                // Insert/Update Subject setup
                await queryAsync(
                    `INSERT INTO user_subjects (user_id, subject_id, exam_date, confidence, difficulty) 
                     VALUES (?, ?, ?, ?, 2) 
                     ON DUPLICATE KEY UPDATE exam_date = VALUES(exam_date), confidence = VALUES(confidence)`,
                    [userId, sub.subject_id, sub.exam_date || null, sub.confidence || 50]
                );

                // Insert/Update completed topics
                if (Array.isArray(sub.completed_topic_ids)) {
                    for (const topicId of sub.completed_topic_ids) {
                        await queryAsync(
                            `INSERT INTO user_topics (user_id, topic_id, is_completed) 
                             VALUES (?, ?, 1) 
                             ON DUPLICATE KEY UPDATE is_completed = 1`,
                            [userId, topicId]
                        );
                    }
                }
            }
            await queryAsync("COMMIT");
            console.log("Setup Transaction Committed successfully!");
        } catch (error) {
            await queryAsync("ROLLBACK");
            console.error("Setup Transaction Rollback:", error);
            return res.status(500).json({ success: false, error: "Setup transaction failed and was rolled back." });
        }
    }
    // -----------------------------------------------------------------

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
                subject_id: r.subject_id, // 🔥 IMPORTANT for history saving
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

            // Burnout control
            proportional = Math.min(proportional, totalMinutesAvailable * MAX_SUBJECT_RATIO);

            const minutes = Math.max(MIN_MINUTES, Math.round(proportional));

            const learning = Math.round(minutes * it.remaining);
            const revision = minutes - learning;

            return {
                subject: it.subject,
                subject_id: it.subject_id, // 🔥 carry forward

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
                    focus: it.remaining > 0.6 ? "New Learning" : "Revision Focus"
                }
            };
        });

    
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