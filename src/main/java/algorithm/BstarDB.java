package algorithm;

import java.util.ArrayList;
import java.util.function.Function;

import gametree.GameTree;
import gametree.GameTree.Result;
import gametree.Node;
import gametree.SearchGameTree.SearchNode;

/**
 * B* Disprove-Best is an addendum to the basic B* algorithm.
 * It modifies the strategy employed by B* when the best optimistic value at root originates
 * from a different node as the best pessimistic value at root.
 * Top-level strategy is identical to any other basic B* variant, but the lower-level strategy
 * is improved when 'best-first' is forced. At each node during such a search the current 'best node'
 * at root will either be moved closer to 'proven' or 'disproven', depending on which goal requires
 * the least amount of 'expected effort'.
 */
public class BstarDB implements GameTreeSearch {
	
	/**
	 * @param useProvebest determines strategy used if (dis)prove best is *not* forced.
	 * @param effort_ratio determines how much effort 'pushing a node up' is relative to
	 * 'pushing a node down'. This is typically a number {@code > 0} and {@code <= 1} and should be lower
	 * for trees with a larger branching factor.
	 */
	public BstarDB(Function<SearchNode<?>, Boolean> useProvebest, boolean usePruning, double effort_ratio) {
		this.useProvebest = useProvebest;
		this.usePruning = usePruning;
		this.effort_ratio = effort_ratio;
	}

	private Function<SearchNode<?>, Boolean> useProvebest;
	private final boolean usePruning;
	private final double effort_ratio;
	
	private boolean provebest, forcedPB;
	private double lowerbound, upperbound;
	
	@Override
	public <E extends Node<E>> SearchNode<E> updateBounds(SearchNode<E> node) {
		// A future smarter algorithm could also use upper and lower bounds to limit how far
		//  we back-propagate to the minimum necessary amount. This would reduce overhead,
		//  but keep overall performance most-likely pretty similar - although it does add complexity
		// --> ANY depth-first version of B* MUST do this, or possibly suffer dramatic inefficiency
		boolean updated_bounds = node.adjust_bounds(true);
		if (usePruning) node.prune(false);
		if (updated_bounds && node.depth > 0)
			return updateBounds(node.parent());
		return node;
	}
	
	@Override
	public <E extends Node<E>> boolean stopCondition(SearchNode<E> root) {
		return GameTreeSearch.intractable(root) || SearchAlgorithm.separation(root);
	}
	
	@Override
	public <E extends Node<E>> void selectStrategy(SearchNode<E> node) {
		if (node.depth > 0) return;
		forcedPB = false;
		ArrayList<SearchNode<E>> children = node.children();
		if (children.size() <= 1) return;
		SearchNode<E> bestOpt = null, bestPes = null;
		double maxOpt = Double.NEGATIVE_INFINITY, maxPes = maxOpt, val;
		for (SearchNode<E> child : children) {
			val = -child.pessimistic();
			if (val > maxOpt) { maxOpt = val; bestOpt = child; }
			val = -child.optimistic();
			if (val > maxPes) { maxPes = val; bestPes = child; }
		}
		if (bestOpt != bestPes && bestOpt.optimistic() != bestPes.optimistic()) {
			// this step sets the bounds for the rest of the search until we return at root again
			provebest = true;
			forcedPB = true;
			lowerbound = maxPes;
			upperbound = Double.NEGATIVE_INFINITY;
			for (SearchNode<E> child : children) {
				if (child == bestOpt) continue;
				upperbound = Math.max(upperbound, -child.pessimistic());
			}
		}
		else provebest = useProvebest.apply(node);
	}
	
	@Override
	public <E extends Node<E>> SearchNode<E> selectNext(SearchNode<E> node) {
		ArrayList<SearchNode<E>> children = node.children();
		if (children.size() <= 0) return null;
		if (children.size() == 1) return children.get(0);
		
		if (forcedPB && node.depth > 0) {
			// ============== B*-DB logc ============== //
			boolean uneven = node.depth % 2 == 1;
			double a = uneven? lowerbound : -upperbound, b = uneven? upperbound : -lowerbound;
			
			double effort_up, effort_down, range;
			double total_up = Double.POSITIVE_INFINITY, total_down = 0, max_down = 0;
			SearchNode<E> bestUp = null, bestDown = null;
			
			boolean proof_down_impossible = false;
			int num_irrelevant = 0, num_proof_up_impossible = 0;
			
			for (int i = 0; i < children.size(); i++) {
				SearchNode<E> child = children.get(i);
				
				range = child.optimistic() - child.pessimistic();
				if (range < 0) System.err.println("HUH wtf");
				
				effort_up = child.optimistic() - a;
				effort_down = b - child.pessimistic();
				
				if (effort_up > range) {
					// this node will never be able to go above -a
					// so what? well... it makes a proof UP impossible from this node
					num_proof_up_impossible++;
				}
				if (effort_down > range) {
					// this node will never be able to go below -b
					// so what? well... it makes ANY proof DOWN impossible
					proof_down_impossible = true;
				}
				
				if (effort_up <= 0) {
					// this node has achieved the upwards goal!
					//  further search in this sub-tree is not adviced
					//  until the bounds a and b are recomputed at root
					System.err.println("upwards solved");
					return null; // go back up to root
				}
				if (effort_down <= 0) {
					// this node is irrelevant, it will not be considered, but NOT pruned
					num_irrelevant++;
				} else {
					if (effort_up < total_up && effort_up <= range) {
						bestUp = child;
						total_up = effort_up;
					}
					if (effort_down > max_down) {
						bestDown = child;
						max_down = effort_down;
					}
					total_down += effort_ratio * effort_down;
				}
			}
			if (num_irrelevant >= children.size()) {
				// all children are irrelevant - this means that the current node
				//  has achieved the downwards objective, further search in this sub-tree 
				//  is not adviced until bounds a and b are recomputed at root
				System.err.println("downwards solved");
				return null; // go back up to root
			}
			boolean proof_up_impossible = num_proof_up_impossible >= children.size();
			
			if (proof_up_impossible && !proof_down_impossible) {
				// we cannot prove the node goes up, this node will always stay BELOW -a
				// WHAT DO I DO??
				// does this even occur at all??
				System.err.println("up impossible");
				return bestDown;
			}
			if (!proof_up_impossible && proof_down_impossible) {
				// we cannot prove the node goes down, this node will always stay ABOVE -b
				// WHAT DO I DO??
				// does this even occur at all??
				System.err.println("down impossible");
				return bestDown;
			}
			if (proof_up_impossible && proof_down_impossible) {
				// we cannot prove anything in particular, this node will always stay BETWEEN a and b
				// WHAT DO I DO??
				// does this even occur at all??
				System.err.println("up and down impossible");
				return bestDown;
			}
			// we can prove both directions, so pick the best proof:
			// if down and up are equally good, go for bestDown, as this is the same as bestOpt
			return total_down <= total_up ? bestDown : bestUp;
		}
		else {
			Result<? extends SearchNode<E>> res = GameTree.findBest2(children);
			SearchNode<E> best = res.best(), secondbest = res.secondbest();
			if (node.depth > 0 || provebest) return best;
			return secondbest;
		}
	}

}
