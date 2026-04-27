const express = require("express");
const cors = require("cors");
const path = require("path");

const app = express();

app.use(cors());
app.use(express.json());

// Request logger for debugging 404s
app.use((req, res, next) => {
    console.log(`${new Date().toISOString()} - ${req.method} ${req.url}`);
    next();
});


const authRoutes = require("./Routes/authRoutes");
const subjectRoutes = require("./Routes/subjectRoutes");
const planRoutes = require("./Routes/planRoutes");


app.use("/api/auth", authRoutes);
app.use("/api/subjects", subjectRoutes);
app.use("/api/plan", planRoutes);

app.get("/", (req, res) => {
    res.send("PrepAndChill Backend Server is Running");
});

// Catch-all for 404s
app.use((req, res) => {
    console.log(`404 Not Found: ${req.method} ${req.url}`);
    res.status(404).json({ error: "Route not found", url: req.url });
});

const PORT = 3000;
app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server started on http://0.0.0.0:${PORT}`);
    console.log(`Local Access: http://localhost:${PORT}`);
});
