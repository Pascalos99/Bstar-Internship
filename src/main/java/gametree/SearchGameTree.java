package gametree;

import java.util.ArrayList;
import java.util.Optional;
import java.util.OptionalDouble;

import gametree.BerlinerGameTree.BerlinerNode;
import gametree.EditNode.MetricKeeper;

/**
 * @param <E> the type of GameTree this SearchGameTree gets its info from. This GameTree need not store any data,
 *            as that is the purpose of this SearchGameTree. The evaluation function should always return a value
 *            that the current player to move wants to MAXIMIZE (so multiply evaluations by -1 for MIN player moves).
 *            As a result, this SearchGameTree also assumes moves always alternate players --- from MIN to MAX to MIN ...
 */
public class SearchGameTree<E extends Node<E>> extends MetricKeeper implements GameTree<SearchGameTree.SearchNode<E>> {

	private SearchNode<E> root;
	
	private SearchGameTree(SearchNode<E> node) {
		super(false);
		root = node;
	}
	public SearchGameTree(Node<E> root) {
		super(false);
		this.root = new SearchNode<E>(root, null);
		// force the parent to be null, so we cannot backtrack further than needed
		// --> the SearchGameTree forces the root at the given root node
		this.root.parent_known = true;
		this.root.metrics = this;
	}
	public SearchGameTree(GameTree<E> source) {
		this(source.root());
	}

	@Override
	public SearchNode<E> root() {
		return root;
	}
	
	public static class SearchNode<E extends Node<E>> extends EditNode<SearchNode<E>, E> {

		protected boolean expanded = false;
		
		private SearchNode<E> parent;
		private boolean parent_known;
		private ArrayList<SearchNode<E>> children;
		
		private SearchNode(Node<E> node, MetricKeeper metrics, long depth) {
			super(node, metrics, depth);
		}
		private SearchNode(Node<E> node, SearchNode<E> _parent) {
			this(node, null, _parent == null ? 0 : _parent.depth() + 1l);
			parent = _parent;
			parent_known = _parent != null;
			metrics = parent_known? parent.metrics : new SearchGameTree<>(this);
		}
		
		@Override
		protected SearchNode<E> getChild(Node<E> source) {
			return new SearchNode<E>(source, this);
		}
		
		private static boolean problem_notice = false;
		
		@Override
		public SearchNode<E> parent() {
			if (!parent_known) {
				parent = new SearchNode<E>(source.parent(), metrics, depth-1l);
				parent.children(true);
				parent.children.add(this);
				parent_known = true;
				if (!problem_notice) {
					System.err.println("A parent was created while none was previously present, this is very suboptimal and not intended behavior");
					problem_notice = true;
				}
			}
			return parent;
		}
		
		public void setTree(SearchGameTree<E> tree) {
			this.metrics = tree;
			if (children == null || children.size() <= 0) return;
			for (SearchNode<E> child : children) child.setTree(tree);
		}
		
		public SearchNode<E> root() {
			return depth <= 0 ? this : parent().root();
		}
		
		public long deepness() {
			if (metrics != null && depth == 0) return metrics.deepness();
			if (children == null || children.size() <= 0) return 0l;
			return children.stream().mapToLong(c -> c.deepness()).max().getAsLong() + 1l;
		}
		@Override
		public long depthOfOpt() {
			if (metrics == null || !metrics.keepTrackOfDepthSource()) {
				if (metrics != null) metrics.keepTrackOfDepthSource = true;
				if (children == null || children.size() <= 0) return 0l;
				depthOfOpt = children.stream().filter(c -> -c.pessimistic() == optimistic).mapToLong(c -> c.depthOfPes()).max().getAsLong() + 1l;
			}
			return depthOfOpt;
		}
		@Override
		public long depthOfPes() {
			if (metrics == null || !metrics.keepTrackOfDepthSource()) {
				if (metrics != null) metrics.keepTrackOfDepthSource = true;
				if (children == null || children.size() <= 0) return 0l;
				depthOfPes = children.stream().filter(c -> -c.optimistic() == pessimistic).mapToLong(c -> c.depthOfOpt()).max().getAsLong() + 1l;
			}
			return depthOfPes;
		}
		
