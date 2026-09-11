package gametree;

import java.util.Collection;

public interface GameTree<R extends Node<R>> {
	
	R root();
	
	record Result<N extends Node<N>>(N best, N secondbest, int bestindex, int best2index) {}
	
	/**
	 * Calculate the best 2 nodes from the perspective of a MAXimizing player
	 * @param <E>
	 * @param children
	 * @return the best two nodes, in terms of optimistic value, in a collection of nodes - 
	 * while breaking ties with the pessimistic value.
	 */
	static <E extends Node<E>> Result<E> findBest2(Collection<E> children) {
		E best = null, secondbest = null;
		int ib1 = -1, ib2 = -1;
		double maxOpt = Double.NEGATIVE_INFINITY, max2Opt = maxOpt, optval;
		double maxPes = Double.NEGATIVE_INFINITY, max2Pes = maxPes, pesval;
		int index = 0;
		for (E child : children) {
			optval = -child.pessimistic();
			pesval = -child.optimistic();
			if (optval > maxOpt || (optval == maxOpt && pesval >= maxPes)) {
				max2Opt = maxOpt;
				max2Pes = maxPes;
				maxOpt = optval;
				maxPes = pesval;
				secondbest = best;
				best = child;
				ib2 = ib1;
				ib1 = index;
			} else if (optval > max2Opt || (optval == max2Opt && pesval >= max2Pes)) {
				max2Opt = optval;
				max2Pes = pesval;
				secondbest = child;
				ib2 = index;
			}
			index++;
		}
		return new Result<E>(best, secondbest, ib1, ib2);
	}	
}
