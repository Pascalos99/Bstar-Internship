package test;

import java.awt.Desktop;
import java.util.function.Function;

import algorithm.BstarBasic;
import algorithm.GameTreeSearch;
import algorithm.SearchAlgorithm;
import algorithm.SearchAlgorithm.Metric;
import gametree.BerlinerGameTree;
import gametree.BerlinerGameTree.BerlinerNode;
import gametree.GenerationSettings;
import gametree.GenerationSettings.Style;
import gametree.Node;
import gametree.ResultNode;
import gametree.SearchGameTree;
import gametree.SearchGameTree.SearchNode;

public class TestAlgorithm {
	
	public static void main(String[] args) {
		// debug
		boolean export_to_svg = true;
		boolean show_step_by_step = false;
		
		// search parameters
		Function<SearchNode<?>, Boolean> strategySelect = BstarBasic.ALTERNATE();
		SearchAlgorithm algo = new BstarBasic(strategySelect, true);
		long timeout_ms = 2000;
		
		// build berliner tree
		long seed = 29542;
		int branching_factor = 9;
		long range = 6400;
		int num_alts = 3;
		double growth = 1.4;
		float force_chance = 0;
		boolean PS = true;
		Style style = Style.BaseRulesBefore;
		
		GenerationSettings variant = new GenerationSettings(style, growth, force_chance, PS);
		BerlinerGameTree source = new BerlinerGameTree(seed, branching_factor, range, num_alts, variant);
		
		// run search algorithm
		long t1 = System.nanoTime();
		Metric result = algo.search(source, timeout_ms);
		long t2 = System.nanoTime();
		System.out.format("%.2f ms used\n", (t2 - t1) / 1000000.);
		System.out.format("evaluated %d nodes\nexpanded %d nodes\n",
				result.numEvaluated(), result.numExpanded());
		System.out.println(result);
		
		if (!export_to_svg) return;
		
		// display results
		ResultNode<?> search_tree_root = result.root();
		long deepness = result.maxDepth();
		DisplayTree.treeToFile(search_tree_root, "data/display/search_tree.svg", deepness, true);
		DisplayTree.treeToFile(search_tree_root, "data/display/search_pruned.svg", deepness, false);
		
		// re-run search algorithm step-by-step
		SearchGameTree<BerlinerNode> stree = new SearchGameTree<BerlinerNode>(source.root());
		
		if (show_step_by_step && algo instanceof GameTreeSearch)
			displayAlgoStepByStep(stree.root(), (GameTreeSearch)algo, "data/display/StepByStep");
		else {
			DisplayTree.open(Desktop.getDesktop(), "data/display/search_tree.svg");
			DisplayTree.open(Desktop.getDesktop(), "data/display/search_pruned.svg");
		}
	}
	
	public static <E extends Node<E>> void displayAlgoStepByStep
			(SearchNode<E> root, GameTreeSearch algo, String name) {
		int iter = 0;
		
		int MAX_ITER = 120;
		
		Desktop dt = Desktop.getDesktop();
		String filename;
		
		SearchNode<E> old = root;
		for (SearchNode<E> current = root; current != null; current = algo.stepB(root, current), iter++) {
			if (iter > MAX_ITER) break;
			DisplayTree.special_node = current;
			filename = String.format("%s_%02d.svg",name,iter);
			DisplayTree.treeToFile(root, filename, root.deepness(), true);
			DisplayTree.open(dt, filename);
			old = current;
			DisplayTree.special_node = current = algo.stepA(root, current);
			if (current == null) break;
			if (old != current) {
				filename = String.format("%s_%02d.svg",name,++iter);
				DisplayTree.treeToFile(root, filename, root.deepness(), true);
				DisplayTree.open(dt, filename);
			}
		}
		DisplayTree.special_node = root;
		DisplayTree.treeToFile(root, String.format("%s_%02d.svg",name,iter), root.deepness(), true);
	}
	
}
