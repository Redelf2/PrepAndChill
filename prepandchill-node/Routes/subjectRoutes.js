const express = require("express");
const router = express.Router();
const db = require("../db");


router.get("/", (req, res) => {
    db.query("SELECT * FROM subjects", (err, result) => {
        if (err) {
            console.log(err);
            return res.json({ error: "DB error" });
        }
        res.json(result);
    });
});


router.post("/add", (req, res) => {
    const { name } = req.body;

    db.query(
        "INSERT INTO subjects (name) VALUES (?)",
        [name],
        (err) => {
            if (err) {
                console.log(err);
                return res.json({ error: "Insert failed" });
            }
            res.json({ message: "Subject added" });
        }
    );
});

module.exports = router;