/**
 * Quiz scoring: weighted proficiency, dynamic subject difficulty,
 * streak / momentum adjustments for planner-facing user_subjects.
 */

const ENGINE = {
    /** Target count per quiz (filled from easy/medium/hard pools). */
    DEFAULT_QUESTIONS_PER_QUIZ: 12,
    /** Desired mix when enough questions exist */
    EASY_PICK: 4,
    MEDIUM_PICK: 4,
    HARD_PICK: 4,
    /** difficulty_level → weight (spec) */
    WEIGHT_BY_LEVEL: { 1: 1, 2: 2, 3: 3 },
    /** Map weighted accuracy (0–1) → user_subjects.difficulty (1 easy … 3 hard for student) */
    DIFFICULTY_HIGH_ACCURACY: 0.72,
    DIFFICULTY_MID_ACCURACY: 0.42,
    /** Bonus: smooth confidence toward previous value if user improved */
    MOMENTUM_BLEND: 0.25,
    /** Repeated poor sessions compress confidence */
    FAIL_STREAK_PENALTY_BASE: 0.92,
    FAIL_STREAK_PER_EXTRA: 0.03,
    /** Strong pass streak nudges confidence up */
    PASS_STREAK_BONUS_PER: 0.015,
    PASS_STREAK_CAP: 0.08,
    /** High miss rate on hard items this session → extra penalty */
    HARD_MISS_RATIO_THRESHOLD: 0.5,
    HARD_MISS_PENALTY: 0.94,
    CLAMP_CONFIDENCE: { min: 0, max: 100 },
    CLAMP_SUBJECT_DIFFICULTY: { min: 1, max: 3 },
};

function weightForLevel(level) {
    const w = ENGINE.WEIGHT_BY_LEVEL[level] ?? ENGINE.WEIGHT_BY_LEVEL[2];
    return w;
}

/**
 * weighted_accuracy = weighted_correct / weighted_total (avoid div0)
 */
function computeWeightedAccuracy(gradedRows) {
    let weightedCorrect = 0;
    let weightedTotal = 0;
    let correctCount = 0;
    let hardAnswered = 0;
    let hardMiss = 0;

    for (const row of gradedRows) {
        const w = weightForLevel(row.difficulty_level);
        weightedTotal += w;
        if (row.is_correct) weightedCorrect += w;
        else if (row.difficulty_level === 3) hardMiss += 1;
        if (row.difficulty_level === 3) hardAnswered += 1;
        if (row.is_correct) correctCount += 1;
    }

    const n = gradedRows.length;
    const plainAccuracy = n > 0 ? correctCount / n : 0;
    const weightedAccuracy =
        weightedTotal > 0 ? weightedCorrect / weightedTotal : 0;

    const hardMissRatio =
        hardAnswered > 0 ? hardMiss / hardAnswered : 0;

    return {
        weighted_correct: weightedCorrect,
        weighted_total: weightedTotal,
        plain_accuracy: plainAccuracy,
        weighted_accuracy: weightedAccuracy,
        hard_miss_ratio: hardMissRatio,
    };
}

/**
 * Core formula: confidence = (weighted_correct / weighted_total) * 100
 */
function baseConfidencePct(metrics) {
    if (metrics.weighted_total <= 0) return 0;
    return Math.round(metrics.weighted_accuracy * 100);
}

/**
 * Planner difficulty_for_user_subject: easier subject rating when quiz goes well.
 */
function mapQuizPerformanceToDifficulty(weightedAccuracy) {
    if (weightedAccuracy >= ENGINE.DIFFICULTY_HIGH_ACCURACY) return 1;
    if (weightedAccuracy >= ENGINE.DIFFICULTY_MID_ACCURACY) return 2;
    return 3;
}

/**
 * Weak topics = any topic with at least one incorrect this session (sorted by misses desc).
 */
function weakTopicsAnalysis(gradedRows) {
    const byTopic = new Map();
    for (const row of gradedRows) {
        if (!byTopic.has(row.topic_id))
            byTopic.set(row.topic_id, {
                topic_id: row.topic_id,
                topic_name: row.topic_name,
                misses: 0,
                attempted: 0,
            });
        const t = byTopic.get(row.topic_id);
        t.attempted += 1;
        if (!row.is_correct) t.misses += 1;
    }
    const weak = [...byTopic.values()]
        .filter((t) => t.misses > 0)
        .sort((a, b) => b.misses - a.misses || b.attempted - a.attempted);
    return weak;
}

/**
 * AI-like adjustments: streaks, momentum from prior confidence, repeated failure damping.
 *
 * @param {number} rawConfidence — after base formula
 * @param {{ prior_confidence:number|null, streak_correct_sessions:number, streak_fail_sessions:number }} stats
 */
function applyAdaptiveAdjustments(rawConfidencePct, metrics, stats) {
    let c = rawConfidencePct;
    let notes = [];

    if (metrics.hard_miss_ratio >= ENGINE.HARD_MISS_RATIO_THRESHOLD) {
        c = Math.round(c * ENGINE.HARD_MISS_PENALTY);
        notes.push("hard_miss_penalty");
    }

    const failStreak = stats.streak_fail_sessions || 0;
    if (failStreak >= 2) {
        const factor =
            ENGINE.FAIL_STREAK_PENALTY_BASE -
            ENGINE.FAIL_STREAK_PER_EXTRA * Math.min(failStreak - 2, 4);
        c = Math.round(c * Math.max(0.75, factor));
        notes.push("fail_streak_damp");
    }

    const passStreak = stats.streak_correct_sessions || 0;
    if (passStreak >= 2) {
        const boost = Math.min(
            ENGINE.PASS_STREAK_CAP,
            (passStreak - 1) * ENGINE.PASS_STREAK_BONUS_PER
        );
        c = Math.round(Math.min(100, c * (1 + boost)));
        notes.push("pass_streak_boost");
    }

    const prev = stats.prior_confidence;
    const prevWa = stats.prior_weighted_accuracy;
    if (
        prev != null &&
        prevWa != null &&
        metrics.weighted_total > 0 &&
        metrics.weighted_accuracy > prevWa
    ) {
        const blended =
            c * (1 - ENGINE.MOMENTUM_BLEND) + prev * ENGINE.MOMENTUM_BLEND;
        c = Math.round(blended + (c - blended) * 0.35);
        notes.push("improvement_momentum");
    }

    return {
        confidence: clamp(
            c,
            ENGINE.CLAMP_CONFIDENCE.min,
            ENGINE.CLAMP_CONFIDENCE.max
        ),
        adjustment_notes: notes,
    };
}

function clamp(v, lo, hi) {
    return Math.max(lo, Math.min(hi, v));
}

/**
 * After this session outcome, update streak counters (prior stats row).
 */
function nextStreakState(priorStats, weightedAccuracy, passThreshold = 0.55) {
    const passed = weightedAccuracy >= passThreshold;
    return {
        streak_correct_sessions: passed
            ? (priorStats?.streak_correct_sessions || 0) + 1
            : 0,
        streak_fail_sessions: passed
            ? 0
            : (priorStats?.streak_fail_sessions || 0) + 1,
        session_passed: passed,
    };
}

module.exports = {
    ENGINE,
    weightForLevel,
    computeWeightedAccuracy,
    baseConfidencePct,
    mapQuizPerformanceToDifficulty,
    weakTopicsAnalysis,
    applyAdaptiveAdjustments,
    nextStreakState,
    confidenceFromAccuracy: baseConfidencePct,
};
