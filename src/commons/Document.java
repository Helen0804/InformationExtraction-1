package commons;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import commons.Sentence.Sent_Attribute;

import ace.acetypes.AceDocument;
import ace.acetypes.AceEntityMention;
import ace.acetypes.AceEventMention;
import ace.acetypes.AceMention;

import opennlp.tools.util.InvalidFormatException;
import util.Controller;
import util.SentDetectorWrapper;
import util.Span;
import util.TokenAnnotations;
import util.TokenizerWrapper;
import util.TypeConstraints;
import cc.mallet.util.FileUtils;
import event.types.SentenceInstance;

/**
 * read the source data of i2b2
 * @author che
 *
 */
public class Document
{
	// file extentions
	static public final String textFileExt = ".sgm";
	static public final String apfFileExt = ".apf.xml";
	static public final String apfFileExt2 = ".sgm.xml";

	// the id (base file name) of the document
	public String docID;
	public String text;
	/**
	 * the difference between text and all text is that, alltext includes headline etc.
	 */
	public String allText;
	public String headline;
	private String before_text;
	public int textoffset;
	protected boolean hasLabel;

	// if the document is monoCase
	boolean monoCase = false;

	/*
	 * the list of sentences they are instances in the learning process, there can be a
	 * dummy list of sentences, where each sentence is a cluster of sentence e.g. the
	 * dummy sentence can be concatenation of sentences that linked by entity coreference
	 */
	protected List<Sentence> sentences;

	/**
	 * this object contains the parsed information from apf file (pretty much everything)
	 * it can be considered as gold standard for event extraction or can provide perfect entities etc.
	 */
	public AceDocument aceAnnotations;

	// Event type --> Trigger token
	public static Map<String, List<String>> triggerTokens = new HashMap<String, List<String>>();
	// Event subtype --> Trigger token
	public static Map<String, List<String>> triggerTokensFineGrained = new HashMap<String, List<String>>();
	// Event subtype --> trigger token with high confidence value
	public static Map<String, List<String>> triggerTokensHighQuality = new HashMap<String, List<String>>();

	/**
	 * the container for the sentence "clusters"
	 */
	protected List<List<Sentence>> sentClusters = new ArrayList<List<Sentence>>();

	/**
	 * return the sentence clusters to the client
	 * @return
	 */
	public List<List<Sentence>> getSentenceClusters()
	{
		return sentClusters;
	}

