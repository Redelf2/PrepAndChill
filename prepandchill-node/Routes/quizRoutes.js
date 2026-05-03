const express = require("express");
const axios = require("axios");
const db = require("../db");
const {
    ENGINE,
    computeWeightedAccuracy,
    baseConfidencePct,
    mapQuizPerformanceToDifficulty,
    weakTopicsAnalysis,
    applyAdaptiveAdjustments,
    nextStreakState,
} = require("../services/quizEngine");
const {
    generateMcqsWithGemini,
    resolveGeminiApiKey,
} = require("../services/geminiQuizService");

const router = express.Router();

function queryAsync(sql, params = []) {
    return new Promise((resolve, reject) => {
        db.query(sql, params, (err, results) => {
            if (err) reject(err);
            else resolve(results);
        });
    });
}

function clampInt(n, lo, hi) {
    const x = Math.round(Number(n));
    if (!Number.isFinite(x)) return lo;
    return Math.max(lo, Math.min(hi, x));
}

function shuffleInPlace(arr) {
    for (let i = arr.length - 1; i > 0; i -= 1) {
        const j = Math.floor(Math.random() * (i + 1));
        const t = arr[i];
        arr[i] = arr[j];
        arr[j] = t;
    }
    return arr;
}

async function resolveSubject(subjectParam) {
    if (subjectParam == null || `${subjectParam}`.trim() === "") return null;

    const rows = await queryAsync(
        `
        SELECT id, name FROM subjects
        WHERE LOWER(TRIM(name)) = LOWER(TRIM(?))
        LIMIT 2
        `,
        [`${subjectParam}`.trim()]
    );

    if (!rows?.length) return null;
    if (rows.length > 1) return null;
    return rows[0];
}

async function fetchRandomForLevel(subjectId, level, limit) {
    if (limit <= 0) return [];
    const cap = Math.min(limit, 500);
    const rows = await queryAsync(
        `
        SELECT
            q.id,
            q.topic_id,
            t.topic_name,
            q.question,
            q.option_a,
            q.option_b,
            q.option_c,
            q.option_d,
            q.difficulty_level
        FROM quiz_questions q
        INNER JOIN topics t ON t.id = q.topic_id
        WHERE t.subject_id = ?
          AND q.difficulty_level = ?
        ORDER BY RAND()
        LIMIT ?
        `,
        [subjectId, level, cap]
    );
    return rows;
}

async function readFailStreak(userId, subjectId) {
    if (!userId || subjectId == null) return 0;
    const rows = await queryAsync(
        `SELECT streak_fail_sessions FROM user_quiz_stats WHERE user_id = ? AND subject_id = ?`,
        [userId, subjectId]
    );
    if (!rows.length) return 0;
    return Math.max(0, Number(rows[0].streak_fail_sessions) || 0);
}

/**
 * Builds interleaved quiz: easy/medium/hard targets adjusted if user repeatedly fails sessions.
 */
