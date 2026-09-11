package algorithm;

import gametree.Node;

@Deprecated
public interface IntervalSearch<N extends Node<?>> {
	
	/**
	 * @param node the current node for which bounds need to be updated
	 * @return the next node to be selected after updating node bounds; may be equal to {@code node}
	 */
	N updateBounds(N node);
	
	/**
	 * @param root the node to test for the stopping criterium
	 * @return {@code true} if the search should be terminated for the sub-tree from the given node, or {@code false} otherwise
	 */
	boolean stopCondition(N root);
	
	/**
	 * @param node the current node from which to evaluate the current strategy
	 */
	void selectStrategy(N node);
	
	/**
	 * @param node the current node from which to select the next node
	 * @return the next node to be explored
	 */
	N selectNext(N node);
	
	/**
	 * Can be used to perform the interval search algorithm step-by-step. Performs a single step of
	 * the algorithm given the current node and the root node, then returns the next node.
	 * @param root the root node of this search
	 * @param current the current node of the search (initiate at {@code root}
	 * @return the next node in the iteration, or {@code null} if the algorithm has terminated.
	 */
	default N step(N root, N current) {
		current = updateBounds(current);
		if (stopCondition(root)) return null;
		selectStrategy(current);
		current = selectNext(current);
		if (current == null) current = root;
		return current;
	}
	
	default N stepA(N root, N current) {
		current = updateBounds(current);
		if (stopCondition(root)) return null;
		selectStrategy(current);
		return current;
	}
	default N stepB(N root, N current) {
		current = selectNext(current);
		if (current == null) current = root;
		return current;
	}
	
	/**
	 * @param root initial node to start the search from
	 * @param timeout_ms maximum time in milliseconds allowed for the search or {@code -1} to disable timeout.
	 * @return the best children node of {@code root} as proven by interval search or {@code null} if none exists or the search reached timeout.
	 */
	default Node<?> search(N root, long timeout_ms) {
		long t0 = System.currentTimeMillis();
		N current = root;
		while (timeout_ms < 0 || System.currentTimeMillis() - t0 < timeout_ms) {
			current = updateBounds(current);
			if (stopCondition(root)) return root.bestChildOpt();
			selectStrategy(current);
			current = selectNext(current);
			if (current == null) current = root;
		}
		return null;
	}
	/**
	 * @param root initial node to start the search from
	 * @return the best children node of {@code root} as proven by interval search or {@code null} if none exists.
	 */
	default Node<?> search(N root) {
		return search(root, -1);
	}
	
}
