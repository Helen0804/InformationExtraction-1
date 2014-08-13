package commons;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Vector;

import util.BrownClusterOriginal;
import util.ChunkWrapper;
import util.FrameNet;
import util.Nomlex;
import util.POSTaggerWrapperStanford;
import util.ParserWrapper;
import util.Span;
import util.TokenAnnotations;
import util.TypeConstraints;
import util.WordNetWrapper;
import util.ParserWrapper.ParseResult;
import util.graph.DependencyGraph;
import util.graph.GraphEdge;
import util.graph.GraphNode;

import commons.Sentence.Sent_Attribute;

import ace.acetypes.AceEntityMention;
import ace.acetypes.AceMention;
import ace.acetypes.AceTimexMention;
import ace.acetypes.AceValueMention;

import edu.mit.jwi.item.ISynset;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;

/**
 * this class is to implement features based on data structure of Document
 * this class may need read some resources, like dictionaries
 * this is for local feature generator
 */
public class TextFeatureGenerator
{
	// dictionary for title/time words
	Map<String, List<String>> dicts = new HashMap<String, List<String>>();

	public void readDict(String dirName) throws IOException
	{
		File dir = new File(dirName);
		File[] children = dir.listFiles();
		for (File child : children)
		{
			if (child.getName().contains("svn") || child.isHidden())
			{
				continue;
			}
			BufferedReader reader = new BufferedReader(new FileReader(child));
			String line = "";
			while ((line = reader.readLine()) != null)
			{
				line = line.trim();
				line = line.toLowerCase();
				if (line.length() > 0)
				{
					List<String> list = dicts.get(child.getName());
					if (list == null)
					{
						list = new ArrayList<String>();
						dicts.put(child.getName(), list);
					}
					list.add(line);
				}
			}
			reader.close();
		}
	}