async function composeQuizMix(subjectId, userIdOpt) {
    const failStreak = await readFailStreak(userIdOpt, subjectId);
    let { EASY_PICK, MEDIUM_PICK, HARD_PICK } = ENGINE;

    if (failStreak >= 2) {
        const bump = Math.min(failStreak, 6);
        HARD_PICK = Math.min(
            10,
            HARD_PICK + Math.min(bump + 2, 6)
        );
        EASY_PICK = Math.max(1, EASY_PICK - Math.min(4, bump + 2));
    }

    let totalPlanned = EASY_PICK + MEDIUM_PICK + HARD_PICK;
    while (totalPlanned > ENGINE.DEFAULT_QUESTIONS_PER_QUIZ) {
        if (MEDIUM_PICK > 2) MEDIUM_PICK -= 1;
        else if (EASY_PICK > 1) EASY_PICK -= 1;
        else break;
        totalPlanned = EASY_PICK + MEDIUM_PICK + HARD_PICK;
    }

    const buckets = [];
    buckets.push(await fetchRandomForLevel(subjectId, 1, EASY_PICK));
    buckets.push(await fetchRandomForLevel(subjectId, 2, MEDIUM_PICK));
    buckets.push(await fetchRandomForLevel(subjectId, 3, HARD_PICK));

    let merged = buckets.flat();

    /** If pool is thin backfill anything available */
    if (merged.length < ENGINE.DEFAULT_QUESTIONS_PER_QUIZ) {
        const need = ENGINE.DEFAULT_QUESTIONS_PER_QUIZ - merged.length;
        const have = new Set(merged.map((q) => q.id));
        const fill = await queryAsync(
            `
            SELECT q.id, q.topic_id, t.topic_name, q.question,
                   q.option_a, q.option_b, q.option_c, q.option_d, q.difficulty_level
            FROM quiz_questions q
            INNER JOIN topics t ON t.id = q.topic_id
            WHERE t.subject_id = ?
            ORDER BY RAND()
            LIMIT ?
            `,
            [subjectId, need + 150]
        );
        for (const row of fill) {
            if (have.has(row.id)) continue;
            have.add(row.id);
            merged.push(row);
            if (merged.length >= ENGINE.DEFAULT_QUESTIONS_PER_QUIZ) break;
        }
    }

    merged = shuffleInPlace(merged);
    merged = merged.slice(0, ENGINE.DEFAULT_QUESTIONS_PER_QUIZ);
    return { questions: merged, mix_meta: { easy: EASY_PICK, medium: MEDIUM_PICK, hard: HARD_PICK, fail_streak_read: failStreak } };
}

router.get("/start", async (req, res) => {
    const { subject, firebase_uid } = req.query;

    if (!subject) {
        return res.status(400).json({ success: false, error: "subject is required" });
    }

    try {
        const subRow = await resolveSubject(subject);
        if (!subRow) {
            return res.status(404).json({
                success: false,
                error: "Subject not found (use exact syllabus name)",
            });
        }

        let userId = null;
        if (firebase_uid) {
            const u = await queryAsync(
                `SELECT id FROM users WHERE firebase_uid = ? LIMIT 1`,
                [firebase_uid]
            );
            userId = u.length ? u[0].id : null;
        }

        const { questions: raw, mix_meta } = await composeQuizMix(subRow.id, userId);

        if (!raw.length) {
            return res.status(404).json({
                success: false,
                error:
                    "No quiz questions seeded for this subject. Add rows to quiz_questions.",
            });
        }

        const questions = raw.map((q) => ({
            id: q.id,
            topic_id: q.topic_id,
            topic_name: q.topic_name,
            difficulty_level: q.difficulty_level,
            question: q.question,
            option_a: q.option_a,
            option_b: q.option_b,
            option_c: q.option_c,
            option_d: q.option_d,
        }));

        return res.json({
            success: true,
            subject: subRow.name,
            subject_id: subRow.id,
            quiz_size: questions.length,
            mix_meta,
            questions,
        });
    } catch (err) {
        console.error("quiz/start", err);
        return res.status(500).json({ success: false, error: "Database error" });
    }
});

