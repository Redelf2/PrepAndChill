/**
 * Gemini MCQ generation — tolerant parsing, model fallbacks, optional JSON schema.
 */

const axios = require("axios");

const MODEL_CANDIDATES = (
    process.env.GEMINI_MODEL_FALLBACKS ||
    "gemini-2.0-flash,gemini-2.0-flash-001,gemini-1.5-flash,gemini-1.5-flash-latest,gemini-1.5-flash-8b"
)
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);

const PRIMARY_MODEL = process.env.GEMINI_MODEL || MODEL_CANDIDATES[0];

/** Gemini REST expects schema types in UPPERCASE (STRING, OBJECT, …). */
const QUESTIONS_WRAPPER_SCHEMA = {
    type: "OBJECT",
    properties: {
        questions: {
            type: "ARRAY",
            items: {
                type: "OBJECT",
                properties: {
                    question: { type: "STRING" },
                    option_a: { type: "STRING" },
                    option_b: { type: "STRING" },
                    option_c: { type: "STRING" },
                    option_d: { type: "STRING" },
                    correct_option: { type: "STRING" },
                    difficulty_level: { type: "INTEGER" },
                },
                required: [
                    "question",
                    "option_a",
                    "option_b",
                    "option_c",
                    "option_d",
                    "correct_option",
                    "difficulty_level",
                ],
            },
        },
    },
    required: ["questions"],
};

function resolveGeminiApiKey() {
    return (
        process.env.GEMINI_API_KEY ||
        process.env.GOOGLE_API_KEY ||
        process.env.GOOGLE_GENERATIVE_AI_API_KEY ||
        ""
    ).trim();
}

function stripCodeFence(text) {
    let t = `${text}`.trim();
    if (t.startsWith("```")) {
        t = t.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/u, "");
    }
    return t.trim();
}

function normalizeLetter(opt) {
    const s = `${opt ?? ""}`.trim().toUpperCase();
    return /^[ABCD]$/.test(s) ? s : "A";
}

function clampDiff(d) {
    const n = Number.parseInt(d, 10);
    if (Number.isNaN(n)) return 2;
    return Math.min(3, Math.max(1, n));
}

function extractResponseText(data) {
    const cand = data?.candidates?.[0];
    if (!cand) {
        const fb = data?.promptFeedback;
        if (fb?.blockReason) {
            throw new Error(`Gemini blocked prompt: ${fb.blockReason}`);
        }
        throw new Error("Gemini returned no candidates (check API key & model name)");
    }

    const parts = cand.content?.parts;
    if (!parts?.length) {
        throw new Error(
            `Gemini empty content (finishReason=${cand.finishReason || "unknown"})`
        );
    }

    const texts = parts.map((p) => p.text).filter((t) => t != null && `${t}`.length > 0);
    const joined = texts.join("").trim();
    if (!joined) {
        throw new Error("Gemini parts had no text (try another model or disable schema)");
    }
    return joined;
}

function normalizeToQuestionArray(parsed) {
    if (Array.isArray(parsed)) return parsed;
    if (parsed && Array.isArray(parsed.questions)) return parsed.questions;
    if (parsed && Array.isArray(parsed.items)) return parsed.items;
    throw new Error("Gemini JSON did not contain a questions array");
}

function parseQuestionsPayload(rawText) {
    let parsed;
    try {
        parsed = JSON.parse(rawText);
    } catch {
        parsed = JSON.parse(stripCodeFence(rawText));
    }
    return normalizeToQuestionArray(parsed);
}

