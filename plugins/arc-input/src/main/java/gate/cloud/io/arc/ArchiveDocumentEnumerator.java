/*
 *  ArchiveDocumentEnumerator.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: ArchiveDocumentEnumerator.java 20268 2017-09-30 22:17:03Z ian_roberts $
 */
package gate.cloud.io.arc;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_SOURCE_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_EXCLUDE_STATUS_CODES;
import static gate.cloud.io.IOConstants.PARAM_INCLUDE_STATUS_CODES;
import static gate.cloud.io.IOConstants.PARAM_MIME_TYPES;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import gate.cloud.util.SimpleArrayMap;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.DocumentEnumerator;
import gate.util.GateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enumerator to build a list of document IDs from an ARC or WARC file. The
 * entries to be considered may be constrained by MIME type or HTTP
 * status code. The IDs returned match the format expected by the
 * corresponding input handler and naming strategy.
 */
public abstract class ArchiveDocumentEnumerator implements DocumentEnumerator {

  private static Logger logger = LoggerFactory.getLogger(ArchiveDocumentEnumerator.class);

  /**
   * By default, exclude any 3xx, 4xx or 5xx status codes.
   */
  public static final String DEFAULT_EXCLUDE_STATUS_CODES = "[345].*";
  /**
   * The mime types we are interested in. If not specified, all files
   * are considered.
   */
  protected String[] mimeTypes;
  /**
   * The directory containing the batch specification file, or
   * <code>null</code> if the batch specification did not come from a
   * file.
   */
  protected File batchDir;

  /**
   * Regular expression matching status codes that should be included.
   */
  protected Pattern includeStatusCodes;

  /**
   * Regular expression matching status codes that should be excluded.
   */
  protected Pattern excludeStatusCodes;

  /**
   * The next ID (if any) to be returned from the iterator.
   */
  protected DocumentID next;

  /**
   * The source archive file we are enumerating.
   */
  protected File srcFile;

  /**
   * Archive file reader used to scan the archive.
   */
  protected ArchiveReader reader;

  /**
   * Iterator obtained from the ARC reader.
   */
  protected Iterator<ArchiveRecord> archiveIterator;

  /**
   * Index into the archive of the current record.
   */
  protected int inputSequence;

  /**
   * DecimalFormat used to pad sequence numbers to at least 6 digits.
   */
  protected DecimalFormat numberPaddingFormat;

  public void config(Map<String, String> configData) {
    String batchFileStr = configData.get(PARAM_BATCH_FILE_LOCATION);
    if(batchFileStr != null) {
      batchDir = new File(batchFileStr).getParentFile();
    }
    // WARC file
    String srcFileStr = configData.get(PARAM_SOURCE_FILE_LOCATION);
    if(srcFileStr == null || srcFileStr.trim().length() == 0) {
      throw new IllegalArgumentException(
              "No value was provided for the required parameter \""
                      + PARAM_SOURCE_FILE_LOCATION + "\"!");
    }
    srcFile = new File(srcFileStr);
    if(!srcFile.isAbsolute()) {
      srcFile = new File(batchDir, srcFileStr);
    }
    if(!srcFile.exists()) {
      throw new IllegalArgumentException("File \"" + srcFile
              + "\", provided as value for required parameter \""
              + PARAM_SOURCE_FILE_LOCATION + "\", does not exist!");
    }
    if(!srcFile.isFile()) {
      throw new IllegalArgumentException("File \"" + srcFile
              + "\", provided as value for required parameter \""
              + PARAM_SOURCE_FILE_LOCATION + "\", is not a file!");
    }

    // mime types
    String mimeTypesStr = configData.get(PARAM_MIME_TYPES);
    if(mimeTypesStr != null && mimeTypesStr.length() > 0) {
      mimeTypes = mimeTypesStr.split("\\s+");
    }

    // status codes
    String includeStatusCodesStr = configData.get(PARAM_INCLUDE_STATUS_CODES);
    if(includeStatusCodesStr != null && includeStatusCodesStr.length() > 0) {
      includeStatusCodes = Pattern.compile(includeStatusCodesStr);
    }
    String excludeStatusCodesStr = configData.get(PARAM_EXCLUDE_STATUS_CODES);
    if(excludeStatusCodesStr == null) {
      if(includeStatusCodes == null) {
        // only apply default excludes if no includes have been
        // specified either.
        excludeStatusCodes = Pattern.compile(DEFAULT_EXCLUDE_STATUS_CODES);
      }
    } else if(excludeStatusCodesStr.length() > 0) {
      excludeStatusCodes = Pattern.compile(excludeStatusCodesStr);
    }

  }

