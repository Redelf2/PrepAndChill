/**
 * Smart study planner: scoring, sustainable daily caps (esp. single-subject),
 * difficulty-aware weights, Pomodoro timelines with breaks.
 */

const PLANNER_PARAMS = {
    DEFAULT_DAILY_HOURS: 6,
    DEFAULT_DAYS_LEFT: 21,
    MIN_DAYS_LEFT: 1,
    DEFAULT_CONFIDENCE: 50,
    MIN_DIFFICULTY: 1,
    MAX_DIFFICULTY: 3,
    URGENCY_DECAY_TAU: 9,
    WEAKNESS_EXP: 1.65,
    WEIGHT_URGENCY: 2.65,
    WEIGHT_WEAKNESS: 2.25,
    WEIGHT_REMAINING: 2.75,
    WEIGHT_DIFFICULTY: 1.85,
    WEIGHT_MOMENTUM: 1.15,
    URGENCY_POWER: 1.82,
    MOMENTUM_LO: 0.12,
    MOMENTUM_HI: 0.92,
    MOMENTUM_BOOST: 1.22,
    MOMENTUM_BASE: 1.0,
    FALLBACK_TOPIC_COUNT: 1,
    MAX_SUBJECT_RATIO: 0.4,
    DEFAULT_MINUTES_PER_SUBJECT: 25,
    SOFTMAX_TEMPERATURE: 2.25,
    WEIGHT_JITTER_MAX: 0.05,
    LEARN_BASE: 0.11,
    LEARN_SPAN: 0.79,
    LEARN_CURVE_ALPHA: 0.74,
    EPSILON: 1e-9,
    /** Extra multiplier on raw score for harder subjects × remaining syllabus */
    DIFFICULTY_PRIORITY_TILT: 0.22,
    /** Pomodoro defaults */
    POMODORO_FOCUS_EASY: 28,
    POMODORO_FOCUS_MID: 25,
    POMODORO_FOCUS_HARD: 22,
    POMODORO_SHORT_BREAK: 5,
    POMODORO_SHORT_BREAK_HARD: 6,
    POMODORO_LONG_BREAK: 15,
    POMODORO_LONG_BREAK_HARD: 22,
    POMODORO_CYCLES_BEFORE_LONG: 4,
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

    const totalTopics = Math.max(Number.parseInt(row.total_topics, 10) || 0, 0);
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

    /** Cognitive-load tilt: harder subjects + more remaining topics pull schedule harder */
    const tilt =
        1 +
        p.DIFFICULTY_PRIORITY_TILT *
            difficultyNorm *
            (0.45 + 0.55 * remainingRatio);

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
        raw_priority: Math.max(rawPriority * tilt, p.EPSILON),
    };
}

/**
 * Single long exam runway → don't burn full "6h preference" every day on one subject.
 * Compresses toward evidence-style sustainable deep-work caps unless exam is imminent.
 */
function computeEffectiveDailyBudget(requestedMinutes, breakdowns) {
    const requested = Math.max(1, Math.round(requestedMinutes));
    const n = breakdowns.length;

    if (n === 1) {
        const b = breakdowns[0];
        const dl = b.days_left;
        let frac = 1;
        if (dl >= 28) frac = 0.34;
        else if (dl >= 21) frac = 0.40;
        else if (dl >= 14) frac = 0.50;
        else if (dl >= 10) frac = 0.58;
        else if (dl >= 7) frac = 0.67;
        else if (dl >= 5) frac = 0.76;
        else if (dl >= 3) frac = 0.88;
        else frac = 1;

        frac *= 1 - 0.06 * Math.max(0, b.difficulty - 1);
        frac *= 0.9 + 0.1 * clamp(b.components.remaining, 0.15, 1);

        let effective = Math.round(requested * frac);
        const cognitiveCeiling =
            dl <= 3 ? requested : dl <= 7 ? Math.min(requested, 330) : Math.min(requested, 255);
        effective = Math.min(effective, cognitiveCeiling);

        const floor = Math.min(requested, b.remaining_ratio > 0.38 ? 44 : 34);
        effective = clamp(effective, floor, requested);

        return {
            effectiveTotal: effective,
            requestedTotal: requested,
            rationale: `One subject: today targets **${(effective / 60).toFixed(2)}h focused work** (not the full ${(requested / 60).toFixed(1)}h slider) — spaced practice beats cramming when the exam is **${dl}d** away and difficulty is **${b.difficulty}/3**.`,
            single_subject_cap: true,
        };
    }

    return {
        effectiveTotal: requested,
        requestedTotal: requested,
        rationale:
            n > 1
                ? `${n} subjects: full **${(requested / 60).toFixed(1)}h** budget reweighted by urgency, confidence gaps, syllabus left, and hardness — capped so no single course steals >40% unless alone.`
                : "Budget unchanged.",
        single_subject_cap: false,
    };
}

