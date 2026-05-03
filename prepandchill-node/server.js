require("dotenv").config();

const express = require("express");
const cors = require("cors");
const db = require("./db");
const app = express();

app.use(cors());
app.use(express.json());


app.use("/api/auth", require("./Routes/authRoutes"));
app.use("/api/subjects", require("./Routes/subjectRoutes"));
app.use("/api/plan", require("./Routes/planRoutes"));
app.use("/api/sessions", require("./Routes/sessionRoutes"));
app.use("/api/quiz", require("./Routes/quizRoutes"));

app.get("/", (req, res) => res.send("Server Running"));

const PORT = 3000;
app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server started. Visit: http://localhost:${PORT}/api/subjects/topics?name=DBMS`);
});