router.post("/submit", async (req, res) => {
    const {
        firebase_uid,
        subject,
        subject_id: subjectIdRaw,
        answers,
    } = req.body || {};

    if (!firebase_uid) {
        return res.status(400).json({ success: false, error: "firebase_uid is required" });
    }
    if (!Array.isArray(answers) || answers.length === 0) {
        return res.status(400).json({ success: false, error: "answers[] required (non-empty)" });
    }

    let subjectRow = null;
    if (subject) subjectRow = await resolveSubject(subject);
    if (!subjectRow && subjectIdRaw != null) {
        const sr = await queryAsync(
            `SELECT id, name FROM subjects WHERE id = ?`,
            [Number(subjectIdRaw)]
        );
        subjectRow = sr.length ? sr[0] : null;
    }

    if (!subjectRow) {
        return res.status(400).json({
            success: false,
            error: "Provide valid subject name or subject_id",
        });
    }

    try {
        const userRows = await queryAsync(
            `SELECT id FROM users WHERE firebase_uid = ?`,
            [firebase_uid]
        );
        if (!userRows.length) {
            return res.status(404).json({ success: false, error: "User not registered" });
        }

        const userId = userRows[0].id;

        const graded = [];

        /** Last answer wins if client sends duplicate question_id */
        const byQ = new Map();
        for (const a of answers) {
            const id = Number(a.question_id);
            if (Number.isFinite(id)) byQ.set(id, a);
        }
        const uniqueIds = [...byQ.keys()];

        if (!uniqueIds.length) {
            return res.status(400).json({
                success: false,
                error: "answers must include numeric question_id",
            });
        }

        const placeholders = uniqueIds.map(() => "?").join(",");
        const qRows = await queryAsync(
            `
            SELECT
                qq.id AS question_id,
                qq.topic_id,
                qq.correct_option,
                qq.difficulty_level,
                t.topic_name AS topic_name,
                t.subject_id
            FROM quiz_questions qq
            INNER JOIN topics t ON t.id = qq.topic_id
            WHERE qq.id IN (${placeholders})
            `,
            uniqueIds
        );

        const byId = new Map(qRows.map((r) => [r.question_id, r]));

        for (const ans of byQ.values()) {
            const qid = Number(ans.question_id);
            const letter = `${ans.selected_option ?? ""}`
                .trim()
                .toUpperCase();

            const meta = byId.get(qid);
            if (!meta) {
                return res.status(400).json({
                    success: false,
                    error: `Unknown question_id ${ans.question_id} for graded set`,
                });
            }

            if (meta.subject_id !== subjectRow.id) {
                return res.status(400).json({
                    success: false,
                    error: `Question ${qid} does not belong to this subject`,
                });
            }

            if (!/^([ABCD])$/.test(letter)) {
                return res.status(400).json({
                    success: false,
                    error: `selected_option must be A,B,C,D for question ${qid}`,
                });
            }

            graded.push({
                question_id: qid,
                topic_id: meta.topic_id,
                topic_name: meta.topic_name,
                difficulty_level: Number(meta.difficulty_level) || 2,
                is_correct: meta.correct_option === letter,
            });
        }

        const metrics = computeWeightedAccuracy(graded);

        /** Edge: weighted_total = 0 (should never happen — levels 1..3 weights > 0 per row) */
        if (metrics.weighted_total <= 0) {
            return res.status(400).json({
                success: false,
                error: "Cannot score empty weighted quiz",
            });
        }

        const priorStatRows = await queryAsync(
            `SELECT * FROM user_quiz_stats WHERE user_id = ? AND subject_id = ?`,
            [userId, subjectRow.id]
        );

        const priorStat = priorStatRows.length ? priorStatRows[0] : {};

        const rawConfidence = baseConfidencePct(metrics);
        const subjectDifficultyGuess = mapQuizPerformanceToDifficulty(
            metrics.weighted_accuracy
        );

        const adaptiveStatsPayload = {
            prior_confidence: priorStat.last_confidence ?? null,
            prior_weighted_accuracy:
                priorStat.prior_weighted_accuracy != null
                    ? Number(priorStat.prior_weighted_accuracy)
                    : null,
            streak_correct_sessions:
                Number(priorStat.streak_correct_sessions) || 0,
            streak_fail_sessions: Number(priorStat.streak_fail_sessions) || 0,
        };

        const { confidence, adjustment_notes } = applyAdaptiveAdjustments(
            rawConfidence,
            metrics,
            adaptiveStatsPayload
        );

        /** Streak evolves from this quiz outcome alone */
        const streakNext = nextStreakState(priorStat, metrics.weighted_accuracy);

        const difficultyFinal = clampInt(subjectDifficultyGuess, 1, 3);

        const weak_topics = weakTopicsAnalysis(graded);

        const accuracy = metrics.plain_accuracy;
        const weightedAccuracy = metrics.weighted_accuracy;

        await queryAsync("START TRANSACTION");

        try {
            for (const g of graded) {
                await queryAsync(
                    `
                    INSERT INTO user_quiz_attempts (
                        user_id, question_id, topic_id, subject_id,
                        is_correct, difficulty_level
                    ) VALUES (?,?,?,?,?,?)
                    `,
                    [
                        userId,
                        g.question_id,
                        g.topic_id,
                        subjectRow.id,
                        g.is_correct ? 1 : 0,
                        g.difficulty_level,
                    ]
                );
            }

            await queryAsync(
                `
                INSERT INTO user_quiz_stats (
                    user_id, subject_id, quiz_sessions,
                    streak_correct_sessions, streak_fail_sessions,
                    prior_weighted_accuracy, last_confidence
                ) VALUES (?, ?, 1, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    quiz_sessions = quiz_sessions + 1,
                    streak_correct_sessions = VALUES(streak_correct_sessions),
                    streak_fail_sessions = VALUES(streak_fail_sessions),
                    prior_weighted_accuracy = VALUES(prior_weighted_accuracy),
                    last_confidence = VALUES(last_confidence),
                    updated_at = CURRENT_TIMESTAMP
                `,
                [
                    userId,
                    subjectRow.id,
                    streakNext.streak_correct_sessions,
                    streakNext.streak_fail_sessions,
                    weightedAccuracy,
                    confidence,
                ]
            );

            await queryAsync(
                `
                INSERT INTO user_subjects (user_id, subject_id, exam_date, confidence, difficulty)
                VALUES (?, ?, NULL, ?, ?)
                ON DUPLICATE KEY UPDATE
                    confidence = VALUES(confidence),
                    difficulty = VALUES(difficulty)
                `,
                [userId, subjectRow.id, confidence, difficultyFinal]
            );

            await queryAsync("COMMIT");
        } catch (txErr) {
            await queryAsync("ROLLBACK");
            throw txErr;
        }

        return res.json({
            success: true,
            confidence,
            difficulty: difficultyFinal,
            weak_topics: weak_topics.map((w) => ({
                topic_id: w.topic_id,
                topic_name: w.topic_name,
                misses: w.misses,
                attempted: w.attempted,
            })),
            accuracy: Number(accuracy.toFixed(4)),
            weighted_accuracy: Number(weightedAccuracy.toFixed(4)),
            weighted_correct: metrics.weighted_correct,
            weighted_total: metrics.weighted_total,
            raw_confidence: rawConfidence,
            adjustment_notes,
            streak: {
                streak_correct_sessions: streakNext.streak_correct_sessions,
                streak_fail_sessions: streakNext.streak_fail_sessions,
                session_passed: streakNext.session_passed,
            },
            subject_id: subjectRow.id,
            subject: subjectRow.name,
        });
    } catch (err) {
        console.error(err);
        return res.status(500).json({ success: false, error: "Database error" });
    }
});

