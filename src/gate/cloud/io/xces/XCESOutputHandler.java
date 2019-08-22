/*
 *  XCESOutputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: XCESOutputHandler.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io.xces;

import gate.Annotation;
import gate.Document;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.file.AbstractFileOutputHandler;
import gate.corpora.DocumentStaxUtils;
import gate.util.Benchmark;
import gate.util.GateException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

public class XCESOutputHandler extends AbstractFileOutputHandler {  
  
  protected void outputDocumentImpl(Document document, DocumentID documentId) 
      throws IOException, GateException {
    //get a Benchmark ID
    String baseBenchmarkID = Benchmark.createBenchmarkId("saveStandoff", 
            document.getName());
    Map<String, Collection<Annotation>> anns = collectAnnotations(document);
    
    long startTime = Benchmark.startPoint();
    //use the default extension
    OutputStream outputStream = getFileOutputStream(documentId);
    try {
      Collection<Annotation> annotationsToSave = new ArrayList<Annotation>();
      for(Collection<Annotation> someAnnots : anns.values()) {
        annotationsToSave.addAll(someAnnots);
      }
      DocumentStaxUtils.writeXcesAnnotations(annotationsToSave, outputStream, 
              ((encoding == null || encoding.length() == 0) ? "UTF-8" : encoding));
    } catch(XMLStreamException e) {
      throw (IOException)new IOException(
          "Error writing XCES annotations!").initCause(e);
    }
    finally {
      // saveContent does not close the stream
      outputStream.close();
    }
    Benchmark.checkPoint(startTime, baseBenchmarkID, this, null);
  }
}
