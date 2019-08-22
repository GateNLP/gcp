/*
 *  AnnotationSetDefinition.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: AnnotationSetDefinition.java 13582 2011-03-29 16:23:58Z ian_roberts $ 
 */
package gate.cloud.batch;

import java.util.List;

/**
 * Definition of the annotations to be output from a single annotation
 * set.
 */
public class AnnotationSetDefinition {
  
private String annotationSetName;
  
  private List<String> annotationTypes;

  public AnnotationSetDefinition(String annotationSetName,
          List<String> annotationTypes) {
    this.annotationSetName = annotationSetName;
    this.annotationTypes = annotationTypes;
  }

  /**
   * Gets the name of the annotation set to be used, or <tt>null</tt> for the
   * default annotation set. 
   * @return a {@link String}
   */
  public String getAnnotationSetName() {
    return annotationSetName;
  }

  /**
   * Gets the annotation types to be saved from the given annotation set. An 
   * empty list or <tt>null</tt> means all annotations should be saved.
   * @return a {@link List} of {@link String}s.
   */
  public List<String> getAnnotationTypes() {
    return annotationTypes;
  }
  
  public String toString() {
    StringBuilder res = new StringBuilder();
    res.append("name: ");
    res.append(String.valueOf(annotationSetName));
    res.append("; types: ");
    res.append(String.valueOf(annotationTypes));
    return res.toString();
  }
}