/*
 *  PlainTextOutputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: PlainTextOutputHandler.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io.file;

import static gate.cloud.io.IOConstants.PARAM_FILE_EXTENSION;
import gate.Document;
import gate.cloud.batch.DocumentID;
import gate.corpora.DocumentStaxUtils;
import gate.util.Benchmark;
import gate.util.GateException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class PlainTextOutputHandler extends AbstractFileOutputHandler {

  @Override
  protected void configImpl(Map<String, String> configData) throws IOException,
          GateException {
    // make sure we default to .txt as the extension
    if(!configData.containsKey(PARAM_FILE_EXTENSION)) {
      configData.put(PARAM_FILE_EXTENSION, ".txt");
    }
    super.configImpl(configData);
  }

  protected void outputDocumentImpl(Document document, DocumentID documentId) 
      throws IOException, GateException {
    //get a Benchmark ID
    String saveContentBID = Benchmark.createBenchmarkId("saveText", 
            document.getName());
    long startTime = Benchmark.startPoint();
    OutputStream outputStream = getFileOutputStream(documentId);
    try {
      DocumentStaxUtils.writeXcesContent(document, outputStream, 
             (encoding == null || encoding.length() == 0 ? "UTF-8" : encoding));
    }
    finally {
      // saveContent does not close the stream
      outputStream.close();
    }
    Benchmark.checkPoint(startTime, saveContentBID, this, null);
  }
}
