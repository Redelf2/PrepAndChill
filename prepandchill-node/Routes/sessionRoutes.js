const express = require("express");
const router = express.Router();
const db = require("../db");

/**
 * Logs a study session. Uses FK columns that match idx_study_sessions_user_session_date.
 */
router.post("/log", (req, res) => {
    const {
        firebase_uid,
        subject_id,
        topic_id,
        minutes_spent,
        performance_score,
        session_date,
    } = req.body;

    if (!firebase_uid || subject_id == null || topic_id == null || minutes_spent == null) {
        return res.status(400).json({
            success: false,
            error:
                "firebase_uid, subject_id, topic_id, and minutes_spent are required",
        });
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
                return res.status(404).json({ success: false, error: "User not found" });
            }

            const userId = userRows[0].id;
            const dateVal = session_date || null;

            const sql =
                `INSERT INTO study_sessions
                    (user_id, subject_id, topic_id, minutes_spent, performance_score, session_date)
                 VALUES (?, ?, ?, ?, ?, COALESCE(?, CURDATE()))`;

            db.query(
                sql,
                [
                    userId,
                    subject_id,
                    topic_id,
                    minutes_spent,
                    performance_score ?? null,
                    dateVal,
                ],
                (insErr, result) => {
                    if (insErr) {
                        console.error(insErr);
                        return res.status(500).json({ success: false, error: "Database error" });
                    }
                    res.json({
                        success: true,
                        id: result.insertId,
                    });
                }
            );
        }
    );
});

/**
 * Lists sessions for a user, optionally filtered by session_date range
 * (uses idx_study_sessions_user_session_date when filtering/sorting).
 */
router.get("/", (req, res) => {
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
                return res.json({ success: true, sessions: [] });
            }

            const userId = userRows[0].id;

            let sql = `
                SELECT ss.id,
                       ss.subject_id,
                       s.name AS subject_name,
                       ss.topic_id,
                       t.topic_name,
                       ss.minutes_spent,
                       ss.performance_score,
                       ss.session_date
                FROM study_sessions ss
                LEFT JOIN subjects s ON s.id = ss.subject_id
                LEFT JOIN topics t ON t.id = ss.topic_id
                WHERE ss.user_id = ?
            `;
            const params = [userId];

            if (from) {
                sql += " AND ss.session_date >= ?";
                params.push(from);
            }
            if (to) {
                sql += " AND ss.session_date <= ?";
                params.push(to);
            }

            sql +=
                " ORDER BY ss.session_date DESC, ss.id DESC LIMIT 500";

            db.query(sql, params, (qErr, rows) => {
                if (qErr) {
                    console.error(qErr);
                    return res.status(500).json({ success: false, error: "Database error" });
                }
                res.json({ success: true, sessions: rows });
            });
        }
    );
});

module.exports = router;
