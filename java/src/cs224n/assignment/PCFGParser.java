package cs224n.assignment;

import cs224n.ling.Tree;
import cs224n.assignment.Grammar.UnaryRule;
import cs224n.assignment.Grammar.BinaryRule;
import cs224n.util.Triplet;

import java.util.*;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {
    private Grammar grammar;
    private Lexicon lexicon;
    
    
    // Data structure 
    private int [][] scoreIdx;
    private List<HashMap<String, Double>> scoreTable;
    private List<HashMap<String, Triplet<Integer, String, String>>> backTable;
    
    
    // Training
    public void train(List<Tree<String>> trainTrees) {
    	List<Tree<String>> binarizedTrees = new ArrayList<Tree<String>>();
    	for (Tree<String> tree : trainTrees) {
    		binarizedTrees.add(TreeAnnotations.annotateTree(tree));
    	}
        lexicon = new Lexicon(binarizedTrees);
        grammar = new Grammar(binarizedTrees);
    }
    
    
    // Build tree
    private Tree<String> backtrackBuildTree(int begin, int end, String tag) {
    	if (!(begin < end && scoreIdx[begin][end] != -1)) {
    		System.err.println("Build tree exception!");
    	}
    	
    	HashMap<String, Triplet<Integer, String, String>> back = backTable.get(scoreIdx[begin][end]);
    	Triplet<Integer, String, String> triple = back.get(tag);
    	
    	// Leaf case
    	if (triple == null) {
    		return new Tree<String>(tag);
    	}
    	
    	// Unary case
    	if (triple.getFirst() == -1) {
    		Tree<String> subtree = null;
    		if (tag.equals(triple.getSecond()))
    			subtree = new Tree<String>(triple.getSecond());
    		else subtree = backtrackBuildTree(begin, end, triple.getSecond());
    		Tree<String> ret = new Tree<String>(tag, Collections.singletonList(subtree));
    		return ret;
    	} else {
    	// Binary case
    		Tree<String> leftTree = backtrackBuildTree(begin, triple.getFirst(), triple.getSecond());
    		Tree<String> rightTree = backtrackBuildTree(triple.getFirst(), end, triple.getThird());
    		List<Tree<String>> child = new ArrayList<Tree<String>>();
    		child.add(leftTree);
    		child.add(rightTree);
     		Tree<String> ret = new Tree<String>(tag, child);
     		return ret;
    	}
    }
    

    public Tree<String> getBestParse(List<String> sentence) {
    	
    	// Initialization
    	scoreIdx = new int[sentence.size()+1][sentence.size()+1];
    	
    	for (int i = 0; i < scoreIdx.length; ++i)
    		for (int j = 0; j < scoreIdx[0].length; ++j)
    			scoreIdx[i][j] = -1;
    	
    	scoreTable = new ArrayList<HashMap<String, Double>>();
    	backTable = new ArrayList<HashMap<String, Triplet<Integer, String, String>>>();

    	// Dynamic programming base case
    	for (int i = 0; i < sentence.size(); ++i) {
    		HashMap<String, Double> score = new HashMap<String, Double>();
    		HashMap<String, Triplet<Integer, String, String>> back
    			= new HashMap<String, Triplet<Integer, String, String>>();
    		
    		for (String tag : lexicon.getAllTags()) {
    			score.put(tag, lexicon.scoreTagging(sentence.get(i), tag));
    			back.put(tag, new Triplet<Integer, String, String>(-1, sentence.get(i), ""));
    		}
    		
    		// Handling unary rules
    		boolean added = true;
    		while (added) {
    			added = false;
    			Set<String> S = new HashSet<String>(score.keySet());
    			for (String key : S) {
    				for  (UnaryRule r : grammar.getUnaryRulesByChild(key)) {
    					double prob = score.get(key) * r.getScore();
    					if (!score.containsKey(r.getParent()) || score.get(r.getParent()) < prob) {
    						score.put(r.getParent(), prob);
    						back.put(	r.getParent(), 
    									new Triplet<Integer, String, String>(-1, r.getChild(), ""));
    						added = true;
    					}
    				}
    			}
    		}
    		scoreIdx[i][i+1] = scoreTable.size();
    		scoreTable.add(score);
    		backTable.add(back);
    	}
    	
    	
    	// Dynamic programming
    	for (int span = 2; span <= sentence.size(); ++span) {
    		for (int begin = 0; begin <= sentence.size() - span; ++begin) {
    			
    			int end = begin + span;
    			HashMap<String, Double> score = new HashMap<String, Double>();
    			HashMap<String, Triplet<Integer, String, String>> back
    				= new HashMap<String, Triplet<Integer, String, String>>();
    			
    			// Binary rules
    			for (int split = begin+1; split <= end-1; ++split) {
    				
    				if (!(scoreIdx[begin][split] != -1 && scoreIdx[split][end] != -1)) {
    					System.err.println("Dynamic programming exception");
    				}
    				
    				HashMap<String, Double> left = scoreTable.get(scoreIdx[begin][split]);
    				HashMap<String, Double> right = scoreTable.get(scoreIdx[split][end]);
    				
    				for (String leftKey : left.keySet()) {
    					for (BinaryRule br : grammar.getBinaryRulesByLeftChild(leftKey)) {
    						if (!right.containsKey(br.getRightChild())) continue;
    						double prob = left.get(br.getLeftChild()) * right.get(br.getRightChild()) 
    										* br.getScore();
    						if (!score.containsKey(br.getParent()) || score.get(br.getParent()) < prob) {
    							score.put(br.getParent(), prob);
    							back.put(br.getParent(), 
    									new Triplet<Integer, String, String>
    									(split, br.getLeftChild(), br.getRightChild()));
    						}
    					}
    				}
    			}
    			
    			// Unary rules
    			boolean added = true;
        		while (added) {
        			added = false;
        			Set<String> S = new HashSet<String>(score.keySet());
        			for (String key : S) {
        				for  (UnaryRule r : grammar.getUnaryRulesByChild(key)) {
        					double prob = score.get(key) * r.getScore();
        					if (!score.containsKey(r.getParent()) || score.get(r.getParent()) < prob) {
        						score.put(r.getParent(), prob);
        						back.put(r.getParent(), 
    									new Triplet<Integer, String, String>(-1, r.getChild(), ""));
        						added = true;
        					}
        				}
        			}
        		}
        		
        		// Add score into table
        		scoreIdx[begin][end] = scoreTable.size();
        		scoreTable.add(score);
        		backTable.add(back);
    		}
    	}
    	
    	
    	// Backtrack to build tree    	
    	Tree<String> bestTree = new Tree<String>("ROOT", 
    			Collections.singletonList(backtrackBuildTree(0, sentence.size(), "S")));
        return TreeAnnotations.unAnnotateTree(bestTree);
    }
}