async function postGemini(apiKey, modelId, body) {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${modelId}:generateContent`;
    const res = await axios.post(`${url}?key=${encodeURIComponent(apiKey)}`, body, {
        headers: { "Content-Type": "application/json" },
        timeout: 120000,
        validateStatus: () => true,
    });

    const { status, data } = res;

    if (status >= 400) {
        const msg =
            data?.error?.message ||
            (typeof data === "string" ? data : JSON.stringify(data)).slice(0, 500);
        const err = new Error(`Gemini HTTP ${status}: ${msg}`);
        err.status = status;
        throw err;
    }

    if (data?.error) {
        const msg =
            data.error.message ||
            data.error.status ||
            JSON.stringify(data.error).slice(0, 400);
        const err = new Error(`Gemini API: ${msg}`);
        err.status = data.error.code || 400;
        throw err;
    }

    if (data?.promptFeedback?.blockReason) {
        throw new Error(`Gemini blocked: ${data.promptFeedback.blockReason}`);
    }

    return data;
}

function buildPrompt(subjectName, topicNames, numQuestions, compact) {
    const topicsSnippet =
        topicNames.slice(0, 80).join(" | ") || "(general subject coverage)";

    if (compact) {
        return `Create exactly ${numQuestions} undergraduate-level MCQs for "${subjectName}". Context topics: ${topicsSnippet}. Output valid JSON only.`;
    }

    return `You write undergraduate exam practice questions.

Subject: "${subjectName}"
Syllabus topic titles (context): ${topicsSnippet}

Return exactly ${numQuestions} multiple-choice questions.

Each question MUST be a JSON object with keys:
"question","option_a","option_b","option_c","option_d","correct_option","difficulty_level"

Rules:
- correct_option is one of: "A","B","C","D"
- difficulty_level is integer 1 (easy), 2 (medium), or 3 (hard)
- Balance difficulty across the set
- Four plausible distinct options per question

Return a JSON object of the form: {"questions":[ {...}, ... ]}`;
}

function cleanQuestionRows(rawArray, numQuestions) {
    const cleaned = [];
    for (const raw of rawArray) {
        if (!raw || typeof raw !== "object") continue;
        const q = `${raw.question ?? ""}`.trim();
        const oa = `${raw.option_a ?? raw.A ?? ""}`.trim();
        const ob = `${raw.option_b ?? raw.B ?? ""}`.trim();
        const oc = `${raw.option_c ?? raw.C ?? ""}`.trim();
        const od = `${raw.option_d ?? raw.D ?? ""}`.trim();
        if (!q || !oa || !ob || !oc || !od) continue;

        cleaned.push({
            question: q,
            option_a: oa,
            option_b: ob,
            option_c: oc,
            option_d: od,
            correct_option: normalizeLetter(raw.correct_option ?? raw.answer),
            difficulty_level: clampDiff(raw.difficulty_level),
        });

        if (cleaned.length >= numQuestions) break;
    }

    if (cleaned.length === 0) {
        throw new Error("No valid questions after parsing Gemini output");
    }
    return cleaned;
}

/**
 * @returns {Promise<Array<{question,option_a,option_b,option_c,option_d,correct_option,difficulty_level}>>}
 */
async function generateMcqsWithGemini(apiKey, subjectName, topicNames, numQuestions) {
    if (!apiKey) throw new Error("Missing Gemini API key");

    const modelsToTry = [PRIMARY_MODEL, ...MODEL_CANDIDATES.filter((m) => m !== PRIMARY_MODEL)];

    const attempts = [];

    for (const modelId of modelsToTry) {
        /** 1) Structured JSON (preferred) */
        attempts.push({
            modelId,
            label: "schema",
            body: {
                contents: [
                    {
                        role: "user",
                        parts: [
                            {
                                text: buildPrompt(subjectName, topicNames, numQuestions, true),
                            },
                        ],
                    },
                ],
                generationConfig: {
                    temperature: 0.55,
                    responseMimeType: "application/json",
                    responseSchema: QUESTIONS_WRAPPER_SCHEMA,
                },
            },
        });

        /** 2) JSON MIME without schema (broader model support) */
        attempts.push({
            modelId,
            label: "json-only",
            body: {
                contents: [
                    {
                        parts: [
                            {
                                text: buildPrompt(subjectName, topicNames, numQuestions, false),
                            },
                        ],
                    },
                ],
                generationConfig: {
                    temperature: 0.65,
                    responseMimeType: "application/json",
                },
            },
        });

        /** 3) Plain text JSON prompt (legacy fallback) */
        attempts.push({
            modelId,
            label: "text",
            body: {
                contents: [
                    {
                        parts: [
                            {
                                text:
                                    buildPrompt(subjectName, topicNames, numQuestions, false) +
                                    "\n\nIf you cannot use an object wrapper, return ONLY a JSON array of question objects.",
                            },
                        ],
                    },
                ],
                generationConfig: {
                    temperature: 0.65,
                },
            },
        });
    }

    let lastErr = null;

    for (const att of attempts) {
        try {
            const data = await postGemini(apiKey, att.modelId, att.body);
            const text = extractResponseText(data);
            let arr;
            try {
                arr = parseQuestionsPayload(text);
            } catch {
                try {
                    const trimmed = stripCodeFence(text);
                    const parsed = JSON.parse(trimmed);
                    arr = normalizeToQuestionArray(parsed);
                } catch (e2) {
                    throw new Error(
                        `Gemini JSON parse failed: ${e2.message}. Snippet: ${text.slice(0, 280)}`
                    );
                }
            }

            const cleaned = cleanQuestionRows(arr, numQuestions);
            if (cleaned.length > 0) {
                return cleaned;
            }
        } catch (e) {
            lastErr = e;
            /** Schema unsupported etc. — try next attempt */
            continue;
        }
    }

    throw lastErr || new Error("All Gemini attempts failed");
}

module.exports = {
    generateMcqsWithGemini,
    resolveGeminiApiKey,
    GEMINI_MODEL: PRIMARY_MODEL,
};
