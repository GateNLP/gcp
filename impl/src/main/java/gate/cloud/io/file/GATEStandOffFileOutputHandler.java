/*
 *  GATEStandOffFileOutputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: GATEStandOffFileOutputHandler.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io.file;

import static gate.cloud.io.IOConstants.PARAM_FILE_EXTENSION;
import gate.Annotation;
import gate.Document;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.OutputHandler;
import gate.corpora.DocumentStaxUtils;
import gate.util.Benchmark;
import gate.util.GateException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * An {@link OutputHandler} that writes to files, in GATE Stand-off format.
 */
public class GATEStandOffFileOutputHandler extends AbstractFileOutputHandler {
  
  private static final XMLOutputFactory staxFactory =
    XMLOutputFactory.newInstance();
  
  @Override
  protected void configImpl(Map<String, String> configData) throws IOException,
          GateException {
    // make sure we default to .txt as the extension
    if(!configData.containsKey(PARAM_FILE_EXTENSION)) {
      configData.put(PARAM_FILE_EXTENSION, ".xml");
    }
    super.configImpl(configData);
  }

  protected void outputDocumentImpl(Document document, DocumentID documentId)
          throws IOException, GateException {
    Map<String, Collection<Annotation>> annotationSetsMap =
            collectAnnotations(document);
    // use the default extension
    OutputStream outputStream = getFileOutputStream(documentId); 
    try {
      // determine the correct encoding to use
      String encodingValue = encoding;
      if(encodingValue == null || encodingValue.length() == 0) {
        encodingValue = "UTF-8";
      }
      // start the document and write the XML decl
      XMLStreamWriter xsw = staxFactory.createXMLStreamWriter(outputStream,
              encodingValue);
      xsw.writeStartDocument(encodingValue, "1.0");
      xsw.writeCharacters("\n");
      
      String saveStandoffBID =
              Benchmark.createBenchmarkId("saveGATEStandoff", document.getName());
      long startTime = Benchmark.startPoint();
      DocumentStaxUtils.writeDocument(document, annotationSetsMap, xsw, "");
      Benchmark.checkPoint(startTime, saveStandoffBID, this, null);
      xsw.close();
    }
    catch(XMLStreamException e) {
      throw (IOException)new IOException("Error writing GATE standoff XML")
              .initCause(e);
    }
    finally {
      // closing the XSW doesn't close the stream (by design)
      outputStream.close();
    }
  }
}
