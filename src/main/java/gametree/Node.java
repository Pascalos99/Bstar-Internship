package gametree;

import java.util.Collection;
import java.util.Comparator;

public interface Node<N extends Node<N>> {
	
	Comparator<Node<?>> compareOpt = (a, b) -> Double.compare(-a.pessimistic(), -b.pessimistic());
	Comparator<Node<?>> comparePes = (a, b) -> Double.compare(-a.optimistic(), -b.optimistic());
	
	N parent();
	
	Collection<N> children();
	
	double optimistic();
	
	double pessimistic();
	
	/** @return {@code 0} at root, the number of parents {@code d} until reaching a root otherwise */
	long depth();
	
	default N bestChildOpt() {
		return children().stream().max(compareOpt).orElse(null);
	}
	default N bestChildPes() {
		return children().stream().max(comparePes).orElse(null);
	}
	
}