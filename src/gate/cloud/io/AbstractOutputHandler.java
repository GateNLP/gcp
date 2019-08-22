/*
 *  AbstractOutputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: AbstractOutputHandler.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io;


import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.cloud.batch.AnnotationSetDefinition;
import gate.cloud.batch.DocumentID;
import gate.util.GateException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for output handlers.
 *
 */
public abstract class AbstractOutputHandler implements OutputHandler {
  
  protected List<AnnotationSetDefinition> annSetDefinitions;

  protected String conditionalSaveFeatureName;
  
  public List<AnnotationSetDefinition> getAnnSetDefinitions() {
    return annSetDefinitions;
  }

  public void setAnnSetDefinitions(List<AnnotationSetDefinition> annSetDefinitions) {
    this.annSetDefinitions = annSetDefinitions;
  }
  
  /* (non-Javadoc)
   * @see gate.cloud.io.OutputHandler#config(java.util.Map)
   */
  public final void config(Map<String, String> configData) throws IOException,
    GateException {
    conditionalSaveFeatureName = configData.get(
      IOConstants.PARAM_CONDITIONAL_SAVE_FEATURE_NAME);
    if(conditionalSaveFeatureName != null) {
      conditionalSaveFeatureName = conditionalSaveFeatureName.trim();
      if(conditionalSaveFeatureName.length() == 0){
        conditionalSaveFeatureName = null;
      }
    }
    configImpl(configData);
  }

  /**
   * Abstract method that subclasses must implement to provide the function of
   * {@link OutputHandler#config(Map)}. 
   * @param configData the {@link Map} containing all the configuration 
   * parameters.
   * @throws IOException
   * @throws GateException
   */
  protected abstract void configImpl(Map<String, String> configData) throws IOException,
    GateException;
  
  
  /**
   * The implementation for this method is empty. Any subclasses that require
   * certain functionality on close, should override the method.
   */
  public void close() throws IOException, GateException {
    // abstract implementation does nothing.
  }
  
  /**
   * The implementation for this method is empty. Any subclasses that require
   * certain functionality on init, should override the method.
   */
  public void init()  throws IOException, GateException {
    // TODO Auto-generated method stub
  }

  protected Map<String, Collection<Annotation>> collectAnnotations(Document document) {
    // build the map of annotations that we are interested in
    Map<String, Collection<Annotation>> annotationSetsMap =
      new HashMap<String, Collection<Annotation>>();

    if(annSetDefinitions != null && annSetDefinitions.size() > 0) {
      for(AnnotationSetDefinition asDef : annSetDefinitions) {
        AnnotationSet annotationSet = document.getAnnotations(
                asDef.getAnnotationSetName());
        // restrict to specified types, if necessary
        if(asDef.getAnnotationTypes() != null && 
           !asDef.getAnnotationTypes().isEmpty()) {
          Set<String> types = new HashSet<String>(asDef.getAnnotationTypes());
          annotationSet = annotationSet.get(types);
        }
        // map "" name to null
        annotationSetsMap.put(("".equals(asDef.getAnnotationSetName())
                ? null : asDef.getAnnotationSetName()), annotationSet);
      }
    } else {
      annotationSetsMap.put(null, document.getAnnotations());
      if(document.getNamedAnnotationSets() != null) {
        annotationSetsMap.putAll(document.getNamedAnnotationSets());
      }
    }
    return annotationSetsMap;
  }
  
  /**
   * Convenience method for processing boolean-valued configuration parameters.
   * 
   * @param value the parameter value
   * @return true iff the value is non-null and equal to one of "yes", "true"
   *         or "on" (case- and whitespace-insensitive).
   */
  protected static boolean booleanValueOf(String value) {
    if(value == null) return false;
    value = value.trim().toLowerCase();
    return (value.equals("true")||  value.equals("yes") || value.equals("on"));
  }

  /**
   * Implementation of the 
   * {@link gate.cloud.io.OutputHandler#outputDocument(gate.Document, java.lang.String)}
   * method that checks whether conditional output is enabled (see 
   * {@link IOConstants#PARAM_CONDITIONAL_SAVE_FEATURE_NAME}). If the save is
   * non conditional, or the condition is met, then it delegates the actual 
   * save to the abstract {@link #outputDocumentImpl(Document, String)}
   * method, which subclasses must implement.
   * @see gate.cloud.io.OutputHandler#outputDocument(gate.Document, java.lang.String)
   * 
   * @param document the document being sent for output.
   * @param documentId the ID of the document being output.
   */
  public final void outputDocument(Document document, DocumentID documentId)
    throws IOException, GateException {
    if(conditionalSaveFeatureName != null) {
      Object featureValue = document.getFeatures().get(conditionalSaveFeatureName);
      if(featureValue == null) return;
      if(!booleanValueOf(featureValue.toString())) return;
    }
    outputDocumentImpl(document, documentId);
  }

  /**
   * Abstract method that subclasses must implement to provide the function of
   * {@link OutputHandler#outputDocument(Document, String)}. 
   * @param document the document being saved
   * @param documentId the ID for the saved document
   * @throws IOException
   * @throws GateException
   */
  protected abstract void outputDocumentImpl(Document document, 
      DocumentID documentId) throws IOException, GateException;
  
}
