// server.js
const express = require("express");
const cors = require("cors");

const app = express();

app.use(cors());
app.use(express.json());


// Note: folder name is `Routes` in this repo (Windows is case-insensitive, Linux isn't)
app.use("/api/auth", require("./Routes/authRoutes"));
app.use("/api/subjects", require("./Routes/subjectRoutes"));

app.get("/", (req, res) => {
    res.send("Server running");
});

app.listen(3000, () => {
    console.log("Server running on port 3000");
});