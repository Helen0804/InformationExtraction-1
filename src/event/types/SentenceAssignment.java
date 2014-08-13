package event.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import util.Controller;
import util.TypeConstraints;

import commons.Alphabet;
import commons.Alphabets;
import commons.FeatureVector;
import commons.FeatureVectorSequence;

import classifiers.perceptron.AbstractAssignment;

import ace.acetypes.AceEventMention;
import ace.acetypes.AceEventMentionArgument;
import ace.acetypes.AceMention;

import event.perceptron.featureGenerator.EdgeFeatureGenerator;
import event.perceptron.featureGenerator.GlobalFeatureGenerator;
import event.types.SentenceInstance.InstanceAnnotations;

/**
 * For the (target) assignment, it should encode two types of assignment:
 * (1) label assignment for each token: refers to the event trigger classification 
 * (2) assignment for any sub-structure of the sentence, e.g. one assignment indicats that 
 * the second token is argument of the first trigger
 * in the simplest case, this type of assignment involves two tokens in the sentence
 * 
 * 
 * @author che
 *
 */
public class SentenceAssignment extends AbstractAssignment
{
	public static final String PAD_Trigger_Label = "O"; // pad for the intial state
	public static final String Default_Trigger_Label = "O";
	public static final String Default_Argument_Label = "NON";

	/**
	 * as the search processed, increament the state for next token
	 * creat a new featurevector for this state
	 */
	@Override
	public void incrementState()
	{
		state++;
		FeatureVector fv = new FeatureVector();
		this.addFeatureVector(fv);
		this.partial_scores.add(0.0);
	}

	/**
	 * assignment to each node, node-->assignment
	 */
	protected Vector<Integer> nodeAssignment;

	public Vector<Integer> getNodeAssignment()
	{
		return nodeAssignment;
	}

	/**
	 * assignment to each argument edge trigger-->arg --> assignment
	 */
	protected Map<Integer, Map<Integer, Integer>> edgeAssignment;

	/**
	 * deep copy an assignment
	 */
	public SentenceAssignment clone()
	{
		// shallow copy the alphabets
		SentenceAssignment assn = new SentenceAssignment(alphabets, controller);

		// shallow copy the assignment
		assn.nodeAssignment = (Vector<Integer>) this.nodeAssignment.clone();
		// deep copy the edge assignment for the last element 
		// (this is because in the beam search, we need expand the last statement for arguments labeling)
		for (Integer key : this.edgeAssignment.keySet())
		{
			Map<Integer, Integer> edgeMap = this.edgeAssignment.get(key);
			if (edgeMap != null)
			{
				if (key < this.getState())
				{
					assn.edgeAssignment.put(key, edgeMap);
				}
				else
				// deep copy the last element
				{
					Map<Integer, Integer> new_edgeMap = new HashMap<Integer, Integer>();
					new_edgeMap.putAll(edgeMap);
					assn.edgeAssignment.put(key, new_edgeMap);
				}
			}
		}

		// deep copy the feature vector sequence for the last element
		assn.featVecSequence = this.featVecSequence.clone2();

		// deep copy attributes
		assn.state = this.state;
		assn.score = this.score;
		assn.local_score = this.local_score;
		assn.partial_scores.addAll(this.partial_scores);

		return assn;
	}

	public Map<Integer, Map<Integer, Integer>> getEdgeAssignment()
	{
		return edgeAssignment;
	}

	/**
	 * get the node label of the current node
	 * @return
	 */
	public String getLabelAtToken(int i)
	{
		assert (i <= state);

		String label;
		if (i >= 0)
		{
			label = (String) this.alphabets.nodeTargetAlphabet.lookupObject(nodeAssignment.get(i));
		}
		else
		{
			label = PAD_Trigger_Label;
		}
		return label;
	}

	/**
	 * get the node label of the current node
	 * @return
	 */
	public String getCurrentNodeLabel()
	{
		String label;
		if (state >= 0)
		{
			label = (String) this.alphabets.nodeTargetAlphabet.lookupObject(nodeAssignment.get(state));
		}
		else
		{
			label = PAD_Trigger_Label;
		}
		return label;
	}