function allocateMinutesStable(items, totalMinutes) {
    const p = PLANNER_PARAMS;
    const n = items.length;
    if (n === 0) return [];

    const maxEach =
        n <= 1 ? totalMinutes : totalMinutes * p.MAX_SUBJECT_RATIO;
    const idealMin = Math.min(
        p.DEFAULT_MINUTES_PER_SUBJECT,
        Math.floor(totalMinutes / Math.max(n, 1))
    );
    const minEach = Math.max(5, idealMin);

    const weights = items.map((it) => it.normalized_weight);
    let alloc = weights.map((w) => clamp(w * totalMinutes, 0, maxEach));

    function sumAlloc() {
        return alloc.reduce((a, b) => a + b, 0);
    }

    let s = sumAlloc();
    if (s < totalMinutes) {
        const slack = totalMinutes - s;
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
        alloc = alloc.map((m) => m * (totalMinutes / s));
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
 * Builds alternating focus / break segments until focus budget consumed.
 */
function buildPomodoroTimeline(focusMinutesTotal, difficultyLevel) {
    const p = PLANNER_PARAMS;
    const d = clamp(Number.parseInt(difficultyLevel, 10) || 2, 1, 3);
    let focusLen =
        d >= 3
            ? p.POMODORO_FOCUS_HARD
            : d <= 1
              ? p.POMODORO_FOCUS_EASY
              : p.POMODORO_FOCUS_MID;
    const shortB =
        d >= 3 ? p.POMODORO_SHORT_BREAK_HARD : p.POMODORO_SHORT_BREAK;
    const longB =
        d >= 3 ? p.POMODORO_LONG_BREAK_HARD : p.POMODORO_LONG_BREAK;
    const every = p.POMODORO_CYCLES_BEFORE_LONG;

    const timeline = [];
    let focusLeft = Math.max(1, Math.round(focusMinutesTotal));
    let cycleIdx = 0;
    let totalBreak = 0;
    let pomodoroCount = 0;

    while (focusLeft > 0) {
        const chunk = Math.min(focusLen, focusLeft);
        cycleIdx += 1;
        pomodoroCount += 1;
        timeline.push({
            phase: "focus",
            minutes: chunk,
            pomodoro_index: pomodoroCount,
            hint:
                d >= 3
                    ? "Hard subject — shorter bursts, higher precision"
                    : "Single-task; phone away",
        });
        focusLeft -= chunk;
        if (focusLeft <= 0) break;

        const longBreakNext = cycleIdx % every === 0;
        const br = longBreakNext ? longB : shortB;
        timeline.push({
            phase: longBreakNext ? "long_break" : "short_break",
            minutes: br,
            hint: longBreakNext ? "Walk, water, eyes off screen" : "Stand & stretch",
        });
        totalBreak += br;
    }

    const totalCal = Math.round(focusMinutesTotal + totalBreak);

    return {
        focus_typical_block_minutes: focusLen,
        short_break_minutes: shortB,
        long_break_minutes: longB,
        pomodoros_before_long_break: every,
        timeline,
        total_focus_minutes: Math.round(focusMinutesTotal),
        total_break_minutes: totalBreak,
        total_calendar_minutes: totalCal,
        summary: `${pomodoroCount} Pomodoro-style focus block(s), ${totalBreak}m recovery breaks → ${Math.round(totalCal / 60)}h ${totalCal % 60}m on the clock`,
    };
}

function strategyOneLiner(b, budgetMeta, minutes) {
    const parts = [];
    if (budgetMeta.single_subject_cap) {
        parts.push("Spaced single-focus day");
    }
    if (b.difficulty >= 3) parts.push("hard course → shorter bursts");
    if (b.confidence < 45) parts.push("confidence gap → extra learning slice");
    if (b.days_left <= 7) parts.push("exam soon → intensity unlocked");
    parts.push(`${minutes}m mapped to Pomodoro + breaks`);
    return parts.slice(0, 4).join("; ") + ".";
}

function buildPlan(dbRows, totalDailyHours, opts = {}) {
    const p = PLANNER_PARAMS;
    const hours = parsePositiveHours(totalDailyHours, p.DEFAULT_DAILY_HOURS);
    const requestedMinutes = Math.round(hours * 60);

    if (!dbRows || dbRows.length === 0) {
        return {
            total_minutes_budget: requestedMinutes,
            effective_minutes_budget: 0,
            plan: [],
            meta: {
                softmax_temperature: p.SOFTMAX_TEMPERATURE,
                weight_jitter_max: p.WEIGHT_JITTER_MAX,
                jitter_applied: false,
                planner_version: 2,
                budget_rationale: "No enrolled subjects.",
            },
        };
    }

    const breakdowns = dbRows.map(computeSubjectBreakdown);
    const budgetMeta = computeEffectiveDailyBudget(requestedMinutes, breakdowns);
    const effectiveBudget = budgetMeta.effectiveTotal;

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

    const minutesVec = allocateMinutesStable(breakdowns, effectiveBudget);

    const zipped = breakdowns.map((b, i) => ({
        b,
        row: dbRows[i],
        minutes: minutesVec[i],
    }));

    zipped.sort((a, c) => c.b.raw_priority - a.b.raw_priority);

    const plan = zipped.map(({ b, row, minutes }) => {
        const split = learningRevisionSplit(minutes, b.remaining_ratio);
        const pomodoro = buildPomodoroTimeline(minutes, b.difficulty);

        const combinedScore =
            p.WEIGHT_URGENCY * b.components.urgency +
            p.WEIGHT_WEAKNESS * b.components.weakness +
            p.WEIGHT_REMAINING * b.components.remaining +
            p.WEIGHT_DIFFICULTY * b.components.difficulty +
            p.WEIGHT_MOMENTUM * b.components.momentum;

        const syllabusCount = Number(row.total_topics);
        const hasSyllabus = syllabusCount > 0;
        const progressLine = hasSyllabus
            ? `${b.completed_topics}/${syllabusCount} topics`
            : "No syllabus topics (minimal slot)";

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
            session_span_minutes: pomodoro.total_calendar_minutes,
            progress: progressLine,
            split: {
                learning_minutes: split.learning_minutes,
                revision_minutes: split.revision_minutes,
            },
            priority_score: Number(combinedScore.toFixed(4)),
            pomodoro,
            insights: {
                urgency:
                    explanationFlags.exam_date_was_missing
                        ? `${b.days_left} day(s) horizon (no exam date — medium urgency)`
                        : `${b.days_left} day(s) to exam`,
                weakness:
                    explanationFlags.confidence_defaulted
                        ? `Baseline weaker profile (confidence not set)`
                        : `${100 - b.confidence}% gap vs full confidence`,
                progress: progressLine,
                focus: focusLabel(b.remaining_ratio),
                strategy: strategyOneLiner(b, budgetMeta, minutes),
            },
            explanation: {
                ...explanationFlags,
                softmax_weight: Number(b.normalized_weight.toFixed(6)),
                raw_priority: Number(b.raw_priority.toFixed(6)),
                difficulty_level: b.difficulty,
                cognitive_note: `Hardness ${b.difficulty}/3 interacts with confidence & days-left to set Pomodoro depth (${pomodoro.focus_typical_block_minutes}m blocks).`,
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

    const avgDiff =
        breakdowns.reduce((s, x) => s + x.difficulty, 0) /
        Math.max(breakdowns.length, 1);

    return {
        total_minutes_budget: requestedMinutes,
        effective_minutes_budget: effectiveBudget,
        plan,
        meta: {
            planner_version: 2,
            softmax_temperature: p.SOFTMAX_TEMPERATURE,
            weight_jitter_max: p.WEIGHT_JITTER_MAX,
            jitter_applied: jitterApplied,
            budget_requested_minutes: requestedMinutes,
            budget_effective_minutes: effectiveBudget,
            budget_rationale: budgetMeta.rationale,
            single_subject_cap_applied: budgetMeta.single_subject_cap,
            pomodoro_defaults: {
                cycles_before_long_break: p.POMODORO_CYCLES_BEFORE_LONG,
            },
            allocator: {
                max_subject_ratio: p.MAX_SUBJECT_RATIO,
                soft_min_minutes_requested: p.DEFAULT_MINUTES_PER_SUBJECT,
            },
            evaluator_notes: [
                "Scores blend exponential urgency, nonlinear weakness, syllabus remainder, hardness (1–3), and momentum.",
                "Single-subject days intentionally **under-fill** your slider unless the exam is near — mirrors spaced practice & avoids false '6h drilling' norms.",
                "Each row includes a **Pomodoro timeline**: focus + mandated breaks; `session_span_minutes` is wall-clock for calendars.",
                `Average enrolled difficulty this snapshot: **${avgDiff.toFixed(2)} / 3**.`,
            ],
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
    buildPomodoroTimeline,
    computeEffectiveDailyBudget,
};
