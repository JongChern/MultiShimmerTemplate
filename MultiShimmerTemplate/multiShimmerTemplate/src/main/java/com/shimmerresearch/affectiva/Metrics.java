package com.shimmerresearch.affectiva;

/**
 * An enum representing all metrics currently available in the Affectiva SDK
 */
public enum Metrics {
    //Emotions
    ANGER,
    DISGUST,
    FEAR,
    JOY,
    SADNESS,
    SURPRISE,
    CONTEMPT,
    ENGAGEMENT,
    VALENCE,

    //Expressions
    ATTENTION,
    BROW_FURROW,
    BROW_RAISE,
    CHEEK_RAISE,
    CHIN_RAISE,
    DIMPLER,
    EYE_CLOSURE,
    EYE_WIDEN,
    INNER_BROW_RAISE,
    JAW_DROP,
    LID_TIGHTEN,
    LIP_DEPRESSOR,
    LIP_PRESS,
    LIP_PUCKER,
    LIP_STRETCH,
    LIP_SUCK,
    MOUTH_OPEN,
    NOSE_WRINKLE,
    SMILE,
    SMIRK,
    UPPER_LIP_RAISER,

    //Measurements
    YAW,
    PITCH,
    ROLL,
    INTER_OCULAR_DISTANCE,

    // Appearances
    AGE,
    ETHNICITY,
    GENDER,
    GLASSES,

    // Qualities
    BRIGHTNESS;


    String getUpperCaseName() {
        return toString().replace("_", " ");
    }

    static int numberOfEmotions() {
        return ATTENTION.ordinal();
    }

    static int numberOfExpressions() {
        return YAW.ordinal() - numberOfEmotions();
    }

    static int numberOfMeasurements() {
        return AGE.ordinal() - numberOfEmotions() - numberOfExpressions();
    }

    static int numberOfAppearances() {
        return BRIGHTNESS.ordinal() - numberOfEmotions() - numberOfExpressions() - numberOfMeasurements();
    }

    static int numberOfQualities() {
        return Metrics.values().length - numberOfEmotions() - numberOfExpressions() - numberOfMeasurements() - numberOfAppearances();
    }

    /**
     * Returns an array to allow for iteration through all Emotions
     */
    public static Metrics[] getEmotions() {
        Metrics[] emotions = new Metrics[numberOfEmotions()];
        Metrics[] allMetrics = Metrics.values();
        System.arraycopy(allMetrics, 0, emotions, 0, numberOfEmotions());
        return emotions;
    }

    /*
    * Returns an array to allow for iteration through all Expressions
    */
    static Metrics[] getExpressions() {
        Metrics[] expressions = new Metrics[numberOfExpressions()];
        Metrics[] allMetrics = Metrics.values();
        System.arraycopy(allMetrics, numberOfEmotions(), expressions, 0, numberOfExpressions());
        return expressions;
    }

    /*
     * Returns an array to allow for iteration through all Measurements
     */
    static Metrics[] getMeasurements() {
        Metrics[] measurements = new Metrics[numberOfMeasurements()];
        Metrics[] allMetrics = Metrics.values();
        System.arraycopy(allMetrics, numberOfEmotions() + numberOfExpressions(),
                measurements, 0, numberOfMeasurements());
        return measurements;
    }

    /*
     * Returns an array to allow for iteration through all Appearances
     */
    static Metrics[] getAppearances() {
        Metrics[] appearances = new Metrics[numberOfAppearances()];
        Metrics[] allMetrics = Metrics.values();
        System.arraycopy(allMetrics, numberOfEmotions() + numberOfExpressions() + numberOfMeasurements(),
                appearances, 0, numberOfAppearances());
        return appearances;
    }

    /*
 * Returns an array to allow for iteration through all Qualities
 */
    static Metrics[] getQualities() {
        Metrics[] qualities = new Metrics[numberOfQualities()];
        Metrics[] allMetrics = Metrics.values();
        System.arraycopy(allMetrics, numberOfEmotions() + numberOfExpressions() + numberOfMeasurements() + numberOfAppearances(),
                qualities, 0, numberOfQualities());
        return qualities;
    }
}