/**
 * Gemini generates MCQs for a subject, INSERTs into quiz_questions (needs syllabus topics),
 * returns sanitized payload for the mobile client (no correct answers).
 */
router.post("/generateForSubject", async (req, res) => {
    const apiKey = resolveGeminiApiKey();
    if (!apiKey) {
        return res.status(503).json({
            success: false,
            error:
                "No Gemini key: set GEMINI_API_KEY or GOOGLE_API_KEY in prepandchill-node/.env (never commit .env). Restart the server after editing.",
        });
    }

    const subjectParam = req.body?.subject ?? req.body?.subject_name;
    const numRaw = Number(req.body?.num_questions ?? 10);
    const numQuestions = Math.min(
        15,
        Math.max(4, Number.isFinite(numRaw) ? numRaw : 10)
    );

    if (!subjectParam || `${subjectParam}`.trim() === "") {
        return res.status(400).json({
            success: false,
            error: "subject or subject_name is required",
        });
    }

    try {
        const subjectRow = await resolveSubject(subjectParam);
        if (!subjectRow) {
            return res.status(404).json({
                success: false,
                error: "Subject not found",
            });
        }

        const topics = await queryAsync(
            `
            SELECT id, topic_name
            FROM topics
            WHERE subject_id = ?
            ORDER BY id ASC
            `,
            [subjectRow.id]
        );

        if (!topics.length) {
            return res.status(400).json({
                success: false,
                error:
                    "No syllabus topics for this subject. Seed `topics` first, then retry.",
            });
        }

        const topicNames = topics.map((t) => t.topic_name);
        const topicIds = topics.map((t) => t.id);

        const generated = await generateMcqsWithGemini(
            apiKey,
            subjectRow.name,
            topicNames,
            numQuestions
        );

        await queryAsync("START TRANSACTION");

        const questionsOut = [];

        try {
            for (let i = 0; i < generated.length; i++) {
                const q = generated[i];
                const topicId = topicIds[i % topicIds.length];

                const ins = await queryAsync(
                    `
                    INSERT INTO quiz_questions (
                        topic_id, question,
                        option_a, option_b, option_c, option_d,
                        correct_option, difficulty_level
                    ) VALUES (?,?,?,?,?,?,?,?)
                    `,
                    [
                        topicId,
                        q.question,
                        q.option_a,
                        q.option_b,
                        q.option_c,
                        q.option_d,
                        q.correct_option,
                        q.difficulty_level,
                    ]
                );

                let insertId =
                    ins?.insertId !== undefined && ins?.insertId !== null
                        ? Number(ins.insertId)
                        : NaN;
                if (!Number.isFinite(insertId) || insertId <= 0) {
                    const lid = await queryAsync(
                        "SELECT LAST_INSERT_ID() AS id"
                    );
                    insertId = Number(lid[0]?.id);
                }
                if (!Number.isFinite(insertId) || insertId <= 0) {
                    throw new Error(
                        "Insert into quiz_questions failed (no insert id)"
                    );
                }

                questionsOut.push({
                    id: insertId,
                    topic_id: topicId,
                    difficulty_level: q.difficulty_level,
                    question: q.question,
                    option_a: q.option_a,
                    option_b: q.option_b,
                    option_c: q.option_c,
                    option_d: q.option_d,
                });
            }

            await queryAsync("COMMIT");
        } catch (inner) {
            await queryAsync("ROLLBACK");
            throw inner;
        }

        return res.json({
            success: true,
            subject: subjectRow.name,
            subject_id: subjectRow.id,
            source: "gemini",
            quiz_size: questionsOut.length,
            questions: questionsOut,
        });
    } catch (err) {
        console.error(
            "generateForSubject",
            err.response?.data ?? err.message ?? err
        );
        const code = err.code || err.errno;
        const msg = err.message || "Gemini or database failed";
        const hint =
            code === "ER_NO_SUCH_TABLE"
                ? " Run database/quiz_schema.sql to create quiz_questions."
                : "";
        return res.status(500).json({
            success: false,
            error: msg + hint,
        });
    }
});

router.get("/generateAIQuiz", async (req, res) => {
    const { topic_name } = req.query;

    if (!topic_name) {
        return res.status(400).json({ error: "Topic required" });
    }

    try {
        const response = await axios.post(
            "https://api.openai.com/v1/chat/completions",
            {
                model: "gpt-4.1-mini",
                messages: [
                    {
                        role: "user",
                        content: `Generate 5 MCQ questions for topic: ${topic_name}.
Return STRICT JSON like:
[
 { "question": "...", "A": "...", "B": "...", "C": "...", "D": "...", "answer": "A" }
]`,
                    },
                ],
            },
            {
                headers: {
                    Authorization: `Bearer ${process.env.OPENAI_API_KEY || "YOUR_API_KEY"}`,
                    "Content-Type": "application/json",
                },
            }
        );

        const text = response.data.choices[0].message.content;

        let questions;
        try {
            questions = JSON.parse(text);
        } catch {
            return res.json({ success: false, raw: text });
        }

        res.json({ success: true, questions });
    } catch (err) {
        console.error(err.message);
        res.status(500).json({ error: "AI failed" });
    }
});

module.exports = router;

