/**
 * Smart study planner: priority scoring, normalization, time allocation,
 * learning/revision split. Tuned via PLANNER_PARAMS (single source of tuning).
 */

const PLANNER_PARAMS = {
    DEFAULT_DAILY_HOURS: 6,
    /** When exam_date missing: equivalent "medium" days-to-exam for urgency decay */
    DEFAULT_DAYS_LEFT: 21,
    MIN_DAYS_LEFT: 1,
    DEFAULT_CONFIDENCE: 50,
    MIN_DIFFICULTY: 1,
    MAX_DIFFICULTY: 3,
    /** Urgency = exp(-daysLeft / URGENCY_DECAY_TAU) — smaller tau → drops faster when far */
    URGENCY_DECAY_TAU: 9,
    /** Weakness = ((100 - conf) / 100) ^ WEAKNESS_EXP — >1 penalizes low confidence more */
    WEAKNESS_EXP: 1.65,
    /** Component weights for raw score (before normalization) */
    WEIGHT_URGENCY: 2.8,
    WEIGHT_WEAKNESS: 2.2,
    WEIGHT_REMAINING: 2.6,
    WEIGHT_DIFFICULTY: 1.4,
    WEIGHT_MOMENTUM: 1.2,
    /** Squared urgency emphasis (already rising when close) */
    URGENCY_POWER: 1.85,
    /** Momentum: boost when progress in (MOMENTUM_LO, MOMENTUM_HI) */
    MOMENTUM_LO: 0.12,
    MOMENTUM_HI: 0.92,
    MOMENTUM_BOOST: 1.22,
    MOMENTUM_BASE: 1.0,
    /** If DB reports 0 topics, use this for syllabus ratio math (still allocate min time) */
    FALLBACK_TOPIC_COUNT: 1,
    /** Max share of daily minutes for one subject */
    MAX_SUBJECT_RATIO: 0.4,
    DEFAULT_MINUTES_PER_SUBJECT: 25,
    /** Softmax temperature for weight normalization (higher → flatter, more stable) */
    SOFTMAX_TEMPERATURE: 2.4,
    /** Optional jitter on normalized weights: 0 = off, 0.04 = ±4% multiplicative */
    WEIGHT_JITTER_MAX: 0.05,
    /** Learning share: base + span * remaining^alpha (remaining in [0,1]) */
    LEARN_BASE: 0.12,
    LEARN_SPAN: 0.78,
    LEARN_CURVE_ALPHA: 0.72,
    EPSILON: 1e-9,
};

function clamp(n, lo, hi) {
    return Math.max(lo, Math.min(hi, n));
}

function parsePositiveHours(value, fallback) {
    const x = Number(value);
    if (!Number.isFinite(x) || x <= 0) return fallback;
    return x;
}

function daysUntilExam(examDate) {
    if (!examDate) return PLANNER_PARAMS.DEFAULT_DAYS_LEFT;
    const d = new Date(examDate);
    if (Number.isNaN(d.getTime())) return PLANNER_PARAMS.DEFAULT_DAYS_LEFT;
    const now = new Date();
    const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const startExam = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    const diffMs = startExam - startToday;
    const days = Math.ceil(diffMs / (1000 * 60 * 60 * 24));
    return Math.max(PLANNER_PARAMS.MIN_DAYS_LEFT, days);
}

/**
 * Deterministic PRNG [0,1) from string seed (stable plans per user+day when desired).
 */
function mulberry32FromString(seedStr) {
    let h = 1779033703 ^ seedStr.length;
    for (let i = 0; i < seedStr.length; i++) {
        h = Math.imul(h ^ seedStr.charCodeAt(i), 3432918353);
        h = (h << 13) | (h >>> 19);
    }
    return function next() {
        h = Math.imul(h ^ (h >>> 16), 2246822507);
        h = Math.imul(h ^ (h >>> 13), 3266489909);
        h ^= h >>> 16;
        return (h >>> 0) / 4294967296;
    };
}

