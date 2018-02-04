/*
 *  ArchiveInputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: ARCInputHandler.java 17349 2014-02-19 18:02:24Z ian_roberts $ 
 */
package gate.cloud.io.arc;

import static gate.cloud.io.IOConstants.PARAM_ARC_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_SOURCE_FILE_LOCATION;
import gate.util.GateException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.httpclient.Header;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.arc.ARCReader;
import org.archive.io.arc.ARCReaderFactory;
import org.archive.io.arc.ARCRecord;

/**
 * Input handler for ARC format archives.
 */
public class ARCInputHandler extends ArchiveInputHandler {

  @SuppressWarnings("deprecation")
  @Override
  public void config(Map<String, String> configData) throws IOException,
          GateException {
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
  
  protected ArchiveRecord getRecord(ArchiveReader reader, long offset) throws IOException {
    return reader.get(offset); 
  }

  protected ArchiveRecord archiveRecordFromByteArray(byte[] data, String url) throws IOException {
    return new ARCRecord(new ByteArrayInputStream(data), url, 0, false, false, true);
  }
  
  protected Header[] httpHeaders(ArchiveRecord record) {
    return ((ARCRecord)record).getHttpHeaders();
  }
}
