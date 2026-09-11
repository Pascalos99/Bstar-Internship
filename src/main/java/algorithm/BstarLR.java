package algorithm;

import static gametree.Node.compareOpt;
import static gametree.Node.comparePes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Function;

import gametree.Node;
import gametree.SearchGameTree.SearchNode;

@Deprecated
/**
 * B* Lower & Raise (B*LR), an alternative implementation of the B* algorithm which keeps track of one of three
 * goals for each node while running. The root node always has the <b>separation</b> goal, whereas (mostly) all
 * sub-trees follow either the <b>lower</b> or <b>raise</b> goal. The goal determines what node is selected next.
 * The <b>lower</b> and <b>raise</b> goals work very simply and without heuristics; whereas the <b>separation</b>
 * goal tries to compute the best candidate for exploration based on probabilities. For now, I will just work with 
 * very basic probability density functions that do not take distributions of children in mind.
 */
public class BstarLR implements GameTreeSearch {
	
	// TODO maybe later on add some way to 'mark' nodes for separation to save memory.
	
	private enum Goal { Separation, Lower, Raise, BestFirst }
	
	/**
	 * The current goal at nodes other than root. <b>Lower</b> will attempt to <i>lower</i> optimistic values for MAX nodes
	 * and <i>raise</i> pessimistic values for MIN nodes. Whereas <b>Raise</b> will attempt to <i>raise</i> pessimistic
	 * values for MAX nodes and <i>lower</i> optimistic values for MIN nodes. <b>Separation</b> means the goal has to be
	 * re-determined from the current position, which is chosen to cause separation at that node as soon as possible.
	 */
	private Goal goal;
	/**
	 * Similarly to the parameter for {@link BstarBasic}, this function determines which strategy to use in ambiguous situations.
	 * It should return {@code true} when PROVEBEST should be applied (searching the Best node at root).
	 */
	private Function<SearchNode<?>, Boolean> useProvebest;
	
	// TODO read more into Palay's PB* -- can we use elements to improve top-level decisions?
	//    ... do their findings and algorithms on lower-level decisions make a positive difference?
	//    ... how does it balance time efficiency in terms of additional computational costs?
	
	public BstarLR(Function<SearchNode<?>, Boolean> useProveBest) {
		goal = Goal.Separation;
		this.useProvebest = useProveBest;
	}

	@Override
	public <E extends Node<E>> SearchNode<E> updateBounds(SearchNode<E> node) {
		if (node.adjust_bounds(true) && node.depth > 0)
			return updateBounds(node.parent());
		return node;
	}
	
	@Override
	public <E extends Node<E>> boolean stopCondition(SearchNode<E> root) {
		return GameTreeSearch.intractable(root) || SearchAlgorithm.separation(root);
	}

	@Override
	public <E extends Node<E>> void selectStrategy(SearchNode<E> node) {
		if (node.depth() <= 0) goal = Goal.Separation;
	}

	@Override
	public <E extends Node<E>> SearchNode<E> selectNext(SearchNode<E> node) {
		
		ArrayList<? extends SearchNode<E>> children = node.children();
		if (children.size() <= 0) return null;
		if (children.size() == 1) return children.get(0);
		
		if (goal == Goal.Separation) {
			/*
			 * temporary implementation (?)
			 * this is overly simplified.. doesn't take probabilities into account, and only looks at ranges..
			 * let's see if it works before making it more complicated (!)
			 */
			SearchNode<E> bestOpt = null, bestPes = null;
			double maxOpt = Double.NEGATIVE_INFINITY, maxPes = maxOpt, val;
			for (SearchNode<E> child : children) {
				val = -child.pessimistic();
				if (val > maxOpt) { maxOpt = val; bestOpt = child; }
				val = -child.optimistic();
				if (val > maxPes) { maxPes = val; bestPes = child; }
			}
			if (bestOpt != bestPes) {
				Function<SearchNode<E>, Double> mean = x -> -x.optimistic() + (x.optimistic() - x.pessimistic())/2.;
				SearchNode<E> bestMean = children.stream().max((a,b) -> Double.compare(mean.apply(a), mean.apply(b))).get();
				goal = bestMean == bestOpt ? Goal.Raise : Goal.Lower;
				goal = Goal.BestFirst;
				return bestOpt;
			} else {
				boolean provebest = useProvebest.apply(node);
				goal = provebest ? Goal.Raise : Goal.Lower;
				goal = Goal.BestFirst;
				if (provebest) return bestOpt;
				SearchNode<E> bestOptf = bestOpt;
				SearchNode<E> secondbest = children.stream().filter(c -> c != bestOptf).max(compareOpt).orElse(null);
				return secondbest;
			}
		} else {
			boolean maxnode = node.depth() % 2 == 0;
			Comparator<Node<?>> comparator = compareOpt;
			if ((goal == Goal.Raise && maxnode) || (goal == Goal.Lower && !maxnode)) comparator = comparePes;
			return children.stream().max(comparator).orElse(null);
		}
	}

}
