package com.github.heussd.droolsruntimeannotator;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
import org.drools.audit.WorkingMemoryFileLogger;
import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.KnowledgeBuilderError;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.ResourceType;
import org.drools.compiler.PackageBuilder;
import org.drools.definition.KnowledgePackage;
import org.drools.io.ResourceFactory;
import org.drools.runtime.StatefulKnowledgeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

/**
 * Establishes a Drools runtime and makes a CAS available within this context.
 * 
 * @author Timm Heuss
 * 
 */
public class DroolsRuntimeAnnotator extends JCasAnnotator_ImplBase {

	/**
	 * Specifies Drools resources to be loaded
	 */
	public static final String PARAM_DROOLS_RESOURCES = "Drools Resource";
	@ConfigurationParameter(name = PARAM_DROOLS_RESOURCES, mandatory = true)
	private String droolsResource;

	public static final String PARAM_DROOLS_LOG_FILE = "Drools Execution Log";
	@ConfigurationParameter(name = PARAM_DROOLS_LOG_FILE, mandatory = false, defaultValue = "")
	private String droolsLogFile;

	private KnowledgeBase productionMemory;// = RuleBaseFactory.newRuleBase();

	// ############################################

	private final static Logger logger = LoggerFactory.getLogger(DroolsRuntimeAnnotator.class);

	/**
	 * Filename filter for Drools rule files
	 */
	public static final FileFilter DROOLS_FILE_FILTER = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			return pathname.toString().endsWith(".drl");
		}
	};

	/**
	 * Finds drools resources at the given path and adds them into the
	 * {@link PackageBuilder}. Use this method as initializer for unit testing.
	 */
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		final KnowledgeBuilder knowledgeBuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
		knowledgeBuilder.add(ResourceFactory.newClassPathResource(droolsResource), ResourceType.CHANGE_SET);

		// Check for resource errors
		if (knowledgeBuilder.hasErrors()) {
			for (KnowledgeBuilderError packageBuilderError : knowledgeBuilder.getErrors()) {
				logger.error("{}", packageBuilderError.toString());
			}
			throw new ResourceInitializationException("Drools resources have errors", null);
		}

		final Collection<KnowledgePackage> knowledgePackages = knowledgeBuilder.getKnowledgePackages();
		productionMemory = KnowledgeBaseFactory.newKnowledgeBase();
		productionMemory.addKnowledgePackages(knowledgePackages);

		// Prepare log capacities, as the drools logger does not like it when
		// the log destination does not exist
		if (!droolsLogFile.equals(""))
			try {
				FileUtils.forceMkdir(new File(this.droolsLogFile).getParentFile());
			} catch (NullPointerException e) {
				// forceMkdir() throws NPE when the parent does not exist (so it
				// has nothing to do) - we ignore that.
			} catch (IOException e) {
				throw new ResourceInitializationException(e);
			}

	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		StatefulKnowledgeSession session = productionMemory.newStatefulKnowledgeSession();
		WorkingMemoryFileLogger workingMemoryFileLogger = null;

		if (!droolsLogFile.equals("")) {
			workingMemoryFileLogger = new WorkingMemoryFileLogger(session);
			workingMemoryFileLogger.setFileName(this.droolsLogFile);
		}

		AnnotationIndex<Annotation> annotationIndex = jCas.getAnnotationIndex();
		FSIterator<Annotation> iterator = annotationIndex.iterator();

		// Recursively process FeatureStructures
		while (iterator.hasNext())
			passFeatureStructures(session, (FeatureStructure) iterator.next());

		// Register an updating event listener that changes the CAS index when
		// inserting, updating or retracting facts from the working memory
		session.addEventListener(new CasUpdatingEventListener(jCas));

		logger.info("Firing rules now!");
		session.fireAllRules();

		logger.info("There are {} facts.", session.getFactCount());

		if (workingMemoryFileLogger != null)
			workingMemoryFileLogger.writeToDisk();

		session.dispose();
	}

	/**
	 * This method recursively passes {@link FeatureStructure} objects and all
	 * contained nested {@link FeatureStructure}s as facts in the given Drools
	 * session.
	 * 
	 * @param session
	 *            Session where {@link FeatureStructure}s should be inserted in.
	 * @param featureStructure
	 *            Base {@link FeatureStructure} to iterate recursively.
	 */
	private void passFeatureStructures(StatefulKnowledgeSession session, FeatureStructure featureStructure) {

		if (featureStructure == null)
			return;

		// It should not be possible to pass primitive FeatureStructures,
		// however, we make sure that none of them will pass this point
		if (featureStructure.getType().isPrimitive())
			return;

		logger.debug("Inserting {}", featureStructure.getType());

		Long countBeforeInsertion = session.getFactCount();
		session.insert(featureStructure);

		// Cancel further deep CAS inspection if the number of facts did not
		// change with the last fact insertion
		if (countBeforeInsertion == session.getFactCount())
			return;

		for (Feature feature : featureStructure.getType().getFeatures()) {

			// logger.info("Encountered {}feature {}",
			// (feature.getRange().isPrimitive() ? " (primitive) " :
			// " (non-primitive) "), feature);

			// Recursive call for the FeatureStructure if the feature is not
			// primitive
			if (!feature.getRange().isPrimitive()) {
				FeatureStructure nestedFeatureStructure = featureStructure.getFeatureValue(feature);

				// Nested FeatureStructures might be of an array type.
				// Those types require special handling, checking and passing.
				if (nestedFeatureStructure instanceof FSArray) {
					FSArray fsArray = (FSArray) nestedFeatureStructure;

					// Recursively fire calls for each array-contained, nested
					// FeatureStructure
					for (int i = 0; i < fsArray.size(); i++) {
						if (!fsArray.get(i).getType().isPrimitive()) {
							passFeatureStructures(session, fsArray.get(i));
						}
					}
				}

				// fire recursive call for the nested FeatureStructure
				passFeatureStructures(session, featureStructure.getFeatureValue(feature));
			}
		}

	}

}
