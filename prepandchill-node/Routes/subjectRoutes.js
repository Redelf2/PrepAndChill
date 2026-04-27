const express = require("express");
const router = express.Router();
const db = require("../db");


router.get("/", (req, res) => {
    const firebase_uid = req.query.firebase_uid;

    if (!firebase_uid) {
        return res.status(400).json({ error: "firebase_uid is required" });
    }

    const sql = `
        SELECT s.id, s.name, us.exam_date, us.confidence
        FROM user_subjects us
        JOIN subjects s ON s.id = us.subject_id
        JOIN users u ON u.id = us.user_id
        WHERE u.firebase_uid = ?
        ORDER BY s.name ASC
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

    // Use LOWER() for case-insensitive matching
    const sql = `
        SELECT t.id, t.topic_name,
        COALESCE((SELECT ut.is_completed FROM user_topics ut
                  JOIN users u ON u.id = ut.user_id
                  WHERE ut.topic_id = t.id AND u.firebase_uid = ? LIMIT 1), 0) as is_completed
        FROM topics t
        JOIN subjects s ON s.id = t.subject_id
        WHERE LOWER(s.name) = LOWER(?)
    `;

    db.query(sql, [firebase_uid || '', name], (err, result) => {
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

module.exports = router;
