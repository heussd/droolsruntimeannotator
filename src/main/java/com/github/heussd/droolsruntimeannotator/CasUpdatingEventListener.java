package com.github.heussd.droolsruntimeannotator;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.impl.FeatureStructureImpl;
import org.apache.uima.jcas.JCas;
import org.drools.event.rule.ObjectInsertedEvent;
import org.drools.event.rule.ObjectRetractedEvent;
import org.drools.event.rule.ObjectUpdatedEvent;
import org.drools.event.rule.WorkingMemoryEventListener;
import org.drools.factmodel.Fact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This listener updates the given {@link CAS} structure based on events
 * happening in the Drools working memory. Therefore, if a new {@link Fact} is
 * inserted in Drools, this listener tries to add it as {@link FeatureStructure}
 * to the {@link CAS}. Same applies to update or retract actions.
 * <p>
 * Inserted, updated or retracted Facts must be of type
 * {@link FeatureStructureImpl} or a sub-type of it.
 * 
 * @author Timm Heuss
 * 
 */
public class CasUpdatingEventListener implements WorkingMemoryEventListener {

	private final static Logger logger = LoggerFactory.getLogger(DroolsRuntimeAnnotator.class);

	private JCas jCas;

	public CasUpdatingEventListener(JCas jCas) {
		this.jCas = jCas;
	}

	@Override
	public void objectInserted(ObjectInsertedEvent event) {
		logger.debug("New object inserted into knowledge base: {}", event.getObject());

		if (event.getObject() instanceof FeatureStructureImpl) {
			jCas.addFsToIndexes((FeatureStructureImpl) event.getObject());
		} else {
			logger.warn("Cannot add feature structure to CAS: {}", event.getObject());
		}
	}

	@Override
	public void objectUpdated(ObjectUpdatedEvent event) {
		logger.debug("Object updated in knowledge base: {}", event.getOldObject());

		if (event.getOldObject() instanceof FeatureStructureImpl)
			if (event.getObject() instanceof FeatureStructureImpl) {
				jCas.removeFsFromIndexes((FeatureStructureImpl) event.getOldObject());
				jCas.addFsToIndexes((FeatureStructureImpl) event.getObject());
			} else {
				logger.warn("Cannot update feature structure in CAS: {}", event.getObject());
			}
	}

	@Override
	public void objectRetracted(ObjectRetractedEvent event) {
		logger.debug("Object retracted from knowledge base: {}", event.getOldObject());
		if (event.getOldObject() instanceof FeatureStructureImpl) {
			jCas.removeFsFromIndexes((FeatureStructureImpl) event.getOldObject());
		} else {
			logger.warn("Cannot remove feature structure from CAS: {}", event.getOldObject());
		}

	}
}
