package algorithm;

import static gametree.Node.compareOpt;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import gametree.GameTree;
import gametree.GameTree.Result;
import gametree.Node;
import gametree.SearchGameTree.SearchNode;

public class BstarBasic implements GameTreeSearch {
	
	private static Random rand = new Random();
	
	// Strategy Selection pre-sets:
	/** will alternate between PROVEBEST and DISPROVEREST, starting at PROVEBEST;
	 * 	unless DISPROVEREST is impossible, which will suspend the alternation count */	
	public static Function<SearchNode<?>, Boolean> ALTERNATE() {
		return new Function<>() {
			int iter = 0;
			public Boolean apply(SearchNode<?> t) {
				return iter++%2==0;
			}
		};
	}
	
	/** will ALWAYS use PROVEBEST */
	public static Function<SearchNode<?>, Boolean> PROVEBEST = n -> true;
	/** will use DISPROVEREST where possible */
	public static Function<SearchNode<?>, Boolean> DISPROVEREST = n -> false;
	/** will select randomly between PROVEBEST and DISPROVEREST when both are applicable */
	public static Function<SearchNode<?>, Boolean> RANDOM = n -> rand.nextBoolean();
	
	/** Berliner's {@link #CRITERIUM_D(int)}, considering 1 alternative*/
	public static Function<SearchNode<?>, Boolean> B_2D = CRITERIUM_D(2);
	/** Berliner's {@link #CRITERIUM_D(int)}, considering 2 alternatives*/
	public static Function<SearchNode<?>, Boolean> B_3D = CRITERIUM_D(3);
	/** Berliner's {@link #CRITERIUM_D(int)}, considering all alternatives*/
	public static Function<SearchNode<?>, Boolean> B_AD = CRITERIUM_D(0);
	/** Berliner's {@link #CRITERIUM_R(int)}, considering 1 alternative*/
	public static Function<SearchNode<?>, Boolean> B_2R = CRITERIUM_R(2);
	/** Berliner's {@link #CRITERIUM_R(int)}, considering 2 alternatives*/
	public static Function<SearchNode<?>, Boolean> B_3R = CRITERIUM_R(3);
	/** Berliner's {@link #CRITERIUM_R(int)}, considering all alternatives */
	public static Function<SearchNode<?>, Boolean> B_AR = CRITERIUM_R(0);
	
	// heuristic Berliner criteria
	/**
	 * If the sum of the squares of the depths from which the optimistic
	 * bounds of the alternatives has been backed up is <b>less than</b> the square
	 * of the depth from which the value of the best arc has been backed up,
	 * then use DISPROVEREST; otherwise use PROVEBEST.
	 *
	 * @param num_alternatives number of alternatives (including best arc) to consider.
	 * 	Or any value {@code <1} to consider ALL. 
	 * @return a function which follows the described behavior for the
	 *   selected number of alternatives considered to determine the strategy.
	 *   This function will return {@code true} when PROVEBEST should be used, and {@code false} otherwise.
	 */
	public static Function<SearchNode<?>, Boolean> CRITERIUM_D(int num_alternatives) {
		return n -> {
			if (num_alternatives == 1 || n.children() == null || n.children().size() <= 1) return true;
			ToLongFunction<SearchNode<?>> D = s -> { long d = s.depthOfPes(); return d * d; };
			List<SearchNode<?>> children_sorted = new ArrayList<>(n.children());
			children_sorted.sort(compareOpt.reversed());
			long sum_of_squares;
			if (num_alternatives < 1) sum_of_squares = children_sorted.stream().skip(1).mapToLong(D).sum();
			else sum_of_squares = children_sorted.stream().skip(1).limit(num_alternatives - 1).mapToLong(D).sum();
			if (sum_of_squares < D.applyAsLong(children_sorted.get(0)))
				return false; // choose the second-best arc
			return true; // choose the best arc
		};
	}
	
	/**
	 * If the sum of the squares of the depths from which the optimistic
	 * bounds of the alternatives has been backed up, <i>divided by the range of the alternatives</i>,
	 * is <b>less than</b> the square of the depth from which the value of the best arc has been backed up,
	 * <i>divided by the range of the best arc</i>, then use DISPROVEREST; otherwise use PROVEBEST.
	 *
	 * @param num_alternatives number of alternatives (including best arc) to consider.
	 * 	Or any value {@code <1} to consider ALL. 
	 * @return a function which follows the described behavior for the
	 *   selected number of alternatives considered to determine the strategy.
	 */
	public static Function<SearchNode<?>, Boolean> CRITERIUM_R(int num_alternatives) {
		return n -> {
			if (num_alternatives == 1 || n.children() == null || n.children().size() <= 1) return true;
			ToDoubleFunction<SearchNode<?>> R = s -> Math.pow(s.depthOfPes(), 2) / (s.optimistic() - s.pessimistic());
			List<SearchNode<?>> children_sorted = new ArrayList<>(n.children());
			children_sorted.sort(compareOpt.reversed());
			double sum_of_squares;
			if (num_alternatives < 1) sum_of_squares = children_sorted.stream().skip(1).mapToDouble(R).sum();
			else sum_of_squares = children_sorted.stream().skip(1).limit(num_alternatives - 1).mapToDouble(R).sum();
			if (sum_of_squares < R.applyAsDouble(children_sorted.get(0)))
				return false; // choose the second-best arc
			return true; // choose the best arc
		};
	}
	
	public BstarBasic(Function<SearchNode<?>, Boolean> useProvebest, boolean usePruning) {
		this.useProvebest = useProvebest;
		this.usePruning = usePruning;
	}
	
	private final Function<SearchNode<?>, Boolean> useProvebest;
	private final boolean usePruning;
	
	private boolean provebest;
	
	@Override
	public <E extends Node<E>> SearchNode<E> updateBounds(SearchNode<E> node) {
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
		if (bestOpt != bestPes) { provebest = true; return; }
		provebest = useProvebest.apply(node);
	}
	
	@Override
	public <E extends Node<E>> SearchNode<E> selectNext(SearchNode<E> node) {
		ArrayList<SearchNode<E>> children = node.children();
		if (children.size() <= 0) return null;
		if (children.size() == 1) return children.get(0);
		
		Result<? extends SearchNode<E>> res = GameTree.findBest2(children);
		SearchNode<E> best = res.best(), secondbest = res.secondbest();
		if (node.depth > 0 || provebest) return best;
		return secondbest;
	}

}
