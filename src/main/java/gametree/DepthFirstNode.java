package gametree;

import java.util.ArrayList;

@Deprecated
public class DepthFirstNode<E extends Node<E>> extends EditNode<DepthFirstNode<E>, E> {

	public DepthFirstNode(Node<E> node, MetricKeeper metrics, long depth) {
		super(node, metrics == null? new MetricKeeper(true) : metrics, depth);
	}
	public DepthFirstNode(Node<E> node) {
		this(node, null, 0);
	}

	@Override
	public long depthOfOpt() { return depthOfOpt; }
	@Override
	public long depthOfPes() { return depthOfPes; }

	@Override
	public DepthFirstNode<E> parent() {
		throw new RuntimeException("Depth-first node has requested parent node - this is an unsupported operation!");
	}

	@Override
	protected DepthFirstNode<E> getChild(Node<E> child_source) {
		return new DepthFirstNode<>(child_source, metrics, depth+1l);
	}
	
	/** A special array reserved for storing the children of the root node. Any other nodes are not saved in memory*/
	private ArrayList<DepthFirstNode<E>> root_children = null;
	
	/**
	 * @return the saved children nodes at root, if this is the root node, or newly generated children nodes otherwise
	 */
	@Override
	public ArrayList<DepthFirstNode<E>> children() {
		if (depth != 0) return super.children();
		if (root_children == null) root_children = super.children();
		return root_children;
	}

}
