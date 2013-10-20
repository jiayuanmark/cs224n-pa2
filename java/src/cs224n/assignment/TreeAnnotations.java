package cs224n.assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.ling.Trees.MarkovizationAnnotationStripper;
import cs224n.util.Filter;

/**
 * Class which contains code for annotating and binarizing trees for
 * the parser's use, and debinarizing and unannotating them for
 * scoring.
 */
public class TreeAnnotations {

	public static Tree<String> annotateTree(Tree<String> unAnnotatedTree) {

		// Currently, the only annotation done is a lossless binarization

		// TODO: change the annotation from a lossless binarization to a
		// finite-order markov process (try at least 1st and 2nd order)

		// TODO : mark nodes with the label of their parent nodes, giving a second
		// order vertical markov process
		//System.out.println(Trees.PennTreeRenderer.render(unAnnotatedTree));
		//System.out.println(Trees.PennTreeRenderer.render(binarizeTree(unAnnotatedTree, 2)));
		//return binarizeTree(unAnnotatedTree, -1);
		return verticalMarkovnizeTree(binarizeTree(unAnnotatedTree, 2), "");
	}

	private static Tree<String> binarizeTree(Tree<String> tree, int order) {
		String label = tree.getLabel();
		if (tree.isLeaf())
			return new Tree<String>(label);
		if (tree.getChildren().size() == 1) {
			return new Tree<String>
			(label, 
					Collections.singletonList(binarizeTree(tree.getChildren().get(0), order)));
		}
		// otherwise, it's a binary-or-more local tree, 
		// so decompose it into a sequence of binary and unary trees.
		String intermediateLabel = "@"+label+"->";
		Tree<String> intermediateTree =
				binarizeTreeHelper(tree, 0, intermediateLabel, order);
		return new Tree<String>(label, intermediateTree.getChildren());
	}
	
	private static Tree<String> verticalMarkovnizeTree(Tree<String> tree, String parent_label) {
		String original_label = tree.getLabel();
		if (tree.isLeaf())
			return new Tree<String>(original_label);
		String label = original_label;
		if (parent_label != "")
			label += "^" + parent_label;
		ArrayList<Tree<String>> children = new ArrayList<Tree<String>>();
		for (Tree<String> node : tree.getChildren()) {
			children.add(verticalMarkovnizeTree(node, original_label));
		}
		return new Tree<String>(label, children);
	}

	private static Tree<String> binarizeTreeHelper(Tree<String> tree,
			int numChildrenGenerated, 
			String intermediateLabel,
			int order) {
		Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
		List<Tree<String>> children = new ArrayList<Tree<String>>();
		children.add(binarizeTree(leftTree, order));
		if (numChildrenGenerated < tree.getChildren().size() - 1) {
			String label = intermediateLabel + "_" + leftTree.getLabel();
			Tree<String> rightTree = 
					binarizeTreeHelper(tree, numChildrenGenerated + 1, 
							label,
							order);
			children.add(rightTree);
		}
		if (order > 0) {
			int index = intermediateLabel.indexOf("_");
			int order_index = -1;
			int count = 0;
			while (index >= 0) {
			    count ++;
			    index = intermediateLabel.indexOf("_", index + 1);
			    if (count == order - 1 && index >= 0) {
			    	order_index = index;
			    }
			    if (count == order)
			    	break;
			}
			if (order_index > -1 && count >= order && index >= 0) {
				intermediateLabel = intermediateLabel.substring(0, order_index) +
						intermediateLabel.substring(index);
			}
		}
		return new Tree<String>(intermediateLabel, children);
	} 

	public static Tree<String> unAnnotateTree(Tree<String> annotatedTree) {

		// Remove intermediate nodes (labels beginning with "@"
		// Remove all material on node labels which follow their base symbol
		// (cuts at the leftmost - or ^ character)
		// Examples: a node with label @NP->DT_JJ will be spliced out, 
		// and a node with label NP^S will be reduced to NP

		Tree<String> debinarizedTree =
				Trees.spliceNodes(annotatedTree, new Filter<String>() {
					public boolean accept(String s) {
						return s.startsWith("@");
					}
				});
		Tree<String> unAnnotatedTree = 
				(new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
		Tree<String> unMarkovizedTree =
				(new Trees.MarkovizationAnnotationStripper()).transformTree(unAnnotatedTree);
		return unMarkovizedTree;
	}
}
