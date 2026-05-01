const express = require("express");
const router = express.Router();
const db = require("../db");


// ==========================
// ✅ GET ALL SUBJECTS (FIXED)
// ==========================
router.get("/", (req, res) => {
    const firebase_uid = req.query.firebase_uid;

    if (!firebase_uid) {
        return res.status(400).json({ error: "firebase_uid is required" });
    }

    const sql = `
        SELECT 
            s.id,
            s.name,
            COALESCE(us.exam_date, '') AS exam_date,
            COALESCE(us.confidence, 0) AS confidence
        FROM subjects s
        LEFT JOIN user_subjects us 
            ON us.subject_id = s.id
        LEFT JOIN users u 
            ON u.id = us.user_id AND u.firebase_uid = ?
        ORDER BY s.name ASC
    `;

    db.query(sql, [firebase_uid], (err, result) => {
        if (err) {
            console.log(err);
            return res.status(500).json({ error: "DB error" });
        }
        res.json(result);
    });
});


// ==========================
// ✅ ADD SUBJECT (ONLY MASTER TABLE)
// ==========================
router.post("/add", (req, res) => {
    const { name } = req.body;

    if (!name) {
        return res.status(400).json({ error: "name is required" });
    }

    db.query(
        "INSERT IGNORE INTO subjects (name) VALUES (?)",
        [name],
        (err) => {
            if (err) {
                console.log(err);
                return res.status(500).json({ error: "DB error" });
            }
            res.json({ message: "Subject added" });
        }
    );
});


// ==========================
// ✅ FINAL TRANSACTION (MOST IMPORTANT)
// ==========================
router.post("/completeSetup", (req, res) => {
    const { firebase_uid, subjects } = req.body;

    if (!firebase_uid || !subjects || !subjects.length) {
        return res.status(400).json({ error: "Invalid data" });
    }

    db.beginTransaction(err => {
        if (err) return res.status(500).json({ error: "Transaction start failed" });

        // 1. Ensure user exists
        db.query(
            "INSERT IGNORE INTO users (firebase_uid, username, email) VALUES (?, ?, ?)",
            [firebase_uid, "User", firebase_uid + "@local"],
            (err) => {
                if (err) return rollback(res, err);

                db.query(
                    "SELECT id FROM users WHERE firebase_uid = ?",
                    [firebase_uid],
                    (err, userRows) => {
                        if (err || !userRows.length) return rollback(res, err);

                        const userId = userRows[0].id;

                        // 2. Process all subjects
                        let completed = 0;

                        subjects.forEach(sub => {

                            db.query(
                                "INSERT IGNORE INTO subjects (name) VALUES (?)",
                                [sub.name],
                                (err) => {
                                    if (err) return rollback(res, err);

                                    db.query(
                                        "SELECT id FROM subjects WHERE name = ?",
                                        [sub.name],
                                        (err, subjRows) => {
                                            if (err || !subjRows.length) return rollback(res, err);

                                            const subjectId = subjRows[0].id;

                                            db.query(
                                                `INSERT INTO user_subjects 
                                                 (user_id, subject_id, exam_date, confidence)
                                                 VALUES (?, ?, ?, ?)
                                                 ON DUPLICATE KEY UPDATE
                                                 exam_date = VALUES(exam_date),
                                                 confidence = VALUES(confidence)
                                                `,
                                                [
                                                    userId,
                                                    subjectId,
                                                    sub.exam_date,
                                                    sub.confidence
                                                ],
                                                (err) => {
                                                    if (err) return rollback(res, err);

                                                    completed++;

                                                    if (completed === subjects.length) {
                                                        db.commit(err => {
                                                            if (err) return rollback(res, err);
                                                            res.json({ message: "Setup completed" });
                                                        });
                                                    }
                                                }
                                            );
                                        }
                                    );
                                }
                            );

                        });

                    }
                );
            }
        );
    });
});

function rollback(res, err) {
    console.log(err);
    db.rollback(() => {
        res.status(500).json({ error: "Transaction failed" });
    });
}


// ==========================
// ✅ DELETE SUBJECT (USER LEVEL)
// ==========================
router.delete("/delete", (req, res) => {
    const { firebase_uid, subject_name } = req.query;

    const sql = `
        DELETE us FROM user_subjects us
        JOIN users u ON u.id = us.user_id
        JOIN subjects s ON s.id = us.subject_id
        WHERE u.firebase_uid = ? AND s.name = ?
    `;

    db.query(sql, [firebase_uid, subject_name], (err) => {
        if (err) return res.status(500).json({ error: "Delete failed" });
        res.json({ message: "Deleted" });
    });
});


// ==========================
// ✅ TOPICS (UNCHANGED)
// ==========================
router.get("/topics", (req, res) => {
    const { name, firebase_uid } = req.query;

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
        if (err) return res.status(500).json({ error: "DB error" });
        res.json(result);
    });
});

module.exports = router;