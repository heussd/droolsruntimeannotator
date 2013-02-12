package com.github.heussd.droolsruntimeannotator;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.collection.CollectionReader;
import org.junit.Before;
import org.junit.Test;
import org.uimafit.factory.AnalysisEngineFactory;
import org.uimafit.factory.CollectionReaderFactory;
import org.uimafit.pipeline.SimplePipeline;

import de.tudarmstadt.ukp.dkpro.core.io.text.TextReader;
import de.tudarmstadt.ukp.dkpro.core.tokit.BreakIteratorSegmenter;

public class DemonstrateDroolsActions {

	private CollectionReader reader = null;
	private AnalysisEngine tokenizer = null;
	private AnalysisEngine drools = null;

	@Before
	public void setUp() throws Exception {

		// This demo is partially taken from
		// http://code.google.com/p/dkpro-core-asl/

		reader = CollectionReaderFactory.createCollectionReader(TextReader.class, TextReader.PARAM_PATH, "src/test/resources/text", TextReader.PARAM_PATTERNS,
				new String[] { "[+]*.txt", "[-]broken.txt" }, TextReader.PARAM_LANGUAGE, "en");

		tokenizer = AnalysisEngineFactory.createPrimitive(BreakIteratorSegmenter.class);

		boolean writeLog = true;
		drools = AnalysisEngineFactory.createPrimitive(DroolsRuntimeAnnotator.class, DroolsRuntimeAnnotator.PARAM_DROOLS_RESOURCES, "drools_rules.xml",
				DroolsRuntimeAnnotator.PARAM_DROOLS_LOG_FILE, (writeLog ? "drools" : ""));

	}

	@Test
	public void testDemo() throws Exception {
		SimplePipeline.runPipeline(reader, tokenizer, drools);
	}
}
