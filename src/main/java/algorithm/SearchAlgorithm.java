package algorithm;

import java.util.Collection;

import gametree.GameTree;
import gametree.GameTree.Result;
import gametree.Node;
import gametree.ResultNode;

public interface SearchAlgorithm {
	
	/**
	 * Encapsulates the results and metrics captured from an adversarial tree search.
	 * @param maxDepth maximum depth of search obtained during the search
	 * @param numEvaluated total number of times a node is evaluated during the search
	 * @param numExpanded total number of times a node is expanded during the search
	 * @param root a result-node at the root position of the original tree. This node should at least
	 *        contain the updated optimistic and pessimistic values of the root's children nodes,
	 *        reflecting the updated values as a result of the search.
	 */
	record Metric(long maxDepth, long numEvaluated, long numExpanded, ResultNode<?> root) {
		public boolean intractable() { return !separation(root); }
	}

	// have to keep track of the following:
	/* maxDepth, numEvaluated, numExpanded, separation, children of root[opt, depthOfOpt, depthOfPes] */
	// solution? SearchGameTree at root, containing only up to specified depth (set to 1)
	//  any deeper nodes must be kept only in transposition table as values, not as objects
	
	/**
	 * @param root root of the tree to be searched
	 * @param timeout_ms maximum time spent in the algorithm in milliseconds; or {@code -1} for no limit
	 * @return result from the search
	 */
	Metric search(Node<?> root, long timeout_ms);
	
	default Metric search(Node<?> root) {
		return search(root, -1);
	}
	default Metric search(GameTree<?> tree, long timeout_ms) {
		return search(tree.root(), timeout_ms);
	}
	default Metric search(GameTree<?> tree) {
		return search(tree, -1);
	}
	
	/**
	 * @param node the node to evaluate the separation condition from
	 * @return {@code true} if the sub-tree from the given {@code node} could be terminated according to the separation rule; {@code false} otherwise.
	 */
	static <N extends Node<N>> boolean separation(Node<N> node) {
		Collection<N> children = node.children();
		if (children == null || children.size() <= 1) return true;
		Result<N> res = GameTree.findBest2(children);
		N best = res.best(), secondbest = res.secondbest();
		return -best.optimistic() >= -secondbest.pessimistic();
	}
	
}
