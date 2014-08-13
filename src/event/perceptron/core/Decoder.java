package event.perceptron.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.DocumentException;

import commons.Alphabets;
import commons.Document;
import commons.TextFeatureGenerator;

import classifiers.perceptron.AbstractAssignment;
import classifiers.perceptron.AbstractInstance;
import classifiers.perceptron.Perceptron;

import ace.acetypes.AceDocument;
import ace.acetypes.AceEntity;
import ace.acetypes.AceEvent;
import ace.acetypes.AceRelation;
import ace.acetypes.AceTimex;
import ace.acetypes.AceValue;
import ace.acetypes.EventScorer;

import event.types.SentenceAssignment;
import event.types.SentenceInstance;

public class Decoder
{
	static public void writeEntities (PrintWriter w, AceDocument aceDoc, List<AceEvent> events) {
		w.println ("<?xml version=\"1.0\"?>");
		w.println ("<!DOCTYPE source_file SYSTEM \"apf.v5.1.1.dtd\">");
		w.print   ("<source_file URI=\"" + aceDoc.sourceFile + "\"");
		w.println (" SOURCE=\"" + aceDoc.sourceType + "\" TYPE=\"text\">");
		w.println ("<document DOCID=\"" + aceDoc.docID + "\">");
		for (int i=0; i<aceDoc.entities.size(); i++) {
			AceEntity entity = (AceEntity) aceDoc.entities.get(i);
			entity.write(w);
		}
		for (int i=0; i<aceDoc.values.size(); i++) {
			AceValue value = (AceValue) aceDoc.values.get(i);
			value.write(w);
		}
		for (int i=0; i<aceDoc.timeExpressions.size(); i++) {
			AceTimex timex = (AceTimex) aceDoc.timeExpressions.get(i);
			timex.write(w);
		}
		for (int i=0; i<aceDoc.relations.size(); i++) {
			AceRelation relation = (AceRelation) aceDoc.relations.get(i);
			relation.write(w);
		}
		for (int i=0; i<events.size(); i++) {
			AceEvent event = (AceEvent) events.get(i);
			event.write(w);
		}
		w.println ("</document>");
		w.println ("</source_file>");
		w.close();
	}
	
	static public void main(String[] args) throws IOException, DocumentException
	{
		if(args.length < 3)
		{
			System.out.println("Usage:");
			System.out.println("args[0]: model");
			System.out.println("args[1]: src dir");
			System.out.println("args[2]: file list");
			System.out.println("args[3]: output dir");
			System.exit(-1);
		}
		
		// Perceptron read model from the serialized file
		Perceptron perceptron = Perceptron.deserializeObject(new File(args[0]));
		Alphabets alphabets = new Alphabets();
		
		File srcDir = new File(args[1]);
		File fileList = new File(args[2]);
		File outDir = new File(args[3]);
		if(!outDir.exists())
		{
			outDir.mkdirs();
		}
		
		BufferedReader reader = new BufferedReader(new FileReader(fileList));
		String line = "";
		TextFeatureGenerator featGen = new TextFeatureGenerator();
		while((line = reader.readLine()) != null)
		{
			List<SentenceInstance> localInstanceList = null;
			boolean monoCase = line.contains("bn/") ? true : false;
			String fileName = srcDir + File.separator + line;
			System.out.println(fileName);
			Document doc = null;
			if(perceptron.controller.crossSent)
			{
				;	
			}
			else
			{
				doc = new Document(fileName, true, monoCase);
				// fill in text feature vector for each token
				featGen.fillTextFeatures(doc);
			}
			localInstanceList = doc.getInstanceList(alphabets, 
					perceptron.controller, true);
			
			// docoding
			List<AbstractAssignment> localResults = perceptron.decoding(localInstanceList);
			
			// print to docs
			File outputFile = new File(outDir + File.separator + line);
			if(!outputFile.getParentFile().exists())
			{
				outputFile.getParentFile().mkdirs();
			}
			String docID = doc.docID.substring(doc.docID.lastIndexOf(File.separator) + 1);
			String id_prefix = docID + "-" + "EV";
			PrintWriter out = new PrintWriter(outputFile);
			
			// output entities and predicted events from doc
			List<AceEvent> eventsInDoc = new ArrayList<AceEvent>();
			List<? extends AbstractInstance> canonicalList = perceptron.getCanonicalInstanceList(localInstanceList);
			for(int inst_id=0; inst_id < canonicalList.size(); inst_id++)
			{
				SentenceAssignment assn = (SentenceAssignment) localResults.get(inst_id);
				SentenceInstance inst = (SentenceInstance) canonicalList.get(inst_id);
				String id = id_prefix + inst_id;
				// each event only contains one single event mention
				List<AceEvent> events = inst.getEvents(assn, id, doc.allText);
				eventsInDoc.addAll(events);
			}
			writeEntities(out, doc.getAceAnnotations(), eventsInDoc);
			out.close();
		}
		reader.close();
		
		// get score
		File outputFile = new File(outDir + File.separator + "Score");
		EventScorer.main(new String[]{args[1], args[3], args[2], outputFile.getAbsolutePath()});
	}
}
