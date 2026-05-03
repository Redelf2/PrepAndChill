require("dotenv").config();
const mysql = require("mysql2");

const port = Number.parseInt(process.env.DB_PORT ?? "3306", 10);

const db = mysql.createConnection({
    host: process.env.DB_HOST || "localhost",
    user: process.env.DB_USER || "root",
    password: process.env.DB_PASSWORD ?? "",
    database: process.env.DB_NAME || "Prepandchiill",
    port: Number.isFinite(port) && port > 0 ? port : 3306,
});

db.connect(err => {
    if (err) console.log(err);
    else console.log("MySQL Connected");
});

module.exports = db;