		public long evaluationCount() {
			if (metrics != null && depth == 0) return metrics.evaluationCount();
			if (children == null || children.size() <= 0) return evaluated ? 1l : 0l;
			return children.stream().mapToLong(c -> c.evaluationCount()).sum() + (evaluated ? 1l : 0l);
		}
		
		public long expansionCount() {
			if (metrics != null && depth == 0) return metrics.expansionCount();
			if (children == null) return 0l;
			return children.stream().mapToLong(c -> c.expansionCount()).sum() + 1l;
		}

		@Override
		public ArrayList<SearchNode<E>> children() {
			return children(true);
		}
		
		public ArrayList<SearchNode<E>> children(boolean expand) {
			if (expand && children == null) {
				if (expanded) System.err.println("nola");
				children = super.children();
				expanded = true;
				// if we have expanded & evaluated this node, we no longer need the source-node:
				if (evaluated) source = null;
			} else if (children == null)
				return new ArrayList<>(0);
			return children;
		}
		
		@Override
		public void evaluate() {
			super.evaluate();
			// if we have expanded & evaluated this node, we no longer need the source-node:
			if (expanded) source = null;
		}
		
		public boolean adjust_bounds(boolean expand) {
			if (expand) children(true);
			return adjust_bounds(children);
		}
		
		/**
		 * Shallow removes all nodes at depth 1 or deeper that are irrelevant for the search.
		 * @param adjust_bounds
		 * @return {@code true} if any direct children of this node were removed
		 */
		public boolean prune(boolean adjust_bounds) {
			if (adjust_bounds) adjust_bounds(true);
			if (children == null || children.size()==0) return false;
			return children.removeIf(n -> {
				if (-n.pessimistic <= pessimistic && n.optimistic != n.pessimistic) {
					// a node is irrelevant if (a) it falls OUTSIDE the adjusted parent bounds
					//  AND (b) does NOT define any of the parent's bounds
					if (depth == 0) {
						// for root children, do not remove them entirely, just remove their descendants
						// -- this makes sure we can still remember which node the solution corresponds to
						n.children = null;
						return false;
					}
					return true;
				}
				return false;
			});
		}
		
		@Override
		public String toString() {
			int sign = depth%2==0? 1 : -1;
			return String.format("%d: [%.2f,% .2f]", depth, sign * pessimistic, sign * optimistic);
		}
	}
	
	/**
	 * @param <K> the type of tree being explored
	 * @param source_tree
	 * @param max_time_ms
	 * @return the {@code depth} of the tree if it was terminated, {@code -depth} otherwise
	 */
	public static <K extends Node<K>> long probeDepth(GameTree<K> source_tree, long max_time_ms) {
		SearchGameTree<K> tree = new SearchGameTree<>(source_tree);
		SearchNode<K> current = tree.root();
		
		Node<K> best = null;
		long depth = 0;
		boolean terminated = false;
		long T0 = System.currentTimeMillis();
		while (System.currentTimeMillis() - T0 < max_time_ms) {
			Optional<SearchNode<K>> has_next = current.children().stream().max((a, b)
					-> Double.compare(a.optimistic() - a.pessimistic(), b.optimistic() - b.pessimistic()));
			if (has_next.isEmpty()) {
				long new_depth = ((BerlinerNode)current.source()).depth();
				if (new_depth > depth) {
					depth = new_depth;
					best = current.source();
				}
				
				if (current.parent() == null) {
					terminated = true;
					break;
				}
				SearchNode<K> parent = current.parent();
				parent.children().remove(current);
				OptionalDouble max = parent.children().stream().mapToDouble(c -> c.optimistic() - c.pessimistic()).max();
				if ((max.isEmpty() || max.getAsDouble() <= 1) && parent.parent() != null) {
					parent.parent().children().remove(parent);
				}
				current = tree.root();
			} else
				current = has_next.get();
		}
		if (best == null) {
			best = current.source();
			depth = ((BerlinerNode)current.source()).depth();
		}
		if (!terminated) return -depth;
		return depth;
	}
	
}