  public void init() throws IOException, GateException {
    numberPaddingFormat = new DecimalFormat("000000");
    logger.debug("Enumerating file {}", srcFile.getAbsolutePath());
    reader = createReader();
    archiveIterator = reader.iterator();
    inputSequence = 0;
    // skip the 0th entry (archive header)
    if(archiveIterator.hasNext()) {
      archiveIterator.next();
      inputSequence++;
      moveToNext();
    }
    else {
      logger.warn("No entries in archive");
      reader.close();
    }
  }

  protected abstract ArchiveReader createReader() throws IOException;

  public boolean hasNext() {
    return (next != null);
  }

  public DocumentID next() {
    DocumentID lastNext = next;
    moveToNext();
    return lastNext;
  }

  public void remove() {
    throw new UnsupportedOperationException(this.getClass().getName()
            + " does not support element removal");
  }

  protected void moveToNext() {
    logger.debug("moveToNext: archiveIterator = {}", archiveIterator);
    while(archiveIterator != null && archiveIterator.hasNext()) {
      try {
        ArchiveRecord record = nextRecord(archiveIterator);
        if(record == null) {
          logger.debug("Got a null record from iterator");
          // skip this record
          continue;
        }
        long recordContentBegin = findContentBegin(record);
        long recordLength = record.getHeader().getLength();
        int recordBodyLength = (int)(recordLength - recordContentBegin);
        // ignore zero-length records
        logger.debug("Found archive record total length: {}, content begin: {}, body length: {}", recordLength, recordContentBegin, recordBodyLength);
        if(recordBodyLength > 0) {
          String statusCode = statusCode(record);
          if(statusCode == null) {
            statusCode = "_not_found_";
          }
          // check the status code against the include and exclude
          // patterns, if provided
          if(includeStatusCodes == null || includeStatusCodes.matcher(
                  statusCode).matches()) {
            if(excludeStatusCodes == null || !excludeStatusCodes.matcher(
                    statusCode).matches()) {
              // check the mime type, if required
              if(mimeTypes == null || interestingMimeType(record)) {
                // found a good document
                Map<String, String> attrs = new SimpleArrayMap<>(
                        new String[] {ArchiveInputHandler.RECORD_OFFSET_ATTR,
                                ArchiveInputHandler.RECORD_LENGTH_ATTR,
                                ArchiveInputHandler.RECORD_POSITION_ATTR},
                        new String[] {Long.toString(record.getHeader().getOffset()),
                                Long.toString(recordLength),
                                Long.toString(inputSequence)}
                );
                next = new DocumentID(record.getHeader().getUrl(), attrs);
                logger.debug("Found valid ID {}", next);
                return;
              } else {
                logger.debug("Not an interesting mime type");
              }
            } else {
              logger.debug("Status code {} matched by excludes", statusCode);
            }
          } else {
            logger.debug("Status code {} not matched by includes", statusCode);
          }
        }
      } finally {
        // increment the current sequence pointer for next time
        inputSequence++;
      }
    }
    // if we fell off the end, there are no more records
    next = null;
    if(archiveIterator != null) {
      try {
        reader.close();
      }
      catch(IOException e) {
        logger.warn("Could not close reader for " + srcFile, e);
      }
    }
  }

  /**
   * Check whether the mime type of the given record is "interesting", i.e. if
   * any of the {@link #mimeTypes} is a prefix of this record's type.
   * @param record
   * @return
   */
  protected boolean interestingMimeType(ArchiveRecord record) {
    String mimeType = mimeType(record);
    for(String targetType : mimeTypes) {
      if(mimeType.startsWith(targetType)) {
        return true;
      }
    }
    return false;
  }

  protected abstract String mimeType(ArchiveRecord record);

  protected abstract String statusCode(ArchiveRecord record);

  protected abstract long findContentBegin(ArchiveRecord record);

  protected abstract ArchiveRecord nextRecord(Iterator<ArchiveRecord> it);

}