function softmaxNormalized(values, temperature) {
    const t = Math.max(PLANNER_PARAMS.EPSILON, temperature);
    const scaled = values.map((v) => v / t);
    const maxS = Math.max(...scaled);
    const exps = scaled.map((s) => Math.exp(s - maxS));
    const sum = exps.reduce((a, b) => a + b, 0) + PLANNER_PARAMS.EPSILON;
    return exps.map((e) => e / sum);
}

/**
 * Apply small multiplicative jitter to weights then renormalize to sum 1.
 */
function jitterWeights(weights, rng) {
    const jm = PLANNER_PARAMS.WEIGHT_JITTER_MAX;
    if (jm <= 0 || !rng) return weights.slice();
    const perturbed = weights.map((w) => {
        const u = rng() * 2 - 1;
        return Math.max(PLANNER_PARAMS.EPSILON, w * (1 + u * jm));
    });
    const s = perturbed.reduce((a, b) => a + b, 0);
    return perturbed.map((w) => w / s);
}

/**
 * Single subject: raw components and combined raw priority (pre-softmax within batch).
 */
function computeSubjectBreakdown(row) {
    const p = PLANNER_PARAMS;
    const difficulty = clamp(
        Number.parseInt(row.difficulty, 10) || 2,
        p.MIN_DIFFICULTY,
        p.MAX_DIFFICULTY
    );
    const confidenceRaw = row.confidence;
    const confidence =
        confidenceRaw == null || confidenceRaw === ""
            ? p.DEFAULT_CONFIDENCE
            : clamp(Number.parseInt(confidenceRaw, 10), 0, 100);

    const totalTopics = Math.max(
        Number.parseInt(row.total_topics, 10) || 0,
        0
    );
    const effectiveTotal = Math.max(totalTopics, p.FALLBACK_TOPIC_COUNT);
    const completed = clamp(
        Number.parseInt(row.completed_topics, 10) || 0,
        0,
        effectiveTotal
    );

    const daysLeft = daysUntilExam(row.exam_date);
    const urgencyBase = Math.exp(-daysLeft / p.URGENCY_DECAY_TAU);
    const urgency = Math.pow(urgencyBase, p.URGENCY_POWER);

    const weakness = Math.pow((100 - confidence) / 100, p.WEAKNESS_EXP);
    const remainingRatio = (effectiveTotal - completed) / effectiveTotal;
    const progress = completed / effectiveTotal;
    const difficultyNorm = difficulty / p.MAX_DIFFICULTY;

    let momentum = p.MOMENTUM_BASE;
    if (progress > p.MOMENTUM_LO && progress < p.MOMENTUM_HI) {
        momentum = p.MOMENTUM_BOOST;
    }

    const rawPriority =
        p.WEIGHT_URGENCY * urgency +
        p.WEIGHT_WEAKNESS * weakness +
        p.WEIGHT_REMAINING * remainingRatio +
        p.WEIGHT_DIFFICULTY * difficultyNorm +
        p.WEIGHT_MOMENTUM * momentum;

    return {
        subject_id: row.subject_id,
        subject: row.subject,
        difficulty,
        confidence,
        days_left: daysLeft,
        total_topics: totalTopics,
        completed_topics: completed,
        effective_total_topics: effectiveTotal,
        remaining_ratio: remainingRatio,
        progress,
        components: {
            urgency,
            weakness,
            remaining: remainingRatio,
            difficulty: difficultyNorm,
            momentum,
        },
        raw_priority: Math.max(rawPriority, p.EPSILON),
    };
}

/**
 * Allocate totalMinutes across subjects with max share cap and minimum per subject.
 * Refines when sum after caps/mins drifts from totalMinutes.
 */