	/**
	 * set current node label
	 * @return
	 */
	public void setCurrentNodeLabel(int index)
	{
		if (state >= 0)
		{
			if (nodeAssignment.size() <= state)
			{
				nodeAssignment.setSize(state + 1);
			}
			this.nodeAssignment.set(state, index);
		}
	}

	/**
	 * set current node label
	 * @return
	 */
	public void setCurrentNodeLabel(String label)
	{
		if (state >= 0)
		{
			int index = this.alphabets.nodeTargetAlphabet.lookupIndex(label);
			if (nodeAssignment.size() <= state)
			{
				nodeAssignment.setSize(state + 1);
			}
			this.nodeAssignment.set(state, index);
		}
	}

	public Map<Integer, Integer> getCurrentEdgeLabels()
	{
		Map<Integer, Integer> map = this.edgeAssignment.get(state);
		if (map == null)
		{
			map = new HashMap<Integer, Integer>();
			this.edgeAssignment.put(state, map);
		}
		return map;
	}

	/**
	 * set current node edge
	 * @return
	 */
	public void setCurrentEdgeLabel(int arg_idx, String label)
	{
		if (state >= 0)
		{
			int index = this.alphabets.edgeTargetAlphabet.lookupIndex(label);
			Map<Integer, Integer> map = this.edgeAssignment.get(state);
			if (map == null)
			{
				map = new HashMap<Integer, Integer>();
			}
			map.put(arg_idx, index);
			this.edgeAssignment.put(state, map);
		}
	}

	/**
	 * set current node edges
	 * @return
	 */
	public void setCurrentEdges(Map<Integer, Integer> edges)
	{
		if (state >= 0)
		{
			Map<Integer, Integer> map = this.edgeAssignment.get(state);
			if (map == null)
			{
				map = new HashMap<Integer, Integer>();
			}
			map.putAll(edges);
			this.edgeAssignment.put(state, map);
		}
	}

	/**
	 * set current node label
	 * @return
	 */
	public void setCurrentEdgeLabel(int arg_idx, int label)
	{
		if (state >= 0)
		{
			Map<Integer, Integer> map = this.edgeAssignment.get(state);
			if (map == null)
			{
				map = new HashMap<Integer, Integer>();
			}
			map.put(arg_idx, label);
			this.edgeAssignment.put(state, map);
		}
	}

	public SentenceAssignment(Alphabets alphabets, Controller controller)
	{
		this.alphabets = alphabets;
		nodeAssignment = new Vector<Integer>();
		edgeAssignment = new HashMap<Integer, Map<Integer, Integer>>();

		featVecSequence = new FeatureVectorSequence();
		partial_scores = new ArrayList<Double>();
		this.controller = controller;
	}

	/**
	 * given an labeled instance, create a target assignment as ground-truth
	 * also create a full featureVectorSequence
	 * @param inst
	 */
	public SentenceAssignment(SentenceInstance inst)
	{
		this(inst.alphabets, inst.controller);

		for (int i = 0; i < inst.size(); i++)
		{
			int label_index = this.alphabets.nodeTargetAlphabet.lookupIndex(Default_Trigger_Label);
			this.nodeAssignment.add(label_index);
			this.incrementState();
		}

		// use sentence data to assign event/arg tags
		for (AceEventMention mention : inst.eventMentions)
		{
			Vector<Integer> headIndices = mention.getHeadIndices();

			// for event, only pick up the first token as trigger
			int trigger_index = headIndices.get(0);
			// ignore the triggers that are with other POS
			if (!TypeConstraints.isPossibleTriggerByPOS(inst, trigger_index))
			{
				continue;
			}
			int feat_index = this.alphabets.nodeTargetAlphabet.lookupIndex(mention.getSubType());
			this.nodeAssignment.set(trigger_index, feat_index);

			Map<Integer, Integer> arguments = edgeAssignment.get(trigger_index);
			if (arguments == null)
			{
				arguments = new HashMap<Integer, Integer>();
				edgeAssignment.put(trigger_index, arguments);
			}

			// set default edge label between each trigger-entity pair
			for (int can_id = 0; can_id < inst.eventArgCandidates.size(); can_id++)
			{
				AceMention can = inst.eventArgCandidates.get(can_id);
				// ignore entity that are not compatible with the event
				if (TypeConstraints.isEntityTypeEventCompatible(mention.getSubType(), can.getType()))
				{
					feat_index = this.alphabets.edgeTargetAlphabet.lookupIndex(Default_Argument_Label);
					arguments.put(can_id, feat_index);
				}
			}

			// set argument role labels
			for (AceEventMentionArgument arg : mention.arguments)
			{
				AceMention arg_mention = arg.value;
				int arg_index = inst.eventArgCandidates.indexOf(arg_mention);
				feat_index = this.alphabets.edgeTargetAlphabet.lookupIndex(arg.role);
				arguments.put(arg_index, feat_index);
			}
		}

		// create featureVectorSequence
		for (int i = 0; i <= state; i++)
		{
			makeAllFeatureForSingleState(inst, i, inst.learnable, inst.learnable);
		}
	}