	static
	{
		// initialize priorityQueueEntities
		try
		{
			// initialize dict of triggerTokens
			BufferedReader reader = new BufferedReader(new FileReader(
					"data/triggerTokens"));
			String line = null;
			while ((line = reader.readLine()) != null)
			{
				if (line.length() == 0)
				{
					continue;
				}
				String[] fields = line.split("\\t");
				String eventSubType = fields[0];
				String triggerToken = fields[1];
				Double confidence = Double.parseDouble(fields[2]);
				if (confidence < 0.150)
				{
					continue;
				}
				String eventType = TypeConstraints.eventTypeMapModified
						.get(eventSubType);
				List<String> triggers = triggerTokens.get(eventType);
				if (triggers == null)
				{
					triggers = new ArrayList<String>();
					triggerTokens.put(eventType, triggers);
				}
				if (!triggers.contains(triggerToken))
				{
					triggers.add(triggerToken);
				}

				triggers = triggerTokensFineGrained.get(eventSubType);
				if (triggers == null)
				{
					triggers = new ArrayList<String>();
					triggerTokensFineGrained.put(eventSubType, triggers);
				}
				if (!triggers.contains(triggerToken))
				{
					triggers.add(triggerToken);
				}

				if (confidence >= 0.50)
				{
					triggers = triggerTokensHighQuality.get(eventSubType);
					if (triggers == null)
					{
						triggers = new ArrayList<String>();
						triggerTokensHighQuality.put(eventSubType, triggers);
					}
					if (!triggers.contains(triggerToken))
					{
						triggers.add(triggerToken);
					}
				}
			}
			reader.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public void printDocCluster(PrintStream out)
	{
		int i = 0;
		for (List<Sentence> cluster : this.sentClusters)
		{
			out.println("cluster " + i++);
			for (Sentence sent : cluster)
			{
				String[] tokens = (String[]) sent.get(Sent_Attribute.TOKENS);
				for (String token : tokens)
				{
					out.print(token + " ");
				}
				out.println();
			}
		}
	}

	public void setSentenceClustersByTokens()
	{
		final String allowedPOS = "IN|JJ|RB|DT|VBG|VBD|NN|NNPS|VB|VBN|NNS|VBP|NNP|PRP|VBZ";

		this.sentClusters.clear();
		// travels each entity to get relevant sents

		for (String eventType : triggerTokens.keySet())
		{
			List<String> triggers = triggerTokens.get(eventType);
			List<Sentence> cluster = new ArrayList<Sentence>();
			// add sentence that contains this entity
			for (int i = 0; i < this.sentences.size(); i++)
			{
				Sentence sent = this.sentences.get(i);
				List<Map<Class<?>, Object>> tokens = (List<Map<Class<?>, Object>>) sent
						.get(Sent_Attribute.Token_FEATURE_MAPs);
				for (int j = 0; j < tokens.size(); j++)
				{
					String pos = (String) tokens.get(j).get(
							TokenAnnotations.PartOfSpeechAnnotation.class);
					if (pos.matches(allowedPOS))
					{
						String lemma = (String) tokens.get(j).get(
								TokenAnnotations.LemmaAnnotation.class);
						if (triggers.contains(lemma))
						{
							cluster.add(sent);
							break;
						}
					}
				}
			}

			// add cluster if doesn't exist
			if (cluster.size() > 0 && !this.sentClusters.contains(cluster))
			{
				this.sentClusters.add(cluster);
			}
		}

		// sort clusters by size and remove overlapping sents
		Collections.sort(this.sentClusters, new Comparator<List<Sentence>>()
		{
			@Override
			public int compare(List<Sentence> set1, List<Sentence> set2)
			{
				if (set1.size() > set2.size())
				{
					return -1;
				}
				else if (set1.size() < set2.size())
				{
					return 1;
				}
				else
				{
					return 0;
				}
			}
		});

		for (int i = 1; i < this.sentClusters.size(); i++)
		{
			List<Sentence> cluster = this.sentClusters.get(i);
			for (int j = 0; j < i; j++)
			{
				List<Sentence> pre_cluster = this.sentClusters.get(j);
				// remove overlapping sents
				cluster.removeAll(pre_cluster);
			}
			if (cluster.size() == 0)
			{
				this.sentClusters.remove(cluster);
				i--;
			}
		}

		// set the remaining sents as singleton clusters
		for (int i = 0; i < this.sentences.size(); i++)
		{
			// only add sents that are not in any existing clusters
			Sentence sent = this.sentences.get(i);
			if (isNotInCluster(sent))
			{
				List<Sentence> cluster = new ArrayList<Sentence>();
				cluster.add(sent);
				this.sentClusters.add(cluster);
			}
		}
	}

	/**
	 * check if sent is in any cluster or not
	 * @param sent
	 * @return
	 */
	private boolean isNotInCluster(Sentence sent)
	{
		for (List<Sentence> cluster : this.sentClusters)
		{
			if (cluster.contains(sent))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * implicit constructor
	 */
	protected Document()
	{
		;
	}

	public Document(String baseFileName) throws IOException
	{
		// by default, the hasLabel is true
		this(baseFileName, true, false);
	}

	/**
	 * Note: this function assume the *.sgm files and *.apf file are in the same folder
	 * @param baseFile the file of *.xml or any file name prefix excluding .txt .extent .tlink
	 * @param hasLabel if this document has .extent/.tlink annotations (ground truth)
	 * @throws Exception 
	 */
	public Document(String baseFileName, boolean hasLabel, boolean monoCase,
			String year) throws IOException
	{
		this.monoCase = monoCase;
		docID = baseFileName;
		File txtFile = new File(baseFileName + textFileExt);

		this.setHasLabel(hasLabel);
		if (this.isHasLabel())
		{
			String apfFile = baseFileName + apfFileExt;
			setAceAnnotations(new AceDocument(txtFile.getAbsolutePath(),
					apfFile, year));
		}

		sentences = new ArrayList<Sentence>();
		readDoc(txtFile, this.monoCase);
	}

	/**
	 * a constructor doesn't assume file name extension convention
	 * @param textFile
	 * @param apfFile
	 * @param hasLabel
	 * @param monoCase
	 * @throws IOException
	 */
	public Document(String textFile, String apfFile, boolean hasLabel,
			boolean monoCase) throws IOException
	{
		this.monoCase = monoCase;
		File txtFile = new File(textFile);

		this.setHasLabel(hasLabel);
		if (this.isHasLabel())
		{
			setAceAnnotations(new AceDocument(txtFile.getAbsolutePath(),
					apfFile));
		}
		this.docID = this.aceAnnotations.docID;
		sentences = new ArrayList<Sentence>();
		readDoc(txtFile, this.monoCase);
	}

	/**
	 * Note: this function assume the *.sgm files and *.apf file are in the same folder
	 * @param baseFile the file of *.xml or any file name prefix excluding .txt .extent .tlink
	 * @param hasLabel if this document has .extent/.tlink annotations (ground truth)
	 * @throws Exception 
	 */
	public Document(String baseFileName, boolean hasLabel, boolean monoCase)
			throws IOException
	{
		this.monoCase = monoCase;
		docID = baseFileName;
		File txtFile = new File(baseFileName + textFileExt);

		this.setHasLabel(hasLabel);
		if (this.isHasLabel())
		{
			String apfFile = baseFileName + apfFileExt;
			setAceAnnotations(new AceDocument(txtFile.getAbsolutePath(),
					apfFile));
		}

		sentences = new ArrayList<Sentence>();
		readDoc(txtFile, this.monoCase);
	}

	/**
	 *  remove all XML markups associated with multiple lines. 
	 *  e.g. <Quote xxxx
	 *   .....
	 *   />
	 * @param fileTextWithXML
	 * @return
	 */
	static String eraseXML(String fileTextWithXML)
	{
		boolean inTag = false;
		int length = fileTextWithXML.length();
		StringBuffer fileText = new StringBuffer();
		for (int i = 0; i < length; i++)
		{
			char c = fileTextWithXML.charAt(i);
			if (c == '<')
			{
				inTag = true;
			}
			if (!inTag)
			{
				fileText.append(c);
			}
			if (c == '>')
			{
				inTag = false;
			}
		}
		return fileText.toString();
	}

	public static class TextSegment
	{
		public String tag;
		public String text;

		public boolean hasTag()
		{
			if (tag == null || tag.equals(""))
			{
				return false;
			}
			return true;
		}

		public TextSegment(String tag, String text)
		{
			this.tag = tag;
			this.text = text;
		}

		public TextSegment(String rawText)
		{
			Pattern pattern = Pattern
					.compile("<([^<]+)>([^<]+)</[^<]+>([.\\n]*)");
			Matcher matcher = pattern.matcher(rawText);
			if (matcher.find())
			{
				this.tag = matcher.group(1);
				this.text = matcher.group(2) + matcher.group(3);
			}
			else
			{
				this.tag = null;
				this.text = rawText;
			}
		}
	}

	/**
	 * read txt document, do POS tagging, chunking, parsing
	 * @param txtFile
	 * @throws IOException
	 */
	public void readDoc(File txtFile, boolean monoCase) throws IOException
	{
		// read text from the original data
		List<TextSegment> segments = getSegments(txtFile);

		// do sentence split and tokenization
		Span[] sentSpans = null;
		sentSpans = splitSents(segments);

		// to deal with sentence splitting for 
		// fisher_transcripts in ACE 04
		if (this.docID.contains("fsh_"))
		{
			sentSpans = fixSentSpansForFSH(sentSpans);
		}

		// cure errors in sentence detection with the help of Ace annotation
		for (Span sentSpan : sentSpans)
		{
			// set the offset of sentence as the absolute offset, as consistent with offsets in APF 
			sentSpan.setStart(sentSpan.start() + this.textoffset);
			sentSpan.setEnd(sentSpan.end() + this.textoffset);
		}
		if (hasLabel())
		{
			sentSpans = fixSentBoundaries(sentSpans);
		}

		int sentID = 0;
		for (Span sentSpan : sentSpans)
		{
			String sentText = sentSpan.getCoveredText(allText).toString();
			/* 再分词 */
			util.Span[] tokenSpans = TokenizerWrapper.getTokenizer()
					.tokenizeSpan(sentText);
			for (int idx = 0; idx < tokenSpans.length; idx++)
			{
				// record offset for each token/sentence
				Span tokenSpan = tokenSpans[idx];
				// calculate absolute offset for tokens
				int offset = sentSpan.start();
				int absoluteStart = offset + tokenSpan.start();
				int absoluteEnd = offset + tokenSpan.end();
				Span absoluteTokenSpan = new Span(absoluteStart, absoluteEnd);
				tokenSpans[idx] = absoluteTokenSpan;
			}
			// fix tokenization error, e.g. split anti-war to three words
			tokenSpans = fixTokenBoudaries(tokenSpans, allText);
			tokenSpans = fixTokenBoudariesAggresive(tokenSpans, allText);
			tokenSpans = fixTokenBoudariesAdhoc(tokenSpans, allText);
			Sentence sent = new Sentence(this, sentID++);
			sent.put(Sent_Attribute.TOKEN_SPANS, tokenSpans);

			String[] tokens = new String[tokenSpans.length];
			for (int idx = 0; idx < tokenSpans.length; idx++)
			{
				// get tokens
				Span tokenSpan = tokenSpans[idx];
				tokens[idx] = tokenSpan.getCoveredText(allText).toString();
			}

			sent.put(Sent_Attribute.TOKENS, tokens);
			// save span of the sent
			sent.setExtent(sentSpan);
			List<Map<Class<?>, Object>> tokenFeatureMaps = new ArrayList<Map<Class<?>, Object>>();
			sent.put(Sent_Attribute.Token_FEATURE_MAPs, tokenFeatureMaps);
			this.sentences.add(sent);
		}
	}

	/**
	 * to deal with sentence splitting for   fisher_transcripts in ACE 04
	 * @param sentSpans
	 * @return
	 */
	private Span[] fixSentSpansForFSH(Span[] sentSpans)
	{
		List<Span> ret = new ArrayList<Span>();
		for (Span span : sentSpans)
		{
			List<Integer> breaks = new ArrayList<Integer>();
			String txt = span.getCoveredText(this.allText);
			Pattern p = Pattern.compile("\n(A:|B:)");
			Matcher matcher = p.matcher(txt);
			while (matcher.find())
			{
				int indx = matcher.start(1);
				if (indx > 0)
				{
					breaks.add(indx);
				}
			}
			breaks.add(txt.length());
			int start = 0;
			for (int i = 0; i < breaks.size(); i++)
			{
				Span new_span = new Span(start, breaks.get(i) - 1);
				new_span.setStart(start + span.start());
				new_span.setEnd(breaks.get(i) - 1 + span.start());
				ret.add(new_span);
				start = breaks.get(i);
			}
		}
		return ret.toArray(new Span[ret.size()]);
	}

	private Span[] splitSents(List<TextSegment> segments)
			throws InvalidFormatException, IOException
	{
		final String tagWhiteList = "SUBJECT";

		this.text = "";
		this.allText = "";
		List<Span> ret = new ArrayList<Span>();
		int offset = 0;
		for (TextSegment sgm : segments)
		{
			// ignore the sents that has tags
			if (!sgm.hasTag() || sgm.tag.matches(tagWhiteList))
			{
				Span[] sentSpans = null;
				if (monoCase)
				{
					sentSpans = SentDetectorWrapper.getSentDetector()
							.detectPosMonocase(sgm.text);
				}
				else
				{
					sentSpans = SentDetectorWrapper.getSentDetector()
							.detectPos(sgm.text);
				}
				for (Span sent : sentSpans)
				{
					sent.setStart(sent.start() + offset);
					sent.setEnd(sent.end() + offset);
					ret.add(sent);
				}
			}
			offset += sgm.text.length();
			this.text += sgm.text;
		}

		allText = before_text + text;

		return ret.toArray(new Span[ret.size()]);
	}

	protected List<TextSegment> getSegments(File txtFile) throws IOException
	{
		List<TextSegment> segments = new ArrayList<TextSegment>();
		String[] lines = FileUtils.readFile(txtFile);

		boolean hasText = false;
		for (int i = 0; i < lines.length; i++)
		{
			String line = lines[i];
			if (line.contains("<TEXT>"))
			{
				hasText = true;
				break;
			}
		}

		String line = ""; // buffer for each line
		String buffer = ""; // buffer of segment
		this.headline = "";
		boolean isText = false;
		if (!hasText)
		{
			isText = true;
		}
		boolean isHeadLine = false;
		before_text = "";
		boolean end_of_beforeText = false;
		for (int lineNum = 0; lineNum < lines.length; lineNum++)
		{
			line = lines[lineNum];
			String textline = line.replaceAll("</?[^<]+>", ""); // remove xml markups

			if (!end_of_beforeText)
			{
				before_text += textline + "\n";
			}

			if (line.equals("<HEADLINE>"))
			{
				isHeadLine = true;
				continue;
			}
			else if (line.equals("</HEADLINE>"))
			{
				isHeadLine = false;
				continue;
			}
			else if (line.equals("<TEXT>"))
			{
				isText = true;
				this.textoffset = before_text.length();
				end_of_beforeText = true;
				continue;
			}
			else if (line.equals("</TEXT>"))
			{
				isText = false;
				continue;
			}
			if (isHeadLine)
			{
				headline += line;
			}
			else if (isText)
			{
				line = line + "\n";
				if (line.matches("<([^<]+)>([^<]+)</[^<]+>\\n"))
				{
					if (buffer.length() > 0)
					{
						TextSegment sgm = new TextSegment(null, buffer);
						segments.add(sgm);
						buffer = "";
					}
					TextSegment sgm = new TextSegment(line);
					segments.add(sgm);
				}
				else
				{
					buffer += line;
				}
			}
		}

		if (buffer.length() > 0)
		{
			TextSegment sgm = new TextSegment(null, buffer);
			segments.add(sgm);
		}

		for (TextSegment sgm : segments)
		{
			sgm.text = eraseXML(sgm.text);
		}

		return segments;
	}

	/**
	 * Use ace annotations to fix tokenization boundaris
	 * e.g. if there is a trigger "war", while the token is "post-war", then split it
	 * if there is a "\"headline" in the token, while annotation is "headline", then split it
	 * i.e. align the tokenization with the ace annotation
	 * @param tokenSpans
	 * @return
	 */
	private Span[] fixTokenBoudaries(Span[] tokenSpans, String text)
	{
		List<Span> ret = new ArrayList<Span>();
		for (int i = 0; i < tokenSpans.length; i++)
		{
			Span token = tokenSpans[i];
			boolean flag = false;
			for (AceMention mention : this.aceAnnotations.allMentionsList)
			{
				if (flag)
				{
					break;
				}

				Span extent = null;
				String headText = null;
				if (mention instanceof AceEventMention)
				{
					AceEventMention event = (AceEventMention) mention;
					extent = event.anchorExtent;
					headText = event.anchorText;
				}
				else if (mention instanceof AceEntityMention)
				{
					AceEntityMention entity = (AceEntityMention) mention;
					extent = entity.head;
					headText = entity.head.getCoveredText(text);
				}
				if (extent != null && extent.smallerThan(token))
				{
					String tokenText = token.getCoveredText(text);
					if (tokenText.contains(headText + "-")
							|| tokenText.contains("-" + headText))
					{
						String subTokens[] = tokenText.split("-");
						int start = token.start();
						for (int k = 0; k < subTokens.length; k++)
						{
							String subToken = subTokens[k];
							if (subToken.length() > 0)
							{
								Span token_1 = new Span(start, start
										+ subToken.length() - 1);
								start = token_1.end() + 1;
								ret.add(token_1);
							}
							if (k < subTokens.length - 1)
							{
								ret.add(new Span(start, start));
							}
							start++;
						}
						flag = true;
					}
					else if (tokenText.contains(headText + "/")
							|| tokenText.contains("/" + headText))
					{
						String subTokens[] = tokenText.split("/");
						int start = token.start();
						for (int k = 0; k < subTokens.length; k++)
						{
							String subToken = subTokens[k];
							if (subToken.length() > 0)
							{
								Span token_1 = new Span(start, start
										+ subToken.length() - 1);
								start = token_1.end() + 1;
								ret.add(token_1);
							}
							if (k < subTokens.length - 1)
							{
								ret.add(new Span(start, start));
							}
							start++;
						}
						flag = true;
					}
					else if (!tokenText.equalsIgnoreCase("its"))
					{
						System.err.print("\t" + tokenText + "--->" + headText
								+ "\t");

						// just breakdown the token into 2 or 3 pieces
						Span token_1 = new Span(token.start(),
								extent.start() - 1);
						Span token_2 = new Span(extent.start(), extent.end());
						Span token_3 = new Span(extent.end() + 1, token.end());
						if (token_1.start() <= token_1.end())
						{
							ret.add(token_1);
						}
						if (token_2.start() <= token_2.end())
						{
							ret.add(token_2);
						}
						if (token_3.start() <= token_3.end())
						{
							ret.add(token_3);
						}
						flag = true;
					}
				}
			}
			if (flag == false)
			{
				ret.add(token);
			}

		}

		return ret.toArray(new Span[ret.size()]);
	}

	/**
	 * aggresively check whether there is conflicts between annotation and 
	 * tokenization
	 * @param tokenSpans
	 * @param text
	 * @return
	 */
	private Span[] fixTokenBoudariesAggresive(Span[] tokenSpans, String text)
	{
		List<Span> ret = new ArrayList<Span>();
		for (int i = 0; i < tokenSpans.length; i++)
		{
			Span token = tokenSpans[i];
			boolean flag = false;
			for (AceMention mention : this.aceAnnotations.allMentionsList)
			{
				if (flag)
				{
					break;
				}

				Span extent = null;
				String headText = null;
				if (mention instanceof AceEntityMention)
				{
					AceEntityMention entity = (AceEntityMention) mention;
					extent = entity.head;
					headText = entity.head.getCoveredText(text);
				}
				if (extent != null && extent.overlap(token))
				{
					String tokenText = token.getCoveredText(text);
					if (token.start() < extent.start()
							&& extent.end() >= token.end())
					{
						System.err.print("\t" + tokenText + "--->" + headText
								+ "\t");

						// just breakdown the token into 2 or 3 pieces
						Span token_1 = new Span(token.start(),
								extent.start() - 1);
						Span token_2 = new Span(extent.start(), token.end());

						if (token_1.start() <= token_1.end())
						{
							ret.add(token_1);
						}
						if (token_2.start() <= token_2.end())
						{
							ret.add(token_2);
						}
						flag = true;
					}
					else if (token.start() >= extent.start()
							&& extent.end() < token.end())
					{
						System.err.print("\t" + tokenText + "--->" + headText
								+ "\t");

						// just breakdown the token into 2 or 3 pieces
						Span token_2 = new Span(token.start(), extent.end());
						Span token_3 = new Span(extent.end() + 1, token.end());

						if (token_2.start() <= token_2.end())
						{
							ret.add(token_2);
						}
						if (token_3.start() <= token_3.end())
						{
							ret.add(token_3);
						}
						flag = true;
					}
					else if (token.start() < extent.start()
							&& extent.end() > token.end())
					{
						System.err.print("\t" + tokenText + "--->" + headText
								+ "\t");

						// just breakdown the token into 2 or 3 pieces
						Span token_1 = new Span(token.start(),
								extent.start() - 1);
						Span token_2 = new Span(extent.start(), extent.end());
						Span token_3 = new Span(extent.end() + 1, token.end());

						if (token_1.start() <= token_1.end())
						{
							ret.add(token_1);
						}
						if (token_2.start() <= token_2.end())
						{
							ret.add(token_2);
						}
						if (token_3.start() <= token_3.end())
						{
							ret.add(token_3);
						}
						flag = true;
					}
				}
			}
			if (flag == false)
			{
				ret.add(token);
			}

		}

		return ret.toArray(new Span[ret.size()]);
	}

	/**
	 * specificly fix the following bad tokenizations:
	 * ,an 
	 * ,he 
	 * ,via
	 * @param tokenSpans
	 * @param text
	 * @return
	 */
	private Span[] fixTokenBoudariesAdhoc(Span[] tokenSpans, String text)
	{
		List<Span> ret = new ArrayList<Span>();
		for (int i = 0; i < tokenSpans.length; i++)
		{
			Span token = tokenSpans[i];
			String tokenText = token.getCoveredText(text);
			if (tokenText.matches(",an|,he|,via"))
			{
				// just breakdown the token into 2 tokens
				Span token_1 = new Span(token.start(), token.start());
				Span token_2 = new Span(token.start() + 1, token.end());

				if (token_1.start() <= token_1.end())
				{
					ret.add(token_1);
				}
				if (token_2.start() <= token_2.end())
				{
					ret.add(token_2);
				}
			}
			else
			{
				ret.add(token);
			}

		}
		return ret.toArray(new Span[ret.size()]);
	}

	/**
	 * use Ace annotations to fix sentence boundaries
	 * the idea is: whennever there is an ace entity across two sents, merge the two sents 
	 * @param sentSpans
	 */
	private Span[] fixSentBoundaries(Span[] sentSpans)
	{
		if (sentSpans.length <= 1)
		{
			return sentSpans;
		}
		List<Span> ret = new ArrayList<Span>();
		for (int i = 0; i < sentSpans.length - 1; i++)
		{
			Span sent_before = sentSpans[i];
			Span sent_after = sentSpans[i + 1];
			for (AceMention mention : this.aceAnnotations.allMentionsList)
			{
				Span extent = mention.extent;
				if (extent.overlap(sent_before) && extent.overlap(sent_after))
				{
					// merge two sents
					Span new_sent = new Span(sent_before.start(),
							sent_after.end());
					sentSpans[i + 1] = new_sent;
					sentSpans[i] = null;
					break;
				}
			}
			if (sentSpans[i] != null)
			{
				ret.add(sent_before);
			}
		}
		ret.add(sentSpans[sentSpans.length - 1]);
		return ret.toArray(new Span[ret.size()]);
	}

	public void printDocBasic(PrintStream out)
	{
		out.println(headline);
		out.println("Text offset: " + this.textoffset);

		for (int i = 0; i < this.sentences.size(); i++)
		{
			Sentence sent = this.sentences.get(i);
			out.println("Sent num:\t" + i);
			sent.printBasicSent(out);
		}
	}

	public List<Sentence> getSentences()
	{
		return this.sentences;
	}

	public boolean hasLabel()
	{
		return this.isHasLabel();
	}

	protected void setAceAnnotations(AceDocument aceAnnotations)
	{
		this.aceAnnotations = aceAnnotations;
	}

	public AceDocument getAceAnnotations()
	{
		return aceAnnotations;
	}

	protected void setHasLabel(boolean hasLabel)
	{
		this.hasLabel = hasLabel;
	}

	public boolean isHasLabel()
	{
		return hasLabel;
	}

	/**
	 * get a list of SentenceInstance from this 
	 * @param nodeTargetAlphabet
	 * @param edgeTargetAlphabet
	 * @param featureAlphabet
	 * @param controller
	 * @param b
	 * @return
	 */
	public List<SentenceInstance> getInstanceList(Alphabets alphabets,
			Controller controller, boolean learnable)
	{
		List<SentenceInstance> instancelist = new ArrayList<SentenceInstance>();
		for (int sent_id = 0; sent_id < this.getSentences().size(); sent_id++)
		{
			Sentence sent = this.getSentences().get(sent_id);
			// add all instances
			SentenceInstance inst = new SentenceInstance(sent, alphabets,
					controller, learnable);
			instancelist.add(inst);
		}
		return instancelist;
	}

	static public void main(String[] args) throws IOException
	{
		System.out.println("Default Charset=" + Charset.defaultCharset());
		File txtFile = new File(
				"/Users/qli/projects/joint_relation_expt/ACE04_English/nwire/APW20001006.0338.0184");
		Document doc = new Document(txtFile.getAbsolutePath(), true, false,
				"2004");
		TextFeatureGenerator.doPreprocessCheap(doc);
		doc.printDocBasic(System.out);

		doc.setSentenceClustersByTokens();
		doc.printDocCluster(System.out);
	}

}
