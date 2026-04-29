const express = require("express");
const router = express.Router();
const db = require("../db");


router.get("/", (req, res) => {
    const firebase_uid = req.query.firebase_uid;

    if (!firebase_uid) {
        return res.status(400).json({ error: "firebase_uid is required" });
    }

    const sql = `
        SELECT subject_id AS id, name, exam_date, confidence
        FROM user_dashboard_view
        WHERE firebase_uid = ?
        ORDER BY name ASC
    `;

    db.query(sql, [firebase_uid], (err, result) => {
        if (err) {
            console.log(err);
            return res.json({ error: "DB error" });
        }
        res.json(result);
    });
});

router.post("/updateExamDate", (req, res) => {
    const { firebase_uid, subject_name, exam_date } = req.body;

    if (!firebase_uid || !subject_name || !exam_date) {
        return res.status(400).json({ error: "firebase_uid, subject_name, exam_date are required" });
    }

    const sql = `
        UPDATE user_subjects us
        JOIN users u ON u.id = us.user_id
        JOIN subjects s ON s.id = us.subject_id
        SET us.exam_date = ?
        WHERE u.firebase_uid = ? AND s.name = ?
    `;

    db.query(sql, [exam_date, firebase_uid, subject_name], (err, result) => {
        if (err) {
            console.log(err);
            return res.status(500).json({ error: "DB error" });
        }
        res.json({ message: "Exam date updated" });
    });
});


router.post("/add", (req, res) => {
    const { firebase_uid, name, email, username } = req.body;

    if (!firebase_uid || !name) {
        return res.status(400).json({ error: "firebase_uid and name are required" });
    }

    const safeEmail = email || `${firebase_uid}@local.invalid`;
    const safeUsername = username || "User";

    db.query(
        "INSERT IGNORE INTO users (firebase_uid, username, email) VALUES (?, ?, ?)",
        [firebase_uid, safeUsername, safeEmail],
        (err) => {
            if (err) return res.status(500).json({ error: "DB error (user)" });

            db.query("SELECT id FROM users WHERE firebase_uid = ?", [firebase_uid], (err, userRows) => {
                if (err || !userRows.length) return res.status(500).json({ error: "User lookup failed" });
                const userId = userRows[0].id;

                db.query("INSERT IGNORE INTO subjects (name) VALUES (?)", [name], (err) => {
                    if (err) return res.status(500).json({ error: "DB error (subject)" });

                    db.query("SELECT id FROM subjects WHERE name = ?", [name], (err, subjRows) => {
                        if (err || !subjRows.length) return res.status(500).json({ error: "Subject lookup failed" });
                        const subjectId = subjRows[0].id;

                        db.query("INSERT IGNORE INTO user_subjects (user_id, subject_id) VALUES (?, ?)", [userId, subjectId], (err) => {
                            if (err) return res.status(500).json({ error: "DB error (mapping)" });
                            res.json({ message: "Subject added" });
                        });
                    });
                });
            });
        }
    );
});

router.post("/updateConfidence", (req, res) => {
    const { firebase_uid, subject_name, confidence } = req.body;
    const sql = `
        UPDATE user_subjects us
        JOIN users u ON u.id = us.user_id
        JOIN subjects s ON s.id = us.subject_id
        SET us.confidence = ?
        WHERE u.firebase_uid = ? AND s.name = ?
    `;
    db.query(sql, [confidence, firebase_uid, subject_name], (err) => {
        if (err) return res.status(500).json({ error: "DB error" });
        res.json({ message: "Confidence updated" });
    });
});

router.get("/topics", (req, res) => {
    const { name, firebase_uid } = req.query;
    if (!name) return res.status(400).json({ error: "Subject name is required" });

   
    const sql = `
        SELECT t.id, t.topic_name,
        COALESCE((SELECT ut.is_completed FROM user_topics ut
                  JOIN users u ON u.id = ut.user_id
                  WHERE ut.topic_id = t.id AND u.firebase_uid = ? LIMIT 1), 0) as is_completed,
        COALESCE((SELECT ut.remaining_seconds FROM user_topics ut
                  JOIN users u ON u.id = ut.user_id
                  WHERE ut.topic_id = t.id AND u.firebase_uid = ? LIMIT 1), NULL) as remaining_seconds
        FROM topics t
        JOIN subjects s ON s.id = t.subject_id
        WHERE LOWER(s.name) = LOWER(?)
    `;

    db.query(sql, [firebase_uid || '', firebase_uid || '', name], (err, result) => {
        if (err) {
            console.error("Error fetching topics:", err);
            return res.status(500).json({ error: "DB error fetching topics" });
        }
        res.json(result);
    });
});

