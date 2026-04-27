const express = require("express");
const cors = require("cors");
const path = require("path");

const app = express();

app.use(cors());
app.use(express.json());

// Import Routes
const authRoutes = require("./Routes/authRoutes");
const subjectRoutes = require("./Routes/subjectRoutes");
const planRoutes = require("./Routes/planRoutes");

// Mount Routes
app.use("/api/auth", authRoutes);
app.use("/api/subjects", subjectRoutes);
app.use("/api/plan", planRoutes);

app.get("/", (req, res) => {
    res.send("PrepAndChill Backend Server is Running");
});

const PORT = 3000;
app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server started on http://localhost:${PORT}`);
    console.log(`Test Subjects: http://localhost:${PORT}/api/subjects?firebase_uid=8eNRvziInvhbXukRy5ZsazONBmx2`);
    console.log(`Test Topics: http://localhost:${PORT}/api/subjects/topics?name=DBMS`);
});