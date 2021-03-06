package dp;

import core.Problem;
import heuristics.DeleteSelector;
import heuristics.MergeSelector;
import heuristics.VariableSelector;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents the DP graph.
 * Gives lower/upper bounds (or the exact solution)
 * by solving the MDD representation with the given width.
 * Solves a maximization problem by default.
 *
 * @author Vianney Coppé
 */
public class DP {

    private Layer root;
    private Layer lastExactLayer;
    private Set<State> frontier;
    private boolean exact;
    private Problem problem;
    private MergeSelector mergeSelector;
    private DeleteSelector deleteSelector;
    private VariableSelector variableSelector;

    /**
     * Returns the DP representation of the problem.
     *
     * @param problem          the implementation of a problem
     * @param mergeSelector    heuristic to select nodes to merge (to build relaxed MDDs)
     * @param deleteSelector   heuristic to select nodes to delete (to build restricted MDDs)
     * @param variableSelector heuristic to select the next variable to be assigned
     */
    public DP(Problem problem, MergeSelector mergeSelector, DeleteSelector deleteSelector, VariableSelector variableSelector) {
        this(problem, mergeSelector, deleteSelector, variableSelector, problem.root());
    }

    /**
     * Returns the DP representation of the problem.
     *
     * @param problem          the implementation of a problem
     * @param mergeSelector    heuristic to select nodes to merge (to build relaxed MDDs)
     * @param deleteSelector   heuristic to select nodes to delete (to build restricted MDDs)
     * @param variableSelector heuristic to select the next variable to be assigned
     * @param initialState     the state where to start the layers
     */
    private DP(Problem problem, MergeSelector mergeSelector, DeleteSelector deleteSelector, VariableSelector variableSelector, State initialState) {
        this.problem = problem;
        this.root = new Layer(problem, variableSelector, initialState, initialState.layerNumber());
        this.exact = true;
        this.lastExactLayer = null;
        this.frontier = new HashSet<>();
        this.mergeSelector = mergeSelector;
        this.deleteSelector = deleteSelector;
        this.variableSelector = variableSelector;
    }

    /**
     * Sets the initial state of the DP representation.
     *
     * @param initialState the state where to start the layers
     */
    public void setInitialState(State initialState) {
        this.root = new Layer(this.problem, this.variableSelector, initialState, initialState.layerNumber());
        this.lastExactLayer = null;
        this.exact = true;
    }

    /**
     * Solves the given problem starting from the given node with layers of at most {@code width}
     * states by deleting some states and thus providing a feasible solution.
     *
     * @param width the maximum width of the layers
     * @return the {@code State} object representing the best solution found
     */
    public State solveRestricted(int width, long startTime, int timeOut) {
        this.lastExactLayer = null;
        Layer lastLayer = root;

        while (!lastLayer.isFinal()) {
            if (System.currentTimeMillis() - startTime > timeOut * 1000) {
                return lastLayer.best();
            }

            lastLayer = lastLayer.nextLayer();

            if (lastLayer.width() > width) {
                State[] toRemove = this.deleteSelector.select(lastLayer, lastLayer.width() - width);
                lastLayer.removeStates(toRemove);
                this.exact = false;
            }

            if (!lastLayer.isExact()) {
                this.exact = false;
            }
        }

        return lastLayer.best();
    }

    /**
     * Solves the given problem starting from the given node with layers of at most {@code width}
     * states by merging some states and thus providing a solution not always feasible.
     *
     * @param width the maximum width of the layers
     * @return the {@code State} object representing the best solution found
     */
    public State solveRelaxed(int width, long startTime, int timeOut) {
        this.lastExactLayer = null;
        this.frontier.clear();
        Layer lastLayer = root;

        while (!lastLayer.isFinal()) {
            if (System.currentTimeMillis() - startTime > timeOut * 1000) {
                return lastLayer.best();
            }

            lastLayer = lastLayer.nextLayer();

            if (lastLayer.width() > width) {
                State[] toMerge = this.mergeSelector.select(lastLayer, lastLayer.width() - width + 1);
                lastLayer.removeStates(toMerge, this.frontier);

                State mergedState = this.problem.merge(toMerge);
                mergedState.setExact(false);

                lastLayer.addState(mergedState);
                this.exact = false;
            }

            if (lastLayer.isExact()) {
                this.lastExactLayer = lastLayer;
            } else {
                this.exact = false;
            }
        }

        for (State s : lastLayer.states()) {
            if (s.isExact()) {
                this.frontier.add(s);
            }
        }

        return lastLayer.best();
    }

    /**
     * Returns a {@code boolean} telling if this DP resolution was exact.
     *
     * @return {@code true} <==> all the layers are exact
     */
    public boolean isExact() {
        return this.exact;
    }

    /**
     * Solves the given problem starting from the given node.
     *
     * @return the {@code State} object representing the best solution found
     */
    public State solveExact() {
        return this.solveRelaxed(Integer.MAX_VALUE, Integer.MIN_VALUE, 0);
    }

    /**
     * Returns an exact cutset of the current DP tree.
     *
     * @return a set of exact states being an exact cutset
     */
    public Set<State> exactCutset() {
        return this.frontierCutset();
    }

    /**
     * Returns the last exact layer cutset,
     * which is the deepest layer equal to the corresponding complete MDD layer.
     *
     * @return the states of the last exact layer
     */
    private Collection<State> lastExactLayerCutset() {
        return this.lastExactLayer.states();
    }

    /**
     * Returns the frontier cutset.
     * A state is in the frontier cutset if it is an exact state
     * and if one of its successors is not.
     *
     * @return the states of the frontier cutset
     */
    private Set<State> frontierCutset() {
        return this.frontier;
    }
}
