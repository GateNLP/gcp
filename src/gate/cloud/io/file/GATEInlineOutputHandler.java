/*
 *  GATEInlineOutputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: GATEInlineOutputHandler.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io.file;

import gate.Annotation;
import gate.Document;
import gate.cloud.batch.DocumentID;
import gate.util.Benchmark;
import gate.util.GateException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GATEInlineOutputHandler extends AbstractFileOutputHandler {
  
  public static final String PARAM_INCLUDE_FEATURES = "includeFeatures";
  
  protected boolean includeFeatures;
  
  protected void outputDocumentImpl(Document document, DocumentID documentId) throws IOException,
          GateException {
    //get a Benchmark ID
    String baseBenchmarkID = Benchmark.createBenchmarkId("saveInline", 
            document.getName());
    Map<String, Collection<Annotation>> anns = collectAnnotations(document);
    
    long startTime = Benchmark.startPoint();
    //use the default extension
    OutputStream outputStream = getFileOutputStream(documentId);
    try {
      OutputStreamWriter writer = new OutputStreamWriter(outputStream,
              (encoding == null || encoding.length() == 0 ? "UTF-8" : encoding));
      try {
        Set<Annotation> annotationsToSave = new HashSet<Annotation>();
        for(Collection<Annotation> someAnnots : anns.values()) {
          annotationsToSave.addAll(someAnnots);
        }
        writer.write(document.toXml(annotationsToSave, includeFeatures));
      }
      finally {
        writer.close();
      }
    }
    finally {
      // saveContent does not close the stream
      outputStream.close();
    }
    Benchmark.checkPoint(startTime, baseBenchmarkID, this, null);
  }

  @Override
  protected void configImpl(Map<String, String> configData) throws IOException,
          GateException {
    super.configImpl(configData);
    includeFeatures = booleanValueOf(configData.get(PARAM_INCLUDE_FEATURES));
  }
}
