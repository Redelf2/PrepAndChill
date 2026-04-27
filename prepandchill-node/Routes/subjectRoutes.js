const express = require("express");
const router = express.Router();
const db = require("../db");


router.get("/", (req, res) => {
    const firebase_uid = req.query.firebase_uid;

    if (!firebase_uid) {
        return res.status(400).json({ error: "firebase_uid is required" });
    }

    const sql = `
        SELECT s.id, s.name, us.exam_date
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

    // Basic validation: YYYY-MM-DD
    if (!/^\d{4}-\d{2}-\d{2}$/.test(exam_date)) {
        return res.status(400).json({ error: "exam_date must be YYYY-MM-DD" });
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
            return res.status(500).json({ error: "DB error (update exam_date)" });
        }

        if (!result || result.affectedRows === 0) {
            return res.status(404).json({ error: "Subject mapping not found for this user" });
        }

        res.json({ message: "Exam date updated", affected_rows: result.affectedRows });
    });
});


router.post("/add", (req, res) => {
    const { firebase_uid, name, email, username } = req.body;

    if (!firebase_uid || !name) {
        return res.status(400).json({ error: "firebase_uid and name are required" });
    }

    // 1) Ensure user exists (auto-create if missing)
    const safeEmail = email || `${firebase_uid}@local.invalid`;
    const safeUsername = username || "User";

    db.query(
        "INSERT IGNORE INTO users (firebase_uid, username, email) VALUES (?, ?, ?)",
        [firebase_uid, safeUsername, safeEmail],
        (err) => {
            if (err) {
                console.log(err);
                return res.status(500).json({ error: "DB error (user upsert)" });
            }

            // 2) Find user_id
            db.query(
                "SELECT id FROM users WHERE firebase_uid = ?",
                [firebase_uid],
                (err, userRows) => {
                    if (err) {
                        console.log(err);
                        return res.status(500).json({ error: "DB error (user lookup)" });
                    }

                    if (!userRows || userRows.length === 0) {
                        return res.status(500).json({ error: "User lookup failed after upsert" });
                    }

                    const userId = userRows[0].id;

                    // 3) Ensure subject exists (global master list)
                    db.query(
                        "INSERT IGNORE INTO subjects (name) VALUES (?)",
                        [name],
                        (err) => {
                            if (err) {
                                console.log(err);
                                return res.status(500).json({ error: "DB error (subject insert)" });
                            }

                            // 4) Fetch subject_id
                            db.query(
                                "SELECT id FROM subjects WHERE name = ?",
                                [name],
                                (err, subjRows) => {
                                    if (err) {
                                        console.log(err);
                                        return res.status(500).json({ error: "DB error (subject lookup)" });
                                    }

                                    if (!subjRows || subjRows.length === 0) {
                                        return res.status(500).json({ error: "Subject lookup failed" });
                                    }

                                    const subjectId = subjRows[0].id;

                                    // 5) Map user -> subject (per-user storage)
                                    db.query(
                                        "INSERT IGNORE INTO user_subjects (user_id, subject_id) VALUES (?, ?)",
                                        [userId, subjectId],
                                        (err, mapResult) => {
                                            if (err) {
                                                console.log(err);
                                                return res.status(500).json({ error: "DB error (mapping insert)" });
                                            }

                                            res.json({
                                                message: "Subject added for user",
                                                user_id: userId,
                                                subject_id: subjectId,
                                                affected_rows: mapResult?.affectedRows ?? 0
                                            });
                                        }
                                    );
                                }
                            );
                        }
                    );
                }
            );
        }
    );
});

module.exports = router;