	private void makeAllFeatureForSingleState(SentenceInstance problem, int i, boolean addIfNotPresent, boolean useIfNotPresent)
	{
		// make basic bigram features for event trigger
		this.makeNodeFeatures(problem, i, addIfNotPresent, useIfNotPresent);
		// make basic features of the argument-trigger link
		this.makeEdgeFeatures(problem, i, addIfNotPresent, useIfNotPresent);
		if (problem.controller.useGlobalFeature)
		{
			// make various global features
			this.makeGlobalFeatures(problem, i, addIfNotPresent, useIfNotPresent);
		}
	}

	/**
	 * extract global features from the current assignment
	 * @param assn
	 * @param problem
	 */
	public void makeEdgeFeatures(SentenceInstance problem, int index, boolean addIfNotPresent, boolean useIfNotPresent)
	{
		// node label
		String nodeLabel = this.getLabelAtToken(index);
		if (!isArgumentable(nodeLabel))
		{
			return;
		}

		Map<Integer, Integer> edge = edgeAssignment.get(index);
		if (edge == null)
		{
			return;
		}
		for (Integer key : edge.keySet())
		{
			// edge label
			makeEdgeLocalFeature(problem, index, addIfNotPresent, key, useIfNotPresent);
		}
	}

	public void makeEdgeLocalFeature(SentenceInstance problem, int index, boolean addIfNotPresent, int entityIndex, boolean useIfNotPresent)
	{
		if (this.edgeAssignment.get(index) == null)
		{
			// skip assignments that don't have edgeAssignment for index-th node
			return;
		}
		Integer edgeLabelIndx = this.edgeAssignment.get(index).get(entityIndex);
		if (edgeLabelIndx == null)
		{
			return;
		}
		String edgeLabel = (String) this.alphabets.edgeTargetAlphabet.lookupObject(edgeLabelIndx);
		// if the argument role is NON, then do not produce any feature for it
		if (controller.skipNonArgument && edgeLabel.equals(SentenceAssignment.Default_Argument_Label))
		{
			return;
		}

		List<List<List<String>>> edgeFeatVectors = (List<List<List<String>>>) problem.get(InstanceAnnotations.EdgeTextFeatureVectors);
		List<String> textFeatures = edgeFeatVectors.get(index).get(entityIndex);
		AceMention mention = problem.eventArgCandidates.get(entityIndex);

		int nodeLabelIndex = this.nodeAssignment.get(index);
		String nodeLabel = (String) this.alphabets.nodeTargetAlphabet.lookupObject(nodeLabelIndex);
		FeatureVector fv = this.getFeatureVectorSequence().get(index);
		if (textFeatures == null)
		{
			textFeatures = EdgeFeatureGenerator.get_edge_text_features(problem, index, mention);
			edgeFeatVectors.get(index).set(entityIndex, textFeatures);
		}
		for (String textFeature : textFeatures)
		{
			String featureStr = "";

			if (!edgeLabel.equals(SentenceAssignment.Default_Argument_Label))
			{
				if (!TypeConstraints.isIndependentRole(edgeLabel))
				{
					// if the role is dependent on event type, then creat feature based on nodeLabel + edge label
					featureStr = "EdgeLocalFeature:\t" + textFeature + "\t" + "triggerLabel:" + nodeLabel + "\tArgRole:" + edgeLabel;
					makeFeature(featureStr, fv, addIfNotPresent, useIfNotPresent);
				}
				else
				{
					// if the role (like Place or Timex) is independent on event type, then creat feature only based on edge label
					featureStr = "EdgeLocalFeature:\t" + textFeature + "\t" + "\tArgRole:" + edgeLabel;
					makeFeature(featureStr, fv, addIfNotPresent, useIfNotPresent);
				}

				// add backoff feature for argument
				featureStr = "EdgeLocalFeature:\t" + textFeature + "\t" + "IsArg";
				makeFeature(featureStr, fv, addIfNotPresent, useIfNotPresent);
			}
			else
			{
				// feature for NON
				featureStr = "EdgeLocalFeature:\t" + textFeature + "\t" + edgeLabel;
				makeFeature(featureStr, fv, addIfNotPresent, useIfNotPresent);
			}
		}
		String featureStr = "EdgeLocalFeature:\tTriggerType=" + nodeLabel + "\t" + "\tArgRole:" + edgeLabel;
		makeFeature(featureStr, fv, addIfNotPresent, useIfNotPresent);
	}

