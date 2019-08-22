/*
 *  SuccessResult.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: SuccessResult.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.batch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gate.AnnotationSet;
import gate.Document;
import gate.cloud.io.DocumentData;
import gate.cloud.io.OutputHandler;

class SuccessResult implements ProcessResult {

  private DocumentID documentId;

  private long executionTime;

  private long fileSize;
  
  private long docLength;
  
  private Map<String, Integer> annotationCounts;

  SuccessResult(DocumentData docData, List<OutputHandler> outputDefs) {
    this.documentId = docData.id;
    this.executionTime = docData.processingTime;
    this.fileSize = docData.fileSize;
    this.docLength = docData.documentLength;
    generateStatistics(docData.document, outputDefs);
  }

  /**
   * Generate statistics about the processed document based on the
   * output definitions. For an output definition that does not specify
   * types, put an entry in the map whose key is the annotation set name
   * and whose value is the number of annotations in that set. For an
   * output definition that does specify types, put one entry for each
   * type, of the form "annotationSetName:type", with the number of
   * annotations of that type as its value.
   */
  private void generateStatistics(Document doc,
          List<OutputHandler> outputHandlers) {
    annotationCounts = new HashMap<String, Integer>();
    for(OutputHandler output : outputHandlers) {
      if(output != null && output.getAnnSetDefinitions() != null) {
        for(AnnotationSetDefinition asDef : output.getAnnSetDefinitions()) {
          AnnotationSet annots =
                  getAnnotationSet(doc, asDef.getAnnotationSetName());
          String asName = asDef.getAnnotationSetName();
          // use empty string rather than null for the default set
          if(asName == null) {
            asName = "";
          }
          if(asDef.getAnnotationTypes() == null
                  || asDef.getAnnotationTypes().isEmpty()) {
            annotationCounts.put(asName, annots.size());
          }
          else {
            for(String annotationType : asDef.getAnnotationTypes()) {
              annotationCounts.put(asName + ":" + annotationType,
                      annots.get(annotationType).size());
            }
          }
        }
      }
    }
  }

  /**
   * Get an annotation set from a document, treating a <code>null</code>
   * or empty name as a request for the default annotation set.
   */
  private AnnotationSet getAnnotationSet(Document doc, String annotationSetName) {
    if(annotationSetName == null || "".equals(annotationSetName)) {
      return doc.getAnnotations();
    }
    else {
      return doc.getAnnotations(annotationSetName);
    }
  }

  public Map<String, Integer> getAnnotationCounts() {
    return annotationCounts;
  }

  public DocumentID getDocumentId() {
    return documentId;
  }

  public long getExecutionTime() {
    return executionTime;
  }
  

  public long getOriginalFileSize() {
    return fileSize;
  }

  public long getDocumentLength() {
    return docLength;
  }

  public ReturnCode getReturnCode() {
    return ReturnCode.SUCCESS;
  }

  public String getErrorDescription() {
    return null;
  }

}
