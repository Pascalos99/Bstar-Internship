package algorithm;

import gametree.Node;
import gametree.SearchGameTree;
import gametree.SearchGameTree.SearchNode;

public interface GameTreeSearch extends SearchAlgorithm {
	
	class Limits {
		public long maxDepth = Long.MAX_VALUE, maxEvaluated = Long.MAX_VALUE, maxExpanded = Long.MAX_VALUE;
		public boolean useLimits = true;
	}
	Limits limits = new Limits();
	
	/**
	 * @param root
	 * @return {@code true} if search beyond this node has reached levels qualified as 'intractable' (depth of 101 or deeper, or over 30.000 nodes explored)
	 */
	static boolean intractable(SearchNode<?> root) {
		if (limits.useLimits)
			return root.deepness() > limits.maxDepth || root.evaluationCount() > limits.maxEvaluated || root.expansionCount() > limits.maxExpanded;
		return intractableExperimental(root);
	}
	
	// Parameters extracted from logistic regression on collected data.
	// This determines the probability that `seconds` will be in the 99.5th percentile.
	// I set the threshold to such a value that the expected time spent on runs is the same
	//   while the expected number of additional data points is maximised.
	double threshold = 0.005;
	double intercept = -10.748030995481102;
	double coeff_dp = 0.0006974008629046038;
	double coeff_ev = 6.324293062075819e-05;
	double coeff_ex = -0.00016916376193954486;
	static boolean intractableExperimental(SearchNode<?> root) {
		double dp = root.deepness();
		double ev = root.evaluationCount();
		double ex = root.expansionCount();
		double Pr = 1. / (1 + Math.exp(intercept + coeff_dp*dp + coeff_ev*ev + coeff_ex*ex));
		return 1 - Pr > threshold;
	}
	
	/**
	 * @param node the current node for which bounds need to be updated
	 * @return the next node to be selected after updating node bounds; may be equal to {@code node}
	 */
	<E extends Node<E>> SearchNode<E> updateBounds(SearchNode<E> node);
	
	/**
	 * @param root the node to test for the stopping criterium
	 * @return {@code true} if the search should be terminated for the sub-tree from the given node, or {@code false} otherwise
	 */
	<E extends Node<E>> boolean stopCondition(SearchNode<E> root);
	
	/**
	 * @param node the current node from which to evaluate the current strategy
	 */
	<E extends Node<E>> void selectStrategy(SearchNode<E> node);
	
	/**
	 * @param node the current node from which to select the next node
	 * @return the next node to be explored
	 */
	<E extends Node<E>> SearchNode<E> selectNext(SearchNode<E> node);
	
	/**
	 * Can be used to perform the interval search algorithm step-by-step. Performs a single step of
	 * the algorithm given the current node and the root node, then returns the next node.
	 * @param root the root node of this search
	 * @param current the current node of the search (initiate at {@code root}
	 * @return the next node in the iteration, or {@code null} if the algorithm has terminated.
	 */
	default <E extends Node<E>> SearchNode<E> step(SearchNode<E> root, SearchNode<E> current) {
		current = updateBounds(current);
		if (stopCondition(root)) return null;
		selectStrategy(current);
		current = selectNext(current);
		if (current == null) current = root;
		return current;
	}
	
	default <E extends Node<E>> SearchNode<E> stepA(SearchNode<E> root, SearchNode<E> current) {
		current = updateBounds(current);
		if (stopCondition(root)) return null;
		selectStrategy(current);
		return current;
	}
	default <E extends Node<E>> SearchNode<E> stepB(SearchNode<E> root, SearchNode<E> current) {
		current = selectNext(current);
		if (current == null) current = root;
		return current;
	}
	
	/**
	 * Searches the given tree and adjusts it while performing the search.
	 * This saves the result of the search in the tree itself (thus, modifying the input node)
	 * @param root initial node to start the search from
	 * @param timeout_ms maximum time in milliseconds allowed for the search or {@code -1} to disable timeout.
	 */
	default void searchTree(SearchNode<?> root, long timeout_ms) {
		long t0 = System.currentTimeMillis();
		SearchNode<?> current = root;
		root.evaluate();
		while (timeout_ms < 0 || System.currentTimeMillis() - t0 < timeout_ms) {
			current = updateBounds(current);
			if (stopCondition(root)) return;
			selectStrategy(current);
			current = selectNext(current);
			if (current == null) current = root;
		}
		return;
	}
	default void searchTree(SearchNode<?> root) { searchTree(root, -1); }
	default void searchTree(SearchGameTree<?> tree, long timeout_ms) { searchTree(tree.root(), -1); }
	default void searchTree(SearchGameTree<?> tree) { searchTree(tree, -1); }
	
	@Override
	default Metric search(Node<?> root, long timeout_ms) {
		SearchGameTree<?> tree = new SearchGameTree<>(root);
		searchTree(tree, timeout_ms);
		return tree.root().metrics();
	}
}