	/**
	 * This type of feature applies in each step of trigger classification
	 * @param problem
	 * @param index
	 * @param addIfNotPresent
	 * @param useIfNotPresent
	 */
	public void makeGlobalFeaturesTrigger(SentenceInstance problem, int index, boolean addIfNotPresent, boolean useIfNotPresent)
	{
		FeatureVector fv = this.getFV(index);
		List<String> featureStrs = GlobalFeatureGenerator.get_global_features_triggers(problem, index, this);
		for (String feature : featureStrs)
		{
			String featureStr = "TriggerLevelGlobalFeature:\t" + feature;
			makeFeature(featureStr, fv, addIfNotPresent, useIfNotPresent);
		}
	}

	/**
	 * this is global feature applicable when arugment searching in a node is complete
	 * @param problem
	 * @param index
	 * @param addIfNotPresent
	 */
	public void makeGlobalFeaturesComplete(SentenceInstance problem, int index, boolean addIfNotPresent, boolean useIfNotPresent)
	{
		FeatureVector fv = this.getFV(index);
		List<String> featureStrs = GlobalFeatureGenerator.get_global_features_node_level_omplete(problem, index, this);
		for (String feature : featureStrs)
		{
			String featureStr = "NodeLevelGlobalFeature:\t" + feature;
			makeFeature(featureStr, fv, addIfNotPresent, useIfNotPresent);
		}
	}

	/**
	 * this version of global features are added when each step of argument searching
	 * @param problem
	 * @param index
	 * @param entityIndex
	 * @param addIfNotPresent
	 */
	public void makeGlobalFeaturesProgress(SentenceInstance problem, int index, int entityIndex, boolean addIfNotPresent, boolean useIfNotPresent)
	{
		FeatureVector fv = this.getFV(index);
		List<String> featureStrs = GlobalFeatureGenerator.get_global_features_node_level(problem, index, this, entityIndex);
		for (String feature : featureStrs)
		{
			String featureStr = "NodeLevelGlobalFeature:\t" + feature;
			makeFeature(featureStr, fv, addIfNotPresent, useIfNotPresent);
		}
		featureStrs = GlobalFeatureGenerator.get_global_features_sent_level(problem, index, this, entityIndex);
		for (String feature : featureStrs)
		{
			String featureStr = "SentLevelGlobalFeature:\t" + feature;
			makeFeature(featureStr, fv, addIfNotPresent, useIfNotPresent);
		}

	}

	/**
	 * this version of global features are added when argument searching for each node is completed 
	 * assume the argument search in token i is finished, then fill-in global features in token i
	 * @param problem
	 * @param i
	 * @param addIfNotPresent
	 */
	public void makeGlobalFeatures(SentenceInstance problem, int index, boolean addIfNotPresent, boolean useIfNotPresent)
	{
		if (this.edgeAssignment.get(index) == null)
		{
			return;
		}
		makeGlobalFeaturesComplete(problem, index, addIfNotPresent, useIfNotPresent);
		for (int entityIndex : edgeAssignment.keySet())
		{
			makeGlobalFeaturesProgress(problem, index, entityIndex, addIfNotPresent, useIfNotPresent);
		}
	}

