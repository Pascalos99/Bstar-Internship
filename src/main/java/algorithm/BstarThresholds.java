package algorithm;

import java.util.ArrayList;
import java.util.Collection;

import gametree.DepthFirstNode;
import gametree.Node;

@Deprecated
public class BstarThresholds implements SearchAlgorithm {
	
	private long starting_time, timeout_limit;
	
	private void startTimer(long timeout_ms) {
		timeout_limit = timeout_ms;
		starting_time = System.currentTimeMillis();
	}
	public boolean timeout() {
		return timeout_limit >= 0 && System.currentTimeMillis() - starting_time >= timeout_limit;
	}
	
	public DepthFirstNode<?> searchDF(DepthFirstNode<?> root, long timeout_ms) {
		
		startTimer(timeout_ms);
		
		DepthFirstNode<?> current = root;
		
		while (!timeout()) {
			current = nextAtRoot(root);
			// TODO figure out what to do with these thresholds
			double idk1 = Double.NEGATIVE_INFINITY;
			double idk2 = Double.POSITIVE_INFINITY;
			searchDF(current, idk1, idk2);
			// TODO what to do with this now?
		}
		return root;
	}
	public void searchDF(DepthFirstNode<?> current, double T1, double T2) {
		// TODO adjust bounds
		// TODO select next
		// TODO backprop
		// TODO somehow save things in transposition table
		// TODO do I have to return anything?
	}
	public <E extends Node<E>> DepthFirstNode<E> nextAtRoot(DepthFirstNode<E> root) {
		if (root.depth != 0) throw new RuntimeException("unexpected node with depth "+root.depth+", expected 0");
		ArrayList<DepthFirstNode<E>> children = root.children();
		root.adjust_bounds(children);
		// TODO select strategy
		// TODO select next
		return root;
	}

	@Override
	public Metric search(Node<?> root, long timeout_ms) {
		return searchDF(new DepthFirstNode<>(root), timeout_ms).metrics();
	}
	
	void explore(Node<?> current) {
		Collection<? extends Node<?>> children = current.children();
		
		
	}
	
	// [!] make sure we save states ONLY in the stack and TT, and don't build up a game tree in memory!
	
	// [Transposition Table]
	// --> variable constant size (should NOT dynamically adjust size!)
	// --> allow hash creation to be abstract and flexible
	// --> hashes must be random but also consistent (same node, same hash)
	
	// [Thresholds]
	// --> if possible, also make a non-df version of thresholds algorithm
	
	// [Experiments]
	// --> do not measure memory usage (it is unnecessary)
	// --> experiment with different TT sizes
	// --> if possible, experiment with different tree structures (allow for transpositions)

}
