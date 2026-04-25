const db = require("../db");

exports.registerUser = (req, res) => {

    const { uid, username, email } = req.body;

    if (!uid || !email || !username) {
        return res.json({ message: "Missing data" });
    }

    const check = "SELECT * FROM users WHERE firebase_uid=?";

    db.query(check, [uid], (err, result) => {

        if (err) {
            return res.json({ message: "DB error (check)" });
        }

        if (result.length > 0) {
            return res.json({ message: "User already exists" });
        }

        const insert =
            "INSERT INTO users (firebase_uid, username, email) VALUES (?, ?, ?)";

        db.query(insert, [uid, username, email], (err) => {

            if (err) return res.json({ message: "DB error (insert)" });

            res.json({ message: "User registered successfully" });
        });
    });
};