	public void makeNodeFeatures(SentenceInstance problem, int i, boolean addIfNotPresent, boolean useIfNotPresent)
	{
		// make node feature (bigram feature)
		List<String> token = ((List<List<String>>) problem.get(InstanceAnnotations.NodeTextFeatureVectors)).get(i);
		String previousLabel = this.getLabelAtToken(i - 1);
		String outcome = this.getLabelAtToken(i);

		// traverse each text feature of the token to explore the bigram featurs
		for (String textFeature : token)
		{
			if (this.controller.order >= 1)
			{
				// bigram features
				// create a bigram feature
				String featureStr = "BigramFeature:\t" + textFeature + "\t" + "PreLabel:" + previousLabel + "\tcurrentLabel:" + outcome;
				makeFeature(featureStr, this.getFV(i), addIfNotPresent, useIfNotPresent);
				// this is a backoff feature for event
				if (!outcome.equals(Default_Trigger_Label) && !outcome.equals("Transport"))
				{
					String superType = TypeConstraints.getEventSuperType(outcome);
					featureStr = "BigramFeature:\t" + textFeature + "\t" + "\tcurrentLabel:" + superType;
					makeFeature(featureStr, this.getFV(i), addIfNotPresent, useIfNotPresent);
				}
			}
			else
			// order = 0
			{
				// unigram features, for history reason, we still call them BigramFeature
				// create a bigram feature
				String featureStr = "BigramFeature:\t" + textFeature + "\t" + "\tcurrentLabel:" + outcome;
				makeFeature(featureStr, this.getFV(i), addIfNotPresent, useIfNotPresent);
				// this is a backoff feature for event, use super event type/ except Transport, since Movement only have one subtype
				if (!outcome.equals(Default_Trigger_Label) && !outcome.equals("Transport"))
				{
					String superType = TypeConstraints.getEventSuperType(outcome);
					featureStr = "BigramFeature:\t" + textFeature + "\t" + "\tcurrentLabel:" + superType;
					makeFeature(featureStr, this.getFV(i), addIfNotPresent, useIfNotPresent);
				}
			}
		}

		// if the previous label is a trigger, then get the bigram labels
		if (!previousLabel.equals(SentenceAssignment.Default_Trigger_Label))
		{
			String featureStr = "BigramFeature:\t" + "PreLabel:" + previousLabel + "\tcurrentLabel:" + outcome;
			makeFeature(featureStr, this.getFV(i), addIfNotPresent, useIfNotPresent);
		}
	}

	/**
	 * add a (possible) feature to feature vector 
	 * @param featureStr
	 * @param fv
	 * @param add_if_not_present true if the feature is not in featureAlphabet, add it
	 * @param use_if_not_present true if the feature is not in featureAlphaebt, still use it in FV
	 */
	protected void makeFeature(String featureStr, FeatureVector fv, boolean add_if_not_present, boolean use_if_not_present)
	{
		// Feature feat = new Feature(null, featureStr);
		// lookup the feature table to create an assignment with the new feature
		if (!use_if_not_present || add_if_not_present)
		{
			int feat_index = lookupFeatures(this.alphabets.featureAlphabet, featureStr, add_if_not_present);
			if (feat_index != -1)
			{
				fv.add(featureStr, 1.0);
			}
		}
		else
		{
			fv.add(featureStr, 1.0);
		}
	}

	/**
	 * lookup the feature (new feature), and then add to alphabet / weights vector if needed
	 * @param assn
	 * @param feat
	 * @return
	 */
	protected static int lookupFeatures(Alphabet dict, Object feat, boolean add_if_not_present)
	{
		int feat_index = dict.lookupIndex(feat, add_if_not_present);

		if (feat_index == -1 && !add_if_not_present)
		{
			return -1;
		}

		return feat_index;
	}

	/**
	 * assume a new state(token) is added during search, then calculate the score for this state, update the total score 
	 * and then, add it to the total score
	 */
	public void updateScoreForNewState(FeatureVector weights)
	{
		FeatureVector fv = this.getCurrentFV();
		double partial_score = fv.dotProduct(weights);
		this.partial_scores.set(state, partial_score);

		this.score = 0;
		for (int i = 0; i <= state; i++)
		{
			this.score += this.partial_scores.get(i);
		}
	}

