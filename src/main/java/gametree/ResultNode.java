package gametree;

public interface ResultNode<E extends ResultNode<E>> extends Node<E> {
	
	/**
	 * @return depth of search from which the optimistic value of this node has been backed up.
	 */
	long depthOfOpt();
	
	/**
	 * @return depth of search from which the pessimistic value of this node has been backed up.
	 */
	long depthOfPes();
	
}
