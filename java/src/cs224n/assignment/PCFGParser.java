package cs224n.assignment;

import cs224n.assignment.Grammar.*;
import cs224n.ling.Tree;
import cs224n.util.PriorityQueue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {
    private Grammar grammar;
    private Lexicon lexicon;
    ArrayList<ArrayList<PriorityQueue<Rule>>> all_scores;
    
    public void train(List<Tree<String>> trainTrees) {
        // TODO: before you generate your grammar, the training trees
        // need to be binarized so that rules are at most binary
      
      List<Tree<String>> annotated_trainTrees = new ArrayList<Tree<String>>();
      for (Tree<String> tree : trainTrees) {
        annotated_trainTrees.add(TreeAnnotations.annotateTree(tree));
      }
      
        lexicon = new Lexicon(annotated_trainTrees);
        grammar = new Grammar(annotated_trainTrees);
    }

    public Tree<String> getBestParse(List<String> sentence) {
       all_scores = 
          new ArrayList<ArrayList<PriorityQueue<Rule>>>();
      
      //filled the pre-terminal score by using lexicon
      ArrayList<PriorityQueue<Rule>> pre_terminal_scores =
            new ArrayList<PriorityQueue<Rule>>(sentence.size());
      for (String word : sentence) {
          PriorityQueue<Rule> scores = new PriorityQueue<Rule>();
          for (String tag : lexicon.getAllTags()) {
            double priority = lexicon.scoreTagging(word, tag);
            if (priority > 0) {
              UnaryRule rule = new UnaryRule(tag, word);
              rule.setTerminal(true);
              scores.add(rule, priority);
            }
          }
          pre_terminal_scores.add(scores);
        }
        ArrayList<PriorityQueue<Rule>> last_rule_scores = pre_terminal_scores;
        for (int l = sentence.size(); l > 0; l--) {
            // handle unary rules for those who being added to this level 
          for (PriorityQueue<Rule> scores : last_rule_scores) {
            
            List<Rule> elements = scores.getElements();
            double[] priorities = scores.getPriorities();
            while (elements.size() > 0) {
              ArrayList<Rule> new_elements = new ArrayList<Rule>();
              double[] new_priorities = new double[elements.size()];
              for (int i = 0; i < elements.size(); i ++) {
                for (UnaryRule rule : grammar.getUnaryRulesByChild(elements.get(i).getParent())) {
                  rule.setTerminal(false);
                  scores.add(rule, priorities[i] * rule.score);
                  new_elements.add(rule);
                  new_priorities[new_elements.size() - 1] = (priorities[i] * rule.score);
                }
              }
              elements = new_elements;
              priorities = new_priorities;
            }
          }
          
          ArrayList<PriorityQueue<Rule>> rule_scores =
              new ArrayList<PriorityQueue<Rule>>();
          // add binary rule to next level
          for (int i = 0; i < last_rule_scores.size() - 1; i++) {
            PriorityQueue<Rule> left_scores = last_rule_scores.get(i);
            PriorityQueue<Rule> right_scores = last_rule_scores.get(i + 1);
              
            List<Rule> left_elements = left_scores.getElements();
            List<Rule> right_elements = right_scores.getElements();
            double[] left_priorities = left_scores.getPriorities();
            double[] right_priorities = right_scores.getPriorities();
            PriorityQueue<Rule> scores = new PriorityQueue<Rule>();
            
            for (int j = 0; j < left_elements.size(); j ++) {
              for (BinaryRule left_rule : 
                grammar.getBinaryRulesByLeftChild(left_elements.get(j).getParent())) {
                for (int k = 0; k < right_elements.size(); k++) {
                  for (BinaryRule right_rule : 
                    grammar.getBinaryRulesByLeftChild(right_elements.get(k).getParent())) {
                    if (left_rule.equals(right_rule)) {
                      left_rule.setSplit(i + 1);
                      left_rule.setTerminal(false);
                      scores.add(left_rule,
                          left_rule.score * left_priorities[j] * right_priorities[k]);
                    }
                  }
                }
              }
            }            
          }
          all_scores.add(0, rule_scores);            
        }
      return buildTree(0, sentence.size(), sentence.size(), "S");
    }
    
    private Tree<String> buildTree(int begin, int end, int size, String label) {
    Tree<String> node = new Tree<String>(label);
      int i = size - (end - begin);
      int j = begin;
      if (i < 0 || j < 0)
        return node;
    PriorityQueue<Rule> scores = all_scores.get(i).get(j);
    HashSet<String> applied_symbols = new HashSet<String>();
    ArrayList<Rule> unapplied_rules = new ArrayList<Rule>();
    double[] priorities = new double[scores.size()];
    boolean not_binary = true;
    Tree<String> current_node = node;
    while (not_binary) {
      for (int k = 0; k < unapplied_rules.size(); k++)
        scores.add(unapplied_rules.get(k), priorities[k]);
      unapplied_rules.clear();
      while (scores.hasNext()) {
        double p = scores.getPriority();
        Rule r = scores.next();
        if (!r.getParent().equals(label)) {
          if (!applied_symbols.contains(r.getParent())) {
            unapplied_rules.add(r);
            priorities[unapplied_rules.size() - 1] = p;
          }
          continue;
        }
        if (r instanceof UnaryRule) {
          applied_symbols.add(r.getParent());
          label = ((UnaryRule) r).getChild();
          Tree<String> child = new Tree<String>(label);
          current_node.getChildren().add(child);
          current_node = child;
          if (r.isTerminal)
            not_binary = false;
          break;
        }
        if (r instanceof BinaryRule) {
          String left_label = ((BinaryRule) r).getLeftChild();
          String right_label = ((BinaryRule) r).getRightChild();
          Tree<String> left_child = buildTree(begin, r.split, size, left_label);
          Tree<String> right_child = buildTree(begin, r.split, size, right_label);
          current_node.getChildren().add(left_child);
          current_node.getChildren().add(right_child);
          not_binary = false;
          break;
        }
      }
      
    }
    return node;
  }

}
