/*
 *  ARCDocumentEnumerator.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: ARCDocumentEnumerator.java 17349 2014-02-19 18:02:24Z ian_roberts $ 
 */
package gate.cloud.io.arc;

import static gate.cloud.io.IOConstants.PARAM_ARC_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_SOURCE_FILE_LOCATION;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.arc.ARCReader;
import org.archive.io.arc.ARCReaderFactory;
import org.archive.io.arc.ARCRecord;

/**
 * Enumerator to build a list of document IDs from an ARC file. The ARC
 * entries to be considered may be constrained by MIME type or HTTP
 * status code. The IDs returned consist of the index of the entry in
 * the archive, followed by an underscore, followed by the original URL
 * of the document. This is suitable for use with the ARC input handler,
 * which references documents by their index into the archive.
 */
public class ARCDocumentEnumerator extends ArchiveDocumentEnumerator {

  @SuppressWarnings("deprecation")
  public void config(Map<String, String> configData) {
    // Backwards compatibility
    if(configData.containsKey(PARAM_ARC_FILE_LOCATION)
            && !configData.containsKey(PARAM_SOURCE_FILE_LOCATION)) {
      configData.put(PARAM_SOURCE_FILE_LOCATION,
              configData.get(PARAM_ARC_FILE_LOCATION));
    }
    super.config(configData);
  }

  protected ArchiveReader createReader() throws IOException {
    ARCReader r = ARCReaderFactory.get(srcFile);
    r.setParseHttpHeaders(true);
    return r;
  }

  protected ArchiveRecord nextRecord(Iterator<ArchiveRecord> it) {
    return it.next();
  }

  protected long findContentBegin(ArchiveRecord record) {
    return ((ARCRecord)record).getMetaData().getContentBegin();
  }

  protected String statusCode(ArchiveRecord record) {
    return ((ARCRecord)record).getMetaData().getStatusCode();
  }

  protected String mimeType(ArchiveRecord record) {
    return ((ARCRecord)record).getMetaData().getMimetype();
  }
}
