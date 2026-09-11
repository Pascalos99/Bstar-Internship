package gametree;

import java.util.ArrayList;
import java.util.Collection;

import algorithm.SearchAlgorithm.Metric;

public abstract class EditNode<K extends EditNode<K,E>, E extends Node<E>> implements ResultNode<K> {
	
	private static final double INF = Double.POSITIVE_INFINITY;
	
	public final long depth;
	protected double optimistic, pessimistic;
	
	protected long depthOfPes, depthOfOpt;
	
	protected boolean evaluated;
	protected MetricKeeper metrics;
	
	protected Node<E> source;
	
	public EditNode(Node<E> node, MetricKeeper metrics, long depth) {
		source = node;
		this.depth = depth >= 0 ? depth : 0;
		evaluated = false;
		optimistic  = +INF;
		pessimistic = -INF;
		depthOfPes = depthOfOpt = 0;
		this.metrics = metrics;
	}
	
	public Node<E> source() {
		return source;
	}
	
	@Override
	public long depth() {
		return depth;
	}
	
	public void evaluate() {
		if (!evaluated) {
			optimistic = source.optimistic();
			pessimistic = source.pessimistic();
			if (metrics != null) metrics.numEvaluated++;
			evaluated = true;
		}
	}
	
	@Override
	public double optimistic() {
		evaluate();
		return optimistic;
	}
	
	@Override
	public double pessimistic() {
		evaluate();
		return pessimistic;
	}
	
	public MetricKeeper metricKeeper() {
		return metrics;
	}
	
	protected abstract K getChild(Node<E> child_source);
	
	@Override
	public ArrayList<K> children() {
		Collection<? extends Node<E>> src_children = source.children();
		ArrayList<K> children = new ArrayList<>(src_children.size());
		for (Node<E> child : src_children) children.add(getChild(child));
		if (metrics != null) {
			metrics.numExpanded++;
			metrics.maxDepth = Math.max(metrics.maxDepth, depth+1);
		}
		return children;
	}
	
	/**
	 * Updates the bounds of this node, given the children of this node.
	 * @param children children of this node.
	 * @return
	 */
	public boolean adjust_bounds(Collection<K> children) {
		if (children == null || children.size()<=0) return false;
		double oldpes = pessimistic(), oldopt = optimistic();
		optimistic = pessimistic = Double.NEGATIVE_INFINITY;
		for (K c : children) {
			pessimistic = Math.max(pessimistic, -c.optimistic());
			optimistic = Math.max(optimistic, -c.pessimistic());
		}
		if (metrics != null && metrics.keepTrackOfDepthSource())
			for (K c : children) {
				if (-c.optimistic == pessimistic) depthOfPes = Math.max(depthOfPes, c.depthOfOpt()+1);
				if (-c.pessimistic == optimistic) depthOfOpt = Math.max(depthOfOpt, c.depthOfPes()+1);
			}
		return pessimistic != oldpes || optimistic != oldopt;
	}
	
	public Metric metrics() {
		return new Metric(metrics.deepness(), metrics.evaluationCount(), metrics.expansionCount(), this);
	}
	
	public static class MetricKeeper {
		protected long maxDepth = 0, numEvaluated = 0, numExpanded = 0;
		/** Toggles whether the nodes of this tree calculate depthOfOpt and depthOfPes while adjusting bounds.
		 *  This saves on computation when these values are needed for heuristics, but costs extra computation
		 *  when they are not - hence this boolean variable.*/
		protected boolean keepTrackOfDepthSource;
		public MetricKeeper(boolean keepTrackOfDepthSource) {
			this.keepTrackOfDepthSource = keepTrackOfDepthSource;
		}
		
		public long deepness() { return maxDepth; }
		public long evaluationCount() { return numEvaluated; }
		public long expansionCount() { return numExpanded; }
		public boolean keepTrackOfDepthSource() {return keepTrackOfDepthSource; }
	}

}
