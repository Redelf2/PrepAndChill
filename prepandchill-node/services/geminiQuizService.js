/**
 * Gemini MCQ generation — resolves model IDs dynamically (fixes 404 "model not found").
 */

const axios = require("axios");

/** Used only if ListModels fails (offline / network). */
const STATIC_MODEL_FALLBACKS =
    process.env.GEMINI_MODEL_FALLBACKS ||
    [
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-2.0-flash-lite",
        "gemini-2.0-flash-001",
        "gemini-2.0-flash",
        "gemini-2.5-flash-lite",
        "gemini-pro-latest",
    ].join(",");

const STATIC_MODELS = [
    process.env.GEMINI_MODEL || "gemini-2.5-flash",
    ...STATIC_MODEL_FALLBACKS.split(",").map((s) => s.trim()),
].filter((m, i, a) => m && a.indexOf(m) === i);

let modelIdCache = null;
let modelIdCacheTime = 0;
const MODEL_CACHE_MS = 8 * 60 * 1000;

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
        throw new Error(
            "Gemini returned no candidates (invalid API key, model disabled, or quota)"
        );
    }

    const parts = cand.content?.parts;
    if (!parts?.length) {
        throw new Error(
            `Gemini empty content (finishReason=${cand.finishReason || "?"})`
        );
    }

    const texts = parts.map((p) => p?.text).filter((t) => t && `${t}`.length);
    const joined = texts.join("").trim();
    if (!joined) throw new Error("Gemini returned no text in parts");

    return joined;
}

function normalizeToQuestionArray(parsed) {
    if (Array.isArray(parsed)) return parsed;
    if (parsed?.questions && Array.isArray(parsed.questions)) return parsed.questions;
    if (parsed?.items && Array.isArray(parsed.items)) return parsed.items;
    if (parsed?.data && Array.isArray(parsed.data)) return parsed.data;
    throw new Error("JSON has no question array");
}

function parseFlexibleJson(text) {
    const trimmed = stripCodeFence(text);
    let parsed;
    try {
        parsed = JSON.parse(trimmed);
    } catch {
        const iObj = trimmed.indexOf("{");
        const iArr = trimmed.indexOf("[");
        let slice = trimmed;
        if (iArr >= 0 && (iObj < 0 || iArr < iObj)) slice = trimmed.slice(iArr);
        else if (iObj >= 0) slice = trimmed.slice(iObj);
        try {
            parsed = JSON.parse(slice);
        } catch (e2) {
            throw new Error(
                `Invalid JSON from model: ${e2.message}. Start: ${trimmed.slice(0, 120)}`
            );
        }
    }
    return normalizeToQuestionArray(parsed);
}