function allocateMinutesStable(items, totalMinutes) {
    const p = PLANNER_PARAMS;
    const n = items.length;
    if (n === 0) return [];

    /** With one subject, no pair to protect — use full budget; cap only when n ≥ 2 */
    const maxEach =
        n <= 1 ? totalMinutes : totalMinutes * p.MAX_SUBJECT_RATIO;
    const idealMin = Math.min(
        p.DEFAULT_MINUTES_PER_SUBJECT,
        Math.floor(totalMinutes / Math.max(n, 1))
    );
    const minEach = Math.max(5, idealMin);

    let weights = items.map((it) => it.normalized_weight);
    const base = weights.map((w) => w * totalMinutes);

    let alloc = base.map((m) => clamp(m, 0, maxEach));

    function sumAlloc() {
        return alloc.reduce((a, b) => a + b, 0);
    }

    let s = sumAlloc();
    if (s < totalMinutes) {
        let slack = totalMinutes - s;
        const headroom = alloc.map((m, i) => Math.max(0, maxEach - m));
        const hw = headroom.reduce((a, b) => a + b, 0);
        if (hw > p.EPSILON) {
            for (let i = 0; i < n; i++) {
                alloc[i] += (headroom[i] / hw) * slack;
            }
        }
    }

    s = sumAlloc();
    if (s > totalMinutes && s > p.EPSILON) {
        const factor = totalMinutes / s;
        alloc = alloc.map((m) => m * factor);
    }

    alloc = alloc.map((m) => Math.max(minEach, Math.round(m)));

    s = sumAlloc();
    if (s > totalMinutes) {
        const factor = totalMinutes / s;
        alloc = alloc.map((m) => Math.max(1, Math.floor(m * factor)));
        s = sumAlloc();
        let deficit = totalMinutes - s;
        let idx = 0;
        while (deficit > 0 && idx < n * totalMinutes) {
            const i = idx % n;
            if (alloc[i] + 1 <= maxEach) {
                alloc[i] += 1;
                deficit -= 1;
            }
            idx += 1;
        }
    } else if (s < totalMinutes) {
        let extra = totalMinutes - s;
        const order = [...Array(n).keys()].sort(
            (a, b) => weights[b] - weights[a]
        );
        let oi = 0;
        while (extra > 0 && oi < n * totalMinutes * 2) {
            const i = order[oi % n];
            if (alloc[i] + 1 <= maxEach) {
                alloc[i] += 1;
                extra -= 1;
            }
            oi += 1;
        }
        if (extra > 0) {
            for (let k = 0; k < n && extra > 0; k++) {
                alloc[k % n] += 1;
                extra -= 1;
            }
        }
    }

    return alloc;
}

function learningRevisionSplit(totalMinutes, remainingRatio) {
    const p = PLANNER_PARAMS;
    const rr = clamp(remainingRatio, 0, 1);
    const learnShare = clamp(
        p.LEARN_BASE + p.LEARN_SPAN * Math.pow(rr, p.LEARN_CURVE_ALPHA),
        0.05,
        0.95
    );
    const learningMinutes = Math.round(totalMinutes * learnShare);
    const revisionMinutes = Math.max(0, totalMinutes - learningMinutes);
    return { learning_minutes: learningMinutes, revision_minutes: revisionMinutes };
}

function focusLabel(remainingRatio) {
    if (remainingRatio >= 0.55) return "New learning";
    if (remainingRatio >= 0.2) return "Balanced practice";
    return "Revision & reinforcement";
}

/**
 * Main pipeline from SQL rows → plan array + persist payload.
 *
 * @param {object[]} dbRows — rows from generatePlan SQL
 * @param {number} totalDailyHours
 * @param {object} [opts]
 * @param {boolean} [opts.useJitter] — perturb weights slightly (deterministic seed)
 * @param {string} [opts.jitterSeed] — e.g. `${firebase_uid}:${dateKey}`
 */
