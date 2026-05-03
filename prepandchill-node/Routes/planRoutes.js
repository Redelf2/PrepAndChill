const express = require("express");
const router = express.Router();
const db = require("../db");
const { buildPlan } = require("../services/smartPlanner");

function queryAsync(sql, params = []) {
    return new Promise((resolve, reject) => {
        db.query(sql, params, (err, results) => {
            if (err) reject(err);
            else resolve(results);
        });
    });
}

/** Per-user syllabus + progress for planner */
const SUBJECT_PROGRESS_SQL = `
    SELECT
        s.id AS subject_id,
        s.name AS subject,
        us.difficulty AS difficulty,
        us.confidence AS confidence,
        us.exam_date AS exam_date,
        (
            SELECT COUNT(*) FROM topics t WHERE t.subject_id = s.id
        ) AS total_topics,
        (
            SELECT COUNT(*) FROM user_topics ut
            INNER JOIN users u ON u.id = ut.user_id
            WHERE ut.topic_id IN (
                SELECT id FROM topics t2 WHERE t2.subject_id = s.id
            )
            AND u.firebase_uid = ?
            AND ut.is_completed = 1
        ) AS completed_topics
    FROM user_subjects us
    INNER JOIN users u ON u.id = us.user_id
    INNER JOIN subjects s ON s.id = us.subject_id
    WHERE u.firebase_uid = ?
`;

// ==========================
//  GENERATE PLAN
// ==========================
router.post("/generatePlan", async (req, res) => {
    const { firebase_uid, total_daily_hours, vary_plan } = req.body;

    if (!firebase_uid) {
        return res
            .status(400)
            .json({ success: false, error: "firebase_uid is required" });
    }

    const dateKey = new Date().toISOString().slice(0, 10);

    try {
        const rows = await queryAsync(SUBJECT_PROGRESS_SQL, [
            firebase_uid,
            firebase_uid,
        ]);

        const useJitter = Boolean(vary_plan);
        const jitterSeed = `${firebase_uid}:${dateKey}`;

        const { plan, meta, total_minutes_budget } = buildPlan(
            rows,
            total_daily_hours,
            {
                useJitter,
                jitterSeed,
            }
        );

        if (plan.length > 0) {
            try {
                const userRows = await queryAsync(
                    "SELECT id FROM users WHERE firebase_uid = ? LIMIT 1",
                    [firebase_uid]
                );
                const userId = userRows.length ? userRows[0].id : null;

                if (userId != null && plan.length > 0) {
                    const values = plan.map(() => "(?, ?, ?, CURDATE())").join(", ");
                    const flat = [];
                    plan.forEach((p) => {
                        flat.push(userId, p.subject_id, p.time_minutes);
                    });
                    await queryAsync(
                        `INSERT INTO plan_history (user_id, subject_id, allocated_minutes, plan_date) VALUES ${values}`,
                        flat
                    );
                }
            } catch (histErr) {
                console.error("plan_history persist failed:", histErr);
                // non-fatal: still return computed plan
            }
        }

        return res.json({
            success: true,
            plan,
            meta: {
                ...meta,
                total_minutes_budget,
                jitter_seed_used: useJitter ? jitterSeed : null,
            },
        });
    } catch (err) {
        console.error("Planner error:", err);
        return res
            .status(500)
            .json({ success: false, error: "Database error" });
    }
});

// ==========================
//  PLAN HISTORY (uses idx_plan_history_user_plan_date)
// ==========================
router.get("/history", (req, res) => {
    const { firebase_uid, from, to } = req.query;

    if (!firebase_uid) {
        return res.status(400).json({ success: false, error: "firebase_uid is required" });
    }

    db.query(
        "SELECT id FROM users WHERE firebase_uid = ?",
        [firebase_uid],
        (err, userRows) => {
            if (err) {
                console.error(err);
                return res.status(500).json({ success: false, error: "Database error" });
            }
            if (!userRows.length) {
                return res.json({ success: true, history: [] });
            }

            const userId = userRows[0].id;

            let sql = `
                SELECT ph.id,
                       ph.subject_id,
                       s.name AS subject_name,
                       ph.allocated_minutes,
                       ph.plan_date
                FROM plan_history ph
                JOIN subjects s ON s.id = ph.subject_id
                WHERE ph.user_id = ?
            `;
            const params = [userId];

            if (from) {
                sql += " AND ph.plan_date >= ?";
                params.push(from);
            }
            if (to) {
                sql += " AND ph.plan_date <= ?";
                params.push(to);
            }

            sql += " ORDER BY ph.plan_date DESC, ph.id DESC LIMIT 500";

            db.query(sql, params, (histErr, rows) => {
                if (histErr) {
                    console.error(histErr);
                    return res.status(500).json({ success: false, error: "Database error" });
                }
                res.json({ success: true, history: rows });
            });
        }
    );
});

module.exports = router;