package it.unifi.dinfo.stlab.modeling;

import it.unifi.dinfo.stlab.modeling.compositeTaskType.CompositeTaskType;


public record Constants() {

    /**
     * The <b>Epsilon</b> value used as a threshold in <b>CDF calculus</b>.
     * <p>
     * This is used as treshold for finding the time limit.
     * <p>
     * This is applied specifically when {@link SagaTask}<code>.analyzeWithMaximumArrayLength(n)</code>
     * is called.
     */
    public static final double EPSILON_FOR_ANALYSIS = 0.00001;

    /**
     * Defines the <b>number of steps</b> used for the <b>time limit</b> and <b>time tick</b> calculus.
     * <p>
     * Used primarily within {@link CompositeTaskType}.
     * <p>
     * <b>Note:</b>
     * <ul>
     * <li>This is only used to search for an upper bound for time limit </li>
     * <li>Then for the final analysis the CDF is calculated by considering the upper bound found and 
     * {@link Constants#RECOMMENDED_ANALYSIS_ARRAY_LENGTH} number of step </li>
     * </ul>
     */
    public static final double N_ELEMENTS_CDF_CALCULUS = 2000;

    /**
     * The array length used for the computation of <b>Expected Completion Time</b> analysis.
     * <p>
     * Utilized by {@link CompositeTaskType}<code>.calc*()</code> methods.
     */
    public static final int ARRAY_LENGTH_FOR_PROXY_ANALYSIS = 1000;

    /**
     * The <b>recommended array length</b> for analysis, based on <i>empirical testing</i>.
     * <p>
     * <b>Note:</b> This value directly impacts <b>analysis precision</b> and should be adjusted based on the
     * workflow complexity.
     * </p>
     * <b>Guidelines:</b>
     * <ul>
     * <li>A value of <code>2000</code> is generally sufficient for workflows with <b>&lt; 100 simple services</b>.</li>
     * <li>This value may also require adjustment based on the specific <b>workflow topology</b>.</li>
     * </ul>
     */
    public static final int RECOMMENDED_ANALYSIS_ARRAY_LENGTH = 1000;

    /**
     * Epsilon value used for the probability calculation exception
     */
    public static final double EPSILON_PROBABILITY = 0.001;

    /**
     * Number of time step used for the regenerative transient analysis
     */
    public static final double REGENERATIVE_TRANSIENT_ANALYSIS_N_TIME_STEP = 50;

    /**
     * Error used in the regenerative transient analysis checking function.
     * The function check if the last row of the regenerative transient analysis sum to a number near to 1.
     * This value represents the max error allowed.
     */
    public static final double ERROR_REGENERATIVE_ANALYSIS_ALLOWED = 0.01;

    public static final int K_PHASES_FOR_GEN_ERLANG_CONVERSION_TO_SHIFTED_EXP = 8;
}