	/**
	 * check if two assignments equals to each other before (inclusive) state step
	 * @param target
	 * @param state2
	 */
	public boolean equals(SentenceAssignment assn, int step)
	{
		for (int i = 0; i <= step && i <= assn.state && i <= this.state; i++)
		{
			if (!assn.nodeAssignment.get(i).equals(this.nodeAssignment.get(i)))
			{
				return false;
			}
			if (assn.edgeAssignment.get(i) != null && !assn.edgeAssignment.get(i).equals(this.edgeAssignment.get(i)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * check if two assignments equals to each other 
	 * @param target
	 * @param state2
	 */
	@Override
	public boolean equals(Object obj)
	{
		if (!(obj instanceof SentenceAssignment))
		{
			return false;
		}
		SentenceAssignment assn = (SentenceAssignment) obj;
		if (this.state != assn.state)
		{
			return false;
		}
		for (int i = 0; i <= assn.state && i <= this.state; i++)
		{
			if (!assn.nodeAssignment.get(i).equals(this.nodeAssignment.get(i)))
			{
				return false;
			}
			if (assn.edgeAssignment.get(i) != null && !assn.edgeAssignment.get(i).equals(this.edgeAssignment.get(i)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * check if two assignments equals to each other before (inclusive) state step
	 * @param target
	 * @param state2
	 */
	public boolean equals(SentenceAssignment assn, int step, int argNum)
	{
		for (int i = 0; i < step && i <= assn.state && i <= this.state; i++)
		{
			if (!assn.nodeAssignment.get(i).equals(this.nodeAssignment.get(i)))
			{
				return false;
			}
			if (assn.edgeAssignment.get(i) != null && !assn.edgeAssignment.get(i).equals(this.edgeAssignment.get(i)))
			{
				return false;
			}
		}
		if (!assn.nodeAssignment.get(step).equals(this.nodeAssignment.get(step)))
		{
			return false;
		}
		if (argNum < 0)
		{
			// if argument num < 0, only consider the trigger labeling
			return true;
		}
		else
		{
			Map<Integer, Integer> map_assn = assn.edgeAssignment.get(step);
			Map<Integer, Integer> map = this.edgeAssignment.get(step);
			if (map_assn == null && map == null)
			{
				return true;
			}
			for (int i = 0; i <= argNum; i++)
			{
				Integer label_assn = null;
				Integer label = null;
				if (map_assn != null)
				{
					label_assn = map_assn.get(i);
				}
				if (map != null)
				{
					label = map.get(i);
				}
				if (label == null && label_assn != null || label != null && label_assn == null)
				{
					return false;
				}
				if (label != null && label_assn != null && (!label.equals(label_assn)))
				{
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public String toString()
	{
		String ret = "";
		int i = 0;
		for (Integer assn : this.nodeAssignment)
		{
			String token_label = (String) this.alphabets.nodeTargetAlphabet.lookupObject(assn);
			ret += " " + token_label;

			Map<Integer, Integer> edges = this.edgeAssignment.get(i);
			if (edges != null)
			{
				ret += "(";
				for (Integer key : edges.keySet())
				{
					ret += " " + key + ":";
					Integer val = edges.get(key);
					String arg_role = (String) this.alphabets.edgeTargetAlphabet.lookupObject(val);
					ret += arg_role;
				}
				ret += ")";
			}
			i++;
		}
		return ret;
	}

	/**
	 * check if the label of the token is an event, thereby can be attached to argument
	 * @param currentNodeLabel
	 * @return
	 */
	public static boolean isArgumentable(String label)
	{
		if (label.equalsIgnoreCase("O"))
		{
			return false;
		}
		return true;
	}

	// to store temprary local score
	protected double local_score = 0.0;

	public void setLocalScore(double local_score)
	{
		this.local_score = local_score;
	}

	public double getLocalScore()
	{
		return local_score;
	}

	public void addLocalScore(double local_score)
	{
		this.local_score += local_score;
	}
}