	public TextFeatureGenerator()
	{
		try
		{
			readDict("data/dict");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public static void doPreprocessCheap(Document doc)
	{
		for (Sentence sent : doc.getSentences())
		{
			String[] tokens = (String[]) sent.get(Sent_Attribute.TOKENS);
			Span[] tokenSpans = (Span[]) sent.get(Sent_Attribute.TOKEN_SPANS);
			try
			{
				// get pos tags
				String[] posTags;
				posTags = POSTaggerWrapperStanford.getPosTagger()
						.posTag(tokens);
				sent.put(Sent_Attribute.POSTAGS, posTags);
				// get chunks
				String[] chunks = ChunkWrapper.getChunker().chunk(tokens,
						posTags);
				sent.put(Sent_Attribute.CHUNKS, chunks);

				List<Map<Class<?>, Object>> tokenFeatureMaps = new ArrayList<Map<Class<?>, Object>>();
				sent.put(Sent_Attribute.Token_FEATURE_MAPs, tokenFeatureMaps);
				for (int idx = 0; idx < tokenSpans.length; idx++)
				{
					HashMap<Class<?>, Object> map = new HashMap<Class<?>, Object>();
					// change the first token in each sentence to lowercase
					if (idx == 0 && tokens[0] != null
							&& Character.isUpperCase(tokens[0].charAt(0)))
					{
						tokens[0] = tokens[0].toLowerCase();
					}
					map.put(TokenAnnotations.TextAnnotation.class, tokens[idx]);
					map.put(TokenAnnotations.PartOfSpeechAnnotation.class,
							posTags[idx]);
					String lemma = ParserWrapper.lemmanize(tokens[idx],
							posTags[idx]);
					map.put(TokenAnnotations.LemmaAnnotation.class,
							lemma.toLowerCase());
					map.put(TokenAnnotations.SpanAnnotation.class,
							tokenSpans[idx]);
					tokenFeatureMaps.add(map);
				}
				// fill in ace annotations such as event/relation/entity mentions
				sent.fillAceAnnotaions();

				// synonyms etc.
				//				fillFeatures_local(doc);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public static void doPreprocess(Document doc)
	{
		for (Sentence sent : doc.getSentences())
		{
			String[] tokens = (String[]) sent.get(Sent_Attribute.TOKENS);
			Span[] tokenSpans = (Span[]) sent.get(Sent_Attribute.TOKEN_SPANS);
			try
			{
				// get pos tags
				String[] posTags;
				posTags = POSTaggerWrapperStanford.getPosTagger()
						.posTag(tokens);
				sent.put(Sent_Attribute.POSTAGS, posTags);

				// get parse/deps tree
				ParseResult parse = ParserWrapper.getParserWrapper()
						.getTypedDeps(tokens);
				// create dependency graph representation, in order to faciliate graph manupulation
				Collection<TypedDependency> tdl = parse.deps;
				DependencyGraph graph = new DependencyGraph(tdl, tokens.length);
				sent.put(Sentence.Sent_Attribute.DepGraph, graph);
				sent.put(Sent_Attribute.ParseTree, parse.tree);

				// get chunks
				String[] chunks = ChunkWrapper.getChunker().chunk(tokens,
						posTags);
				sent.put(Sent_Attribute.CHUNKS, chunks);

				// parse chunk tags into structure
				List<List<Object>> chunk_structure = new ArrayList<List<Object>>();
				List<Integer> phrase = null;
				for (int i = 0; i < chunks.length; i++)
				{
					if (chunks[i].equals("O"))
					{
						phrase = null;
						continue;
					}
					if (chunks[i].startsWith("B"))
					{
						phrase = new ArrayList<Integer>();
						phrase.add(i);
						String type = chunks[i].substring(2);
						List<Object> new_entry = new ArrayList<Object>();
						new_entry.add(type);
						new_entry.add(phrase);
						chunk_structure.add(new_entry);
					}
					else if (phrase != null)
					{
						phrase.add(i);
					}
				}
				sent.put(Sent_Attribute.ChunkStructure, chunk_structure);

				List<Map<Class<?>, Object>> tokenFeatureMaps = new ArrayList<Map<Class<?>, Object>>();
				sent.put(Sent_Attribute.Token_FEATURE_MAPs, tokenFeatureMaps);
				for (int idx = 0; idx < tokenSpans.length; idx++)
				{
					HashMap<Class<?>, Object> map = new HashMap<Class<?>, Object>();
					// change the first token in each sentence to lowercase
					if (idx == 0 && tokens[0] != null
							&& Character.isUpperCase(tokens[0].charAt(0)))
					{
						tokens[0] = tokens[0].toLowerCase();
					}
					map.put(TokenAnnotations.TextAnnotation.class, tokens[idx]);
					map.put(TokenAnnotations.PartOfSpeechAnnotation.class,
							posTags[idx]);
					String lemma = ParserWrapper.lemmanize(tokens[idx],
							posTags[idx]).toLowerCase();
					map.put(TokenAnnotations.LemmaAnnotation.class, lemma);
					map.put(TokenAnnotations.ChunkingAnnotation.class,
							chunks[idx]);
					map.put(TokenAnnotations.SpanAnnotation.class,
							tokenSpans[idx]);

					// get base form of verb and noun according to Nomlex. e.g. retirement --> retire
					if (posTags[idx].startsWith("V")
							&& Nomlex.getSingleTon().contains(lemma))
					{
						map.put(TokenAnnotations.NomlexbaseAnnotation.class,
								lemma);
					}
					else if (posTags[idx].startsWith("N"))
					{
						String comlexBase = Nomlex.getSingleTon().getBaseForm(
								lemma);
						if (comlexBase != null)
						{
							map.put(TokenAnnotations.NomlexbaseAnnotation.class,
									comlexBase);
						}
					}

					tokenFeatureMaps.add(map);
				}
				// fill in ace annotations such as event/relation/entity mentions
				sent.fillAceAnnotaions();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	protected static void fillClauseNumber(Document doc)
	{
		for (Sentence sent : doc.getSentences())
		{
			fillClauseNumber(sent);
		}
	}

	/**
	 * fill in clause number for each token in the sentence 
	 * tokens with the same clause number are considered in the same clause
	 * @param sent
	 */
	protected static void fillClauseNumber(Sentence sent)
	{
		List<Map<Class<?>, Object>> tokens = (List<Map<Class<?>, Object>>) sent
				.get(Sent_Attribute.Token_FEATURE_MAPs);
		Tree root = (Tree) sent.get(Sent_Attribute.ParseTree);
		if (root == null)
		{
			return;
		}
		int clauseNum = 0;
		// default clause number is 0
		for (int i = 0; i < tokens.size(); i++)
		{
			Map<Class<?>, Object> current_token = tokens.get(i);
			current_token.put(TokenAnnotations.ClauseAnnotation.class,
					clauseNum);
		}
		String snodes = "S|SBAR|SBARQ|SQ";
		List<Tree> terminals = root.getLeaves();
		// traverse the tree
		Queue<Tree> queue = new LinkedList<Tree>();
		queue.add(root);
		while (!queue.isEmpty())
		{
			Tree node = queue.poll();
			Tree[] children = node.children();
			for (Tree child : children)
			{
				if (!child.isPreTerminal())
				{
					if (child.value().matches(snodes))
					{
						clauseNum++;
						// a clause node, get all leaves
						List<Tree> leaves = child.getLeaves();
						for (Tree leaf : leaves)
						{
							int index = terminals.indexOf(leaf);
							Map<Class<?>, Object> token = tokens.get(index);
							token.put(TokenAnnotations.ClauseAnnotation.class,
									clauseNum);
						}
						queue.add(child);
					}
					else
					{
						queue.add(child);
					}
				}
			}
		}
	}

	protected static void fillDependencyFeatures(Document doc)
	{
		for (Sentence sent : doc.getSentences())
		{
			fillDependencyFeatures(sent);
		}
	}

	protected void fillEntityInformation(Document doc)
	{
		for (Sentence sent : doc.getSentences())
		{
			fillEntityInformation(sent);
		}
	}

	protected void fillEntityInformation(Sentence sent)
	{
		List<Map<Class<?>, Object>> tokens = (List<Map<Class<?>, Object>>) sent
				.get(Sent_Attribute.Token_FEATURE_MAPs);

		for (AceEntityMention entity : sent.entityMentions)
		{
			List<Integer> heads = entity.getHeadIndices();
			for (Integer index : heads)
			{
				List<String> entityInfo = (List<String>) tokens.get(index).get(
						TokenAnnotations.EntityAnnotation.class);
				if (entityInfo == null)
				{
					entityInfo = new ArrayList<String>();
					tokens.get(index)
							.put(TokenAnnotations.EntityAnnotation.class,
									entityInfo);
				}
				if (!entityInfo.contains(entity.getType()))
				{
					entityInfo.add(entity.getType());
				}
			}
		}

		// use Title List instead of the Job-Title value to normalize   
		for (AceValueMention value : sent.valueMentions)
		{
			if (value.getType().matches("Numeric|Contact-Info"))
			{
				List<Integer> heads = value.getHeadIndices();
				for (Integer index : heads)
				{
					List<String> entityInfo = (List<String>) tokens.get(index)
							.get(TokenAnnotations.EntityAnnotation.class);
					if (entityInfo == null)
					{
						entityInfo = new ArrayList<String>();
						tokens.get(index).put(
								TokenAnnotations.EntityAnnotation.class,
								entityInfo);
					}
					if (!entityInfo.contains(value.getType()))
					{
						entityInfo.add(value.getType());
					}
				}
			}
			if (value.getType().equals("Job-Title"))
			{
				List<Integer> heads = value.getHeadIndices();
				for (Integer index : heads)
				{
					String text = (String) tokens.get(index).get(
							TokenAnnotations.TextAnnotation.class);
					text = text.toLowerCase();
					if (dicts.get("TITLE") != null
							&& dicts.get("TITLE").contains(text))
					{
						List<String> entityInfo = (List<String>) tokens.get(
								index).get(
								TokenAnnotations.EntityAnnotation.class);
						if (entityInfo == null)
						{
							entityInfo = new ArrayList<String>();
							tokens.get(index).put(
									TokenAnnotations.EntityAnnotation.class,
									entityInfo);
						}
						if (!entityInfo.contains(value.getType()))
						{
							entityInfo.add(value.getType());
						}
					}
				}
			}
		}

		// use timex list instead of Timex entity in ace annotation to normalize
		for (AceTimexMention timex : sent.timexMentions)
		{
			List<Integer> heads = timex.getHeadIndices();
			for (Integer index : heads)
			{
				String text = (String) tokens.get(index).get(
						TokenAnnotations.TextAnnotation.class);
				text = text.toLowerCase();
				if (dicts.get("TIME") != null
						&& dicts.get("TIME").contains(text))
				{
					List<String> entityInfo = (List<String>) tokens.get(index)
							.get(TokenAnnotations.EntityAnnotation.class);
					if (entityInfo == null)
					{
						entityInfo = new ArrayList<String>();
						tokens.get(index).put(
								TokenAnnotations.EntityAnnotation.class,
								entityInfo);
					}
					if (!entityInfo.contains(timex.getType()))
					{
						entityInfo.add(timex.getType());
					}
				}
			}
		}

		// use Title list to detect titles
		for (int index = 0; index < sent.size(); index++)
		{
			String text = (String) tokens.get(index).get(
					TokenAnnotations.TextAnnotation.class);
			text = text.toLowerCase();
			String pos = (String) tokens.get(index).get(
					TokenAnnotations.PartOfSpeechAnnotation.class);
			if (pos.startsWith("N")) // only consider Non for Title
			{
				if (dicts.get("TITLE") != null
						&& dicts.get("TITLE").contains(text))
				{
					List<String> entityInfo = (List<String>) tokens.get(index)
							.get(TokenAnnotations.EntityAnnotation.class);
					if (entityInfo == null)
					{
						entityInfo = new ArrayList<String>();
						tokens.get(index).put(
								TokenAnnotations.EntityAnnotation.class,
								entityInfo);
					}

					if (!entityInfo.contains("Title"))
					{
						entityInfo.add("Title");
					}
				}
			}
		}
	}

	/**
	 * fill in dependency features
	 * or it's ROOT of the sentences
	 * before calling this, the dictionary features should have been filled
	 */
	private static void fillDependencyFeatures(Sentence sent)
	{
		List<Map<Class<?>, Object>> tokens = (List<Map<Class<?>, Object>>) sent
				.get(Sent_Attribute.Token_FEATURE_MAPs);
		DependencyGraph graph = (DependencyGraph) sent
				.get(Sent_Attribute.DepGraph);

		// traverse each dependency link
		if (graph == null)
		{
			return;
		}

		for (int i = 0; i < tokens.size(); i++)
		{
			Map<Class<?>, Object> current_token = tokens.get(i);
			GraphNode node = graph.getVertices().get(i);
			for (GraphEdge edge : node.edges)
			{
				int index = edge.getGovernor();
				String label = "Gov";
				if (index == i)
				{
					index = edge.getDependent();
					label = "Dep";
				}
				Vector<String> dep_features = (Vector<String>) current_token
						.get(TokenAnnotations.DependencyAnnotation.class);
				if (dep_features == null)
				{
					dep_features = new Vector<String>();
				}
				// use entity information to normalize the dependencies
				String related_word = "";
				String related_entity = "";
				String related_pos = "";

				// use the lemma to normalize the dependencies
				Map<Class<?>, Object> related_token = tokens.get(index);
				List<String> relatedEntityInfo = (List<String>) related_token
						.get(TokenAnnotations.EntityAnnotation.class);
				if (relatedEntityInfo != null)
				{
					related_entity = relatedEntityInfo.get(0);
				}
				related_word = (String) related_token
						.get(TokenAnnotations.LemmaAnnotation.class);
				// use the pos to normalize the depdencies
				related_pos = (String) related_token
						.get(TokenAnnotations.PartOfSpeechAnnotation.class);

				if (related_word.length() > 0)
				{
					String feature = label + "=" + edge.getRelation() + "_"
							+ related_word;
					dep_features.add(feature);
				}
				if (related_entity != null && related_entity.length() > 0)
				{
					String feature = label + "=" + edge.getRelation() + "_"
							+ related_entity;
					dep_features.add(feature);
				}
				if (related_pos.length() > 0)
				{
					String feature = label + "=" + edge.getRelation() + "_"
							+ related_pos;
					dep_features.add(feature);
				}
				// only consider the dependency type
				String feature = label + "=" + edge.getRelation();
				dep_features.add(feature);

				current_token.put(TokenAnnotations.DependencyAnnotation.class,
						dep_features);
			}
		}
	}

	protected static Vector<String> getConjuctionFeatureSingleVector(
			List<Map<Class<?>, Object>> sent, int i, Class<?> featureType,
			String featureName, int position)
	{
		Vector<String> ret = new Vector<String>();

		if (i >= sent.size() || i < 0)
		{
			return null;
		}
		position = (position + i);

		// hit margins of a sentence, then pick up Padding to feed the feature
		if (position >= sent.size())
		{
			return ret;
		}
		else if (position < 0)
		{
			return ret;
		}
		else
		{
			Vector<String> temp = (Vector<String>) sent.get(position).get(
					featureType);
			if (temp == null)
			{
				return ret;
			}
			else
			{
				for (String value : temp)
				{
					ret.add(featureName + "=" + value);
				}
			}

		}
		return ret;
	}

	/**
	 * fill in features for a Document doc
	 * this is only API provided by this class, derived class should Override this method
	 * @param doc
	 * @throws IOException 
	 */
	public void fillTextFeatures(Document doc) throws IOException
	{
		/* 预处理提取pos, lemma, 句法，依存，chunk, Nomlex */
		doPreprocess(doc);
		/* 局部特征：第一个上位词； 最常见意思的同义词表；brown聚类；Frame */
		fillFeatures_local(doc);
		/* 实体特征：1.entity；2.value；3.timex；4.title，是保存在doc中sent的token中*/
		fillEntityInformation(doc);
		/* 提取基本的依存特征，仅考虑依存节点的词形、词性、实体 */
		fillDependencyFeatures(doc);
		/* 最近实体特征：1.依存句法上最近；2.物理距离上 */
		fillNearestEntityInformation(doc);
		/* 在同一子句特征的序号  */
		fillClauseNumber(doc);
		/* 获得句子聚类中高置信度的事件类型 */
		fillFeaturesFromSentCluster(doc);
	}

	/**
	 * fill in features the are local to a token
	 * @param doc
	 * @throws IOException 
	 */
	protected static void fillFeatures_local(Document doc) throws IOException
	{
		for (Sentence sent : doc.getSentences())
		{
			List<Map<Class<?>, Object>> tokens = (List<Map<Class<?>, Object>>) sent
					.get(Sent_Attribute.Token_FEATURE_MAPs);
			for (Map<Class<?>, Object> token : tokens)
			{
				// Wordnet Hypernym
				String text = (String) token
						.get(TokenAnnotations.TextAnnotation.class);
				String lemma = (String) token
						.get(TokenAnnotations.LemmaAnnotation.class);
				String pos = (String) token
						.get(TokenAnnotations.PartOfSpeechAnnotation.class);
				ISynset hypernym = WordNetWrapper.getSingleTon().getHypernym(
						lemma, pos);
				if (hypernym != null)
				{
					token.put(TokenAnnotations.HypernymAnnotation.class,
							hypernym.getID().toString());
				}

				// Wordnet Synonyms
				List<String> synonyms = WordNetWrapper.getSingleTon()
						.getSynonyms(lemma, pos);
				if (synonyms != null)
				{
					token.put(TokenAnnotations.SynonymsAnnotation.class,
							synonyms);
				}

				// brown clusters
				String cluster = BrownClusterOriginal.getSingleton()
						.getBrownClusterPrefix(text);
				if (cluster != null)
				{
					token.put(TokenAnnotations.BrownClusterAnnotation.class,
							cluster);
				}

				/* 在local feature中居然用到了FrameNet */
				List<String> frames = FrameNet.getSingleton().getFrameIDs(
						lemma, pos);
				if (frames != null)
				{
					token.put(TokenAnnotations.FrameAnnotation.class, frames);
				}
			}
		}
	}

	/**
	 * according to Zheng Chen 2009, fill in the type of syntactically/physically nearest entity info
	 * @param doc
	 */
	protected static void fillNearestEntityInformation(Document doc)
	{
		for (Sentence sent : doc.getSentences())
		{
			List<Map<Class<?>, Object>> tokens = (List<Map<Class<?>, Object>>) sent
					.get(Sent_Attribute.Token_FEATURE_MAPs);

			for (int t = 0; t < tokens.size(); t++)
			{
				Map<Class<?>, Object> token = tokens.get(t);
				// fill nearst entity feature for t
				fillSyntacticallyNearestEntityInformation(sent, t, token);
				fillPhysicallyNearestEntityInformation(sent, t, token);
			}
		}
	}

	/**
	 * the entity type of syntactically nearest entity to the token t
	 * @param sent
	 * @param t
	 * @param token 
	 */
	private static void fillPhysicallyNearestEntityInformation(Sentence sent,
			int t, Map<Class<?>, Object> token)
	{
		AceMention nearest_mention = null;
		int nearest_distance = Integer.MAX_VALUE;
		for (AceEntityMention mention : sent.entityMentions)
		{
			Vector<Integer> indices = mention.getHeadIndices();
			for (Integer index : indices)
			{
				int distance = Math.abs(t - index);
				if (distance < nearest_distance && distance <= 5)
				{
					nearest_distance = distance;
					nearest_mention = mention;
				}
			}
		}
		for (AceMention mention : sent.valueMentions)
		{
			Vector<Integer> indices = mention.getHeadIndices();
			for (Integer index : indices)
			{
				int distance = Math.abs(t - index);
				if (distance < nearest_distance && distance <= 5)
				{
					nearest_distance = distance;
					nearest_mention = mention;
				}
			}
		}

		if (nearest_mention != null)
		{
			token.put(TokenAnnotations.PhysicallyNearestEntity.class,
					nearest_mention);
		}
	}

	/**
	 * the entity type of the physically nearest entity to the token t 
	 * @param sent
	 * @param t
	 * @param token 
	 */
	private static void fillSyntacticallyNearestEntityInformation(
			Sentence sent, int t, Map<Class<?>, Object> token)
	{
		AceMention nearest_mention = null;
		int nearest_distance = Integer.MAX_VALUE;

		// BFS search the depdency tree (possibly a graph)
		DependencyGraph graph = (DependencyGraph) sent
				.get(Sent_Attribute.DepGraph);
		if (graph == null)
		{
			return;
		}

		for (AceEntityMention mention : sent.entityMentions)
		{
			Vector<Integer> indices = mention.getHeadIndices();
			Vector<Integer> temp = new Vector<Integer>();
			temp.add(t);
			Vector<Integer> path = graph.getShortestPath(indices, temp);
			if (path != null && path.size() < nearest_distance)
			{
				int distance = path.size();
				if (distance <= 5)
				{
					nearest_distance = path.size();
					nearest_mention = mention;
				}
			}
		}

		for (AceMention mention : sent.valueMentions)
		{
			Vector<Integer> indices = mention.getHeadIndices();
			Vector<Integer> temp = new Vector<Integer>();
			temp.add(t);
			Vector<Integer> path = graph.getShortestPath(indices, temp);
			if (path != null && path.size() < nearest_distance)
			{
				int distance = path.size();
				if (distance <= 5)
				{
					nearest_distance = path.size();
					nearest_mention = mention;
				}
			}
		}

		if (nearest_mention != null)
		{
			token.put(TokenAnnotations.SyntacticallyNearestEntity.class,
					nearest_mention);
		}
	}

	/**
	 * given a lemma, get possible event types according to 
	 * stats from training data
	 * @param lemma
	 * @return
	 */
	static List<String> getPotentialEventTypes(String lemma)
	{
		List<String> ret = new ArrayList<String>();
		for (String eventType : Document.triggerTokensFineGrained.keySet())
		{
			if (Document.triggerTokensFineGrained.get(eventType)
					.contains(lemma))
			{
				ret.add(eventType);
			}
		}

		return ret;
	}

	static List<String> getPotentialEventTypesHighConf(String lemma)
	{
		List<String> ret = new ArrayList<String>();
		for (String eventType : Document.triggerTokensHighQuality.keySet())
		{
			if (Document.triggerTokensHighQuality.get(eventType)
					.contains(lemma))
			{
				ret.add(eventType);
			}
		}

		return ret;
	}

	/**
	 * get a list of features based on the cluster of sents
	 * @return
	 */
	protected void fillFeaturesFromSentCluster(Document doc)
	{
		// set the cluster in advance
		doc.setSentenceClustersByTokens();

		for (List<Sentence> cluster : doc.getSentenceClusters())
		{
			for (int sent_id = 0; sent_id < cluster.size(); sent_id++)
			{
				Sentence sent = cluster.get(sent_id);
				List<Map<Class<?>, Object>> tokens = (List<Map<Class<?>, Object>>) sent
						.get(Sent_Attribute.Token_FEATURE_MAPs);

				for (int token_id = 0; token_id < tokens.size(); token_id++)
				{
					String lemma = (String) tokens.get(token_id).get(
							TokenAnnotations.LemmaAnnotation.class);
					List<String> eventTypes = getPotentialEventTypes(lemma);

					List<String> features = new ArrayList<String>();
					tokens.get(token_id)
							.put(TokenAnnotations.HighConfidenceTriggerInCluster.class,
									features);

					// skip if doesn't match event types
					if (eventTypes.size() == 0)
					{
						continue;
					}

					for (int i = 0; i < cluster.size(); i++)
					{
						if (i != sent_id)
						{
							Sentence anotherSent = cluster.get(i);
							List<Map<Class<?>, Object>> pre_tokens = (List<Map<Class<?>, Object>>) anotherSent
									.get(Sent_Attribute.Token_FEATURE_MAPs);
							for (int j = 0; j < pre_tokens.size(); j++)
							{
								String pre_lemma = (String) pre_tokens
										.get(j)
										.get(TokenAnnotations.LemmaAnnotation.class);
								List<String> pre_eventTypes = getPotentialEventTypesHighConf(pre_lemma);
								for (String pre_eventType : pre_eventTypes)
								{
									String pre_eventSuperType = TypeConstraints.eventTypeMapModified
											.get(pre_eventType);
									for (String eventType : eventTypes)
									{
										String eventSuperType = TypeConstraints.eventTypeMapModified
												.get(eventType);
										if (pre_eventSuperType
												.equals(eventSuperType))
										{
											String feature = pre_eventType
													+ "#" + eventType;
											if (!features.contains(feature))
											{
												features.add(feature);
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

}