function buildPlan(dbRows, totalDailyHours, opts = {}) {
    const p = PLANNER_PARAMS;
    const hours = parsePositiveHours(
        totalDailyHours,
        p.DEFAULT_DAILY_HOURS
    );
    const totalMinutes = Math.round(hours * 60);

    if (!dbRows || dbRows.length === 0) {
        return {
            total_minutes_budget: totalMinutes,
            plan: [],
            meta: {
                softmax_temperature: p.SOFTMAX_TEMPERATURE,
                weight_jitter_max: p.WEIGHT_JITTER_MAX,
                jitter_applied: false,
            },
        };
    }

    const breakdowns = dbRows.map(computeSubjectBreakdown);
    const raw = breakdowns.map((b) => b.raw_priority);

    let normalized = softmaxNormalized(raw, p.SOFTMAX_TEMPERATURE);

    let jitterApplied = false;
    let rng = null;
    if (opts.useJitter && opts.jitterSeed) {
        rng = mulberry32FromString(opts.jitterSeed);
        normalized = jitterWeights(normalized, rng);
        jitterApplied = true;
    }

    breakdowns.forEach((b, i) => {
        b.normalized_weight = normalized[i];
    });

    const minutesVec = allocateMinutesStable(breakdowns, totalMinutes);

    const zipped = breakdowns.map((b, i) => ({
        b,
        row: dbRows[i],
        minutes: minutesVec[i],
    }));

    zipped.sort((a, c) => c.b.raw_priority - a.b.raw_priority);

    const plan = zipped.map(({ b, row, minutes }) => {
        const split = learningRevisionSplit(minutes, b.remaining_ratio);

        const combinedScore =
            p.WEIGHT_URGENCY * b.components.urgency +
            p.WEIGHT_WEAKNESS * b.components.weakness +
            p.WEIGHT_REMAINING * b.components.remaining +
            p.WEIGHT_DIFFICULTY * b.components.difficulty +
            p.WEIGHT_MOMENTUM * b.components.momentum;

        const syllabusCount = Number(row.total_topics);
        const hasSyllabus = syllabusCount > 0;

        const explanationFlags = {
            exam_date_was_missing:
                row.exam_date == null ||
                row.exam_date === "" ||
                String(row.exam_date).startsWith("0000"),
            confidence_defaulted:
                row.confidence == null || row.confidence === "",
            difficulty_defaulted:
                row.difficulty == null || row.difficulty === "",
            topics_empty_in_db: !(Number(row.total_topics) > 0),
        };

        return {
            subject: b.subject,
            subject_id: b.subject_id,
            time_hours: Number((minutes / 60).toFixed(3)),
            time_minutes: minutes,
            split: {
                learning_minutes: split.learning_minutes,
                revision_minutes: split.revision_minutes,
            },
            priority_score: Number(combinedScore.toFixed(4)),
            insights: {
                urgency:
                    explanationFlags.exam_date_was_missing
                        ? `${b.days_left} day(s) horizon (no exam date — medium urgency)`
                        : `${b.days_left} day(s) to exam`,
                weakness:
                    explanationFlags.confidence_defaulted
                        ? `Baseline weaker profile (confidence not set)`
                        : `${100 - b.confidence}% gap vs full confidence`,
                progress: hasSyllabus
                    ? `${b.completed_topics}/${syllabusCount} topics`
                    : "No syllabus topics (minimal daily slot)",
                focus: focusLabel(b.remaining_ratio),
            },
            explanation: {
                ...explanationFlags,
                softmax_weight: Number(b.normalized_weight.toFixed(6)),
                raw_priority: Number(b.raw_priority.toFixed(6)),
                component_breakdown: {
                    urgency_raw: Number(b.components.urgency.toFixed(4)),
                    weakness_raw: Number(b.components.weakness.toFixed(4)),
                    remaining: Number(b.components.remaining.toFixed(4)),
                    difficulty_norm: Number(
                        b.components.difficulty.toFixed(4)
                    ),
                    momentum_multiplier: b.components.momentum,
                },
                weights_used: {
                    urgency: p.WEIGHT_URGENCY,
                    weakness: p.WEIGHT_WEAKNESS,
                    remaining: p.WEIGHT_REMAINING,
                    difficulty: p.WEIGHT_DIFFICULTY,
                    momentum: p.WEIGHT_MOMENTUM,
                },
            },
        };
    });

    return {
        total_minutes_budget: totalMinutes,
        plan,
        meta: {
            softmax_temperature: p.SOFTMAX_TEMPERATURE,
            weight_jitter_max: p.WEIGHT_JITTER_MAX,
            jitter_applied: jitterApplied,
            allocator: {
                max_subject_ratio: p.MAX_SUBJECT_RATIO,
                soft_min_minutes_requested: p.DEFAULT_MINUTES_PER_SUBJECT,
            },
        },
    };
}

module.exports = {
    PLANNER_PARAMS,
    buildPlan,
    daysUntilExam,
    computeSubjectBreakdown,
    allocateMinutesStable,
    learningRevisionSplit,
};