router.post("/updateTopicProgress", (req, res) => {
    const { firebase_uid, topic_id, is_completed } = req.body;
    db.query("SELECT id FROM users WHERE firebase_uid = ?", [firebase_uid], (err, userRows) => {
        if (err || !userRows.length) return res.status(500).json({ error: "User not found" });
        const userId = userRows[0].id;
        const sql = "INSERT INTO user_topics (user_id, topic_id, is_completed) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE is_completed = VALUES(is_completed)";
        db.query(sql, [userId, topic_id, is_completed ? 1 : 0], (err) => {
            if (err) return res.status(500).json({ error: "DB error" });
            res.json({ message: "Progress updated" });
        });
    });
});

router.post("/saveRemainingTime", (req, res) => {
    const { firebase_uid, topic_id, remaining_seconds } = req.body;
    if (!firebase_uid || !topic_id) return res.status(400).json({ error: "Missing data" });

    db.query("SELECT id FROM users WHERE firebase_uid = ?", [firebase_uid], (err, userRows) => {
        if (err || !userRows.length) return res.status(500).json({ error: "User not found" });
        const userId = userRows[0].id;

        const sql = `
            INSERT INTO user_topics (user_id, topic_id, remaining_seconds)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE remaining_seconds = VALUES(remaining_seconds)
        `;
        db.query(sql, [userId, topic_id, remaining_seconds], (err) => {
            if (err) return res.status(500).json({ error: "DB error" });
            res.json({ message: "Remaining time saved" });
        });
    });
});

router.post("/saveSession", (req, res) => {
    const { firebase_uid, subject_id, topic_id, minutes_spent, performance_score } = req.body;

    if (!firebase_uid || !subject_id || !minutes_spent) {
        return res.status(400).json({ error: "Missing data" });
    }

    db.query("SELECT id FROM users WHERE firebase_uid = ?", [firebase_uid], (err, userRows) => {
        if (err || !userRows.length) return res.status(500).json({ error: "User not found" });

        const userId = userRows[0].id;

        const insertSQL = `
            INSERT INTO study_sessions 
            (user_id, subject_id, topic_id, minutes_spent, performance_score, session_date)
            VALUES (?, ?, ?, ?, ?, CURDATE())
        `;

        db.query(insertSQL, [
            userId,
            subject_id,
            topic_id || null,
            minutes_spent,
            performance_score || 50
        ], (err) => {

            if (err) return res.status(500).json({ error: "DB error" });

            //  AUTO UPDATE CONFIDENCE (AI LOGIC)
            const updateConfidenceSQL = `
                UPDATE user_subjects
                SET confidence = LEAST(100, GREATEST(0,
                    (COALESCE(confidence,50) * 0.7) + (? * 0.3)
                ))
                WHERE user_id = ? AND subject_id = ?
            `;

            db.query(updateConfidenceSQL, [
                performance_score || 50,
                userId,
                subject_id
            ]);

            res.json({ message: "Session saved + confidence updated" });
        });
    });
});

router.delete("/delete", (req, res) => {
    const { firebase_uid, subject_name } = req.query;

    if (!firebase_uid || !subject_name) {
        return res.status(400).json({ error: "Missing data" });
    }

    const sql = `
        DELETE us FROM user_subjects us
        JOIN users u ON u.id = us.user_id
        JOIN subjects s ON s.id = us.subject_id
        WHERE u.firebase_uid = ? AND s.name = ?
    `;

    db.query(sql, [firebase_uid, subject_name], (err) => {
        if (err) {
            console.log(err);
            return res.status(500).json({ error: "Delete failed" });
        }

        res.json({ message: "Subject deleted" });
    });
});

module.exports = router;
