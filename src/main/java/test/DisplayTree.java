package test;

import java.awt.Desktop;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;

import org.abego.treelayout.NodeExtentProvider;
import org.abego.treelayout.TreeForTreeLayout;
import org.abego.treelayout.TreeLayout;
import org.abego.treelayout.util.DefaultConfiguration;
import org.abego.treelayout.util.DefaultTreeForTreeLayout;

import gametree.BerlinerGameTree;
import gametree.GenerationSettings;
import gametree.GenerationSettings.Style;
import gametree.Node;
import gametree.SearchGameTree.SearchNode;

public class DisplayTree {
	
	public static boolean expandSearchTrees = false;
	
	public static Node<?> special_node = null;
	
	public static void main(String[] args) {
		//// parameters:
		// Tree:
		long seed = 15407;
		int branching_factor = 5;
		long range = 100;
		int num_alts = 3;
		double growth = 0.75;
		float force_chance = 0;
		boolean PS = false;
		GenerationSettings variant = new GenerationSettings(Style.BaseRulesAfter, growth, force_chance, PS);
		// Display:
		boolean drawCutNodes = false;
		long maxDepth = 5;
		
		// get berliner tree
		BerlinerGameTree source = new BerlinerGameTree(seed, branching_factor, range, num_alts, variant);
		
		// write to SVG
		String filename = "tree_result.svg";
		treeToFile(source.root(), filename, maxDepth, drawCutNodes);
		
		// display SVG in browser
		open(Desktop.getDesktop(), filename);
	}
	
	/**
	 * Exports a game tree to a viewable SVG file at {@code "tree_result.svg"} with Abego Tree Layout.
	 * @param root the root of the tree
	 * @param maxDepth the maximum depth to display
	 * @param drawCutNodes set {@code true} to include irrelevant nodes and their sub-trees, {@code false} to exclude them
	 */
	public static void treeToFile(Node<?> root, long maxDepth, boolean drawCutNodes) {
		treeToFile(root, "tree_result.svg", maxDepth, drawCutNodes);
	}
	/**
	 * Exports a game tree to a viewable SVG file with Abego Tree Layout.
	 * @param root the root of the tree
	 * @param filename the name of the file to export to
	 * @param maxDepth the maximum depth to display
	 * @param drawCutNodes set {@code true} to include irrelevant nodes and their sub-trees, {@code false} to exclude them
	 */
	public static void treeToFile(Node<?> root, String filename, long maxDepth, boolean drawCutNodes) {
		double levelGap = 50;
		double nodeGap = 10;
		TreeForTreeLayout<TextInColorBox> tree = generate(root, maxDepth, drawCutNodes);
		// get the resulting svg
		String svg = toSVG(tree, levelGap, nodeGap);
		
		// write svg to file
		try {
			FileWriter fw = new FileWriter(filename);
			fw.write(svg);
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Demo code from Abego TreeLayout
	 */
	public static String toSVG(TreeForTreeLayout<TextInColorBox> tree, double levelGap, double nodeGap) {
		// setup the tree layout configuration
		DefaultConfiguration<TextInColorBox> configuration = new DefaultConfiguration<TextInColorBox>(
				levelGap, nodeGap);

		// create the NodeExtentProvider for TextInBox nodes
		NodeExtentProvider<TextInColorBox> nodeExtentProvider = new NodeExtentProvider<>() {
			public double getWidth(TextInColorBox treeNode) {
				return treeNode.width;
			}
			public double getHeight(TextInColorBox treeNode) {
				return treeNode.height;
			}
		};

		// create the layout
		TreeLayout<TextInColorBox> treeLayout = new TreeLayout<TextInColorBox>(tree,
				nodeExtentProvider, configuration);

		// Generate the SVG and write it to System.out
		TreeSVGGenerator generator = new TreeSVGGenerator(treeLayout);
		return generator.getSVG();
	}
	
	private static boolean isRelevant(Node<?> n) {
		if (n.depth() != 0) {
			Node<?> p = n.parent();
			
			Collection<? extends Node<?>> children;
			if (!expandSearchTrees && p instanceof SearchNode<?>) children = ((SearchNode<?>)p).children(false);
			else children = p.children();
			
			double parent_pes = children.stream().mapToDouble(c -> -c.optimistic()).max().getAsDouble();
			if (-n.pessimistic() <= parent_pes) return false;
		}
		return true;
	}
	
	private static TextInColorBox nodeToBox(Node<?> n) {
		long d = n.depth();
		int sign = d % 2 == 0 ? 1 : -1;
		String txt = String.format("[%d,% d]", sign * (long) n.pessimistic(), sign * (long) n.optimistic());
		String color = d%2==0 ? "orange" : "#5080ff";
		if (!isRelevant(n)) color = "white";
		color = n.pessimistic() == n.optimistic() ? "#a7a7a7" : color;
		if (n.pessimistic() > n.optimistic()) {
			color = "red";
			System.out.println("FOUND INCORRECT NODE "+txt);
		}
		if (n == special_node) {
			color = d%2==0 ? "#ffe659" : "#78f8ff";
		}
		
		// compute the size of the box:
		Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
		FontRenderContext frc = new FontRenderContext(font.getTransform(),true,true); 
		int width = 0, height = 0;
		String[] lines = txt.split("\n");
		for (int i=0; i < lines.length; i++) {
			Rectangle2D fr = font.getStringBounds(lines[i], frc);
			width = (int) Math.max(width, fr.getWidth());
			height += (int) fr.getHeight();
		}
		return new TextInColorBox(txt, color, width + 15, height + 8);
	}
	
	private static void addChildren(DefaultTreeForTreeLayout<TextInColorBox> tree, Node<?> n, TextInColorBox nt, long remaining_depth, boolean drawCutNodes) {
		if (remaining_depth <= 0) return;
		
		Collection<? extends Node<?>> children;
		if (!expandSearchTrees && n instanceof SearchNode<?>) children = ((SearchNode<?>)n).children(false);
		else children = n.children();
		
		for (Node<?> c : children) {
			TextInColorBox ct = nodeToBox(c);
			tree.addChild(nt, ct);
			if (drawCutNodes || isRelevant(c)) addChildren(tree, c, ct, remaining_depth-1l, drawCutNodes);
		}
	}
	
	private static TreeForTreeLayout<TextInColorBox> generate(Node<?> root, long maxDepth, boolean drawCutNodes) {
		TextInColorBox roott = nodeToBox(root);
		DefaultTreeForTreeLayout<TextInColorBox> tree = new DefaultTreeForTreeLayout<TextInColorBox>(roott);
		addChildren(tree, root, roott, maxDepth, drawCutNodes);
		return tree;
	}
	
	static class TextInColorBox {
		public final String text;
		public final String color;
		public final int height;
		public final int width;

		public TextInColorBox(String text, String color, int width, int height) {
			this.text = text;
			this.color = color;
			this.width = width;
			this.height = height;
		}
	}
	
	public static void open(Desktop dt, String filename) {
		try {
			dt.open(new File(filename));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