/** All model ids supporting generateContent (paginated). */
async function fetchAvailableModelIds(apiKey) {
    const out = [];
    let pageToken;
    let pages = 0;
    do {
        const params = new URLSearchParams({ key: apiKey });
        if (pageToken) params.set("pageToken", pageToken);

        const url = `https://generativelanguage.googleapis.com/v1beta/models?${params}`;
        const res = await axios.get(url, {
            timeout: 45000,
            validateStatus: () => true,
        });

        if (res.status >= 400) {
            const msg =
                res.data?.error?.message ||
                JSON.stringify(res.data).slice(0, 400);
            throw new Error(`ListModels ${res.status}: ${msg}`);
        }

        for (const m of res.data.models || []) {
            if (m.supportedGenerationMethods?.includes("generateContent")) {
                const id = `${m.name || ""}`.replace(/^models\//, "").trim();
                if (id) out.push(id);
            }
        }
        pageToken = res.data.nextPageToken;
        pages += 1;
        if (pages > 40) break;
    } while (pageToken);

    return out;
}

/** Prefer fast text models; skip obvious non-MCQ modalities. */
function prioritizeModelIds(ids) {
    const skip =
        /tts|text-to-speech|embed|embedding|image|vision|robotics|computer-use|live|audio|video/i;
    const usable = ids.filter((id) => !skip.test(id));

    const score = (id) => {
        let s = 0;
        if (/flash/i.test(id)) s += 100;
        if (/2\.5|3\.|3-/i.test(id)) s += 30;
        if (/lite/i.test(id)) s += 5;
        if (/preview|experimental/i.test(id)) s -= 15;
        if (/latest/i.test(id)) s += 10;
        return s;
    };

    return [...usable].sort((a, b) => score(b) - score(a));
}

/**
 * Prefer live ListModels order; prepend GEMINI_MODEL; append static guesses.
 */
async function buildModelAttemptOrder(apiKey) {
    const seen = new Set();
    const ordered = [];

    const push = (id) => {
        const x = `${id || ""}`.trim();
        if (!x || seen.has(x)) return;
        seen.add(x);
        ordered.push(x);
    };

    if (process.env.GEMINI_MODEL?.trim()) {
        push(process.env.GEMINI_MODEL.trim());
    }

    try {
        const now = Date.now();
        let discovered = modelIdCache;
        if (!discovered || now - modelIdCacheTime > MODEL_CACHE_MS) {
            discovered = await fetchAvailableModelIds(apiKey);
            modelIdCache = discovered;
            modelIdCacheTime = now;
            if (/^1|true|yes$/i.test(process.env.GEMINI_VERBOSE || "")) {
                console.log(
                    "[Gemini] ListModels count:",
                    discovered.length,
                    "top:",
                    discovered.slice(0, 8)
                );
            }
        }
        for (const id of prioritizeModelIds(discovered)) push(id);
    } catch (e) {
        console.warn("[Gemini] ListModels failed; using static list:", e.message);
    }

    for (const id of STATIC_MODELS) push(id);

    return ordered.length ? ordered : ["gemini-2.0-flash", "gemini-pro-latest"];
}

async function postGenerateContent(apiKey, apiVersion, modelId, body) {
    const base = `https://generativelanguage.googleapis.com/${apiVersion}`;
    const url = `${base}/models/${encodeURIComponent(modelId)}:generateContent`;

    const res = await axios.post(`${url}?key=${encodeURIComponent(apiKey)}`, body, {
        headers: { "Content-Type": "application/json" },
        timeout: 120000,
        validateStatus: () => true,
    });

    const { status, data } = res;

    if (status === 429) {
        const msg =
            data?.error?.message ||
            "Quota or rate limit exceeded. Wait or check billing in Google AI Studio.";
        const err = new Error(`Gemini quota (429): ${msg}`);
        err.status = 429;
        throw err;
    }

    if (status >= 400) {
        const msg =
            data?.error?.message ||
            (typeof data === "string" ? data : JSON.stringify(data)).slice(0, 700);
        const err = new Error(`Gemini HTTP ${status}: ${msg}`);
        err.status = status;
        throw err;
    }

    if (data?.error) {
        const msg =
            data.error.message || JSON.stringify(data.error).slice(0, 500);
        const err = new Error(`Gemini error: ${msg}`);
        throw err;
    }

    if (data?.promptFeedback?.blockReason) {
        throw new Error(`Gemini blocked: ${data.promptFeedback.blockReason}`);
    }

    return data;
}

/** Try v1beta then v1 for the same payload (some keys only expose one API version). */
async function postGeminiBestEffort(apiKey, modelId, body) {
    try {
        return await postGenerateContent(apiKey, "v1beta", modelId, body);
    } catch (e) {
        if (e.status === 404 || /not found|404/i.test(e.message || "")) {
            return await postGenerateContent(apiKey, "v1", modelId, body);
        }
        throw e;
    }
}

function buildFullPrompt(subjectName, topicNames, numQuestions) {
    const topicsSnippet =
        topicNames.slice(0, 120).join(" | ") || "general syllabus";

    return `You are writing practice exam MCQs.

Subject name: "${subjectName}"
Topics (reference only): ${topicsSnippet}

Create exactly ${numQuestions} distinct multiple-choice questions.

Output MUST be a single JSON object (no markdown) with this shape only:
{"questions":[
  {
    "question":"...",
    "option_a":"...",
    "option_b":"...",
    "option_c":"...",
    "option_d":"...",
    "correct_option":"A",
    "difficulty_level":2
  }
]}

Rules:
- difficulty_level is 1, 2, or 3
- correct_option must be exactly A, B, C, or D
- Balance difficulties across questions;
- Four meaningful options each.`;
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
        throw new Error("Model output had no usable MCQ rows after validation");
    }
    return cleaned;
}

/**
 * @returns {Promise<Array<{question,...}>>}
 */
async function generateMcqsWithGemini(apiKey, subjectName, topicNames, numQuestions) {
    if (!apiKey) throw new Error("Missing Gemini API key");

    const verbose = /^1|true|yes$/i.test(process.env.GEMINI_VERBOSE || "");
    const promptText = buildFullPrompt(subjectName, topicNames, numQuestions);

    const modelIds = await buildModelAttemptOrder(apiKey);

    const bodies = [
        {
            label: "jsonMime",
            body: {
                contents: [{ parts: [{ text: promptText }] }],
                generationConfig: {
                    temperature: 0.6,
                    maxOutputTokens: 8192,
                    responseMimeType: "application/json",
                },
            },
        },
        {
            label: "text",
            body: {
                contents: [{ parts: [{ text: promptText }] }],
                generationConfig: {
                    temperature: 0.6,
                    maxOutputTokens: 8192,
                },
            },
        },
    ];

    let lastErr = null;

    for (const modelId of modelIds) {
        for (const { label, body } of bodies) {
            try {
                if (verbose) console.log("[Gemini] try:", modelId, label);
                const data = await postGeminiBestEffort(apiKey, modelId, body);
                const text = extractResponseText(data);
                const arr = parseFlexibleJson(text);
                const cleaned = cleanQuestionRows(arr, numQuestions);
                if (verbose) console.log("[Gemini] ok:", modelId, label);
                return cleaned;
            } catch (e) {
                lastErr = e;
                if (verbose) console.warn("[Gemini] fail:", modelId, label, e.message);
                /** If JSON MIME unsupported, second body often works */
                continue;
            }
        }
    }

    const hint =
        "Set GEMINI_MODEL to an id from GET https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_KEY (field name without `models/`).";
    throw new Error(
        `${lastErr?.message || "All Gemini attempts failed"}. ${hint}`
    );
}

function clearGeminiModelCache() {
    modelIdCache = null;
    modelIdCacheTime = 0;
}

module.exports = {
    generateMcqsWithGemini,
    resolveGeminiApiKey,
    clearGeminiModelCache,
    fetchAvailableModelIds,
    GEMINI_MODEL: process.env.GEMINI_MODEL || "(resolved at runtime)",
};
